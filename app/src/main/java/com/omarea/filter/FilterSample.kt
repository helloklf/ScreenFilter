package com.omarea.filter

import android.content.Context
import com.omarea.shared.FileWrite
import org.json.JSONArray
import java.io.File
import java.nio.charset.Charset

public class FilterSample {
    companion object {
        // 样本数据（lux, Sample）
        private var samples = HashMap<Double, Int>()
    }
    private fun  init() {
        val customConfig = FilterApp.context.assets.open("ref.json").readBytes()

        val originData = JSONArray(String(customConfig, Charset.defaultCharset()))
        for (index in 0 until originData.length()) {
            val sample = originData.getJSONArray(index)
            val filter = sample.getInt(0)
            val ratio = sample.getDouble(1)

            if (!samples.containsKey(ratio)) {
                samples.put(ratio, filter)
            }
        }
    }
    public fun getFilterAlpha(targetRatio: Int):Int? {
        return getFilterAlpha(if (targetRatio > FilterViewConfig.FILTER_MAX_ALPHA) {
            1.0
        } else if (targetRatio < 1) {
            0.001
        } else {
            targetRatio.toDouble() / FilterViewConfig.FILTER_MAX_ALPHA
        })
    }

    public fun getFilterAlpha(targetRatio: Double):Int? {
        if (samples.isEmpty()) {
            init()
        }

        val ratio: Double = if (targetRatio > 1) {
            1.0
        } else if (targetRatio < 0.001) {
            0.001
        } else {
            targetRatio
        }

        if (samples.size > 1) {
            var sampleValue = 0
            // 如果有现成的样本 直接获取样本值
            if (samples.containsKey(ratio)) {
                sampleValue = samples.get(ratio) as Int
            } else {
                // 计算生成虚拟样本
                val keys = samples.keys.sorted()
                var rangeMin = keys.first()
                var rangeMax = keys.last()

                return FilterViewConfig.FILTER_MAX_ALPHA - (if (ratio < rangeMin) {
                        samples[rangeMin]!!
                    } else if (ratio > rangeMax) {
                        samples[rangeMax]!!
                    } else {
                        for (sampleKey in keys) {
                            if (ratio > sampleKey) {
                                rangeMin = sampleKey
                            } else {
                                rangeMax = sampleKey
                                break
                            }
                        }
                        val rangeLeftBrightness = samples.get(rangeMin)!!
                        val rangeRightBrightness = samples.get(rangeMax)!!
                        if (rangeLeftBrightness == rangeRightBrightness || rangeMin == rangeMax) {
                            return rangeLeftBrightness
                        }
                        (rangeLeftBrightness + ((rangeRightBrightness - rangeLeftBrightness) * (ratio - rangeMin) / (rangeMax - rangeMin)).toInt())
                    }
                )
            }
            return sampleValue
        }
        return null
    }
}