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
import java.util.*

class FilterAccessibilityService : AccessibilityService() {
    private lateinit var config: SharedPreferences
    private var lightSensorManager: LightSensorManager = LightSensorManager.getInstance()
    private var handler = Handler()
    private var isLandscapf = false
    private val lightHistory = Stack<LightHistory>()

    private var filterBrightness = 0 // 当前由滤镜控制的屏幕亮度

    private lateinit var mWindowManager: WindowManager
    private lateinit var display: Display

    // 悬浮窗
    private var popupView: View? = null
    // 滤镜控件
    private var filterView: FilterView? = null
    // 计算平滑亮度的定时器
    private var smoothLightTimer: Timer? = null

    private var systemBrightness = 0 // 当前由系统控制的亮度
    private var systemBrightnessMode = Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC // Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
    private var systemBrightnessObserver: ContentObserver = object : ContentObserver(Handler()) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            updateFilterByBrightness()
        }
    }
    private var systemBrightnessModeObserver = object : ContentObserver(Handler()) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            switchAutoBrightness()
        }
    }

    /**
     * 根据系统设置，切换自动亮度调整功能
     */
    private fun switchAutoBrightness() {
        // 获取屏幕亮度
        systemBrightness = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS)
        // 获取屏幕亮度模式
        systemBrightnessMode = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE)

        if (systemBrightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
            // 切换到自动模式
            updateFilterSmooth(filterView!!)
            startSmoothLightTimer(filterView!!)
        } else if (systemBrightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) {
            // 切换到手动模式
            updateFilterByBrightness()
            stopSmoothLightTimer()
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
                val config = GlobalStatus.sampleData!!.getFilterConfigByRatio(ratio)
                updateFilterNow(config, filterView!!)
            }
        } else { // 如果是自动亮度
            Toast.makeText(this, "自动模式下，检测到屏幕亮度改变...", Toast.LENGTH_SHORT).show()
            // config.edit().putInt(SpfConfig.FILTER_LEVEL_OFFSET, ((1 - ratio) * 100 - 50).toInt()).apply() // 有时候自动亮度模式也会出现屏幕亮度变化...所以，不能跟随调整
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        params.gravity = Gravity.LEFT or Gravity.TOP
        params.format = PixelFormat.TRANSLUCENT
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        val p = Point()
        display.getRealSize(p)
        var maxSize = p.y
        if (p.x > maxSize) {
            maxSize = p.x
        }
        params.width = maxSize // p.x // 直接按屏幕最大宽度x最大宽度显示，避免屏幕旋转后盖不住全屏
        params.height = maxSize

        // 不知道是不是真的有效
        params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL

        popupView = LayoutInflater.from(this).inflate(R.layout.filter, null)
        mWindowManager.addView(popupView, params)
        filterView = popupView!!.findViewById(R.id.filter_view)

        lightSensorManager.start(this, object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            }

            // FIXME:已知部分手机，即使环境亮度没改变也可能会疯狂报告亮度 - 比如说三星S8，这可咋整呢？
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && event.values.size > 0) {
                    // 获取光线强度
                    val lux = event.values[0].toInt()
                    Log.d("环境光亮度", event.values[0].toString())
                    GlobalStatus.currentLux = lux
                    val history = LightHistory()
                    history.time = System.currentTimeMillis()
                    history.lux = lux

                    if (lightHistory.size > 100) {
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

        filterBrightness = FilterViewConfig.FILTER_BRIGHTNESS_MAX

        // 监控屏幕亮度
        getContentResolver().registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), true, systemBrightnessObserver)
        getContentResolver().registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE), true, systemBrightnessModeObserver)

        switchAutoBrightness()

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
    private fun startSmoothLightTimer(filterView: FilterView) {
        if (smoothLightTimer == null && systemBrightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
            smoothLightTimer = Timer()
            smoothLightTimer!!.schedule(object : TimerTask() {
                override fun run() {
                    updateFilterSmooth(filterView)
                }
            }, 1000, 5000)
        }
    }

    /**
     * 更新滤镜 使用最近的光线传感器样本平均值
     */
    private fun updateFilterSmooth(filterView: FilterView) {
        try {
            val currentTime = System.currentTimeMillis()
            val historys = lightHistory.filter {
                (currentTime - it.time) < 10001
            }
            if (historys.size > 0 && this.filterView != null) {
                var total: Long = 0
                for (history in historys) {
                    total += history.lux
                }
                val avg = total / historys.size
                handler.post {
                    // if (avg > 0 && avg < 1) {
                        // 小数精确度问题，如果0.5也当做1，=0才理解为全黑环境
                    //    updateFilterNow(1, filterView)
                    //} else {
                        updateFilterNow(avg.toInt(), filterView)
                    //}
                }
            }
        } catch (ex: Exception) {
            handler.post {
                Toast.makeText(this, "更新滤镜异常：" + ex.message, Toast.LENGTH_SHORT).show()
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
    private fun updateFilter(lux: Int, filterView: FilterView) {
        if (config.getBoolean(SpfConfig.SMOOTH_ADJUSTMENT, SpfConfig.SMOOTH_ADJUSTMENT_DEFAULT)) {
            startSmoothLightTimer(filterView)
        } else {
            stopSmoothLightTimer()
            updateFilterNow(lux, filterView)
        }
        GlobalStatus.currentFilterBrightness = filterBrightness
    }

    private fun updateFilterNow(lux: Int, filterView: FilterView) {
        // 深夜极暗光 22:00~06:00
        if (lux == 0 && config.getBoolean(SpfConfig.NIGHT_MODE, SpfConfig.NIGHT_MODE_DEFAULT)) {
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            if (hour >= 22 || hour < 6) {
                val filterViewConfig = GlobalStatus.sampleData!!.getFilterConfigByRatio(0.05f)
                updateFilterNow(filterViewConfig, filterView)
                return
            }
        }
        val filterViewConfig = GlobalStatus.sampleData!!.getFilterConfig(lux)
        val offset = config.getInt(SpfConfig.FILTER_LEVEL_OFFSET, SpfConfig.FILTER_LEVEL_OFFSET_DEFAULT) / 100.0
        var alpha = filterViewConfig.filterAlpha + ((filterViewConfig.filterAlpha * offset).toInt())
        if (isLandscapf) {
            alpha -= 25
        }
        if (alpha > 250) {
            alpha = 250
        } else if (alpha < 0) {
            alpha = 0
        }
        filterViewConfig.filterAlpha = alpha

        updateFilterNow(filterViewConfig, filterView)
    }

    private fun updateFilterNow(filterViewConfig: FilterViewConfig, filterView: FilterView) {
        if (isLandscapf && config.getBoolean(SpfConfig.LANDSCAPE_OPTIMIZE, SpfConfig.LANDSCAPE_OPTIMIZE_DEFAULT)) {
            filterView.setFilterColor(filterViewConfig.filterAlpha, 0, 0, 0, true)
        } else {
            val filterDynamicColor = config.getInt(SpfConfig.FILTER_DYNAMIC_COLOR, SpfConfig.FILTER_DYNAMIC_COLOR_DEFAULT)
            filterView.setFilterColor(filterViewConfig.filterAlpha, filterDynamicColor, filterDynamicColor / 2, 0, true)
        }
        if (filterViewConfig.filterBrightness != filterBrightness) {
            val layoutParams = popupView!!.layoutParams as WindowManager.LayoutParams?
            if (layoutParams != null) {
                layoutParams.screenBrightness = filterViewConfig.getFilterBrightnessRatio()
                mWindowManager.updateViewLayout(popupView, layoutParams)
            }
            filterBrightness = filterViewConfig.filterBrightness
        }

        GlobalStatus.currentFilterAlpah = filterViewConfig.filterAlpha

        GlobalStatus.currentFilterBrightness = filterBrightness
    }
}
