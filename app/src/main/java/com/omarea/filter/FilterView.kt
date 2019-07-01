package com.omarea.filter

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.BaseInterpolator
import kotlin.math.abs


/**
 * TODO: document your custom view class.
 */
class FilterView : View {
    private var red = 0
    private var green = 0
    private var blue = 0
    private var toAlpha = 0f
    private var stepByStep = 0.035f

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

    fun cgangePer(per: Float) {
        val perOld = this.alpha
        if (valueAnimator != null && valueAnimator!!.isRunning) {
            valueAnimator!!.cancel()
        }
        ValueAnimator.setFrameDelay(100); // 10帧
        valueAnimator = ValueAnimator.ofFloat(perOld, per)
        valueAnimator!!.run {
            duration = 600
            // interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                alpha = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun init(attrs: AttributeSet?, defStyle: Int) {
        // Load attributes
        val a = context.obtainStyledAttributes(attrs, R.styleable.MyView, defStyle, 0)
        a.recycle()
        invalidateTextPaintAndMeasurements()
        alpha = 0.5f
        toAlpha = alpha
    }

    private fun invalidateTextPaintAndMeasurements() {}

    fun setFilterColor(alpha: Int, red: Int = 0, green: Int = 0, blue: Int = 0, soomth: Boolean = false) {
        var effectiveValue = alpha
        if (effectiveValue < 0) {
            effectiveValue = 0
        } else if (effectiveValue > FilterViewConfig.FILTER_MAX_ALPHA) {
            effectiveValue = FilterViewConfig.FILTER_MAX_ALPHA
        }
        if (this.red != red || this.blue != blue || this.green != green) {
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.setBackgroundColor(Color.rgb(red, green, blue))
            invalidate()
        }

        if (this.toAlpha != effectiveValue / 1000f) {
            this.toAlpha = effectiveValue / 1000f
            // val distance = abs(this.toAlpha - this.alpha)
            // stepByStep = distance / 10
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRGB(red, green, blue)
        val distance = this.toAlpha - this.alpha
        if (abs(distance) >= stepByStep) {
            if (this.toAlpha > this.alpha) {
                this.alpha += stepByStep
            } else {
                this.alpha -= stepByStep
            }
        } else {
            this.alpha = this.toAlpha
        }
    }
}
