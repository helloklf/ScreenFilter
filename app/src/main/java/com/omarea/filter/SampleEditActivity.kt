package com.omarea.filter

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import com.omarea.common.ui.DialogHelper
import com.omarea.filter.common.UITools
import com.omarea.filter.light.LightSensorManager
import kotlinx.android.synthetic.main.activity_sample_edit.*

class SampleEditActivity : AppCompatActivity() {
    private var filterPopup: View? = null
    private var hasChange = false
    private var alertDialog: AlertDialog? = null
    private lateinit var config: SharedPreferences
    private lateinit var dynamicOptimize: DynamicOptimize

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
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId);
        }
        return result
    }

    private fun filterClose() {
        GlobalStatus.filterManualBrightness = -1
        GlobalStatus.filterManualUpdate?.run()
    }

    private fun filterUpdate(screenBrightness: Int) {
        GlobalStatus.filterManualBrightness = screenBrightness
        GlobalStatus.filterManualUpdate?.run()
    }

    private fun filterUpdateByLux(lux: Float): Int? {
        DynamicOptimize().optimizedBrightness(lux, config)?.run {
            GlobalStatus.filterManualBrightness = this
            GlobalStatus.filterManualUpdate?.run()
            return this
        }
        return null
    }

    /**
     * 更新图表
     */
    private fun updateChart() {
        screen_light_min.progress = GlobalStatus.sampleData!!.getScreentMinLight()
        screen_light_min_ratio.text = (screen_light_min.progress / 10.0).toString()

        sample_chart.invalidate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sample_edit)
        config = getSharedPreferences(SpfConfig.FILTER_SPF, Context.MODE_PRIVATE)
        dynamicOptimize = DynamicOptimize()

        // 全屏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        smaple_add.setOnClickListener {
            openSampleCreate()
        }
        smaple_clear.setOnClickListener {
            DialogHelper.animDialog(AlertDialog.Builder(this)
                    .setTitle(R.string.smaple_clear)
                    .setPositiveButton(R.string.sample_edit_confirm) { _, _ ->
                        GlobalStatus.sampleData!!.readConfig(true)
                        this.hasChange = true
                        updateChart()
                    }
                    .setNegativeButton(R.string.sample_edit_cancel) { _, _ ->
                    })
        }

        // 屏幕最低亮度调整
        screen_light_min.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                var value = progress
                if (progress < 1) {
                    value = 1
                }
                if (GlobalStatus.sampleData!!.getScreentMinLight() != value) {
                    GlobalStatus.sampleData!!.setScreentMinLight(value)
                    hasChange = true
                }
                screen_light_min_ratio.text = (value / 10.0).toString()
            }
        })

        screen_light_minus.setOnClickListener {
            if (screen_light_min.progress > 1) {
                screen_light_min.progress -= 1
            }
        }

        screen_light_plus.setOnClickListener {
            if (screen_light_min.progress < screen_light_min.max - 1) {
                screen_light_min.progress += 1
            } else if (screen_light_min.progress < screen_light_min.max) {
                screen_light_min.progress = screen_light_min.max
            }
        }

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

        setViewBackground(filter_color, GlobalStatus.sampleData!!.getFilterColor())
        filter_color.setOnClickListener { openColorPicker() }
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
     * 选择滤镜颜色
     */
    private fun openColorPicker() {
        val view: View = getLayoutInflater().inflate(R.layout.filter_color_picker, null)
        val currentColor: Int = GlobalStatus.sampleData!!.getFilterColor()
        val alphaBar = view.findViewById<SeekBar>(R.id.color_alpha)
        val redBar = view.findViewById<SeekBar>(R.id.color_red)
        val greenBar = view.findViewById<SeekBar>(R.id.color_green)
        val blueBar = view.findViewById<SeekBar>(R.id.color_blue)
        val colorPreview = view.findViewById<Button>(R.id.color_preview)
        alphaBar.progress = Color.alpha(currentColor)
        redBar.progress = Color.red(currentColor)
        greenBar.progress = Color.green(currentColor)
        blueBar.progress = Color.blue(currentColor)
        colorPreview.setBackgroundColor(currentColor)
        val listener: OnSeekBarChangeListener = object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val color = Color.argb(alphaBar.progress, redBar.progress, greenBar.progress, blueBar.progress)
                colorPreview.setBackgroundColor(color)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        }
        alphaBar.setOnSeekBarChangeListener(listener)
        redBar.setOnSeekBarChangeListener(listener)
        greenBar.setOnSeekBarChangeListener(listener)
        blueBar.setOnSeekBarChangeListener(listener)
        DialogHelper.animDialog(AlertDialog.Builder(this)
                .setTitle(getString(R.string.choose_color))
                .setView(view)
                .setPositiveButton(android.R.string.ok) { dialog, which ->
                    val color = Color.argb(alphaBar.progress, redBar.progress, greenBar.progress, blueBar.progress)
                    GlobalStatus.sampleData!!.setFilterColor(color)
                    val filterView = filterPopup?.findViewById<FilterView>(R.id.filter_view)
                    filterView?.setFilterColor(redBar.progress, greenBar.progress, blueBar.progress)
                    setViewBackground(filter_color, GlobalStatus.sampleData!!.getFilterColor())
                }
                .setNegativeButton(getString(android.R.string.cancel)) { dialog, which -> })
    }

    protected fun setViewBackground(view: View, color: Int) {
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE
        drawable.gradientType = GradientDrawable.RECTANGLE
        drawable.cornerRadius = UITools.dp2px(this, 15F).toFloat()
        drawable.setColor(color)
        drawable.setStroke(2, -0x777778)
        view.background = drawable
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
        val sampleBrightness = dialogView.findViewById<SeekBar>(R.id.sample_brightness)
        val sampleBrightnessText = dialogView.findViewById<TextView>(R.id.sample_brightness_text)
        var currentLux = -1f

        alertDialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create()
        DialogHelper.animDialog(alertDialog)
        alertDialog?.getWindow()?.setDimAmount(0f);
        GlobalStatus.filterManualUpdate?.run()

        dialogView.findViewById<View>(R.id.sample_edit_cancel).setOnClickListener {
            // GlobalStatus.sampleData!!.removeSample(sampleBrightness.progress)
            alertDialog!!.dismiss()
        }

        dialogView.findViewById<View>(R.id.sample_save).setOnClickListener {
            GlobalStatus.sampleData!!.replaceSample(sampleLuxView.progress, sampleBrightness.progress)

            alertDialog!!.dismiss()
        }
        alertDialog!!.setOnDismissListener {
            lightSensorManager.stop()
            filterClose()
            updateChart()
            alertDialog = null
        }
        val currentLuxView = dialogView.findViewById<TextView>(R.id.sample_edit_brightness)

        lightSensorManager.start(this, object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            }

            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && event.values.size > 0) {
                    // 获取光线强度
                    val lux = event.values[0]
                    currentLuxView.text = lux.toString()
                    if (currentLux < 0) {
                        sampleLuxValueView.text = lux.toString()
                        sampleLuxView.progress = lux.toInt()
                        if (lux > sampleLuxView.max) {
                            sampleLuxView.max = lux.toInt()
                        }

                        filterUpdateByLux(lux)?.run {
                            sampleBrightness.progress = this
                            sampleBrightnessText.text = (this / 10.0).toString()
                        }
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
                    sampleBrightness.progress = sample
                    sampleBrightnessText.text = (sample / 10.0).toString()
                }
            }
        })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            sampleBrightness.min = 1
        }

        sampleBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                filterUpdate(progress)

                sampleBrightnessText.text = (progress / 10.0).toString()
            }
        })
        dialogView.findViewById<View>(R.id.sample_edit_applay).setOnClickListener {
            sampleLuxValueView.text = currentLux.toString()
            val lux = currentLux.toInt()
            if (lux > sampleLuxView.max) {
                sampleLuxView.max = lux
            }
            sampleLuxView.progress = lux
        }
        dialogView.findViewById<View>(R.id.sample_edit_minus).setOnClickListener {
            if (sampleLuxView.progress > 0) {
                sampleLuxView.progress -= 1
            }
        }
        dialogView.findViewById<View>(R.id.sample_edit_plus).setOnClickListener {
            if (sampleLuxView.progress < sampleLuxView.max) {
                sampleLuxView.progress += 1
            }
        }
        dialogView.findViewById<View>(R.id.sample_edit_alpha_minus).setOnClickListener {
            if (sampleBrightness.progress > 0) {
                sampleBrightness.progress -= 1
            }
        }
        dialogView.findViewById<View>(R.id.sample_edit_alpha_plus).setOnClickListener {
            if (sampleBrightness.progress < sampleLuxView.max) {
                sampleBrightness.progress += 1
            }
        }
    }
}
