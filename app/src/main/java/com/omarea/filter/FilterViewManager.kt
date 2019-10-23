package com.omarea.filter

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.omarea.filter.common.ViewHelper
import java.util.*

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

        if (popupView != null) {
            mWindowManager.removeView(popupView)
            popupView = null
        }
    }

    fun updateFilterByConfig(filterViewConfig: FilterViewConfig) {
        if (filterView != null) {

            if (filterViewConfig.smoothChange) {
                smoothUpdateFilter(filterViewConfig)
            } else {
                fastUpdateFilter(filterViewConfig)
            }

            GlobalStatus.currentFilterAlpah = filterViewConfig.filterAlpha
            GlobalStatus.currentFilterBrightness = filterBrightness
        }
    }

    private fun stopUpdate() {
        if (valueAnimator != null && valueAnimator!!.isRunning) {
            valueAnimator!!.cancel()
            valueAnimator = null
        }
    }

    // TODO: 注意异常处理
    private fun smoothUpdateFilter(filterViewConfig: FilterViewConfig) {
        stopUpdate()
        val layoutParams = popupView!!.layoutParams as WindowManager.LayoutParams

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

        val currentBrightness = (Math.abs(layoutParams.screenBrightness) * 1000).toInt()
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

        val frames = if (alphaFrames.size > brightnessFrames.size) alphaFrames.size else brightnessFrames.size
        if (frames == 0) {
            return
        }
        var frame = 0

        valueAnimator = ValueAnimator.ofInt(frame, frames)
        valueAnimator!!.run {
            duration = 2000L
            addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                if (value != frame) {
                    frame = value
                    val alpha = if(alphaFrames.isEmpty()) null else alphaFrames.removeFirst()
                    val brightness = if(brightnessFrames.isEmpty()) null else brightnessFrames.removeFirst()

                    if (alpha != null) {
                        currentAlpha = alpha
                        filterView?.run {
                            filterView?.setFilterColorNow(currentAlpha)
                        }
                    }

                    if (brightness != null) {
                        if (brightness >= FilterViewConfig.FILTER_BRIGHTNESS_MAX) {
                            layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                        } else {
                            layoutParams.screenBrightness = brightness / FilterViewConfig.FILTER_BRIGHTNESS_MAX.toFloat()
                        }
                        mWindowManager.updateViewLayout(popupView, layoutParams)
                        filterBrightness = brightness
                    }
                }
            }
            start()
        }
    }

    private fun fastUpdateFilter(filterViewConfig: FilterViewConfig) {
        stopUpdate()

        filterView!!.setFilterColorNow(filterViewConfig.filterAlpha)
        currentAlpha = filterViewConfig.filterAlpha

        if (filterViewConfig.filterBrightness != filterBrightness) {
            val layoutParams = popupView!!.layoutParams as WindowManager.LayoutParams
            if (filterViewConfig.filterBrightness >= FilterViewConfig.FILTER_BRIGHTNESS_MAX) {
                layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            } else {
                layoutParams.screenBrightness = filterViewConfig.getFilterBrightnessRatio()
            }
            filterView!!.setFilterColorNow(filterViewConfig.filterAlpha)
            popupView!!.postDelayed({
                mWindowManager.updateViewLayout(popupView, layoutParams)
            }, 100)

            filterBrightness = filterViewConfig.filterBrightness
        }
    }
}
