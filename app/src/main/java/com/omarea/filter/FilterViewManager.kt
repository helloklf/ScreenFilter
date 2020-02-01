package com.omarea.filter

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import com.omarea.filter.common.ViewHelper
import kotlin.math.abs

class FilterViewManager(private var context: Context) {
    private val mWindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val display = mWindowManager.defaultDisplay
    private var config = context.getSharedPreferences(SpfConfig.FILTER_SPF, Context.MODE_PRIVATE)
    private var viewHelper = ViewHelper(context)

    private var popupView: View? = null
    private var filterView: FilterView? = null
    private var filterBrightness = 0 // 当前由滤镜控制的屏幕亮度
    private var currentAlpha: Int = 0
    private var valueAnimator: ValueAnimator? = null
    private var valueAnimator2: ValueAnimator? = null
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

    private fun brightnessTransAlpha(absBrightness: Float, originBrightness: Float, targetBrightness: Float) {
        val layoutParams = this.layoutParams
        if (layoutParams != null) {
            val distanceBrightness = originBrightness - targetBrightness

            val frames = 40
            val stepBrightness = distanceBrightness / frames // 每一帧要调整的亮度百分比

            stopUpdate()

            var frame = 0
            if (originBrightness > targetBrightness) {
                if (targetBrightness >= FilterViewConfig.FILTER_BRIGHTNESS_MAX) {
                    layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                } else {
                    layoutParams.screenBrightness = targetBrightness / FilterViewConfig.FILTER_BRIGHTNESS_MAX.toFloat()
                }
                mWindowManager.updateViewLayout(popupView, layoutParams)
                filterBrightness = targetBrightness.toInt()
                GlobalStatus.currentFilterBrightness = targetBrightness.toInt()

                valueAnimator = ValueAnimator.ofInt(frame, frames)
                valueAnimator!!.run {
                    duration = 1000
                    addUpdateListener { animation ->
                        val value = animation.animatedValue as Int
                        if (value != frame) {
                            frame = value

                            val brightness = (originBrightness - (stepBrightness * frame)).toInt() // 当前帧应该设置到的亮度

                            val alpha = 1 - (absBrightness / brightness) // 相应的 根据亮度的提升，也要降低滤镜的不透明度
                            val alphaTo = if (alpha > 0) (alpha * FilterViewConfig.FILTER_MAX_ALPHA).toInt() else 0

                            if (currentAlpha != alphaTo) {
                                currentAlpha = alphaTo
                                filterView?.run {
                                    filterView?.setAlpha(currentAlpha)
                                }
                                GlobalStatus.currentFilterAlpah = currentAlpha
                            }
                        }
                    }
                    start()
                }
            } else {
                /*
                val alpha = 1 - (absBrightness / targetBrightness) // 相应的 根据亮度的提升，也要降低滤镜的不透明度
                val alphaTo = if (alpha > 0) (alpha * FilterViewConfig.FILTER_MAX_ALPHA).toInt() else 0

                if (currentAlpha != alphaTo) {
                    currentAlpha = alphaTo
                    filterView?.run {
                        filterView?.setFilterColorNow(currentAlpha)
                    }
                    GlobalStatus.currentFilterAlpah = currentAlpha
                }
                */

                valueAnimator = ValueAnimator.ofInt(frame, frames)
                valueAnimator!!.run {
                    duration = 1000
                    addUpdateListener { animation ->
                        val value = animation.animatedValue as Int
                        if (value != frame) {
                            frame = value

                            val brightness = (originBrightness - (stepBrightness * frame)).toInt() // 当前帧应该设置到的亮度

                            val alpha = 1 - (absBrightness / brightness) // 相应的 根据亮度的提升，也要降低滤镜的不透明度
                            val alphaTo = if (alpha > 0) (alpha * FilterViewConfig.FILTER_MAX_ALPHA).toInt() else 0

                            if (currentAlpha != alphaTo) {
                                currentAlpha = alphaTo
                                filterView?.run {
                                    filterView?.setAlpha(currentAlpha)
                                }
                                GlobalStatus.currentFilterAlpah = currentAlpha
                            }

                            if (brightness >= FilterViewConfig.FILTER_BRIGHTNESS_MAX) {
                                layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                            } else {
                                layoutParams.screenBrightness = brightness / FilterViewConfig.FILTER_BRIGHTNESS_MAX.toFloat()
                            }
                            mWindowManager.updateViewLayout(popupView, layoutParams)
                            filterBrightness = brightness
                            GlobalStatus.currentFilterBrightness = brightness
                        }
                    }
                    start()
                }
            }
        }
    }

    /*
    // 精确到帧的调整滤镜，实际上...并没有那么理想
    private fun brightnessTransAlpha(absBrightness:Float, originBrightness:Float, targetBrightness: Float) {
        val layoutParams = this.layoutParams
        if (layoutParams != null) {
            val distanceBrightness = originBrightness - targetBrightness

            val frames = 50
            val stepBrightness = distanceBrightness / frames // 每一帧要调整的亮度百分比

            stopUpdate()

            var frame = 0
            valueAnimator = ValueAnimator.ofInt(frame, frames)
            valueAnimator!!.run {
                duration = 1000
                addUpdateListener { animation ->
                    val value = animation.animatedValue as Int
                    if (value != frame) {
                        frame = value

                        val brightness = (originBrightness - (stepBrightness * frame)).toInt() // 当前帧应该设置到的亮度

                        val alpha = 1 - (absBrightness / brightness) // 相应的 根据亮度的提升，也要降低滤镜的不透明度
                        val alphaTo = if (alpha > 0) (alpha * FilterViewConfig.FILTER_MAX_ALPHA).toInt() else 0

                        if (currentAlpha != alphaTo) {
                            currentAlpha = alphaTo
                            filterView?.run {
                                filterView?.setFilterColorNow(currentAlpha)
                            }
                            GlobalStatus.currentFilterAlpah = currentAlpha
                        }

                        if (brightness >= FilterViewConfig.FILTER_BRIGHTNESS_MAX) {
                            layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                        } else {
                            layoutParams.screenBrightness = brightness / FilterViewConfig.FILTER_BRIGHTNESS_MAX.toFloat()
                        }
                        mWindowManager.updateViewLayout(popupView, layoutParams)
                        filterBrightness = brightness
                        GlobalStatus.currentFilterBrightness = brightness
                    }
                }
                start()
            }
        }
    }
    */

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
        if (valueAnimator2 != null && valueAnimator2!!.isRunning) {
            valueAnimator2!!.cancel()
            valueAnimator2 = null
        }
    }

    // 使用平滑的动画更新滤镜
    private fun smoothUpdateFilter(filterViewConfig: FilterViewConfig) {
        stopUpdate()
        val layoutParams = this.layoutParams!!

        val perOld = this.currentAlpha
        val toAlpha = filterViewConfig.filterAlpha
        var alphaFrameCount: Int
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

            var frame = 0
            valueAnimator = ValueAnimator.ofInt(frame, alphaFrameCount).apply {
                duration = 2000
                addUpdateListener { animation ->
                    (animation.animatedValue as Int).run {
                        if (this != frame) {
                            frame = this
                            val alpha = (perOld + (frame * stepByStep)).toInt()

                            currentAlpha = alpha
                            filterView?.run {
                                filterView?.setAlpha(currentAlpha)
                            }
                            GlobalStatus.currentFilterAlpah = currentAlpha
                        }
                    }
                }
                start()
            }
        }

        val currentBrightness = (Math.abs(layoutParams.screenBrightness) * FilterViewConfig.FILTER_BRIGHTNESS_MAX).toInt()
        val toBrightness = filterViewConfig.filterBrightness
        var brightnessFrameCount: Int
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
            var frame = 0
            valueAnimator2 = ValueAnimator.ofInt(frame, brightnessFrameCount).apply {
                duration = 2000
                addUpdateListener { animation ->
                    (animation.animatedValue as Int).run {
                        if (this != frame) {
                            frame = this
                            val brightness = (currentBrightness + (frame * stepByStep2)).toInt()

                            if (brightness >= FilterViewConfig.FILTER_BRIGHTNESS_MAX) {
                                layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                            } else {
                                layoutParams.screenBrightness = brightness / FilterViewConfig.FILTER_BRIGHTNESS_MAX.toFloat()
                            }
                            mWindowManager.updateViewLayout(popupView, layoutParams)
                            filterBrightness = brightness
                            GlobalStatus.currentFilterBrightness = brightness
                        }
                    }
                }
                start()
            }
        }
    }

    // 快速更新滤镜 而不是使用动画
    private fun fastUpdateFilter(filterViewConfig: FilterViewConfig) {
        stopUpdate()

        // 为了避免亮度调高导致亮瞎眼，优先降低亮度

        val delay = if (currentAlpha < filterViewConfig.filterAlpha) {
            filterView!!.setAlpha(filterViewConfig.filterAlpha)
            currentAlpha = filterViewConfig.filterAlpha
            200L
        } else {
            0L
        }

        val to = if (filterViewConfig.filterBrightness >= FilterViewConfig.FILTER_BRIGHTNESS_MAX) {
            FilterViewConfig.FILTER_BRIGHTNESS_MAX
        } else {
            filterViewConfig.filterBrightness
        }
        filterView?.postDelayed({
            try {
                val layoutParams = this.layoutParams!!
                if (to >= FilterViewConfig.FILTER_BRIGHTNESS_MAX) {
                    layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                } else {
                    layoutParams.screenBrightness = filterViewConfig.getFilterBrightnessRatio()
                }
                filterBrightness = to
                mWindowManager.updateViewLayout(popupView, layoutParams)
                if (currentAlpha != filterViewConfig.filterAlpha) {
                    filterView!!.setAlpha(filterViewConfig.filterAlpha)
                    currentAlpha = filterViewConfig.filterAlpha
                }
            } catch (ex: Exception){
            }

            GlobalStatus.currentFilterBrightness = filterBrightness
            GlobalStatus.currentFilterAlpah = currentAlpha
        }, delay)
    }
}
