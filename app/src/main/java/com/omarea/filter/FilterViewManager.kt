package com.omarea.filter

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import com.omarea.filter.common.ViewHelper

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
    private var lastFilterViewConfig: FilterViewConfig? = null
    private var fadeInDuration = 3000L

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
        currentAlpha = 0
        lastFilterViewConfig = null
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

    private val layoutParams: WindowManager.LayoutParams?
        get() {
            return popupView?.layoutParams as WindowManager.LayoutParams?
        }

    // 停止正在运行的滤镜更新动画
    private fun stopUpdate() {
        if (valueAnimator != null && valueAnimator!!.isRunning) {
            valueAnimator!!.cancel()
            valueAnimator = null
        }
    }

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

    private var paused = false

    public fun setBrightness(brightness: Int) {
        if (paused) {
            return
        }

        val sampleData = GlobalStatus.sampleData!!
        val view = filterView!!
        val lp = layoutParams!!
        stopUpdate()
        valueAnimator = ValueAnimator.ofInt(lastBrightness, brightness).apply {
            var lastTick = -2
            duration = fadeInDuration
            addUpdateListener { animation ->
                val current = (animation.animatedValue as Int)
                if (current != lastTick) {
                    lastTick = -2
                    lastBrightness = current
                    sampleData.getConfigByBrightness(current).run {
                        if (hardwareBrightness != filterBrightness) {
                            if (filterBrightness >= FilterViewConfig.FILTER_BRIGHTNESS_MAX) {
                                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                            } else {
                                lp.screenBrightness = filterBrightness.toFloat() / FilterViewConfig.FILTER_BRIGHTNESS_MAX
                            }
                            mWindowManager.updateViewLayout(popupView, layoutParams)
                            hardwareBrightness = filterBrightness
                        }
                        if (filterAlpha != (view.alpha * 1000).toInt()) {
                            lastFilterAlpha = filterAlpha
                            view.setAlpha(filterAlpha)
                        }
                    }
                }
            }
            start()
        }
    }

    fun pause() {
        if (paused) {
            return
        }

        paused = true
        val sampleData = GlobalStatus.sampleData!!
        val view = filterView!!
        val lp = layoutParams!!
        val toB = lastBrightness
        stopUpdate()
        valueAnimator = ValueAnimator.ofInt(sampleData.getScreentMinLight(), 1).apply {
            duration = 2000
            addUpdateListener { animation ->
                FilterViewConfig.getConfigByBrightness(toB, (animation.animatedValue as Int)).run {
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
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator?) {
                }
                override fun onAnimationEnd(animation: Animator?) {

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
        if (!paused) {
            return
        }
        paused = false

        val sampleData = GlobalStatus.sampleData!!
        val view = filterView!!
        val lp = layoutParams!!
        stopUpdate()
        valueAnimator = ValueAnimator.ofInt(1, sampleData.getScreentMinLight()).apply {
            duration = fadeInDuration
            addUpdateListener { animation ->
                FilterViewConfig.getConfigByBrightness(lastBrightness, (animation.animatedValue as Int)).run {
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
            start()
        }
    }

    fun filterManualUpdate() {
        if (GlobalStatus.filterManualBrightness > -1) {
            paused = true
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
            paused = false
        }
    }
}
