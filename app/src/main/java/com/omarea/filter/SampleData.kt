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
    private var screentMinLight  = 100
    //
    private var filterExchangeRate = 2.0

    private var filterConfig = "filterConfig.json"

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
                if (screentMinLight > 100) {
                    screentMinLight = 100
                } else if (screentMinLight < 1) {
                    screentMinLight = 1
                }
                this.screentMinLight = screentMinLight
            } else {
                this.screentMinLight = 100
            }
            if (jsonObject.has("filterExchangeRate")) {
                var filterExchangeRate = jsonObject.getDouble("filterExchangeRate")
                if (filterExchangeRate > 2.1) {
                    filterExchangeRate = 2.1
                } else if (filterExchangeRate < 1.9) {
                    filterExchangeRate = 1.9
                }
                this.filterExchangeRate = filterExchangeRate
            } else {
                this.filterExchangeRate = 2.02
            }
            Log.d("obj", "1")
        } catch (ex: Exception) {
            samples.put(0, 240)
            samples.put(1000, 0)
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
        config.put("filterExchangeRate", this.filterExchangeRate)
        val jsonStr = config.toString(2)

        if (FileWrite.writePrivateFile(jsonStr.toByteArray(Charset.defaultCharset()), filterConfig, context)) {

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
    public fun getFilterConfig(lux: Int): FilterViewConfig {
        val sampleValue = getVitualSample(lux)
        if (sampleValue != null) {
            if (this.screentMinLight == 100) {
                val config = FilterViewConfig()
                config.filterAlpha = sampleValue
                return config
            } else {
                val x = 2.424

                val ratio = sampleValue / x // 滤镜浓度百分比（越高表示屏幕越暗）

                return getFilterConfigByRatio(ratio)
            }
        }
        return FilterViewConfig()
    }

    /**
     * 根据意图亮度百分比，获取滤镜配置
     */
    public fun getFilterConfigByRatio(ratio:Double): FilterViewConfig {
        val config = FilterViewConfig()

        if (this.screentMinLight == 100) {
            config.filterAlpha = (240 * ratio / 100.0).toInt()
            config.systemBrightness = 100
        } else {
            if (ratio <= 100 - this.screentMinLight) { // 如果还能通过调整物理亮度解决问题，那就别开滤镜
                config.filterAlpha = 0
                config.systemBrightness = (100 - ratio).toInt()
            } else {
                val sampleValue = 240 * ratio / 100.0
                val screenRatio = (100 - this.screentMinLight)
                val filterRatio = ratio - screenRatio
                val filterAlpha = ((screenRatio * 0.8 + filterRatio) * sampleValue / 100.0).toInt()
                config.filterAlpha = filterAlpha
                config.systemBrightness = this.screentMinLight
            }
        }
        return config
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

                val minSample = if (min > lux) 240 else (samples[min] as Int)
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
     * 获取屏幕最低亮度百分比
     */
    public fun setScreentMinLight(value: Int) {
        if (value > 100) {
            this.screentMinLight = 100
        } else if (value < 1) {
            this.screentMinLight = 1
        } else {
            this.screentMinLight = value
        }
    }

    /**
     *
     */
    public fun getFilterExchangeRate(): Double {
        return filterExchangeRate
    }

    /**
     *
     */
    public fun setFilterExchangeRate(value: Double) {
        if (value > 2.1) {
            this.filterExchangeRate = 2.1
        } else if (value < 1.9) {
            this.filterExchangeRate = 1.9
        } else {
            this.filterExchangeRate = value
        }
    }
}
