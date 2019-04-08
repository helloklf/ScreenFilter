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
     * 获取虚拟样本（根据其它样本获取某个环境光下的理论样本数值）
     */
    public fun getVitualSample(lux: Int): FilterViewConfig {
        val config = FilterViewConfig()
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
            if (this.screentMinLight == 100) {
                config.filterAlpha = sampleValue
                return config
            } else {
                val ratio = sampleValue / 2.4 // 2.0 = 240 / 100.0，由于滤镜强度240亮度和屏幕亮度1%相近，因此以240作为有效最大值换算
                if (ratio < (100 - this.screentMinLight)) {
                    val screenLight = 100 - ratio.toInt()
                    val offset = (((100 - screenLight) / 4) * sampleValue / 100.0).toInt()
                    if (screenLight - offset < this.screentMinLight) {
                        config.filterAlpha = (screenLight + offset - this.screentMinLight)
                        config.systemBrightness = this.screentMinLight
                    } else {
                        config.filterAlpha = 0
                        config.systemBrightness = screenLight - offset
                    }
                } else {
                    config.filterAlpha = (sampleValue * ((this.screentMinLight + ((100 - this.screentMinLight) / 4)) / 100.0)).toInt()
                    config.systemBrightness = this.screentMinLight
                }
                return config
            }
        }
        return config
    }

    public fun getAllSamples(): HashMap<Int, Int> {
        return this.samples
    }

    public fun getScreentMinLight(): Int {
        return screentMinLight
    }

    public fun setScreentMinLight(value: Int) {
        if (value > 100) {
            this.screentMinLight = 100
        } else if (value < 1) {
            this.screentMinLight = 1
        } else {
            this.screentMinLight = value
        }
    }
}
