package com.omarea.filter

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var config: SharedPreferences
    private var timer: Timer? = null
    private var myHandler = Handler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (GlobalStatus.sampleData == null) {
            GlobalStatus.sampleData = SampleData(applicationContext)
        }
        config = getSharedPreferences(SpfConfig.FILTER_SPF, Context.MODE_PRIVATE)

        setContentView(R.layout.activity_main)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

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
                    } catch (ex: java.lang.Exception) {}
                } else {
                    config.edit().putBoolean(SpfConfig.FILTER_AUTO_START, true).apply()
                    GlobalStatus.filterOpen!!.run()
                }
            } else {
                if (GlobalStatus.filterClose != null) {
                    GlobalStatus.filterClose!!.run()
                }
                config.edit().putBoolean(SpfConfig.FILTER_AUTO_START, false).apply()
            }
        }

        // 屏幕滤镜强度偏移量
        filter_level_offset.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onStopTrackingTouch(seekBar: SeekBar?) { }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {  }

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                config.edit().putInt(SpfConfig.FILTER_LEVEL_OFFSET, progress - 50).apply()
                filterRefresh()
            }
        })
        filter_level_offset.progress = config.getInt(SpfConfig.FILTER_LEVEL_OFFSET, SpfConfig.FILTER_LEVEL_OFFSET_DEFAULT) + 50

        // 动态颜色
        filter_dynamic_color.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onStopTrackingTouch(seekBar: SeekBar?) { }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {  }

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                config.edit().putInt(SpfConfig.FILTER_DYNAMIC_COLOR, progress).apply()
                filterRefresh()
            }
        })
        filter_dynamic_color.progress = config.getInt(SpfConfig.FILTER_DYNAMIC_COLOR, SpfConfig.FILTER_DYNAMIC_COLOR_DEFAULT)

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
    }

    private fun filterRefresh() {
        if (GlobalStatus.filterEnabled && GlobalStatus.filterRefresh != null) {
            GlobalStatus.filterRefresh!!.run()
        }
    }

    private fun updateInfo(){
        myHandler.post {
            light_lux.text = GlobalStatus.currentLux.toString()
            screen_light.text = if(GlobalStatus.filterEnabled) GlobalStatus.currentSystemBrightness.toString() else Utils.getSystemBrightness(applicationContext).toString()
            filter_alpha.text = GlobalStatus.currentFilterAlpah.toString()
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
    }

    override fun onPause() {
        stopTimer()
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
}
