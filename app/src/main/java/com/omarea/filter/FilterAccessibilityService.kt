package com.omarea.filter

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.util.Log
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.omarea.filter.light.LightHandler
import com.omarea.filter.light.LightHistory
import com.omarea.filter.light.LightSensorWatcher
import java.io.File
import java.lang.Math.abs
import java.util.*


class FilterAccessibilityService : AccessibilityService() {
    private lateinit var config: SharedPreferences
    private var dynamicOptimize: DynamicOptimize = DynamicOptimize()
    private var lightSensorWatcher: LightSensorWatcher? = null
    private var handler = Handler()
    private var isLandscapf = false
    private val lightHistory = LinkedList<LightHistory>()

    private var filterBrightness = 0 // 当前由滤镜控制的屏幕亮度

    private lateinit var mWindowManager: WindowManager
    private lateinit var display: Display
    private var isFristScreenCap = true;

    // 当前手机屏幕是否处于开启状态
    private var screenOn = false;
    private var reciverLock: ReciverLock? = null

    // 悬浮窗
    private var popupView: View? = null
    // 滤镜控件
    private var filterView: FilterView? = null
    // 计算平滑亮度的定时器
    private var smoothLightTimer: Timer? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onServiceConnected() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            screenOn = ScreenState(this).isScreenOn()
        }
        if (reciverLock == null) {
            reciverLock = ReciverLock.autoRegister(this, ScreenEventHandler({
                screenOn = false
            }, {
                screenOn = true
                if (GlobalStatus.filterEnabled) {
                    if (lightHistory.size > 0) {
                        val last = lightHistory.last()
                        lightHistory.clear()
                        updateFilterNow(last.lux)
                    }
                }
            }))
        }

        if (GlobalStatus.sampleData == null) {
            GlobalStatus.sampleData = SampleData(applicationContext)
        }

        config = getSharedPreferences(SpfConfig.FILTER_SPF, Context.MODE_PRIVATE)
        GlobalStatus.filterOpen = Runnable {
            filterOpen()
        }
        GlobalStatus.filterClose = Runnable {
            filterClose()
        }

        if (config.getBoolean(SpfConfig.FILTER_AUTO_START, SpfConfig.FILTER_AUTO_START_DEFAULT)) {
            filterOpen()
        }
        super.onServiceConnected()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (reciverLock != null) {
            ReciverLock.unRegister(this)
            reciverLock = null
        }

        GlobalStatus.filterOpen = null
        GlobalStatus.filterClose = null
        GlobalStatus.filterRefresh = null
        filterClose()

        return super.onUnbind(intent)
    }

    override fun onInterrupt() {
    }

    private fun filterClose() {
        try {
            if (valueAnimator != null && valueAnimator!!.isRunning) {
                valueAnimator!!.cancel()
                valueAnimator = null
            }
            currentAlpha = 0

            if (popupView != null) {
                val mWindowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                mWindowManager.removeView(popupView)
                popupView = null
            }

            lightSensorWatcher?.stopSystemConfigWatcher()

            GlobalStatus.filterRefresh = null
            GlobalStatus.filterEnabled = false
            lightHistory.clear()
            stopSmoothLightTimer()
            filterBrightness = -1
        } catch (ex: Exception) {
            Toast.makeText(this, "关闭滤镜时出现异常\n" + ex.message, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * dp转换成px
     */
    private fun dp2px(context: Context, dpValue: Float): Int {
        val scale = context.resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }

    /**
     * 获取导航栏高度
     * @param context
     * @return
     */
    fun getNavBarHeight(): Int {
        val height = resources.getDimensionPixelSize(resources.getIdentifier("navigation_bar_height", "dimen", "android"))
        if (height < 1) {
            return dp2px(this, 55f)
        }
        return height
    }

    /**
     * 获取状态栏高度
     */
    fun getStatusHeight(): Int {
        var result = 0;
        val resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result
    }

    /**
     * 系统亮度改变时触发
     */
    private fun brightnessChangeHandle(brightness: Int) {
        // 系统最大亮度值
        var maxLight = config.getInt(SpfConfig.SCREENT_MAX_LIGHT, SpfConfig.SCREENT_MAX_LIGHT_DEFAULT)
        // 部分设备最大亮度不符合谷歌规定的1-255，会出现2047 4096等超大数值，因此要自适应一下
        if (brightness > maxLight) {
            config.edit().putInt(SpfConfig.SCREENT_MAX_LIGHT, brightness).apply()
            maxLight = brightness
        }
        // 当前亮度比率
        val ratio = (brightness.toFloat() / maxLight)

        if (filterView != null) {
            val config = GlobalStatus.sampleData!!.getFilterConfigByRatio(ratio)
            config.smoothChange = false
            updateFilterNow(config)
        }
    }

    /**
     * 周围光线发生变化时触发
     */
    private fun luxChangeHandle(currentLux: Float) {
        if (config.getBoolean(SpfConfig.SMOOTH_ADJUSTMENT, SpfConfig.SMOOTH_ADJUSTMENT_DEFAULT)) {
            val history = LightHistory()
            history.run {
                time = System.currentTimeMillis()
                lux = currentLux
            }

            if (lightHistory.size > 100) {
                lightHistory.removeFirst()
            }

            lightHistory.add(history)

            startSmoothLightTimer()
        } else {
            stopSmoothLightTimer()
            updateFilterNow(currentLux)
        }
    }

    private fun filterOpen() {
        if (popupView != null) {
            filterClose()
        }

        mWindowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        display = mWindowManager.getDefaultDisplay()

        val params = WindowManager.LayoutParams()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        params.gravity = Gravity.NO_GRAVITY
        params.format = PixelFormat.TRANSLUCENT
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        if (config.getBoolean(SpfConfig.HARDWARE_ACCELERATED, SpfConfig.HARDWARE_ACCELERATED_DEFAULT)) {
            params.flags = params.flags.or(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        }

        val p = Point()
        display.getRealSize(p)
        var maxSize = p.y
        if (p.x > maxSize) {
            maxSize = p.x
        }
        params.width = maxSize + getNavBarHeight() // p.x // 直接按屏幕最大宽度x最大宽度显示，避免屏幕旋转后盖不住全屏
        params.height = maxSize + getNavBarHeight()

        params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE // BRIGHTNESS_OVERRIDE_FULL

        popupView = LayoutInflater.from(this).inflate(R.layout.filter, null)
        mWindowManager.addView(popupView, params)
        filterView = popupView!!.findViewById(R.id.filter_view)

        if (lightSensorWatcher == null) {
            lightSensorWatcher = LightSensorWatcher(this, object : LightHandler {
                override fun onModeChange(auto: Boolean) {
                    if (auto) {
                        startSmoothLightTimer()
                        Toast.makeText(this@FilterAccessibilityService, "滤镜切换到“自动亮度”", Toast.LENGTH_LONG).show()
                    } else {
                        stopSmoothLightTimer()
                        Toast.makeText(this@FilterAccessibilityService, "滤镜切换到“手动亮度”", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onBrightnessChange(brightness: Int) {
                    brightnessChangeHandle(brightness)
                }

                override fun onLuxChange(currentLux: Float) {
                    luxChangeHandle(currentLux)
                }
            })
        }
        lightSensorWatcher?.startSystemConfigWatcher()

        GlobalStatus.filterRefresh = Runnable {
            updateFilterNow(GlobalStatus.currentLux)
        }

        GlobalStatus.screenCap = Runnable {
            screenCap()
        }

        GlobalStatus.filterEnabled = true
    }

    /**
     * 截屏
     */
    private fun screenCap() {
        if (GlobalStatus.filterEnabled) {
            filterClose()
            handler.postDelayed({
                filterOpen()
            }, 3000)
        }
        handler.postDelayed({
            triggerSsceenCap()
        }, 700)
    }

    /**
     * 触发屏幕截图
     */
    private fun triggerSsceenCap() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            this.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        } else {
            if (isFristScreenCap) {
                Toast.makeText(this, "抱歉，对于Android 9.0以前的系统，需要ROOT权限才能调用截屏~", Toast.LENGTH_LONG).show()
                isFristScreenCap = false
            }
            val output = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Pictures/" + System.currentTimeMillis() + ".png"
            if (KeepShellPublic.doCmdSync("screencap -p \"$output\"") != "error") {
                if (File(output).exists()) {
                    Toast.makeText(this, "截图保存至 \n" + output, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "未能通过ROOT权限调用截图", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "未能通过ROOT权限调用截图", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * 屏幕配置改变（旋转、分辨率更改、DPI更改等）
     */
    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        if (newConfig != null) {
            if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
                isLandscapf = false
            } else if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                isLandscapf = true
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
                override fun run() {
                    handler.post {
                        smoothLightTimerTick()
                    }
                }
            }, 4500, 6000)
        }
    }

    /**
     * 更新滤镜 使用最近的光线传感器样本平均值
     */
    private fun smoothLightTimerTick() {
        try {
            if (filterView != null) {
                val currentTime = System.currentTimeMillis()
                val historys = lightHistory.filter {
                    (currentTime - it.time) < 11000
                }
                if (historys.size > 0) {
                    var total: Double = 0.toDouble()
                    for (history in historys) {
                        total += history.lux
                    }
                    val avg = (total / historys.size).toFloat()
                    updateFilterNow(avg)
                } else if (lightHistory.size > 0) {
                    updateFilterNow(lightHistory.last().lux)
                }
            }
        } catch (ex: Exception) {
            handler.post {
                Toast.makeText(this, "更新滤镜出现异常", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopSmoothLightTimer() {
        if (smoothLightTimer != null) {
            smoothLightTimer!!.cancel()
            smoothLightTimer = null
        }
    }

    private fun updateFilterNow(lux: Float) {
        if (filterView != null) {
            var optimizedLux = lux

            // 亮度微调
            var staticOffset = config.getInt(SpfConfig.BRIGTHNESS_OFFSET, SpfConfig.BRIGTHNESS_OFFSET_DEFAULT) / 100.0

            // 横屏
            if (isLandscapf) {
                staticOffset += 0.1
            }

            var offsetPractical = 0.toDouble()
            // 场景优化
            optimizedLux += dynamicOptimize.luxOptimization(lux)
            offsetPractical += dynamicOptimize.brightnessOptimization(
                    lux,
                    GlobalStatus.sampleData!!.getScreentMinLight())

            val filterViewConfig = GlobalStatus.sampleData!!.getFilterConfig(optimizedLux, staticOffset, offsetPractical)
            var alpha = filterViewConfig.filterAlpha

            if (alpha > FilterViewConfig.FILTER_MAX_ALPHA) {
                alpha = FilterViewConfig.FILTER_MAX_ALPHA
            } else if (alpha < 0) {
                alpha = 0
            }
            filterViewConfig.filterAlpha = alpha

            updateFilterNow(filterViewConfig)
        }
    }

    private fun updateFilterNow(filterViewConfig: FilterViewConfig) {
        if (filterView != null) {
            if (!screenOn) {
                // 如果开启了息屏暂停滤镜更新功能
                if (config.getBoolean(SpfConfig.SCREEN_OFF_PAUSE, SpfConfig.SCREEN_OFF_PAUSE_DEFAULT)) {
                    return
                } else {
                    Log.e("updateFilterNow", "屏幕关闭时未暂停更新滤镜")
                }
            }

            if (filterViewConfig.smoothChange) {
                smoothUpdateFilter(filterViewConfig)
            } else {
                fastUpdateFilter(filterViewConfig)
            }

            GlobalStatus.currentFilterAlpah = filterViewConfig.filterAlpha
            GlobalStatus.currentFilterBrightness = filterBrightness
        }
    }

    private var currentAlpha: Int = 0
    private var valueAnimator: ValueAnimator? = null
    // TODO: 注意异常处理
    fun smoothUpdateFilter(filterViewConfig: FilterViewConfig) {
        if (valueAnimator != null && valueAnimator!!.isRunning) {
            valueAnimator!!.cancel()
            valueAnimator = null
        }
        val layoutParams = popupView!!.layoutParams as WindowManager.LayoutParams

        // 首次刷新滤镜 - 立即调整亮度，而不是延迟执行
        if (layoutParams.screenBrightness == WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
            fastUpdateFilter(filterViewConfig)
        } else {
            val perOld = this.currentAlpha
            val toAlpha = filterViewConfig.filterAlpha
            var alphaFrameCount = 0
            val alphaFrames = LinkedList<Int>()
            if (perOld != toAlpha) {
                val alphaDistance = toAlpha - perOld
                val absDistance = abs(alphaDistance)
                if (absDistance < 1) {
                    alphaFrameCount = 1
                } else if (absDistance > 60) {
                    alphaFrameCount = 60
                } else {
                    alphaFrameCount = absDistance
                }
                if (alphaFrameCount > 20 && !filterView!!.isHardwareAccelerated) {
                    alphaFrameCount = 20
                }

                val stepByStep = alphaDistance / alphaFrameCount.toFloat()
                for (frame in 1..alphaFrameCount - 1) {
                    alphaFrames.add(perOld + (frame * stepByStep).toInt())
                }
                alphaFrames.add(toAlpha)
            }

            val currentBrightness = (layoutParams.screenBrightness * 1000).toInt()
            val toBrightness = filterViewConfig.filterBrightness
            var brightnessFrameCount = 0
            val brightnessFrames = LinkedList<Int>()
            if (toBrightness != currentBrightness) {
                val brightnessDistance = toBrightness - currentBrightness
                val absDistance = abs(brightnessDistance)
                if (absDistance < 1) {
                    brightnessFrameCount = 1
                } else if (absDistance > 60) {
                    brightnessFrameCount = 60
                } else {
                    brightnessFrameCount = absDistance
                }
                if (brightnessFrameCount > 20 && !filterView!!.isHardwareAccelerated) {
                    brightnessFrameCount = 20
                }
                val stepByStep2 = brightnessDistance / brightnessFrameCount.toFloat()
                for (frame in 1..brightnessFrameCount - 1) {
                    brightnessFrames.add(currentBrightness + (frame * stepByStep2).toInt())
                }
                brightnessFrames.add(toBrightness)
            }

            val frames = if (alphaFrames.size > brightnessFrames.size) alphaFrames.size else brightnessFrames.size
            if (frames == 0) {
                return
            }
            var frame = 0

            valueAnimator = ValueAnimator.ofInt(frame, frames)
            valueAnimator!!.run {
                duration = 2000L
                addUpdateListener { animation ->
                    val value = animation.animatedValue as Int
                    if (value != frame) {
                        frame = value
                        val alpha = if(alphaFrames.isEmpty()) null else alphaFrames.removeFirst()
                        val brightness = if(brightnessFrames.isEmpty()) null else brightnessFrames.removeFirst()

                        if (alpha != null) {
                            currentAlpha = alpha
                            filterView?.run {
                                filterView?.setFilterColorNow(currentAlpha)
                            }
                        }

                        if (brightness != null) {
                            layoutParams.screenBrightness = brightness / 1000F
                            mWindowManager.updateViewLayout(popupView, layoutParams)
                            filterBrightness = brightness
                        }
                    }
                }
                start()
            }
        }
    }

    fun fastUpdateFilter(filterViewConfig: FilterViewConfig) {
        filterView!!.setFilterColorNow(filterViewConfig.filterAlpha)
        currentAlpha = filterViewConfig.filterAlpha

        if (filterViewConfig.filterBrightness != filterBrightness) {
            val layoutParams = popupView!!.layoutParams as WindowManager.LayoutParams
            if (layoutParams.screenBrightness == WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
                layoutParams.screenBrightness = filterViewConfig.getFilterBrightnessRatio()
                filterView!!.setFilterColorNow(filterViewConfig.filterAlpha)
                popupView!!.postDelayed({
                    mWindowManager.updateViewLayout(popupView, layoutParams)
                }, 100)
            } else {
                layoutParams.screenBrightness = filterViewConfig.getFilterBrightnessRatio()
                mWindowManager.updateViewLayout(popupView, layoutParams)
            }

            filterBrightness = filterViewConfig.filterBrightness
        }
    }
}
