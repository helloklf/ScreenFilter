package com.omarea.filter

class FilterViewConfig {
    constructor() {

    }

    // 0 - 255 // 255表示全黑，0 表示不使用滤镜
    internal var filterAlpha = 0
    // 0  - 1000，1000 表示最大亮度
    internal var filterBrightness = FILTER_BRIGHTNESS_MAX
    // 是否开启像素滤镜
    internal var pixelFilter = false

    /**
     * 获取亮度的比率（0.00 ~ 1.00）
     */
    internal fun getFilterBrightnessRatio (): Float {
        return (filterBrightness.toFloat() / FILTER_BRIGHTNESS_MAX)
    }

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
        fun getConfigByBrightness(brightness: Int, screentMinLight: Int = FILTER_BRIGHTNESS_MAX, allowPixelFilter: Boolean = false): FilterViewConfig {
            val brightnessValue = if (brightness < FILTER_BRIGHTNESS_MIN) FILTER_BRIGHTNESS_MIN else if (brightness > FILTER_BRIGHTNESS_MAX) FILTER_BRIGHTNESS_MAX else brightness

            return getConfigByRatio(getBrightnessRatio(brightnessValue), screentMinLight, allowPixelFilter)
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

        /**
         * 根据亮度比例（0.01-1.0）计算滤镜设置
         */
        fun getConfigByRatio(ratio: Float, screentMinLight: Int, allowPixelFilter: Boolean = false): FilterViewConfig {
            val config = getDefault()
            if (ratio > 1 || ratio < 0) {
                //
                return config
            }

            if (screentMinLight == FILTER_BRIGHTNESS_MAX && !allowPixelFilter) { // 如果屏幕最低亮度为1000 并且没开像素滤镜
                config.filterAlpha = FILTER_MAX_ALPHA - (ratio * FILTER_MAX_ALPHA).toInt()
                config.filterBrightness = FILTER_BRIGHTNESS_MAX
                config.pixelFilter = false
            } else if (screentMinLight == FILTER_BRIGHTNESS_MAX && allowPixelFilter) { // 如果屏幕最低亮度为1000，并且开了像素滤镜
                if (ratio < 0.5) { // 如果亮度低于50%，开启像素滤镜
                    val filterAlpha = FILTER_MAX_ALPHA - (ratio * 0.5 * FILTER_MAX_ALPHA).toInt() // 开启滤镜等于屏幕亮度减半
                    config.filterAlpha = filterAlpha
                    config.filterBrightness = FILTER_BRIGHTNESS_MAX
                    config.pixelFilter = true
                } else { // 亮度大于50%不启用像素滤镜
                    config.filterAlpha = FILTER_MAX_ALPHA - (ratio * FILTER_MAX_ALPHA).toInt()
                    config.filterBrightness = FILTER_BRIGHTNESS_MAX
                    config.pixelFilter = false
                }
            } else { // 如果屏幕最低亮度不到1000
                // 如果亮度还没低于屏幕最低亮度限制
                if (ratio >= screentMinLight.toFloat() / FILTER_BRIGHTNESS_MAX) {
                    config.filterAlpha = 0
                    config.filterBrightness = (ratio * FILTER_BRIGHTNESS_MAX).toInt()
                } else if (!allowPixelFilter || ratio >= (screentMinLight.toFloat() / FILTER_BRIGHTNESS_MAX / 2)) { // 如果没开像素滤镜 或者 未达到需要开像素滤镜的水平
                    // 如果已经低于最低屏幕亮度限制，进行换算
                    val filterAlpha = FILTER_MAX_ALPHA - (ratio * FILTER_BRIGHTNESS_MAX / screentMinLight * FILTER_MAX_ALPHA).toInt()
                    config.filterAlpha = filterAlpha
                    config.filterBrightness = screentMinLight
                } else {
                    // 如果已经低于最低屏幕亮度限制，进行换算
                    val filterAlpha = FILTER_MAX_ALPHA - (ratio * FILTER_BRIGHTNESS_MAX / screentMinLight * 2 * FILTER_MAX_ALPHA).toInt()
                    config.filterAlpha = filterAlpha
                    config.filterBrightness = screentMinLight
                    config.pixelFilter = true
                }
            }
            return config
        }
    }
}
