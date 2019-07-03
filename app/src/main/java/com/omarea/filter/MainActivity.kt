package com.omarea.filter

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import android.widget.SeekBar
import android.widget.Switch
import android.widget.Toast
import kotlinx.android.synthetic.main.content_main.*
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var config: SharedPreferences
    private var timer: Timer? = null
    private var myHandler = Handler()
    private lateinit var systemBrightnessModeObserver: ContentObserver
    private val OVERLAY_PERMISSION_REQ_CODE = 0

    public fun askForPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))
                startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
                return
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(if (GlobalStatus.filterEnabled) R.style.AppTheme_Default else R.style.AppTheme_OFF)

        if (GlobalStatus.sampleData == null) {
            GlobalStatus.sampleData = SampleData(applicationContext)
        }
        config = getSharedPreferences(SpfConfig.FILTER_SPF, Context.MODE_PRIVATE)

        if (!config.contains(SpfConfig.SCREENT_MAX_LIGHT)) {
            if (Build.PRODUCT == "perseus") { // Xiaomi MIX3 屏幕最大亮度2047
                config.edit().putInt(SpfConfig.SCREENT_MAX_LIGHT, 2047).apply()
                // GlobalStatus.sampleData!!.setScreentMinLight(30)
            }
        }

        setContentView(R.layout.activity_main)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        setExcludeFromRecents()
        askForPermission()

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
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        startActivity(intent)
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
        brightness_offset.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val lightLuxOffset = progress - (brightness_offset.max / 4)
                config.edit().putInt(SpfConfig.BRIGTHNESS_OFFSET, lightLuxOffset).apply()
                if (lightLuxOffset > 0) {
                    brightness_offset_text.text = "+" + lightLuxOffset
                } else if (lightLuxOffset < 0) {
                    brightness_offset_text.text = lightLuxOffset.toString()
                } else {
                    brightness_offset_text.text = "100"
                }
                filterRefresh()
            }
        })
        val lightLuxOffset = config.getInt(SpfConfig.BRIGTHNESS_OFFSET, SpfConfig.BRIGTHNESS_OFFSET_DEFAULT)
        val lightLuxOffsetProgress = lightLuxOffset + (brightness_offset.max / 4)
        if (lightLuxOffsetProgress > 0) {
            brightness_offset_text.text = "+" + lightLuxOffset
        } else if (lightLuxOffsetProgress < 0) {
            brightness_offset_text.text = lightLuxOffset.toString()
        } else {
            brightness_offset_text.text = "100"
        }
        brightness_offset.progress = lightLuxOffsetProgress


        // 息屏暂停
        screen_off_mode.isChecked = config.getBoolean(SpfConfig.SCREEN_OFF_PAUSE, SpfConfig.SCREEN_OFF_PAUSE_DEFAULT)
        screen_off_mode.setOnClickListener {
            config.edit().putBoolean(SpfConfig.SCREEN_OFF_PAUSE, (it as Switch).isChecked).apply()
        }

        // 平滑亮度
        smooth_adjustment.isChecked = config.getBoolean(SpfConfig.SMOOTH_ADJUSTMENT, SpfConfig.SMOOTH_ADJUSTMENT_DEFAULT)
        smooth_adjustment.setOnClickListener {
            config.edit().putBoolean(SpfConfig.SMOOTH_ADJUSTMENT, (it as Switch).isChecked).apply()
            filterRefresh()
        }

        // 横屏优化
        landscape_optimize.isChecked = config.getBoolean(SpfConfig.LANDSCAPE_OPTIMIZE, SpfConfig.LANDSCAPE_OPTIMIZE_DEFAULT)
        landscape_optimize.setOnClickListener {
            config.edit().putBoolean(SpfConfig.LANDSCAPE_OPTIMIZE, (it as Switch).isChecked).apply()
        }

        // 深夜极暗光模式
        night_mode.setOnClickListener {
            config.edit().putBoolean(SpfConfig.NIGHT_MODE, (it as Switch).isChecked).apply()
        }
        night_mode.isChecked = config.getBoolean(SpfConfig.NIGHT_MODE, SpfConfig.NIGHT_MODE_DEFAULT)

        // 从最近任务隐藏
        hide_in_recent.isChecked = config.getBoolean(SpfConfig.HIDE_IN_RECENT, SpfConfig.HIDE_IN_RECENT_DEFAULT)
        hide_in_recent.setOnClickListener {
            config.edit().putBoolean(SpfConfig.HIDE_IN_RECENT, (it as Switch).isChecked).apply()
            setExcludeFromRecents()
        }

        // 自动亮度
        auto_adjustment.isChecked = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    }

    private fun filterRefresh() {
        if (GlobalStatus.filterEnabled && GlobalStatus.filterRefresh != null) {
            GlobalStatus.filterRefresh!!.run()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateInfo() {
        myHandler.post {
            light_lux.text = GlobalStatus.currentLux.toString() + "lux"
            if (GlobalStatus.filterEnabled) {
                filter_light.text = (GlobalStatus.currentFilterBrightness / 10f).toString() + "%"
                screen_light.text = "×"
            } else {
                filter_light.text = "×"
                screen_light.text = Utils.getSystemBrightness(applicationContext).toString()
            }
            filter_alpha.text = ((GlobalStatus.currentFilterAlpah * 1000 / FilterViewConfig.FILTER_MAX_ALPHA).toInt() / 10.0).toString() + "%"
        }
    }

    override fun onResume() {
        super.onResume()

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
                auto_adjustment.isChecked = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            }
        }
        getContentResolver().registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE), true, systemBrightnessModeObserver)
    }

    override fun onPause() {
        stopTimer()
        getContentResolver().unregisterContentObserver(systemBrightnessModeObserver)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 1 && GlobalStatus.filterOpen != null) {
            GlobalStatus.filterOpen!!.run()
        } else if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, getString(R.string.get_permission_fail), Toast.LENGTH_LONG).show()
                    finishAndRemoveTask()
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId

        return if (id == R.id.sample_edit) {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(applicationContext)) {
                Toast.makeText(this, R.string.overlays_required, Toast.LENGTH_LONG).show()
                return true
            }

            try {
                val intent = Intent(this, SampleEditActivity::class.java)
                startActivityForResult(intent, if (GlobalStatus.filterEnabled) 1 else 0)

                if (GlobalStatus.filterClose != null) {
                    GlobalStatus.filterClose!!.run()
                }
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
}
