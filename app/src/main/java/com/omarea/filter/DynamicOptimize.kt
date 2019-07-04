package com.omarea.filter

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import java.util.*

class DynamicOptimize(private var context: Context) {
    private var sensorEventListener: SensorEventListener? = null
    private var x = 0f
    private var y = 0f
    private var z = 0f

    fun registerListener() {
        if(sensorEventListener == null) {
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
                    // Log.d("TYPE_ACCELEROMETER", "x:${x}, y:${y}, z:${z}")
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                }
            }
            sm.registerListener(sensorEventListener, sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun unregisterListener() {
        if (sensorEventListener != null) {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            sm.unregisterListener(sensorEventListener)
            sensorEventListener = null
        }
    }

    /**
     * @param sensitivity 角度亮度纠正的灵敏度
     */
    fun brightnessOptimization(sensitivity:Float = 0.5F, offset: Double = 0.toDouble()): Double {
        if (sensorEventListener != null) {
            var offsetValue: Double = offset;
            // 手机处于屏幕朝下时，根据角度降低亮度
            if (z < 0) {
                offsetValue = offset + ((z * 10).toInt() / 100.0 * sensitivity);
            } else if (z > 5) {
                offsetValue = offset + (((z - 5) * 10).toInt() / 200.0 * sensitivity);
            }

            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            if (hour >= 21 || hour < 7) {
                if (hour > 20) {
                    offsetValue -= ((hour - 20) / 10.0)
                } else if (hour > 5) {
                    offsetValue += ((hour - 7) / 10.0)
                } else {
                    offsetValue -= 0.3
                }
            }

            if (offsetValue < -0.999) {
                offsetValue = -0.999
            }

            Log.d("brightnessOptimization", ">>> " + offsetValue)
            return offsetValue;
        } else {
            return offset
        }
    }
}
