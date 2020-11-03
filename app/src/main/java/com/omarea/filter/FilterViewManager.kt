package com.omarea.filter

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import com.omarea.filter.common.ViewHelper

class FilterViewManager(private var context: Context) {
    private val mWindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val display = mWindowManager.defaultDisplay
    private var viewHelper = ViewHelper(context)

    private var popupView: View? = null
    private var filterView: FilterView? = null
    private var valueAnimator: ValueAnimator? = null
    private val filterFadeInDuration = 3000L

    private var filterPaused = false
    private var lastBrightness: Int = 0
    private var lastFilterAlpha: Int
        get() {
            return GlobalStatus.currentFilterAlpah
        }
        set(value) {
            GlobalStatus.currentFilterAlpah = value
        }
    private var hardwareBrightness: Int
        get() {
            return GlobalStatus.currentFilterBrightness
        }
        set(value) {
            GlobalStatus.currentFilterBrightness = value
        }

    private fun resetState() {
        lastBrightness = 0
        lastFilterAlpha = 0
        hardwareBrightness = 0
        filterPaused = false
    }

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

        params.gravity = Gravity.NO_GRAVITY // Gravity.START or Gravity.TOP
        params.format = PixelFormat.TRANSLUCENT
        params.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

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
        resetState()

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

    private val layoutParams: WindowManager.LayoutParams?
        get() {
            return popupView?.layoutParams as WindowManager.LayoutParams?
        }

    // 停止正在运行的滤镜更新动画
    public fun stopUpdate() {
        if (valueAnimator != null && valueAnimator!!.isRunning) {
            valueAnimator!!.cancel()
            valueAnimator = null
        }
    }

    public fun setBrightness(brightness: Int, smooth: Boolean = true) {
        if (filterPaused) {
            return
        }

        stopUpdate()
        if (smooth) {
            valueAnimator = ValueAnimator.ofInt(lastBrightness, brightness).apply {
                var lastTick = -2
                duration = filterFadeInDuration
                addUpdateListener { animation ->
                    val current = (animation.animatedValue as Int)
                    if (current != lastTick) {
                        lastTick = current
                        lastBrightness = current
                        setBrightnessNow(current)
                    }
                }

                addListener(object : Animator.AnimatorListener {
                    override fun onAnimationStart(animation: Animator?) {
                    }
                    override fun onAnimationEnd(animation: Animator?) {
                        if (brightness != lastTick) {
                            lastTick = brightness
                            lastBrightness = brightness
                            setBrightnessNow(brightness)
                        }
                    }
                    override fun onAnimationCancel(animation: Animator?) {
                    }
                    override fun onAnimationRepeat(animation: Animator?) {
                    }
                })
                start()
            }
        } else {
            lastBrightness = brightness
            setBrightnessNow(brightness)
        }
    }

    private fun setBrightnessNow(brightness: Int) {
        GlobalStatus.sampleData!!.getConfigByBrightness(brightness).run {
            if (hardwareBrightness != filterBrightness) {
                if (filterBrightness >= FilterViewConfig.FILTER_BRIGHTNESS_MAX) {
                    layoutParams!!.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                } else {
                    layoutParams!!.screenBrightness = filterBrightness.toFloat() / FilterViewConfig.FILTER_BRIGHTNESS_MAX
                }
                mWindowManager.updateViewLayout(popupView, layoutParams)
                hardwareBrightness = filterBrightness
            }

            lastFilterAlpha = filterAlpha
            filterView!!.setAlpha(filterAlpha)
        }
    }

    fun pause(next: Runnable? = null) {
        if (filterPaused) {
            return
        }

        filterPaused = true
        val sampleData = GlobalStatus.sampleData!!
        val view = filterView!!
        val lp = layoutParams!!
        val toB = lastBrightness
        stopUpdate()
        valueAnimator = ValueAnimator.ofInt(sampleData.getScreentMinLight(), 1).apply {
            duration = 2000
            var lastTick = -2
            addUpdateListener { animation ->
                val current = (animation.animatedValue as Int)
                if (current != lastTick) {
                    lastTick = current

                    FilterViewConfig.getConfigByBrightness(toB, current).run {
                        if (hardwareBrightness != filterBrightness) {
                            if (filterBrightness >= FilterViewConfig.FILTER_BRIGHTNESS_MAX) {
                                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                            } else {
                                lp.screenBrightness = filterBrightness.toFloat() / FilterViewConfig.FILTER_BRIGHTNESS_MAX
                            }
                            mWindowManager.updateViewLayout(popupView, layoutParams)
                            hardwareBrightness = filterBrightness
                        }
                        if (filterAlpha != view.alpha.toInt()) {
                            lastFilterAlpha = filterAlpha
                            view.setAlpha(filterAlpha)
                        }
                    }
                }
            }

            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator?) {
                }
                override fun onAnimationEnd(animation: Animator?) {
                    next?.run()
                }
                override fun onAnimationCancel(animation: Animator?) {
                }
                override fun onAnimationRepeat(animation: Animator?) {
                }
            })
            start()
        }
    }

    fun resume() {
        if (!filterPaused) {
            return
        }
        filterPaused = false

        val sampleData = GlobalStatus.sampleData!!
        val view = filterView!!
        val lp = layoutParams!!
        stopUpdate()
        valueAnimator = ValueAnimator.ofInt(1, sampleData.getScreentMinLight()).apply {
            duration = filterFadeInDuration
            var lastTick = -2
            addUpdateListener { animation ->
                val current = (animation.animatedValue as Int)
                if (current != lastTick) {
                    lastTick = current

                    FilterViewConfig.getConfigByBrightness(lastBrightness, current).run {
                        if (hardwareBrightness != filterBrightness) {
                            if (filterBrightness >= FilterViewConfig.FILTER_BRIGHTNESS_MAX) {
                                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                            } else {
                                lp.screenBrightness = filterBrightness.toFloat() / FilterViewConfig.FILTER_BRIGHTNESS_MAX
                            }
                            mWindowManager.updateViewLayout(popupView, layoutParams)
                            hardwareBrightness = filterBrightness
                        }
                        if (filterAlpha != view.alpha.toInt()) {
                            lastFilterAlpha = filterAlpha
                            view.setAlpha(filterAlpha)
                        }
                    }
                }
            }

            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator?) {
                }
                override fun onAnimationEnd(animation: Animator?) {
                    lastTick = lastBrightness
                    setBrightnessNow(lastBrightness)
                }
                override fun onAnimationCancel(animation: Animator?) {
                }
                override fun onAnimationRepeat(animation: Animator?) {
                }
            })
            start()
        }
    }

    fun filterManualUpdate() {
        if (GlobalStatus.filterManualBrightness > -1) {
            filterPaused = true
            val sampleData = GlobalStatus.sampleData!!
            val view = filterView!!
            val lp = layoutParams!!
            stopUpdate()
            sampleData.getConfigByBrightness(GlobalStatus.filterManualBrightness).run {
                if (filterBrightness >= FilterViewConfig.FILTER_BRIGHTNESS_MAX) {
                    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                } else {
                    lp.screenBrightness = filterBrightness.toFloat() / FilterViewConfig.FILTER_BRIGHTNESS_MAX
                }
                mWindowManager.updateViewLayout(popupView, layoutParams)
                lastFilterAlpha = filterAlpha
                view.setAlpha(filterAlpha)
            }
        } else {
            filterPaused = false
        }
    }
}
