package com.omarea.filter

class FilterViewConfig {
    // 0 - 1000 // 1000表示全黑，0 表示不使用滤镜
    internal var filterAlpha = 0
    // 0  - 1000，1000 表示最大亮度
    internal var filterBrightness = FILTER_BRIGHTNESS_MAX

    // 是否平滑变更
    internal var smoothChange = true

    /**
     * 获取亮度的比率（0.00 ~ 1.00）
     */
    internal fun getFilterBrightnessRatio(): Float {
        return (filterBrightness.toFloat() / FILTER_BRIGHTNESS_MAX)
    }

    companion object {
        var FILTER_BRIGHTNESS_MAX = 1000
        var FILTER_BRIGHTNESS_MIN = 1
        var FILTER_MAX_ALPHA = 940

        fun getDefault(): FilterViewConfig {
            return FilterViewConfig()
        }

        /**
         * 根据所需的亮度值获取滤镜配置
         */
        fun getConfigByBrightness(brightness: Int, screentMinLight: Int = FILTER_BRIGHTNESS_MAX): FilterViewConfig {
            val brightnessValue = if (brightness < FILTER_BRIGHTNESS_MIN) FILTER_BRIGHTNESS_MIN else if (brightness > FILTER_BRIGHTNESS_MAX) FILTER_BRIGHTNESS_MAX else brightness
            if (screentMinLight == FILTER_BRIGHTNESS_MAX) {
                val config = getDefault()
                config.filterBrightness = FILTER_BRIGHTNESS_MAX
                config.filterAlpha = FILTER_MAX_ALPHA - (brightnessValue * FILTER_MAX_ALPHA / 1000)
                return config
            } else {
                return getConfigByRatio(getBrightnessRatio(brightnessValue), screentMinLight)
            }
        }

        /**
         * 根据亮度值获取亮度比率（0.01~1）
         */
        fun getBrightnessRatio(brightness: Int): Float {
            if (brightness > 1000) {
                return 1f
            } else if (brightness < 1) {
                return 0.01f
            }
            return (brightness / 1000.0).toFloat()
        }

        fun getConfigByRatio(ratio: Float, screentMinLight: Int): FilterViewConfig {
            val config = getDefault()
            var avalibRatio = ratio
            if (ratio > 1) {
                avalibRatio = 1f
            } else if (ratio < 0f) {
                avalibRatio = 0.01f
            }

            if (screentMinLight == FILTER_BRIGHTNESS_MAX) {
                config.filterAlpha = FILTER_MAX_ALPHA - (avalibRatio * FILTER_MAX_ALPHA).toInt()
                config.filterBrightness = FILTER_BRIGHTNESS_MAX
            } else {
                // 如果亮度还没低于屏幕最低亮度限制
                if (avalibRatio >= screentMinLight.toFloat() / FILTER_BRIGHTNESS_MAX) {
                    config.filterAlpha = 0
                    config.filterBrightness = (avalibRatio * FILTER_BRIGHTNESS_MAX).toInt()
                } else {
                    // 如果已经低于最低屏幕亮度限制，进行换算
                    // to:30% b:100% f:70%
                    // to:30% b:50% f:40%
                    // to:30%.b:40% f:25%
                    // f = 1 - (to / b)
                    val filterAlpha = ((1 - (avalibRatio * FILTER_BRIGHTNESS_MAX / screentMinLight)) * FILTER_MAX_ALPHA).toInt()
                    config.filterAlpha = filterAlpha
                    config.filterBrightness = screentMinLight
                }
            }
            return config
        }
    }
}
