package com.omarea.filter.light

import android.content.Context
import android.database.ContentObserver
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.net.Uri
import android.os.Handler
import android.provider.Settings
import com.omarea.filter.GlobalStatus

class LightSensorWatcher(private var context: Context, private var lightHandler: LightHandler) {
    private var lightSensorManager: LightSensorManager = LightSensorManager.getInstance()

    private var systemBrightness = 0 // 当前由系统控制的亮度
    private var systemBrightnessMode = Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC // Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL

    private fun getSystemConfig() {
        val contentResolver = context.contentResolver
        // 获取屏幕亮度
        systemBrightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        // 获取屏幕亮度模式
        systemBrightnessMode = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
    }

    private var systemBrightnessObserver: ContentObserver = object : ContentObserver(Handler()) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            getSystemConfig()

            if (systemBrightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) {
                lightHandler.onBrightnessChange(systemBrightness)
            }
        }
    }

    private var systemBrightnessModeObserver = object : ContentObserver(Handler()) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            getSystemConfig()

            lightHandler.onBrightnessChange(systemBrightness)
            if (systemBrightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) {
                stop()
            } else {
                start()
            }
            lightHandler.onModeChange(systemBrightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
        }
    }

    private fun start() {
        lightSensorManager.start(context, object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            }

            // FIXME:已知部分手机，即使环境亮度没改变也可能会疯狂报告亮度 - 比如说三星S8，这可咋整呢？
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && event.values.size > 0) {

                    // 获取光线强度
                    val lux = event.values[0]
                    GlobalStatus.currentLux = lux

                    // 自动亮度模式下才根据环境光自动调整滤镜强度
                    if (systemBrightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                        lightHandler.onLuxChange(lux)
                    }
                }
            }
        })
    }

    private fun stop() {
        lightSensorManager.stop()
    }

    fun startSystemConfigWatcher() {
        getSystemConfig()

        val auto = systemBrightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

        lightHandler.onModeChange(auto)
        if (auto) {
            start()
        } else {
            stop()
            lightHandler.onBrightnessChange(systemBrightness)
        }

        val contentResolver = context.contentResolver

        // 监控屏幕亮度
        contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), true, systemBrightnessObserver)
        contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE), true, systemBrightnessModeObserver)
    }

    fun stopSystemConfigWatcher() {
        stop()
        val contentResolver = context.contentResolver

        // 监控屏幕亮度
        contentResolver.unregisterContentObserver(systemBrightnessObserver)
        contentResolver.unregisterContentObserver(systemBrightnessModeObserver)
    }
}
