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
import android.provider.Settings
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

class FilterAccessibilityService : AccessibilityService() {
    private lateinit var config: SharedPreferences
    private var dynamicOptimize: DynamicOptimize = DynamicOptimize()
    private var lightSensorWatcher: LightSensorWatcher? = null
    private var handler = Handler()
    private var isLandscape = false
    private val lightHistory = LinkedList<LightHistory>()
    private lateinit var filterViewManager: FilterViewManager

    private var notificationHelper: NotificationHelper? = null
    private var brightnessControlerBroadcast: BrightnessControlerBroadcast? = null

    private var filterBrightness = 0 // 当前由滤镜控制的屏幕亮度
    private var isFirstScreenCap = true

    private var displayWidth:Int = 0;
    private var displayHeight:Int = 0;

    // 当前手机屏幕是否处于开启状态
    private var screenOn = true
    // 屏幕解锁事件监听
    private var receiverLock: ReceiverLock? = null

    // 是否是首次更新屏幕滤镜
    private var isFirstUpdate = false

    // 是否正在使用自动亮度调节
    private var isAutoBrightness = false

    // 计算平滑亮度的定时器
    private var smoothLightTimer: Timer? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onServiceConnected() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            brightnessControlerBroadcast = BrightnessControlerBroadcast()
        }
        config = getSharedPreferences(SpfConfig.FILTER_SPF, Context.MODE_PRIVATE)
        notificationHelper = NotificationHelper(this)

        if (GlobalStatus.sampleData == null) {
            GlobalStatus.sampleData = SampleData(applicationContext)
        }

        filterViewManager = FilterViewManager(this)

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
            screenOn = ScreenState(this).isScreenOn()
        }

        GlobalStatus.filterOpen = Runnable {
            filterOpen()
        }
        GlobalStatus.filterClose = Runnable {
            filterClose()
        }

        receiverLock = ReceiverLock.autoRegister(this, ScreenEventHandler({
            // 为啥已经监听到息屏了还要手动判断屏幕状态呢？
            // 因为，丢特么的的，在某些手机上，
            // 使用电源键快速息屏，并用指纹立即解锁（息屏时间不超过5秒）的话，息屏广播会在解锁广播之后发送
            if (!ScreenState(this).isScreenOn()) {
                screenOn = false
                if (config.getBoolean(SpfConfig.SCREEN_OFF_CLOSE, SpfConfig.SCREEN_OFF_CLOSE_DEFAULT)) {
                    filterClose()
                }
            }
        }, {
            screenOn = true
            if ((!GlobalStatus.filterEnabled) && config.getBoolean(SpfConfig.FILTER_AUTO_START, SpfConfig.FILTER_AUTO_START_DEFAULT)) {
                filterOpen()
            } else if (GlobalStatus.filterEnabled && !config.getBoolean(SpfConfig.SCREEN_OFF_CLOSE, SpfConfig.SCREEN_OFF_CLOSE_DEFAULT)) {
                filterRefresh()
                lightHistory.clear()
            }
        }))

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
            override fun onBrightnessChange(brightness: Int) { onBrightnessChanged(brightness) }
            override fun onLuxChange(currentLux: Float) { onLuxChanged(currentLux) }
        })

        // 获取分辨率大小
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val point = Point()
        wm.getDefaultDisplay().getRealSize(point);

        displayHeight = point.y
        displayWidth = point.x


        if (config.getBoolean(SpfConfig.FILTER_AUTO_START, SpfConfig.FILTER_AUTO_START_DEFAULT)) {
            filterOpen()
        }

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
        synchronized(GlobalStatus.filterEnabled) {
            isFirstUpdate = true

            filterViewManager.open()

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                screenOn = ScreenState(this).isScreenOn()
            }

            lightSensorWatcher?.startSystemConfigWatcher()

            GlobalStatus.filterRefresh = Runnable { filterRefresh() }

            GlobalStatus.screenCap = Runnable { onScreenCap() }

            GlobalStatus.filterEnabled = true

            brightnessControlerBroadcast?.run {
                registerReceiver(this, IntentFilter(getString(R.string.action_minus)))
                registerReceiver(this, IntentFilter(getString(R.string.action_plus)))
                registerReceiver(this, IntentFilter(getString(R.string.action_auto)))
                registerReceiver(this, IntentFilter(getString(R.string.action_manual)))
            }
            updateNotification()
        }
    }

    // 关闭滤镜
    private fun filterClose() {
        synchronized(GlobalStatus.filterEnabled) {
            try {
                val config = FilterViewConfig()
                config.smoothChange = false
                config.filterBrightness = 1

                filterViewManager.updateFilterByConfig(config)

                lightSensorWatcher?.stopSystemConfigWatcher()

                filterViewManager.close()

                GlobalStatus.filterRefresh = null
                GlobalStatus.filterEnabled = false
                lightHistory.clear()
                stopSmoothLightTimer()
                filterBrightness = -1
            } catch (ex: Exception) {
            }

            notificationHelper?.cancelNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                brightnessControlerBroadcast?.run {
                    try {
                        unregisterReceiver(this)
                    } catch (ex: java.lang.Exception) {
                    }
                }
            }
        }
    }

    // 刷新滤镜
    private fun filterRefresh() {
        if (GlobalStatus.filterEnabled) {
            if (isAutoBrightness) {
                lightHistory.lastOrNull()?.run {
                    updateFilterByLux(this.lux)
                }
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
            }, 4000)
        }
        handler.postDelayed({
            triggerScreenCap()
        }, 1050)
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

        val config = GlobalStatus.sampleData!!.getFilterConfigByRatio(ratio)
        config.smoothChange = false
        updateFilterByConfig(config)

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

        lightHistory.add(history)

        if (isFirstUpdate) {
            updateFilterByLux(currentLux)
        }

        startSmoothLightTimer()
    }

    /**
     * 屏幕配置改变（旋转、分辨率更改、DPI更改等）
     */
    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        if (newConfig != null) {
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
        }
    }

    /**
     * 启动平滑亮度定时任务
     */
    private fun startSmoothLightTimer() {
        if (smoothLightTimer == null) {
            smoothLightTimer = Timer()
            smoothLightTimer!!.schedule(object : TimerTask() {
                private var upOnly = false;
                private var lastValue = -1f
                override fun run() {
                    handler.post {
                        lastValue = smoothLightTimerTick(upOnly, lastValue)
                    }
                    upOnly = !upOnly
                }
            }, 4500, 3000)
        }
    }

    /**
     * 更新滤镜 使用最近的光线传感器样本平均值
     */
    private fun smoothLightTimerTick(upOnly: Boolean, lastValue: Float): Float {
        var targetLux: Float = -1F
        try {
            if (lightHistory.size > 0) {
                if (config.getBoolean(SpfConfig.SMOOTH_ADJUSTMENT, SpfConfig.SMOOTH_ADJUSTMENT_DEFAULT)) {
                    val currentTime = System.currentTimeMillis()
                    val result = lightHistory.filter { (currentTime - it.time) < 11000 }

                    var avgLux: Float
                    val lastSample = lightHistory.last().lux
                    if (result.isNotEmpty()) {
                        var total: Double = 0.toDouble()
                        for (history in result) {
                            total += history.lux
                        }
                        avgLux = (total / result.size).toFloat()
                        // 如果侦测到大幅的环境光变化，积极的提高亮度
                        if (lastSample > (avgLux + 5)) {
                            avgLux = lastSample
                            // 清空样本数据
                            synchronized(lightHistory) {
                                lightHistory.clear()
                            }
                        }
                    } else {
                        avgLux = lastSample
                    }
                    targetLux = avgLux
                } else {
                    targetLux = lightHistory.last().lux
                }
                if (lastValue < targetLux || !upOnly) {
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
    }

    private fun updateFilterByLux(lux: Float, smoothChange: Boolean = !isFirstUpdate) {
        val luxValue = if (lux < 0) 0f else lux
        val optimizedLux = dynamicOptimize.luxOptimization(luxValue)

        // 亮度微调
        var staticOffset = config.getInt(SpfConfig.BRIGTHNESS_OFFSET, SpfConfig.BRIGTHNESS_OFFSET_DEFAULT) / 100.0
        var offsetPractical = 0.toDouble()

        // 横屏
        if (isLandscape && config.getBoolean(SpfConfig.LANDSCAPE_OPTIMIZE, SpfConfig.LANDSCAPE_OPTIMIZE_DEFAULT)) {
            staticOffset += 0.1
        } else {
            offsetPractical += dynamicOptimize.brightnessOptimization(luxValue, GlobalStatus.sampleData!!.getScreentMinLight())
        }

        val filterViewConfig = GlobalStatus.sampleData!!.getFilterConfig(optimizedLux, staticOffset, offsetPractical)
        var alpha = filterViewConfig.filterAlpha

        if (alpha > FilterViewConfig.FILTER_MAX_ALPHA) {
            alpha = FilterViewConfig.FILTER_MAX_ALPHA
        } else if (alpha < 0) {
            alpha = 0
        }
        filterViewConfig.filterAlpha = alpha
        filterViewConfig.smoothChange = smoothChange

        updateFilterByConfig(filterViewConfig)
    }

    private fun updateFilterByConfig(filterViewConfig: FilterViewConfig) {
        // 如果开启了息屏暂停滤镜更新功能
        if (!screenOn && config.getBoolean(SpfConfig.SCREEN_OFF_PAUSE, SpfConfig.SCREEN_OFF_PAUSE_DEFAULT)) {
            return
        }
        filterViewManager.updateFilterByConfig(filterViewConfig)
        isFirstUpdate = false
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
