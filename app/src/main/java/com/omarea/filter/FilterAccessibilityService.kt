package com.omarea.filter

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.os.Build
import android.os.Handler
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class FilterAccessibilityService : AccessibilityService() {
    private lateinit var config: SharedPreferences
    private var lightSensorManager: LightSensorManager = LightSensorManager.getInstance()
    private var handler = Handler()
    private var isLandscapf = false

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

    var view: View? = null

    private fun filterClose() {
        if (view != null) {
            val mWindowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            mWindowManager.removeView(view)
            view = null
            lightSensorManager.stop()
        }

        GlobalStatus.filterRefresh = null
        GlobalStatus.filterEnabled = false
    }

    /**
     * 获取导航栏高度
     * @param context
     * @return
     */
    fun getNavBarHeight(): Int {
        val resourceId: Int
        val rid = resources.getIdentifier("config_showNavigationBar", "bool", "android")
        if (rid != 0) {
            resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
            return resources.getDimensionPixelSize(resourceId)
        } else {
            return 0
        }
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

    private fun getSystemBrightness(): Int {
        var systemBrightness = 0;
        try {
            systemBrightness = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
        } catch (e: Settings.SettingNotFoundException) {
            e.printStackTrace();
        }
        return systemBrightness;
    }

    private fun filterOpen() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(applicationContext)) {
            Toast.makeText(this, R.string.overlays_required, Toast.LENGTH_LONG).show()
            return
        }
        if (view != null) {
            filterClose()
        }

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

        val mWindowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val display = mWindowManager.getDefaultDisplay()
        val p = Point()
        display.getRealSize(p)
        params.width = p.y // p.x
        params.height = p.y

        // 貌似无效
        params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL

        view = LayoutInflater.from(this).inflate(R.layout.filter, null)
        mWindowManager.addView(view, params)
        val filterView = view!!.findViewById<FilterView>(R.id.filter_view)

        lightSensorManager.start(this, object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            }

            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && event.values.size > 0) {
                    // 获取光线强度
                    val lux = event.values[0].toInt()
                    updateFilter(lux, filterView)
                }
            }
        })

        GlobalStatus.filterRefresh = Runnable {
            updateFilter(GlobalStatus.currentLux, filterView)
        }

        // 最高亮度锁定
        // val lp = getWindow().getAttributes()
        // lp.screenBrightness = 1.0f
        // getWindow().setAttributes(lp)

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
     * 更新滤镜
     */
    private fun updateFilter(lux:Int, filterView:FilterView) {
        if (lux > 254) {
            filterView.setFilterColor(0)
            GlobalStatus.currentFilterAlpah = 0
        } else {
            val sample = GlobalStatus.sampleData!!.getVitualSample(lux)
            if (sample > -1) {
                val offset = config.getInt(SpfConfig.FILTER_LEVEL_OFFSET, SpfConfig.FILTER_LEVEL_OFFSET_DEFAULT) / 100.0
                var alpha =  sample + ((sample * offset).toInt())
                if (isLandscapf) {
                    alpha -= 25
                }
                if (alpha > 250) {
                    alpha = 250
                } else if (alpha < 0) {
                    alpha = 0
                }

                val filterDynamicColor = config.getInt(SpfConfig.FILTER_DYNAMIC_COLOR, SpfConfig.FILTER_DYNAMIC_COLOR_DEFAULT)

                if (config.getBoolean(SpfConfig.SMOOTH_ADJUSTMENT, SpfConfig.SMOOTH_ADJUSTMENT_DEFAULT)) {
                    handler.postDelayed({
                        if (GlobalStatus.currentLux == lux) {
                            if (isLandscapf) {
                                filterView.setFilterColor(alpha, 0, 0, 0, true)
                            } else {
                                filterView.setFilterColor(alpha, filterDynamicColor, filterDynamicColor / 2, 0, true)
                            }
                            GlobalStatus.currentFilterAlpah = alpha
                        }
                    }, 1000)
                } else {
                    if (isLandscapf) {
                        filterView.setFilterColor(alpha, 0, 0, 0, true)
                    } else {
                        filterView.setFilterColor(alpha, filterDynamicColor, filterDynamicColor / 2, 0, true)
                    }
                    GlobalStatus.currentFilterAlpah = alpha
                }
            } else {
                Log.d("获取样本失败", lux.toString())
            }
        }
        GlobalStatus.currentLux = lux
        GlobalStatus.currentSystemBrightness = getSystemBrightness()
    }
}
