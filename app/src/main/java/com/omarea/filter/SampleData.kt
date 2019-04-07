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
    private var samples = HashMap<Int, Sample>()
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
                val sampleConfig = samples.getJSONObject(item)
                val sample = Sample()
                sample.systemBrightness = sampleConfig.getInt("systemBrightness")
                sample.filterAlpha = sampleConfig.getInt("filterAlpha")
                sample.pixelFilter = sampleConfig.getBoolean("pixelFilter")

                if (this.samples.containsKey(lux)) {
                    this.samples.remove(lux)
                }
                this.samples.put(lux, sample)
            }
            Log.d("obj", "1")
        } catch (ex: Exception) {
            val minLight = Sample()
            minLight.systemBrightness = 1000
            minLight.filterAlpha = 255
            samples.put(0, minLight)

            val maxLight = Sample()
            maxLight.systemBrightness = 1000
            maxLight.filterAlpha = 0
            samples.put(1000, maxLight)
        }
    }

    fun saveConfig(context: Context) {
        val sampleConfig = JSONObject()
        for (item in samples) {
            val sample = JSONObject()
            sample.put("systemBrightness", item.value.systemBrightness)
            sample.put("filterAlpha", item.value.filterAlpha)
            sample.put("pixelFilter", item.value.pixelFilter)
            sampleConfig.putOpt(item.key.toString(), sample)
        }
        val config = JSONObject()
        config.putOpt("samples", sampleConfig)
        val jsonStr = config.toString(2)

        if (FileWrite.writePrivateFile(jsonStr.toByteArray(Charset.defaultCharset()), filterConfig, context)) {

        }
    }

    /**
     * 添加样本数据
     */
    public fun addSample(lux: Int, sample: Sample) {
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
    public fun replaceSample(lux: Int, sample: Sample) {
        removeSample(lux)
        addSample(lux, sample)
    }

    /**
     * 获取样本（）
     */
    public fun getSample(lux: Int): Sample? {
        if (samples.containsKey(lux)) {
            return samples.get(lux)
        }
        return null
    }

    private fun getDefaultMinSample(): Sample {
        val sample = Sample()
        sample.filterAlpha = 240
        sample.systemBrightness = 1024
        sample.pixelFilter = false
        return sample
    }

    private fun getDefaultMaxSample(): Sample {
        val sample = Sample()
        sample.filterAlpha = 0
        sample.systemBrightness = 1024
        sample.pixelFilter = false
        return sample
    }

    /**
     * 获取虚拟样本（根据其它样本获取某个环境光下的理论样本数值）
     */
    public fun getVitualSample(lux: Int): Sample? {
        if (samples.size > 1) {
            if (samples.containsKey(lux)) {
                return samples.get(lux)
            }

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

            val minSample = if (min > lux) getDefaultMinSample() else samples[min]
            val maxSample = samples[max]

            val vitualSample = Sample()
            val ratio = (lux - min) * 1.0 / (max - min)

            if (minSample!!.systemBrightness != maxSample!!.systemBrightness) {
                vitualSample.systemBrightness = minSample.systemBrightness + ((maxSample.systemBrightness - minSample.systemBrightness) * ratio).toInt()
            } else {
                vitualSample.systemBrightness = minSample.systemBrightness
            }

            if (minSample.filterAlpha != maxSample.filterAlpha) {
                vitualSample.filterAlpha = minSample.filterAlpha + ((maxSample.filterAlpha - minSample.filterAlpha) * ratio).toInt()
            } else {
                vitualSample.filterAlpha = minSample.filterAlpha
            }

            // FIXME: 这个参数对显示亮度效果影响较大，需要在做考虑
            vitualSample.pixelFilter = minSample.pixelFilter
            return vitualSample
        }
        return null
    }

    public fun getAllSamples(): HashMap<Int, Sample> {
        return this.samples
    }
}
