package com.omarea.filter

import java.util.*

class DynamicOptimize {
    private val enableLuxOptimize = false
    fun luxOptimization(lux: Float): Float {
        if (!enableLuxOptimize) {
            return lux
        }

        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        if (hour < 21 && hour > 6) {
            if (lux <= 2f) {
                // 不在深夜时段，且光线传感器数值小于2，返回2，避免正常使用时间误入暗光模式，导致屏幕太暗
                return 2f
            }
        } else if (lux < 1f) {
            return 0f
        }
        return lux
    }

    private val nightTime = 20 * 60 + 50 // 夜晚时间 20:50
    private val lateNightTime = 22*60; // 深夜时间 22:00
    private val morningTime = 7 * 60 + 30 // 早晨 7:30
    private val dawnTime = 6 * 60 // 黎明 6：00
    fun brightnessOptimization(intensity: Float, screentMinLight: Int): Double {
        var offsetValue: Double = 0.toDouble();
        val calendar = Calendar.getInstance()
        val value = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        // 在深夜光线极暗的情况下
        if (intensity > 0f && (value >= nightTime || value < morningTime)) {
            if (value >= nightTime) {
                offsetValue -= ((value - nightTime) / 10.0 / 23) // 24:00 - 20:30 = 210 , 210 / 10.0 / 23 ≈ 0.9
            } else if (value < dawnTime) {
                offsetValue -= 0.9
            } else if (value < morningTime) {
                offsetValue -= ((morningTime - value) / 10.0 / 10) // 7:30 - 6:00 = 90 , 90 / 10.0 / 10 ≈ 0.9
            }
        }

        return offsetValue * intensity
    }
}
