package com.omarea.filter

import java.util.*

class DynamicOptimize {
    fun brightnessOptimization(lux: Float, screentMinLight: Int): Double {
        var offsetValue: Double = 0.toDouble();
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        if (hour >= 21 || hour < 7) {
            if (lux <= 0f) {
                if (hour > 20) {
                    offsetValue -= ((hour - 20) / 10.0)
                    if (hour >= 22) {
                        offsetValue -= ((1 - (screentMinLight.toFloat() / FilterViewConfig.FILTER_BRIGHTNESS_MAX)) / 2)
                    }
                } else if (hour > 5) {
                    offsetValue += ((hour - 7) / 10.0)
                } else {
                    offsetValue -= 0.3
                    offsetValue -= ((1 - (screentMinLight.toFloat() / FilterViewConfig.FILTER_BRIGHTNESS_MAX)) / 2)
                }
            }
        }

        return offsetValue
    }

    fun luxOptimization(lux: Float): Float {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        if (lux <= 2f) {
            if (hour < 21 && hour >= 7) {
                return 1f
            }
        }
        return 0f
    }
}
