package com.omarea.filter.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Point
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.omarea.filter.*
import com.omarea.filter.broadcast.BrightnessControlerBroadcast
import com.omarea.filter.common.KeepShellPublic
import com.omarea.filter.common.NotificationHelper
import com.omarea.filter.light.LightHandler
import com.omarea.filter.light.LightHistory
import com.omarea.filter.light.LightSensorWatcher
import java.io.File
import java.util.*

class FilterAccessibilityService : AccessibilityService(), WindowAnalyzer.Companion.IWindowAnalyzerResult {
    private lateinit var config: SharedPreferences
    private var dynamicOptimize: DynamicOptimize = DynamicOptimize()
    private var lightSensorWatcher: LightSensorWatcher? = null
    private var handler = Handler(Looper.getMainLooper())
    private var isLandscape = false
    private val lightHistory = LinkedList<LightHistory>()
    private lateinit var filterViewManager: FilterViewManager

    private var notificationHelper: NotificationHelper? = null
    private var brightnessControlerBroadcast: BrightnessControlerBroadcast? = null

    private var filterBrightness = 0 // 当前由滤镜控制的屏幕亮度
    private var isFirstScreenCap = true

    private var displayWidth: Int = 0;
    private var displayHeight: Int = 0;

    // 当前手机屏幕是否处于开启状态
    private var screenOn = true

    // 屏幕解锁事件监听
    private var receiverLock: ReceiverLock? = null

    // 是否正在使用自动亮度调节
    private var isAutoBrightness = false

    // 计算平滑亮度的定时器
    private var smoothLightTimer: Timer? = null

    // 窗口层次分析器
    private lateinit var windowAnalyzer: WindowAnalyzer

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onServiceConnected() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            brightnessControlerBroadcast = BrightnessControlerBroadcast()
        }
        config = getSharedPreferences(SpfConfig.FILTER_SPF, Context.MODE_PRIVATE)
        notificationHelper = NotificationHelper(this)
        updateNotification()
        windowAnalyzer = WindowAnalyzer(this)

        if (GlobalStatus.sampleData == null) {
            GlobalStatus.sampleData = SampleData(applicationContext)
        }

        filterViewManager = FilterViewManager(this)

        screenOn = ScreenState(this).isScreenOn()

        GlobalStatus.filterOpen = Runnable {
            filterOpen()
        }
        GlobalStatus.filterClose = Runnable {
            filterClose()
        }
        GlobalStatus.filterManualUpdate = Runnable {
            filterViewManager.filterManualUpdate()
        }

        receiverLock = ReceiverLock.autoRegister(this, ScreenEventHandler({
            // 为啥已经监听到息屏了还要手动判断屏幕状态呢？
            // 因为，丢特么的的，在某些手机上，
            // 使用电源键快速息屏，并用指纹立即解锁（息屏时间不超过5秒）的话，息屏广播会在解锁广播之后发送
            if (!ScreenState(this).isScreenOn()) {
                screenOn = false
                if (config.getBoolean(SpfConfig.SCREEN_OFF_CLOSE, SpfConfig.SCREEN_OFF_CLOSE_DEFAULT)) {
                    if (GlobalStatus.filterEnabled) {
                        // filterClose()
                        // FIXME:效果非常好，但是有个致命的问题，那就是如果正在淡出滤镜期间，屏幕被点亮...
                        fadeOut()
                    }
                }
            }
        }, {
            if(screenOn == true) {
                return@ScreenEventHandler
            }
            screenOn = true
            if (
                    (!GlobalStatus.filterEnabled) &&
                    config.getBoolean(SpfConfig.FILTER_AUTO_START, SpfConfig.FILTER_AUTO_START_DEFAULT)
            ) {
                lightHistory.clear()
                filterOpen()
            } else if (GlobalStatus.filterEnabled) {
                synchronized(lightHistory) {
                    val history = lightHistory.lastOrNull()
                    if (history != null) {
                        lightHistory.clear()
                        lightHistory.add(history)
                    }
                }
                filterRefresh()
            }
        }))

        brightnessControlerBroadcast?.run {
            registerReceiver(this, IntentFilter(getString(R.string.action_minus)))
            registerReceiver(this, IntentFilter(getString(R.string.action_plus)))
            registerReceiver(this, IntentFilter(getString(R.string.action_auto)))
            registerReceiver(this, IntentFilter(getString(R.string.action_manual)))
            registerReceiver(this, IntentFilter(getString(R.string.action_on)))
            registerReceiver(this, IntentFilter(getString(R.string.action_off)))
        }

        lightSensorWatcher = LightSensorWatcher(this, object : LightHandler {
            override fun onModeChange(auto: Boolean) {
                isAutoBrightness = auto
                if (auto) {
                    startSmoothLightTimer()
                } else {
                    stopSmoothLightTimer()
                }
                updateNotification()
            }

            override fun onBrightnessChange(brightness: Int) {
                onBrightnessChanged(brightness)
            }

            override fun onLuxChange(currentLux: Float) {
                onLuxChanged(currentLux)
            }
        })

        // 获取分辨率大小
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val point = Point()
        wm.defaultDisplay.getRealSize(point);

        displayHeight = point.y
        displayWidth = point.x


        if (config.getBoolean(SpfConfig.FILTER_AUTO_START, SpfConfig.FILTER_AUTO_START_DEFAULT)) {
            filterOpen()
        }

        GlobalStatus.screenCap = Runnable { onScreenCap() }

        super.onServiceConnected()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        GlobalStatus.filterOpen = null
        GlobalStatus.filterClose = null
        filterClose()

        if (receiverLock != null) {
            ReceiverLock.unRegister(this)
            receiverLock = null
        }

        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    // 打开滤镜
    private fun filterOpen() {
        filterViewManager.open()

        screenOn = ScreenState(this).isScreenOn()

        lightSensorWatcher?.startSystemConfigWatcher()

        GlobalStatus.filterRefresh = Runnable { filterRefresh() }

        GlobalStatus.filterEnabled = true

        updateNotification()
        Log.d("Filter", "Filter - ON")
    }

    // 关闭滤镜
    private fun filterClose() {
        try {
            lightSensorWatcher?.stopSystemConfigWatcher()

            filterViewManager.close()

            GlobalStatus.filterRefresh = null
            GlobalStatus.filterEnabled = false
            lightHistory.clear()
            stopSmoothLightTimer()
            filterBrightness = -1
            Log.d("Filter", "Filter - OFF")
        } catch (ex: Exception) {
            Log.d("Filter", "Filter - OFF" + ex.message)
        }

        updateNotification()
    }

    // 淡出滤镜
    private fun fadeOut() {
        filterViewManager.pause {
            this.filterClose()
        }
    }

    // 刷新滤镜
    private fun filterRefresh() {
        if (GlobalStatus.filterEnabled) {
            filterViewManager.stopUpdate()
            if (isAutoBrightness) {
                smoothLightTimerTick(false, -1f)
            } else {
                onBrightnessChanged(Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS))
            }
            updateNotification()
        }
    }

    /**
     * 截屏
     */
    private fun onScreenCap() {
        if (GlobalStatus.filterEnabled) {
            filterViewManager.pause()
            handler.postDelayed({
                filterViewManager.resume()
            }, 5500)
        }
        handler.postDelayed({
            triggerScreenCap()
        }, 1900)
    }

    /**
     * 触发屏幕截图
     */
    private fun triggerScreenCap() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            this.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        } else {
            if (isFirstScreenCap) {
                Toast.makeText(this, "抱歉，Android 9.0以前的系统，需要ROOT权限才能调用截屏~", Toast.LENGTH_LONG).show()
                isFirstScreenCap = false
            }
            val output = Environment.getExternalStorageDirectory().absolutePath + "/Pictures/" + System.currentTimeMillis() + ".png"
            if (KeepShellPublic.doCmdSync("screencap -p \"$output\"") != "error") {
                if (File(output).exists()) {
                    Toast.makeText(this, "截图保存至 \n$output", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "未能通过ROOT权限调用截图", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "未能通过ROOT权限调用截图", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 系统亮度改变时触发
     */
    private fun onBrightnessChanged(brightness: Int) {
        // 系统最大亮度值
        var maxLight = config.getInt(SpfConfig.SCREENT_MAX_LIGHT, SpfConfig.SCREENT_MAX_LIGHT_DEFAULT)
        // 部分设备最大亮度不符合谷歌规定的1-255，会出现2047 4096等超大数值，因此要自适应一下
        if (brightness > maxLight) {
            config.edit().putInt(SpfConfig.SCREENT_MAX_LIGHT, brightness).apply()
            maxLight = brightness
        }

        // 当前亮度比率
        val ratio = (brightness.toFloat() / maxLight)

        updateFilterToBrightness((ratio * FilterViewConfig.FILTER_BRIGHTNESS_MAX).toInt(), false)

        updateNotification()
    }

    /**
     * 周围光线发生变化时触发
     */
    private fun onLuxChanged(currentLux: Float) {
        val history = LightHistory()
        history.run {
            time = System.currentTimeMillis()
            lux = currentLux
        }

        if (lightHistory.size > 100) {
            lightHistory.removeFirst()
        }

        if (lightHistory.isEmpty()) {
            updateFilterByLux(currentLux)
        }

        lightHistory.add(history)

        startSmoothLightTimer()
    }

    /**
     * 屏幕配置改变（旋转、分辨率更改、DPI更改等）
     */
    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        if (newConfig != null) {
            val currentLandscape = isLandscape
            if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
                isLandscape = false
            } else if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                isLandscape = true
            }

            // 获取分辨率大小
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val point = Point()
            wm.getDefaultDisplay().getRealSize(point);

            // 如果分辨率改变，则需要重新调整滤镜，以便完全遮盖屏幕了
            if ((displayHeight != point.y && displayWidth != point.y) || (displayHeight != point.x && displayWidth != point.x)) {
                displayHeight = point.y
                displayWidth = point.x
                if (GlobalStatus.filterEnabled) {
                    filterViewManager.updateSize()
                }
            }

            // 判断是否发生了屏幕旋转
            if (currentLandscape != isLandscape) {
                if (isLandscape) {
                    if (
                            config.getBoolean(SpfConfig.LANDSCAPE_OPTIMIZE, SpfConfig.LANDSCAPE_OPTIMIZE_DEFAULT) &&
                            config.getBoolean(SpfConfig.FILTER_AUTO_START, SpfConfig.FILTER_AUTO_START_DEFAULT)
                    ) {
                        windowAnalyzer.analysis(null, this)
                    }
                } else if (videoPlaying && config.getBoolean(SpfConfig.LANDSCAPE_OPTIMIZE, SpfConfig.LANDSCAPE_OPTIMIZE_DEFAULT)) {
                    onViedPlayerLeave()
                }
            }
        }
    }

    private val videoApps = arrayListOf<String>(
            "com.bilibili.app.in",
            "tv.danmaku.bili",
            "com.qiyi.video",
            "com.qiyi.video.sdkplayer",
            "com.tencent.qqlive",
            "com.mxtech.videoplayer.pro",
            "com.mxtech.videoplayer.ad",
            "com.netease.cloudmusic")

    // 是否在视频播放中
    private var videoPlaying = false

    // 处理窗口层次分析结果
    override fun onWindowAnalyzerResult(packageName: String) {
        handler.post {
            if (videoApps.contains(packageName)) {
                Log.d("Filter", "LANDSCAPE_OPTIMIZE " + packageName + " - isVideoApp")
                onViedPlayerEnter()
            } else {
                onViedPlayerLeave()
                Log.d("Filter", "LANDSCAPE_OPTIMIZE " + packageName + " Unknown")
            }
        }
    }

    private fun onViedPlayerEnter() {
        fadeOut()
        videoPlaying = true
    }

    private fun onViedPlayerLeave() {
        if (videoPlaying) {
            if (
                    config.getBoolean(SpfConfig.LANDSCAPE_OPTIMIZE, SpfConfig.LANDSCAPE_OPTIMIZE_DEFAULT) &&
                    config.getBoolean(SpfConfig.FILTER_AUTO_START, SpfConfig.FILTER_AUTO_START_DEFAULT)
            ) {
                filterOpen()
            }
            videoPlaying = false
        }
    }

    /**
     * 启动平滑亮度定时任务
     */
    private fun startSmoothLightTimer() {
        if (smoothLightTimer == null) {
            smoothLightTimer = Timer()
            smoothLightTimer!!.schedule(object : TimerTask() {
                // 为了让从滤镜能在从室内走到室外时快速响应及时的提高亮度
                // 同时又不至于因为短时间的手指遮挡屏幕导致屏幕突然变暗
                // 这里加一个周期循环，0:正常调整滤镜亮度，1:只响应环境光线大幅提升
                private var periodNum = 0

                private var lastValue = -1f
                override fun run() {
                    if (screenOn) {
                        handler.post {
                            lastValue = smoothLightTimerTick(periodNum != 0, lastValue,if(periodNum != 0) 4000 else 8000) // 取最近10秒内的数据)
                        }
                    }
                    periodNum += 1
                    periodNum %= 2
                }
            }, 500, 4000)
        }
    }

    /**
     * 更新滤镜 使用最近的光线传感器样本平均值
     */
    private fun smoothLightTimerTick(upOnly: Boolean, lastValue: Float, range: Long = 4000L): Float {
        var targetLux: Float = -1F
        try {
            if (lightHistory.size > 0) {
                // 计算一段时间内的绝对平均亮度(环境光)
                val currentTime = System.currentTimeMillis()
                val copy = LinkedList<LightHistory>().apply {
                    addAll(lightHistory)

                    // 虚拟一个样本，表示光线传感器最后一次上报数值，并数值保持至今
                    // 这样就可以让样本数据成为可以闭合的区间
                    val virtualSample = lightHistory.last.apply {
                        time = currentTime
                    }
                    add(virtualSample)
                }

                var totalLux = 0.0
                val rangeStart = currentTime - range
                var lastHistory: LightHistory? = null
                for (history in copy) {
                    if (history.time > rangeStart) {
                        if (lastHistory != null) {
                            val startTime = if (lastHistory.time < rangeStart) rangeStart else lastHistory.time
                            val endTime = history.time
                            val lux = lastHistory.lux
                            totalLux += (lux * (endTime - startTime))
                        }
                    }
                    lastHistory = history
                }
                val absAvgLux = (if (totalLux > 0.0) {
                    (totalLux / range).toFloat()
                } else {
                    copy.last.lux
                })

                targetLux = absAvgLux

                if (!upOnly || (targetLux - lastValue > 200)) {
                    GlobalStatus.avgLux = targetLux
                    updateFilterByLux(targetLux)
                }
            }
        } catch (ex: Exception) {
            handler.post {
                Toast.makeText(this, "更新滤镜出现异常", Toast.LENGTH_SHORT).show()
            }
        }
        return targetLux
    }

    private fun stopSmoothLightTimer() {
        if (smoothLightTimer != null) {
            smoothLightTimer!!.cancel()
            smoothLightTimer = null
        }

        GlobalStatus.avgLux = -1f
    }

    private fun updateFilterByLux(lux: Float) {
        dynamicOptimize.optimizedBrightness(lux, config)?.run {
            updateFilterToBrightness(this, true)
        }
    }

    private fun updateFilterToBrightness(brightness: Int, smooth: Boolean) {
        filterViewManager.setBrightness(brightness, smooth)
    }

    // 更新通知
    private fun updateNotification() {
        if (config.getBoolean(SpfConfig.BRIGHTNESS_CONTROLLER, SpfConfig.BRIGHTNESS_CONTROLLER_DEFAULT)) {
            notificationHelper?.updateNotification(isAutoBrightness)
        } else {
            notificationHelper?.cancelNotification()
        }
    }
}
