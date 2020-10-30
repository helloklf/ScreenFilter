package com.omarea.filter

import org.json.JSONArray
import java.nio.charset.Charset
import java.util.*
import kotlin.collections.LinkedHashMap

public class FilterSample {
    companion object {
        // 样本数据（lux, Sample）
        private var samples = TreeMap<Double, Int>()
    }

    private fun init() {
        val customConfig = FilterApp.context.assets.open("ref.json").readBytes()

        val originData = JSONArray(String(customConfig, Charset.defaultCharset()))
        val temp = LinkedHashMap<Double, Int>()
        for (index in 0 until originData.length()) {
            val sample = originData.getJSONArray(index)
            val filter = sample.getInt(0)
            val ratio = sample.getDouble(1)

            if (!temp.containsKey(ratio)) {
                temp.put(ratio, filter)
            }
        }
        temp.keys.sorted().forEach {
            temp.get(it)?.let { it1 -> samples.put(it, it1) }
        }
    }

    public fun getFilterAlpha(targetRatio: Int): Int? {
        return getFilterAlpha(if (targetRatio > FilterViewConfig.FILTER_MAX_ALPHA) {
            1.0
        } else if (targetRatio < 1) {
            0.001
        } else {
            targetRatio.toDouble() / FilterViewConfig.FILTER_MAX_ALPHA
        })
    }

    public fun getFilterAlpha(targetRatio: Double): Int? {
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
            // 如果有现成的样本 直接获取样本值
            if (samples.containsKey(ratio)) {
                return FilterViewConfig.FILTER_MAX_ALPHA - (samples.get(ratio) as Int)
            } else {
                // 计算生成虚拟样本
                var rangeMin = samples.keys.first()
                var rangeMax = samples.keys.last()

                val alpha = FilterViewConfig.FILTER_MAX_ALPHA - (if (ratio < rangeMin) {
                    samples[rangeMin]!!
                } else if (ratio > rangeMax) {
                    samples[rangeMax]!!
                } else {
                    for (sampleKey in samples.keys) {
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
                return alpha
            }
        }
        return null
    }
}