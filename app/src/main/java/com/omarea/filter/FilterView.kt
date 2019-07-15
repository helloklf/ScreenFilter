package com.omarea.filter

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.util.Log
import android.view.View
import java.lang.Math.abs

class FilterView : View {
    private var red = 0
    private var green = 0
    private var blue = 0
    private var currentAlpha: Int = 0
    private var toAlpha: Int = 0
    private var valueAnimator: ValueAnimator? = null

    constructor(context: Context) : super(context) {
        init(null, 0)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        init(attrs, defStyle)
    }

    fun cgangePer(per: Int) {
        if (valueAnimator != null && valueAnimator!!.isRunning) {
            valueAnimator!!.cancel()
            valueAnimator = null
        }
        val perOld = this.currentAlpha
        ValueAnimator.setFrameDelay(100); // 10帧（这个设置好像是无效的）
        valueAnimator = ValueAnimator.ofInt(perOld, per)
        valueAnimator!!.run {
            duration = 2500
            // interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                if (value != currentAlpha) {
                    currentAlpha = value
                    invalidate()
                }
            }
            start()
        }
    }

    private fun init(attrs: AttributeSet?, defStyle: Int) {
        // Load attributes
        // val a = context.obtainStyledAttributes(attrs, R.styleable.MyView, defStyle, 0)
        // a.recycle()
        // invalidateTextPaintAndMeasurements()
        currentAlpha = 0
        toAlpha = currentAlpha
    }

    fun setFilterColor(alpha: Int) {
        var effectiveValue = alpha
        if (effectiveValue < 0) {
            effectiveValue = 0
        } else if (effectiveValue > FilterViewConfig.FILTER_MAX_ALPHA) {
            effectiveValue = FilterViewConfig.FILTER_MAX_ALPHA
        }

        if (this.toAlpha != effectiveValue / 4) {
            this.toAlpha = effectiveValue / 4
            // val distance = abs(this.toAlpha - this.alpha)
            // stepByStep = distance / 10
            if (isHardwareAccelerated) {
                cgangePer(this.toAlpha)
            } else {
                // this.currentAlpha = this.toAlpha
                basicRefresh()
            }
        }
    }

    fun setFilterColorNow(alpha: Int) {
        if (valueAnimator != null && valueAnimator!!.isRunning) {
            valueAnimator!!.cancel()
            valueAnimator = null
        }

        var effectiveValue = alpha
        if (effectiveValue < 0) {
            effectiveValue = 0
        } else if (effectiveValue > FilterViewConfig.FILTER_MAX_ALPHA) {
            effectiveValue = FilterViewConfig.FILTER_MAX_ALPHA
        }

        if (this.currentAlpha != effectiveValue / 4) {
            this.toAlpha = effectiveValue / 4
            this.currentAlpha = this.toAlpha
            invalidate()
        }
    }

    private fun basicRefresh() {
        if (abs(toAlpha - currentAlpha) > 5) {
            if (currentAlpha > toAlpha) {
                currentAlpha -= 5
            } else {
                currentAlpha += 5
            }
        } else {
            currentAlpha = toAlpha
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawARGB(currentAlpha, red, green, blue)
        if (!isHardwareAccelerated && currentAlpha != toAlpha) {
            basicRefresh()
        }
    }
}
