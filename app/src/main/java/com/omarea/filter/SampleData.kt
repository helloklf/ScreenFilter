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
    public fun getVitualSample(lux: Int): Int {
        if (samples.size > 1) {
            if (samples.containsKey(lux)) {
                return samples.get(lux) as Int
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

            val minSample = if (min > lux) 240 else (samples[min] as Int)
            val maxSample = samples[max] as Int

            val ratio = (lux - min) * 1.0 / (max - min)

            if (minSample != maxSample) {
                return minSample + ((maxSample - minSample) * ratio).toInt()
            } else {
                return minSample
            }
        }
        return -1
    }

    public fun getAllSamples(): HashMap<Int, Int> {
        return this.samples
    }
}
