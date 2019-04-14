package com.omarea.filter

import android.content.Context
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

    private var filterConfig = "Samples.json"

    constructor (context: Context) {
        this.readConfig(context)
    }

    public fun readConfig(context: Context, officialOnlay: Boolean = false) {
        try {
            samples.clear()
            val customConfig = FileWrite.getPrivateFilePath(context, filterConfig)
            val configFile = if ((!officialOnlay) && File(customConfig).exists()) File(customConfig).readBytes() else context.assets.open(filterConfig).readBytes()

            val jsonObject = JSONObject(String(configFile))
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
                if (screentMinLight > FilterViewConfig.FILTER_BRIGHTNESS_MAX) {
                    screentMinLight = FilterViewConfig.FILTER_BRIGHTNESS_MAX
                } else if (screentMinLight < FilterViewConfig.FILTER_BRIGHTNESS_MIN) {
                    screentMinLight = FilterViewConfig.FILTER_BRIGHTNESS_MIN
                }
                this.screentMinLight = screentMinLight
            } else {
                this.screentMinLight = FilterViewConfig.FILTER_BRIGHTNESS_MAX
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
        val jsonStr = config.toString(2)

        if (!FileWrite.writePrivateFile(jsonStr.toByteArray(Charset.defaultCharset()), filterConfig, context)) {
            Log.e("ScreenFilter", "存储样本失败！！！")
        }
    }

    /**
     * 添加样本数据
     */
    public fun addSample(lux: Int, sample: Int) {
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
     */
    public fun getFilterConfig(lux: Int, offset: Double): FilterViewConfig {
        val sampleValue = getVitualSample(lux)
        if (sampleValue != null) {
            return FilterViewConfig.getConfigByBrightness((sampleValue  * (1 + offset)).toInt(), screentMinLight)
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

    /**
     * 获取虚拟样本，根据已有样本计算数值
     */
    public fun getVitualSample(lux: Int): Int? {
        if (samples.size > 1) {
            var sampleValue = 0
            if (samples.containsKey(lux)) {
                sampleValue = samples.get(lux) as Int
            } else {
                val keys = samples.keys.sorted()
                var min = keys[0]
                var max = keys[keys.size - 1]
                for (item in keys) {
                    if (item < lux) {
                        min = item
                    } else {
                        max = item
                        break
                    }
                }

                val minSample = if (min > lux) FilterViewConfig.FILTER_BRIGHTNESS_MAX else (samples[min] as Int)
                val maxSample = samples[max] as Int

                val ratio = (lux - min) * 1.0 / (max - min)

                if (minSample != maxSample) {
                    sampleValue = minSample + ((maxSample - minSample) * ratio).toInt()
                } else {
                    sampleValue = minSample
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
}