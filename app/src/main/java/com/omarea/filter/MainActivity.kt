package com.omarea.filter

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.omarea.common.ui.DialogHelper
import com.omarea.filter.common.NotificationHelper
import com.omarea.filter.common.RadioGroupSimulator
import kotlinx.android.synthetic.main.content_main.*
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var config: SharedPreferences
    private var timer: Timer? = null
    private var myHandler = Handler()
    private lateinit var systemBrightnessModeObserver: ContentObserver
    private val OVERLAY_PERMISSION_REQ_CODE = 0

    /*
    private fun askForPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))
                startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
                return
            }
        }
    }
    */

    private var filterEnabled = GlobalStatus.filterEnabled
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = this.intent
        if (intent != null && intent.action != null) {
            val action = intent.action
            if (action == "open") {
                if (!GlobalStatus.filterEnabled && GlobalStatus.filterOpen != null) {
                    GlobalStatus.filterOpen?.run()
                }
                Toast.makeText(this, "滤镜已开启", Toast.LENGTH_SHORT).show()
                finish()
                return
            } else if (action == "close") {
                if (GlobalStatus.filterEnabled && GlobalStatus.filterClose != null) {
                    GlobalStatus.filterClose?.run()
                }
                Toast.makeText(this, "滤镜已关闭", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        }

        setTheme(if (filterEnabled) R.style.AppTheme_Default else R.style.AppTheme_OFF)

        if (GlobalStatus.sampleData == null) {
            GlobalStatus.sampleData = SampleData(applicationContext)
        }
        config = getSharedPreferences(SpfConfig.FILTER_SPF, Context.MODE_PRIVATE)

        setContentView(R.layout.activity_main)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        setExcludeFromRecents()
        FilterSample().getFilterAlpha(40)

        // 启用滤镜
        filter_switch.setOnClickListener { v ->
            val filterSwitch = v as Switch
            if (filterSwitch.isChecked) {
                if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(applicationContext)) {
                    Toast.makeText(this, R.string.overlays_required, Toast.LENGTH_LONG).show()
                    filterSwitch.isChecked = false
                } else if (GlobalStatus.filterOpen == null) {
                    Toast.makeText(this, R.string.accessibility_service_required, Toast.LENGTH_SHORT).show()
                    filterSwitch.isChecked = false
                    try {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    } catch (ex: java.lang.Exception) {
                    }
                } else {
                    config.edit().putBoolean(SpfConfig.FILTER_AUTO_START, true).apply()
                    GlobalStatus.filterOpen!!.run()
                    recreate()
                }
            } else {
                if (GlobalStatus.filterClose != null) {
                    GlobalStatus.filterClose!!.run()
                }
                config.edit().putBoolean(SpfConfig.FILTER_AUTO_START, false).apply()
                recreate()
            }
        }

        // 屏幕滤镜强度偏移量
        brightness_offset.max = SpfConfig.BRIGTHNESS_OFFSET_LEVELS
        brightness_offset.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val lightLuxOffset = progress - (brightness_offset.max / 2)
                config.edit().putInt(SpfConfig.BRIGTHNESS_OFFSET, lightLuxOffset).apply()
                brightness_offset_text.text = (when {
                    lightLuxOffset > 0 -> "+$lightLuxOffset"
                    lightLuxOffset < 0 -> lightLuxOffset.toString()
                    else -> "100"
                }) + "%"
                filterRefresh()
            }
        })
        brightness_offset.progress = config.getInt(SpfConfig.BRIGTHNESS_OFFSET, SpfConfig.BRIGTHNESS_OFFSET_DEFAULT) + (brightness_offset.max / 2)

        // 横屏优化
        landscape_optimize.isChecked = config.getBoolean(SpfConfig.LANDSCAPE_OPTIMIZE, SpfConfig.LANDSCAPE_OPTIMIZE_DEFAULT)
        landscape_optimize.setOnClickListener {
            config.edit().putBoolean(SpfConfig.LANDSCAPE_OPTIMIZE, (it as Switch).isChecked).apply()
        }

        // 动态优化（虚拟环境）
        dynamic_optimize.isChecked = config.getBoolean(SpfConfig.DYNAMIC_OPTIMIZE, SpfConfig.DYNAMIC_OPTIMIZE_DEFAULT)
        dynamic_optimize.setOnClickListener {
            config.edit().putBoolean(SpfConfig.DYNAMIC_OPTIMIZE, (it as Switch).isChecked).apply()
            GlobalStatus.filterRefresh?.run()
        }

        val limitLux = config.getFloat(SpfConfig.DYNAMIC_OPTIMIZE_LIMIT, SpfConfig.DYNAMIC_OPTIMIZE_LIMIT_DEFAULT)
        dynamic_optimize_limit.progress = (limitLux * 10).toInt()
        dynamic_optimize_limit_text.text = limitLux.toString() + "Lux"
        dynamic_optimize_limit.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                dynamic_optimize_limit_text.text = (progress / 10f).toString() + "Lux"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                config.edit().putFloat(SpfConfig.DYNAMIC_OPTIMIZE_LIMIT, seekBar!!.progress / 10f).apply()
                GlobalStatus.filterRefresh?.run()
            }
        })

        // 从最近任务隐藏
        hide_in_recent.isChecked = config.getBoolean(SpfConfig.HIDE_IN_RECENT, SpfConfig.HIDE_IN_RECENT_DEFAULT)
        hide_in_recent.setOnClickListener {
            config.edit().putBoolean(SpfConfig.HIDE_IN_RECENT, (it as Switch).isChecked).apply()
            setExcludeFromRecents()
        }

        // 息屏关闭
        lock_off.isChecked = config.getBoolean(SpfConfig.SCREEN_OFF_CLOSE, SpfConfig.SCREEN_OFF_CLOSE_DEFAULT)
        lock_off.setOnClickListener {
            config.edit().putBoolean(SpfConfig.SCREEN_OFF_CLOSE, (it as Switch).isChecked).apply()
        }

        // 自动亮度
        val contentResolver = contentResolver
        auto_adjustment.isChecked = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        auto_adjustment.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(this)) {
                val current = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                if (current == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                    Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                } else {
                    Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
                }
            } else {
                it.isEnabled = true
                Toast.makeText(this, getString(R.string.write_settings_unallowed), Toast.LENGTH_LONG).show()
                (it as Checkable).isChecked = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            }
        }

        // 亮度控制通知
        brightness_controller.isChecked = config.getBoolean(SpfConfig.BRIGHTNESS_CONTROLLER, SpfConfig.BRIGHTNESS_CONTROLLER_DEFAULT)
        brightness_controller.setOnClickListener {
            val checkable = it as Checkable
            if (checkable.isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!Settings.System.canWrite(this)) {
                        Toast.makeText(this, getString(R.string.write_settings_unallowed), Toast.LENGTH_SHORT).show()
                        checkable.isChecked = false
                    }
                } else {
                    Toast.makeText(this, getString(R.string.write_settings_unsupported), Toast.LENGTH_SHORT).show()
                    checkable.isChecked = false
                }
            } else {
                NotificationHelper(this).cancelNotification()
            }
            config.edit().putBoolean(SpfConfig.BRIGHTNESS_CONTROLLER, checkable.isChecked).apply()

            try {
                if (checkable.isChecked && GlobalStatus.filterEnabled) {
                    NotificationHelper(this).updateNotification()
                }
            } catch (ex: java.lang.Exception) {
            }
        }

        val p: PackageManager = packageManager
        val startActivity = ComponentName(applicationContext, MainActivity::class.java)
        hide_start_icon.setOnClickListener { v ->
            try {
                if (hide_start_icon.isChecked) {
                    p.setComponentEnabledSetting(startActivity, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                } else {
                    p.setComponentEnabledSetting(startActivity, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
                }
            } catch (ex: java.lang.Exception) {
                Toast.makeText(v.context, ex.message, Toast.LENGTH_SHORT).show()
            }
        }

        val activityState = p.getComponentEnabledSetting(startActivity)
        hide_start_icon.isChecked = activityState != PackageManager.COMPONENT_ENABLED_STATE_ENABLED && activityState != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
    }

    private fun restartFilter() {
        if (GlobalStatus.filterEnabled) {
            GlobalStatus.filterClose?.run()
            GlobalStatus.filterOpen?.run()
        }
    }

    private fun filterRefresh() {
        if (GlobalStatus.filterEnabled && GlobalStatus.filterRefresh != null) {
            GlobalStatus.filterRefresh!!.run()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateInfo() {
        myHandler.post {
            if (GlobalStatus.currentLux > -1) {
                light_lux.text = GlobalStatus.currentLux.toString() + "lux"
            } else {
                light_lux.text = ""
            }
            if (GlobalStatus.avgLux > -1) {
                light_lux_avg.text = GlobalStatus.avgLux.toString() + "lux"
            } else {
                light_lux_avg.text = ""
            }
            if (GlobalStatus.filterEnabled) {
                filter_light.text = (GlobalStatus.currentFilterBrightness / 10f).toString() + "%"
            } else {
                filter_light.text = "×"
            }
            filter_alpha.text = ((GlobalStatus.currentFilterAlpah * 1000 / FilterViewConfig.FILTER_MAX_ALPHA).toInt() / 10.0).toString() + "%"

            brightness_offset.progress = config.getInt(SpfConfig.BRIGTHNESS_OFFSET, SpfConfig.BRIGTHNESS_OFFSET_DEFAULT) + (brightness_offset.max / 2)
        }
    }

    override fun onResume() {
        super.onResume()

        if (GlobalStatus.filterEnabled && !filterEnabled) {
            filterEnabled = true
            recreate()
            return
        }

        filter_switch.isChecked = GlobalStatus.filterEnabled

        stopTimer()
        timer = Timer()
        timer!!.schedule(object : TimerTask() {
            override fun run() {
                updateInfo()
            }
        }, 0, 1000)

        systemBrightnessModeObserver = object : ContentObserver(Handler()) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                auto_adjustment.isChecked = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            }
        }
        contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE), true, systemBrightnessModeObserver)

        if (!(config.contains(SpfConfig.SCREENT_MAX_LIGHT) && config.contains(SpfConfig.TARGET_DEVICE))) {
            // Xiaomi MIX3、CC9、CC9(Meitu)、M9、K20 Pro
            if (Build.PRODUCT == "perseus" || Build.PRODUCT == "pyxis" || Build.PRODUCT == "vela" || Build.PRODUCT == "cepheus" || Build.PRODUCT == "raphael") {
                config.edit().putInt(SpfConfig.SCREENT_MAX_LIGHT, 2047).apply()
                config.edit().putInt(SpfConfig.TARGET_DEVICE, SpfConfig.TARGET_DEVICE_AMOLED).apply()
                GlobalStatus.sampleData!!.setScreentMinLight((FilterViewConfig.FILTER_BRIGHTNESS_MAX * 0.3).toInt())
            } else if (Build.PRODUCT == "tucana") { // Xiaomi CC9 Pro
                config.edit().putInt(SpfConfig.SCREENT_MAX_LIGHT, 2047).apply()
                config.edit().putInt(SpfConfig.TARGET_DEVICE, SpfConfig.TARGET_DEVICE_AMOLED).apply()
                GlobalStatus.sampleData!!.setScreentMinLight((FilterViewConfig.FILTER_BRIGHTNESS_MAX * 0.7).toInt())
            } else {
                Handler().postDelayed({
                    openGuide()
                }, 30)
            }
        }
    }

    override fun onPause() {
        stopTimer()
        contentResolver.unregisterContentObserver(systemBrightnessModeObserver)
        super.onPause()
    }

    private fun stopTimer() {
        if (this.timer != null) {
            timer!!.cancel()
            timer = null
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        return if (id == R.id.sample_edit) {
            if (!GlobalStatus.filterEnabled) {
                Toast.makeText(this, R.string.please_enable_filter, Toast.LENGTH_LONG).show()
                return true
            }

            try {
                val intent = Intent(this, SampleEditActivity::class.java)
                startActivityForResult(intent, if (GlobalStatus.filterEnabled) 1 else 0)
            } catch (ex: Exception) {
            }
            return true
        } else if (id == R.id.question) {
            try {
                val intent = Intent(this, HelpActivity::class.java)
                startActivity(intent)
            } catch (ex: Exception) {
            }
            return true
        } else super.onOptionsItemSelected(item)
    }

    private fun setExcludeFromRecents(exclude: Boolean? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val service = this.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                for (task in service.appTasks) {
                    if (task.taskInfo.id == this.taskId) {
                        val b = if (exclude == null) config.getBoolean(SpfConfig.HIDE_IN_RECENT, SpfConfig.HIDE_IN_RECENT_DEFAULT) else exclude
                        task.setExcludeFromRecents(b)
                    }
                }
            } catch (ex: Exception) {
            }
        } else {
            Toast.makeText(this, "您的系统版本过低，暂不支持本功能~", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGuide() {
        val dialog = layoutInflater.inflate(R.layout.dialog_guide, null)
        val guideAmoled = dialog.findViewById<CompoundButton>(R.id.guide_amoled)
        val guideLcd = dialog.findViewById<CompoundButton>(R.id.guide_lcd)
        RadioGroupSimulator(guideAmoled, guideLcd)
        var dialogWrap: DialogHelper.DialogWrap? = null
        dialog.findViewById<View>(R.id.guide_next).setOnClickListener {
            if (guideAmoled.isChecked || guideLcd.isChecked) {
                try {
                    val contentResolver = contentResolver
                    val current = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                    if (current >= 100) {
                        config.edit().putInt(SpfConfig.SCREENT_MAX_LIGHT, current).apply()
                        if (guideLcd.isChecked) {
                            config.edit().putInt(SpfConfig.TARGET_DEVICE, SpfConfig.TARGET_DEVICE_LCD).apply()
                        } else {
                            config.edit().putInt(SpfConfig.TARGET_DEVICE, SpfConfig.TARGET_DEVICE_AMOLED).apply()
                        }
                        GlobalStatus.sampleData!!.readConfig(true)
                        myHandler.postDelayed({
                            dialogWrap?.dismiss()
                            recreate()
                        }, 200)
                    } else {
                        Toast.makeText(this, getString(R.string.step_warn), Toast.LENGTH_SHORT).show()
                    }
                } catch (ex: Exception) {
                    Toast.makeText(this, getString(R.string.guide_fail), Toast.LENGTH_SHORT).show()
                }
            }
        }
        dialogWrap = DialogHelper.customDialog(this, dialog).setCancelable(false)
    }
}
