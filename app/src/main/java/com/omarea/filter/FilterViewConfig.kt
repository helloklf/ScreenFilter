package com.omarea.filter

class FilterViewConfig {
    constructor() {

    }

    // 0 - 255 // 255表示全黑，0 表示不使用滤镜
    internal var filterAlpha = 0
    // 0  - 1000，1000 表示最大亮度
    internal var filterBrightness = FILTER_BRIGHTNESS_MAX

    companion object {
        var FILTER_BRIGHTNESS_MAX = 1000
        var FILTER_BRIGHTNESS_MIN = 1
        var FILTER_MAX_ALPHA = 240

        fun getDefault(): FilterViewConfig {
            return FilterViewConfig()
        }

        /**
         * 根据所需的亮度值获取滤镜配置
         */
        fun getConfigByBrightness(brightness: Int, screentMinLight: Int = FILTER_BRIGHTNESS_MAX): FilterViewConfig {
            if (screentMinLight == FILTER_BRIGHTNESS_MAX) {
                val config = FilterViewConfig.getDefault()
                config.filterBrightness = FILTER_BRIGHTNESS_MAX
                config.filterAlpha = FILTER_MAX_ALPHA - (brightness * FILTER_MAX_ALPHA / 1000).toInt()
                return config
            } else {
                return getConfigByRatio(getBrightnessRatio(brightness), screentMinLight)
            }
        }

        /**
         * 根据亮度值获取亮度比率（0.05~1）
         */
        fun getBrightnessRatio(brightness: Int): Float {
            if (brightness > 1000) {
                return 1f
            } else if (brightness < 5) {
                return 0.05f
            }
            return (brightness / 1000.0).toFloat()
        }

        fun getConfigByRatio(ratio: Float, screentMinLight: Int): FilterViewConfig {
            val config = FilterViewConfig.getDefault()
            if (ratio > 1 || ratio < 0) {
                //
                return config
            }

            if (screentMinLight == FILTER_BRIGHTNESS_MAX) {
                config.filterAlpha = FILTER_MAX_ALPHA - (ratio * FILTER_MAX_ALPHA).toInt()
                config.filterBrightness = FILTER_BRIGHTNESS_MAX
            } else {
                // 如果亮度还没低于屏幕最低亮度限制
                if (ratio >= screentMinLight) {
                    config.filterAlpha = 0
                    config.filterBrightness = (ratio * 1000).toInt()
                } else {
                    // 如果已经低于最低屏幕亮度限制，进行换算
                    val filterAlpha = FILTER_MAX_ALPHA - (ratio * FILTER_BRIGHTNESS_MAX / screentMinLight * FILTER_MAX_ALPHA).toInt()
                    config.filterAlpha = filterAlpha
                    config.filterBrightness = screentMinLight
                }
            }
            return config
        }
    }
}
