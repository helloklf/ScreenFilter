package com.omarea.filter

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.provider.Settings
import android.util.Log
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import java.lang.Exception
import java.util.*

class FilterAccessibilityService : AccessibilityService() {
    private lateinit var config: SharedPreferences
    private var lightSensorManager: LightSensorManager = LightSensorManager.getInstance()
    private var handler = Handler()
    private var isLandscapf = false
    private val lightHistory  = Stack<LightHistory>()

    private var filterBrightness = 0 // 当前由滤镜控制的屏幕亮度（0-100）

    private lateinit var mWindowManager:WindowManager
    private lateinit var display: Display

    // 悬浮窗
    private var popupView: View? = null
    // 滤镜控件
    private var filterView: FilterView? = null
    // 计算平滑亮度的定时器
    private var smoothLightTimer: Timer? = null

    private var systemBrightness = 0 // 当前由系统控制的亮度
    private var systemBrightnessMode = Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC // Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
    private var systemBrightnessObserver: ContentObserver = object: ContentObserver(Handler()) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            updateFilterByBrightness()
        }
    }
    private var systemBrightnessModeObserver = object: ContentObserver(Handler()) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            val currentMode =  Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE)
            if (systemBrightnessMode != currentMode) {
                systemBrightnessMode = currentMode
                if (currentMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                    // 切换到自动模式
                    updateFilterSmooth(filterView!!)
                } else if (currentMode == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) {
                    // 切换到手动模式
                    updateFilterByBrightness()
                }
            }
        }
    }

    /**
     * 根据当前系统亮度调整滤镜浓度
     */
    private fun updateFilterByBrightness() {
        // 获取当前系统亮度
        systemBrightness = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS)
        // 系统最大亮度值
        val maxLight = config.getInt(SpfConfig.SCREENT_MAX_LIGHT, SpfConfig.SCREENT_MAX_LIGHT_DEFAULT)
        // 当前亮度比率
        val ratio = (systemBrightness.toFloat() / maxLight)

        // 如果不是自动亮度模式（直接调整滤镜）
        if (systemBrightnessMode != Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
            if (filterView != null) {
                // TODO:系统亮度转换为滤镜浓度
                // TODO:如果当前滤镜控制的亮度不是100%，应该怎么处理？
                var alpha = 255 - (ratio * 255).toInt()
                if (alpha > 245) {
                    alpha = 245
                } else if (alpha < 0) {
                    alpha = 0
                }
                GlobalStatus.currentFilterAlpah = alpha
                val layoutParams =  popupView!!.layoutParams as WindowManager.LayoutParams?
                if (layoutParams != null) {
                    GlobalStatus.currentSystemBrightness = (layoutParams.screenBrightness * 100).toInt()
                }
                filterView!!.setFilterColor(alpha, 0, 0, 0, false)
            }
        } else { // 如果是自动亮度（微调offset）
            config.edit().putInt(SpfConfig.FILTER_LEVEL_OFFSET, (ratio * 100 - 50).toInt()).apply()
            updateFilterSmooth(filterView!!)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onServiceConnected() {
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

    override fun onInterrupt() {
        GlobalStatus.filterOpen = null
        GlobalStatus.filterClose = null
        GlobalStatus.filterRefresh = null
        filterClose()
    }

    private fun filterClose() {
        try {
            if (popupView != null) {
                val mWindowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                mWindowManager.removeView(popupView)
                popupView = null
                lightSensorManager.stop()
            }

            GlobalStatus.filterRefresh = null
            GlobalStatus.filterEnabled = false
            lightHistory.empty()
            stopSmoothLightTimer()
            getContentResolver().unregisterContentObserver(systemBrightnessObserver)
            getContentResolver().unregisterContentObserver(systemBrightnessModeObserver)
        } catch (ex: Exception) {
            Toast.makeText(this, "关闭滤镜服务时出现异常\n" + ex.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun filterOpen() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(applicationContext)) {
            Toast.makeText(this, R.string.overlays_required, Toast.LENGTH_LONG).show()
            return
        }
        if (popupView != null) {
            filterClose()
        }

        mWindowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        display = mWindowManager.getDefaultDisplay()

        val params = WindowManager.LayoutParams()

        // 类型
        params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        // 设置window type
        //params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {//6.0+
            params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY // TYPE_APPLICATION_OVERLAY
        } else {
            params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        params.gravity = Gravity.LEFT or Gravity.TOP
        params.format = PixelFormat.TRANSLUCENT
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        // or WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or WindowManager.LayoutParams.FLAG_DIM_BEHIND
        // 无法覆盖状态栏和导航栏图标
        // params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

        val p = Point()
        display.getRealSize(p)
        params.width = p.y // p.x
        params.height = p.y

        // 不知道是不是真的有效
        params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL

        popupView = LayoutInflater.from(this).inflate(R.layout.filter, null)
        mWindowManager.addView(popupView, params)
        filterView = popupView!!.findViewById(R.id.filter_view)

        lightSensorManager.start(this, object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            }

            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && event.values.size > 0) {
                    // 获取光线强度
                    val lux = event.values[0].toInt()
                    GlobalStatus.currentLux = lux
                    val history = LightHistory()
                    history.time = System.currentTimeMillis()
                    history.lux = lux

                    if (lightHistory.size > 10) {
                        lightHistory.pop()
                    }
                    lightHistory.push(history)

                    // 自动亮度模式下才根据环境光自动调整滤镜强度
                    if (systemBrightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                        updateFilter(lux, filterView!!)
                    }
                }
            }
        })

        GlobalStatus.filterRefresh = Runnable {
            updateFilter(GlobalStatus.currentLux, filterView!!)
        }

        // 最高亮度锁定
        // val lp = getWindow().getAttributes()
        // lp.screenBrightness = 1.0f
        // getWindow().setAttributes(lp)

        filterBrightness = 100

        // 监控屏幕亮度
        getContentResolver().registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), true, systemBrightnessObserver)
        getContentResolver().registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE), true, systemBrightnessModeObserver)

        // 获取屏幕亮度
        systemBrightness = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS)
        systemBrightnessMode =  Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE)

        // 如果是手动亮度
        if (systemBrightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) {
            updateFilterByBrightness()
        }

        GlobalStatus.filterEnabled = true
    }

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
    private fun startSmoothLightTimer(filterView:FilterView) {
        if (smoothLightTimer == null) {
            smoothLightTimer = Timer()
            smoothLightTimer!!.schedule(object : TimerTask() {
                override fun run() {
                    updateFilterSmooth(filterView)
                }
            }, 2000, 2000)
        }
    }

    /**
     * 更新滤镜 使用最近的光线传感器样本平均值
     */
    private fun updateFilterSmooth(filterView: FilterView) {
        val currentTime = System.currentTimeMillis()
        val historys = lightHistory.filter {
            currentTime - it.time < 100001
        }
        if (historys.size > 0 && this.filterView != null) {
            var total = 0
            for (history in historys) {
                total += history.lux
            }
            handler.post {
                updateFilterNow(total / historys.size, filterView)
            }
        }
    }

    private fun stopSmoothLightTimer() {
        if (smoothLightTimer != null) {
            smoothLightTimer!!.cancel()
            smoothLightTimer = null
        }
    }

    /**
     * 更新滤镜
     */
    private fun updateFilter(lux:Int, filterView:FilterView) {
        if (config.getBoolean(SpfConfig.SMOOTH_ADJUSTMENT, SpfConfig.SMOOTH_ADJUSTMENT_DEFAULT)) {
            startSmoothLightTimer(filterView)
            /*
            handler.postDelayed({
                if (GlobalStatus.currentLux == lux) {
                    updateFilterNow(lux, filterView)
                } else {
                    val currentTime = System.currentTimeMillis()
                    val historys = lightHistory.filter {
                        it.time - currentTime < 100001
                    }
                    if (historys.size > 0) {
                        var total = 0
                        for (history in historys) {
                            total += history.lux
                        }
                        updateFilterNow(total / historys.size, filterView)
                    }
                }
            }, 2000)
            */
        } else {
            stopSmoothLightTimer()
            updateFilterNow(lux, filterView)
        }
        GlobalStatus.currentSystemBrightness = filterBrightness
    }

    private fun updateFilterNow(lux:Int, filterView:FilterView) {
        val sample = GlobalStatus.sampleData!!.getVitualSample(lux)
        val offset = config.getInt(SpfConfig.FILTER_LEVEL_OFFSET, SpfConfig.FILTER_LEVEL_OFFSET_DEFAULT) / 100.0
        var alpha =  sample.filterAlpha + ((sample.filterAlpha * offset).toInt())
        if (isLandscapf) {
            alpha -= 25
        }
        if (alpha > 250) {
            alpha = 250
        } else if (alpha < 0) {
            alpha = 0
        }

        val filterDynamicColor = config.getInt(SpfConfig.FILTER_DYNAMIC_COLOR, SpfConfig.FILTER_DYNAMIC_COLOR_DEFAULT)

        if (isLandscapf) {
            filterView.setFilterColor(alpha, 0, 0, 0, true)
        } else {
            filterView.setFilterColor(alpha, filterDynamicColor, filterDynamicColor / 2, 0, true)
        }
        if (sample.systemBrightness != filterBrightness) {
            val layoutParams = popupView!!.layoutParams as WindowManager.LayoutParams?
            if (layoutParams != null) {
                layoutParams.screenBrightness = (sample.systemBrightness / 100.0).toFloat()
                // Toast.makeText(this, "设置亮度" + sample.filterBrightness,Toast.LENGTH_SHORT).show()
                mWindowManager.updateViewLayout(popupView, layoutParams)
            }
            filterBrightness = sample.systemBrightness
        }

        GlobalStatus.currentFilterAlpah = alpha

        GlobalStatus.currentSystemBrightness = filterBrightness

        // GlobalStatus.currentSystemBrightness = Utils.getSystemBrightness(applicationContext)
    }
}
