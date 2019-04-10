package com.omarea.filter

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.*
import kotlinx.android.synthetic.main.activity_sample_edit.*

class SampleEditActivity : AppCompatActivity() {
    private var filterPopup: View? = null
    private var hasChange = false
    private var alertDialog: AlertDialog? = null

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

    private fun filterClose() {
        if (filterPopup != null) {
            val mWindowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            mWindowManager.removeView(filterPopup)
            filterPopup = null
        }

    }

    private fun filterOpen() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(applicationContext)) {
            Toast.makeText(this, R.string.overlays_required, Toast.LENGTH_LONG).show()
            return
        }
        if (filterPopup != null) {
            filterClose()
        }

        val params = WindowManager.LayoutParams()

        // 类型
        params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        // 设置window type
        //params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {//6.0+
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        params.format = PixelFormat.TRANSLUCENT
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
        // or WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or WindowManager.LayoutParams.FLAG_DIM_BEHIND
        // 无法覆盖状态栏和导航栏图标
        // params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

        val mWindowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val display = mWindowManager.getDefaultDisplay()
        val p = Point()
        display.getRealSize(p)
        params.width = p.y + getNavBarHeight() * 2 // -1
        params.height = p.y + getNavBarHeight() * 2

        filterPopup = LayoutInflater.from(this).inflate(R.layout.filter, null)
        mWindowManager.addView(filterPopup, params)
    }

    /**
     * 更新图表
     */
    private fun updateChart() {
        screen_light_min.progress = GlobalStatus.sampleData!!.getScreentMinLight()
        screen_light_min_ratio.text = (screen_light_min.progress / 10f).toString()

        sample_chart.invalidate()
    }

    private fun setWindowLight() {
        // 亮度锁定
        val lp = getWindow().getAttributes()
        lp.screenBrightness = 1.0f // (GlobalStatus.sampleData!!.getScreentMinLight() / 100.0).toFloat()
        getWindow().setAttributes(lp)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sample_edit)
        setWindowLight()

        // 全屏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        smaple_add.setOnClickListener {
            openSampleCreate()
        }
        smaple_clear.setOnClickListener {
            AlertDialog.Builder(this)
                    .setTitle(R.string.smaple_clear)
                    .setPositiveButton(R.string.sample_edit_confirm, { _, _ ->
                        GlobalStatus.sampleData!!.readConfig(this.applicationContext, true)
                        this.hasChange = true
                        updateChart()
                    })
                    .setNegativeButton(R.string.sample_edit_cancel, { _, _ ->
                    })
                    .create()
                    .show()
        }

        // 屏幕最低亮度调整
        screen_light_min.max = FilterViewConfig.FILTER_BRIGHTNESS_MAX
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            screen_light_min.min = FilterViewConfig.FILTER_BRIGHTNESS_MIN
        }
        screen_light_min.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (GlobalStatus.sampleData!!.getScreentMinLight() != progress) {
                    GlobalStatus.sampleData!!.setScreentMinLight(progress)
                    hasChange = true
                }
                setWindowLight()
                screen_light_min_ratio.text = (progress / 10f).toString()
            }
        })

        /*
        getContentResolver().registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), true, object:ContentObserver(Handler()) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
            }
        })
        getContentResolver().registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE), true, object:ContentObserver(Handler()) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
            }
        })
        */
        updateChart()
    }

    override fun onPause() {
        filterClose()
        if (hasChange) {
            GlobalStatus.sampleData!!.saveConfig(applicationContext)
        }
        if (alertDialog != null) {
            alertDialog!!.dismiss()
        }
        super.onPause()
    }

    /**
     * 打开样本创建界面
     */
    private fun openSampleCreate() {
        hasChange = true
        val dialogView = layoutInflater.inflate(R.layout.sample_edit, null)
        val lightSensorManager = LightSensorManager()
        val sampleLuxView = dialogView.findViewById<SeekBar>(R.id.sample_lux)
        val sampleLuxValueView = dialogView.findViewById<TextView>(R.id.sample_lux_text)
        val sampleFilterView = dialogView.findViewById<SeekBar>(R.id.sample_filter)
        var currentLux = -1

        alertDialog = AlertDialog.Builder(this)
                .setTitle(R.string.sample_add_title)
                .setView(dialogView)
                .setCancelable(false)
                .create()
        alertDialog!!.show()
        filterOpen()

        dialogView.findViewById<Button>(R.id.sample_edit_cancel).setOnClickListener {
            // GlobalStatus.sampleData!!.removeSample(sampleFilterView.progress)
            alertDialog!!.dismiss()
        }

        dialogView.findViewById<Button>(R.id.sample_save).setOnClickListener {
            GlobalStatus.sampleData!!.replaceSample(sampleLuxView.progress, sampleFilterView.progress)

            alertDialog!!.dismiss()
        }
        alertDialog!!.setOnDismissListener {
            lightSensorManager.stop()
            filterClose()
            updateChart()
            alertDialog = null
        }
        val currentLuxView = dialogView.findViewById<TextView>(R.id.sample_edit_currentlux)
        val filterView = filterPopup!!.findViewById<FilterView>(R.id.filter_view)

        lightSensorManager.start(this, object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            }

            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && event.values.size > 0) {
                    // 获取光线强度
                    val lux = event.values[0].toInt()
                    currentLuxView.text = lux.toString()
                    if (currentLux == -1) {
                        var limitLux = lux
                        if (limitLux > sampleLuxView.max) {
                            limitLux = sampleLuxView.max
                        }
                        sampleLuxValueView.text = limitLux.toString()
                        sampleLuxView.progress = limitLux
                    }
                    currentLux = lux
                }
            }
        })

        sampleLuxView.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                sampleLuxValueView.text = progress.toString()
                val sample = GlobalStatus.sampleData!!.getVitualSample(progress)
                if (sample != null) {
                    sampleFilterView.progress = FilterViewConfig.getConfigByBrightness(sample).filterAlpha
                }
            }
        })
        sampleFilterView.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                filterView.setFilterColor(progress)
            }
        })
        dialogView.findViewById<TextView>(R.id.sample_edit_applay).setOnClickListener {
            var lux = currentLux
            if (lux > 1000) {
                lux = 1000
            }
            sampleLuxValueView.text = lux.toString()
            sampleLuxView.progress = lux
        }
        dialogView.findViewById<TextView>(R.id.sample_edit_minus).setOnClickListener {
            if (sampleLuxView.progress > 0) {
                sampleLuxView.progress -= 1
            }
        }
        dialogView.findViewById<TextView>(R.id.sample_edit_plus).setOnClickListener {
            if (sampleLuxView.progress < sampleLuxView.max) {
                sampleLuxView.progress += 1
            }
        }
        dialogView.findViewById<TextView>(R.id.sample_edit_alpha_minus).setOnClickListener {
            if (sampleFilterView.progress > 0) {
                sampleFilterView.progress -= 1
            }
        }
        dialogView.findViewById<TextView>(R.id.sample_edit_alpha_plus).setOnClickListener {
            if (sampleFilterView.progress < sampleLuxView.max) {
                sampleFilterView.progress += 1
            }
        }
    }
}
