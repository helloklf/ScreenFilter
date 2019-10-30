package com.omarea.filter

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import com.omarea.filter.common.ViewHelper
import java.util.*
import kotlin.math.abs

class FilterViewManager(private var context: Context){
    private val mWindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val display = mWindowManager.defaultDisplay
    private var config = context.getSharedPreferences(SpfConfig.FILTER_SPF, Context.MODE_PRIVATE)
    private var viewHelper = ViewHelper(context)

    private var popupView: View? = null
    private var filterView: FilterView? = null
    private var filterBrightness = 0 // 当前由滤镜控制的屏幕亮度
    private var currentAlpha: Int = 0
    private var valueAnimator: ValueAnimator? = null
    private var isPaused = false
    private var lastFilterViewConfig: FilterViewConfig? = null

    fun open() {
        if (popupView != null) {
            close()
        }

        val params = WindowManager.LayoutParams()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        params.gravity = Gravity.NO_GRAVITY
        params.format = PixelFormat.TRANSLUCENT
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        if (config.getBoolean(SpfConfig.HARDWARE_ACCELERATED, SpfConfig.HARDWARE_ACCELERATED_DEFAULT)) {
            params.flags = params.flags.or(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        }

        val p = Point()
        display.getRealSize(p)
        var maxSize = p.y
        if (p.x > maxSize) {
            maxSize = p.x
        }
        params.width = maxSize + viewHelper.getNavBarHeight() // p.x // 直接按屏幕最大宽度x最大宽度显示，避免屏幕旋转后盖不住全屏
        params.height = maxSize + viewHelper.getNavBarHeight()

        params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE // BRIGHTNESS_OVERRIDE_FULL

        popupView = LayoutInflater.from(context).inflate(R.layout.filter, null)
        mWindowManager.addView(popupView, params)
        filterView = popupView!!.findViewById(R.id.filter_view)
    }

    fun close() {
        stopUpdate()
        currentAlpha = 0
        lastFilterViewConfig = null
        isPaused = false
        filterBrightness = -1

        if (popupView != null) {
            mWindowManager.removeView(popupView)
            popupView = null
        }
    }

    fun updateSize() {
        val params = layoutParams
        params?.run {
            val p = Point()
            display.getRealSize(p)
            var maxSize = p.y
            if (p.x > maxSize) {
                maxSize = p.x
            }
            width = maxSize + viewHelper.getNavBarHeight() // p.x // 直接按屏幕最大宽度x最大宽度显示，避免屏幕旋转后盖不住全屏
            height = maxSize + viewHelper.getNavBarHeight()
            mWindowManager.updateViewLayout(popupView, params)
        }
    }

    fun pause() {
        stopUpdate()
        if (currentAlpha != 0) {
            /*
            // updateFilterByConfig 不太适用于这种情况

            val layoutParams = this.layoutParams
            if (layoutParams != null) {
                val config = FilterViewConfig()
                val originBrightness = abs(layoutParams.screenBrightness)

                val screenBrightness = originBrightness - ((currentAlpha.toFloat() / FilterViewConfig.FILTER_MAX_ALPHA) * originBrightness)
                config.filterBrightness = (screenBrightness * FilterViewConfig.FILTER_BRIGHTNESS_MAX).toInt()
                config.filterAlpha = 0
                config.smoothChange = true
                updateFilterByConfig(config, false)
            }
            */
            val layoutParams = this.layoutParams
            if (layoutParams != null) {
                val originBrightness = abs(layoutParams.screenBrightness) * FilterViewConfig.FILTER_BRIGHTNESS_MAX
                val originAlpha = currentAlpha
                val targetBrightness = originBrightness - ((originAlpha.toFloat() / FilterViewConfig.FILTER_MAX_ALPHA) * originBrightness)

                brightnessTransAlpha(targetBrightness, originBrightness, targetBrightness)
            }
        }
        isPaused = true
    }

    fun resume() {
        isPaused = false
        stopUpdate()
        if (layoutParams != null && lastFilterViewConfig != null) {
            brightnessTransAlpha(filterBrightness.toFloat(), filterBrightness.toFloat(), lastFilterViewConfig!!.filterBrightness.toFloat())
        }
    }

    private fun brightnessTransAlpha(absBrightness:Float, originBrightness:Float, targetBrightness: Float) {
        val layoutParams = this.layoutParams
        if (layoutParams != null) {
            val distanceBrightness = originBrightness - targetBrightness

            val alphaFrames = LinkedList<Int>()
            val brightnessFrames = LinkedList<Int>()

            val frames = 35
            val stepBrightness = distanceBrightness / frames
            for (i in 1 .. frames) {
                val b = (originBrightness - (stepBrightness * i)).toInt()
                brightnessFrames.add(b)
                val alpha = 1 - (absBrightness / b)
                alphaFrames.add((alpha * FilterViewConfig.FILTER_MAX_ALPHA).toInt())
            }

            playFrames(alphaFrames, brightnessFrames, 1000)
        }
    }

    private val layoutParams: WindowManager.LayoutParams?
        get() {
            return popupView?.layoutParams as WindowManager.LayoutParams?
        }

    fun updateFilterByConfig(filterViewConfig: FilterViewConfig, backup: Boolean = true) {
        if (filterView != null) {
           if (!isPaused) {
               if (filterViewConfig.smoothChange) {
                   smoothUpdateFilter(filterViewConfig)
               } else {
                   fastUpdateFilter(filterViewConfig)
               }
           }

            if (backup) {
                lastFilterViewConfig = filterViewConfig
            }
        }
    }

    // 停止正在运行的滤镜更新动画
    private fun stopUpdate() {
        if (valueAnimator != null && valueAnimator!!.isRunning) {
            valueAnimator!!.cancel()
            valueAnimator = null
        }
    }

    // 使用平滑的动画更新滤镜
    private fun smoothUpdateFilter(filterViewConfig: FilterViewConfig) {
        stopUpdate()
        val layoutParams = this.layoutParams!!

        val perOld = this.currentAlpha
        val toAlpha = filterViewConfig.filterAlpha
        var alphaFrameCount: Int
        val alphaFrames = LinkedList<Int>()
        if (perOld != toAlpha) {
            val alphaDistance = toAlpha - perOld
            val absDistance = Math.abs(alphaDistance)
            alphaFrameCount = when {
                absDistance < 1 -> 1
                absDistance > 60 -> 60
                else -> absDistance
            }
            if (alphaFrameCount > 20 && !filterView!!.isHardwareAccelerated) {
                alphaFrameCount = 20
            }

            val stepByStep = alphaDistance / alphaFrameCount.toFloat()
            for (frame in 1 until alphaFrameCount) {
                alphaFrames.add(perOld + (frame * stepByStep).toInt())
            }
            alphaFrames.add(toAlpha)
        }

        val currentBrightness = (Math.abs(layoutParams.screenBrightness) * FilterViewConfig.FILTER_BRIGHTNESS_MAX).toInt()
        val toBrightness = filterViewConfig.filterBrightness
        var brightnessFrameCount:Int
        val brightnessFrames = LinkedList<Int>()
        if (toBrightness != currentBrightness) {
            val brightnessDistance = toBrightness - currentBrightness
            val absDistance = Math.abs(brightnessDistance)
            brightnessFrameCount = when {
                absDistance < 1 -> 1
                absDistance > 60 -> 60
                else -> absDistance
            }
            if (brightnessFrameCount > 20 && !filterView!!.isHardwareAccelerated) {
                brightnessFrameCount = 20
            }
            val stepByStep2 = brightnessDistance / brightnessFrameCount.toFloat()
            for (frame in 1 until brightnessFrameCount) {
                brightnessFrames.add(currentBrightness + (frame * stepByStep2).toInt())
            }
            brightnessFrames.add(toBrightness)
        }

        playFrames(alphaFrames, brightnessFrames)
    }

    private fun playFrames(alphaFrames: LinkedList<Int>,brightnessFrames: LinkedList<Int>, durationMS: Long = 2000L) {
        stopUpdate()

        val layoutParams = this.layoutParams!!
        val frames = if (alphaFrames.size > brightnessFrames.size) alphaFrames.size else brightnessFrames.size
        if (frames == 0) {
            return
        }
        var frame = 0
        valueAnimator = ValueAnimator.ofInt(frame, frames)
        valueAnimator!!.run {
            duration = durationMS
            addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                if (value != frame) {
                    frame = value
                    val alpha = if(alphaFrames.isEmpty()) null else alphaFrames.removeFirst()
                    val brightness = if(brightnessFrames.isEmpty()) null else brightnessFrames.removeFirst()

                    if (alpha != null) {
                        currentAlpha = alpha
                        filterView?.run {
                            filterView?.setFilterColorNow(currentAlpha, brightness == null)
                        }
                    }

                    if (brightness != null) {
                        if (brightness >= FilterViewConfig.FILTER_BRIGHTNESS_MAX) {
                            layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                        } else {
                            layoutParams.screenBrightness = brightness / FilterViewConfig.FILTER_BRIGHTNESS_MAX.toFloat()
                        }
                        mWindowManager.updateViewLayout(popupView, layoutParams)
                        filterBrightness = brightness
                        GlobalStatus.currentFilterBrightness = brightness
                        GlobalStatus.currentFilterAlpah = currentAlpha
                    }
                }
            }
            start()
        }
    }

    // 快速更新滤镜 而不是使用动画
    private fun fastUpdateFilter(filterViewConfig: FilterViewConfig) {
        stopUpdate()

        filterView!!.setFilterColorNow(filterViewConfig.filterAlpha)
        currentAlpha = filterViewConfig.filterAlpha

        if (filterViewConfig.filterBrightness != filterBrightness) {
            val layoutParams = this.layoutParams!!
            if (filterViewConfig.filterBrightness >= FilterViewConfig.FILTER_BRIGHTNESS_MAX) {
                layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            } else {
                layoutParams.screenBrightness = filterViewConfig.getFilterBrightnessRatio()
            }
            filterView!!.setFilterColorNow(filterViewConfig.filterAlpha, false)
            mWindowManager.updateViewLayout(popupView, layoutParams)

            filterBrightness = filterViewConfig.filterBrightness
            GlobalStatus.currentFilterBrightness = filterBrightness
            GlobalStatus.currentFilterAlpah = currentAlpha
        }
    }
}
