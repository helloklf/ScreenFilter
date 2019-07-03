package com.omarea.filter

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

class DynamicOptimize(private var context: Context) {
    private var sensorEventListener: SensorEventListener? = null
    private var x = 0f
    private var y = 0f
    private var z = 0f

    fun registerListener() {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val values = event.values
                val ax = values[0]
                val ay = values[1]
                val az = values[2]
                x = ax
                y = ay
                z = az
                Log.d("TYPE_ACCELEROMETER", "x:${x}, y:${y}, z:${z}")
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            }
        }
        sm.registerListener(sensorEventListener, sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun unregisterListener() {
        if (sensorEventListener != null) {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            sm.unregisterListener(sensorEventListener)
            sensorEventListener = null
        }
    }

    val isScreenInverse: Boolean
        get() {
            return z < 0
        }

    fun brightnessOptimization(brightness: Int): Int {
        var value:Int = brightness;
        if (isScreenInverse) {
            value = (brightness * 0.9).toInt();
        }
        if (value < FilterViewConfig.FILTER_BRIGHTNESS_MIN) {
            value = FilterViewConfig.FILTER_BRIGHTNESS_MIN;
        } else if (value > FilterViewConfig.FILTER_BRIGHTNESS_MAX) {
            value = FilterViewConfig.FILTER_BRIGHTNESS_MAX;
        }

        return value;
    }
}
