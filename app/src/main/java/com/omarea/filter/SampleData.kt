package com.omarea.filter

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.Log
import com.omarea.shared.FileWrite
import org.json.JSONObject
import java.io.File
import java.nio.charset.Charset

/**
 * 样本数据
 */
class SampleData {
    // 样本数据（lux, Sample）
    private var samples = HashMap<Int, Int>()

    // 屏幕亮度低于此值时才开启滤镜功能
    private var screentMinLight = FilterViewConfig.FILTER_BRIGHTNESS_MAX
    // 滤镜颜色
    private var filterColor:Int = Color.BLACK

    private var filterConfig = "Samples.json"

    constructor (context: Context) {
        this.readConfig(context)
    }

    private fun getOriginConfigObject(context: Context, officialOnlay: Boolean): JSONObject {
        val customConfig = FileWrite.getPrivateFilePath(context, filterConfig)
        val configFile = if ((!officialOnlay) && File(customConfig).exists()) {
            File(customConfig).readBytes()
        } else {
            try {
                context.assets.open("for_" + Build.PRODUCT + ".json").readBytes()
            } catch (ex: java.lang.Exception) {
                context.assets.open("amoled.json").readBytes()
            }
        }

        val jsonObject = JSONObject(String(configFile))

        return jsonObject
    }

    public fun readConfig(context: Context, officialOnlay: Boolean = false) {
        try {
            val jsonObject = getOriginConfigObject(context, officialOnlay)

            samples.clear()
            val samples = jsonObject.getJSONObject("samples")
            for (item in samples.keys()) {
                val lux = item.toInt()
                val filterAlpha = samples.getInt(item)

                if (this.samples.containsKey(lux)) {
                    this.samples.remove(lux)
                }
                this.samples.put(lux, filterAlpha)
            }
            if (jsonObject.has("screentMinLight")) {
                var screentMinLight = jsonObject.getInt("screentMinLight")
                if (screentMinLight > FilterViewConfig.FILTER_BRIGHTNESS_MAX || screentMinLight < FilterViewConfig.FILTER_BRIGHTNESS_MIN) {
                    screentMinLight = FilterViewConfig.FILTER_BRIGHTNESS_MAX
                }
                this.screentMinLight = screentMinLight
            } else {
                this.screentMinLight = FilterViewConfig.FILTER_BRIGHTNESS_MAX
            }
            if (jsonObject.has("filterColor")) {
                this.filterColor = jsonObject.getInt("filterColor")
            } else {
                this.filterColor = Color.BLACK
            }
            if (officialOnlay) {
                // Xiaomi MIX3、CC9、CC9(Meitu)、M9、K20 Pro
                if (Build.PRODUCT == "perseus" || Build.PRODUCT == "pyxis"  || Build.PRODUCT == "vela" || Build.PRODUCT == "cepheus" || Build.PRODUCT == "raphael") {
                    setScreentMinLight((FilterViewConfig.FILTER_BRIGHTNESS_MAX * 0.3).toInt())
                } else if (Build.PRODUCT == "tucana") { // Xiaomi CC9 Pro
                    setScreentMinLight((FilterViewConfig.FILTER_BRIGHTNESS_MAX * 0.7).toInt())
                }
            }
        } catch (ex: Exception) {
            samples.put(0, FilterViewConfig.FILTER_BRIGHTNESS_MIN)
            samples.put(10000, FilterViewConfig.FILTER_BRIGHTNESS_MAX)
        }
    }

    fun saveConfig(context: Context) {
        val sampleConfig = JSONObject()
        for (sample in samples) {
            sampleConfig.put(sample.key.toString(), sample.value)
        }
        val config = JSONObject()
        config.putOpt("samples", sampleConfig)
        config.put("screentMinLight", this.screentMinLight)
        config.put("filterColor", this.filterColor)
        val jsonStr = config.toString(2)

        if (!FileWrite.writePrivateFile(jsonStr.toByteArray(Charset.defaultCharset()), filterConfig, context)) {
            Log.e("ScreenFilter", "存储样本失败！！！")
        }
    }

    /**
     * 添加样本数据
     */
    public fun addSample(lux: Int, sample: Int) {
        // 查找现存样本中与新增样本冲突的旧样本
        val invalidSamples = samples.filter {
            (it.key >= lux && it.value <= sample) || (it.key <= lux && it.value >= sample)
        }
        invalidSamples.forEach {
            samples.remove(it.key)
        }

        if (!samples.containsKey(lux)) {
            samples.put(lux, sample)
        }
    }

    /**
     * 移除样本
     */
    public fun removeSample(lux: Int) {
        if (samples.containsKey(lux)) {
            samples.remove(lux)
        }
    }

    /**
     * 替换样本
     */
    public fun replaceSample(lux: Int, sample: Int) {
        removeSample(lux)
        addSample(lux, sample)
    }

    /**
     * 获取样本（）
     */
    public fun getSample(lux: Int): Int {
        if (samples.containsKey(lux)) {
            return samples.get(lux) as Int
        }
        return -1
    }

    /**
     * 根据样本计算滤镜浓度和屏幕亮度
     * @param staticOffset 固定亮度增益 按总亮度的比例增加亮度
     * @param offsetpractical 按实际亮度的比例增加亮度
     */
    public fun getFilterConfig(lux: Float, staticOffset: Double = 0.toDouble(), offsetpractical: Double = 0.toDouble()): FilterViewConfig {
        val sampleValue = getVitualSample(lux)
        if (sampleValue != null) {
            val brightness = ((sampleValue + (FilterViewConfig.FILTER_BRIGHTNESS_MAX * staticOffset)) * (1 + offsetpractical)).toInt()
            return FilterViewConfig.getConfigByBrightness(brightness, screentMinLight)
        }
        return FilterViewConfig.getDefault()
    }

    /**
     * 根据意图亮度百分比，获取滤镜配置
     */
    public fun getFilterConfigByRatio(ratio: Float): FilterViewConfig {
        return FilterViewConfig.getConfigByRatio(ratio, screentMinLight)
    }

    /**
     * 根据意向屏幕亮度获取配置
     */
    public fun getConfigByBrightness(brightness: Int): FilterViewConfig {
        return FilterViewConfig.getConfigByBrightness(brightness, screentMinLight)
    }

    public fun getVitualSample(lux: Int): Int? {
        return getVitualSample(lux.toFloat())
    }

    /**
     * 获取虚拟样本，根据已有样本计算数值
     */
    public fun getVitualSample(lux: Float): Int? {
        if (samples.size > 1) {
            var sampleValue = 0
            val intValue = lux.toInt()
            // 如果有现成的样本 直接获取样本值
            if (intValue.toFloat() == lux && samples.containsKey(intValue)) {
                sampleValue = samples.get(intValue) as Int
            } else {
                // 计算生成虚拟样本
                val keys = samples.keys.sorted()
                var rangeLeftLux = keys.first()
                var rangeRightLux = keys.last()

                if (lux < rangeLeftLux) {
                    return samples[rangeLeftLux]
                } else if (lux > rangeRightLux) {
                    return samples[rangeRightLux]
                } else {
                    for (sampleLux in keys) {
                        if (lux > sampleLux) {
                            rangeLeftLux = sampleLux
                        } else {
                            rangeRightLux = sampleLux
                            break
                        }
                    }
                    val rangeLeftBrightness = samples.get(rangeLeftLux)!!
                    val rangeRightBrightness = samples.get(rangeRightLux)!!
                    if (rangeLeftBrightness == rangeRightBrightness || rangeLeftLux == rangeRightLux) {
                        return rangeLeftBrightness
                    }
                    return rangeLeftBrightness + ((rangeRightBrightness - rangeLeftBrightness) * (lux - rangeLeftLux) / (rangeRightLux - rangeLeftLux)).toInt()
                }
            }
            return sampleValue
        }
        return null
    }

    /**
     * 获取所有样本
     */
    public fun getAllSamples(): HashMap<Int, Int> {
        return this.samples
    }

    /**
     * 设置屏幕最低亮度百分比
     */
    public fun getScreentMinLight(): Int {
        return screentMinLight
    }

    /**
     * 设置屏幕最低亮度百分比
     */
    public fun setScreentMinLight(value: Int) {
        if (value > FilterViewConfig.FILTER_BRIGHTNESS_MAX) {
            this.screentMinLight = FilterViewConfig.FILTER_BRIGHTNESS_MAX
        } else if (value < FilterViewConfig.FILTER_BRIGHTNESS_MIN) {
            this.screentMinLight = FilterViewConfig.FILTER_BRIGHTNESS_MIN
        } else {
            this.screentMinLight = value
        }
    }

    public fun setFilterColor(color: Int) {
        this.filterColor = color
    }

    public fun getFilterColor(): Int {
        return filterColor
    }
}