package com.omarea.filter

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.util.Log
import android.view.View
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
        ValueAnimator.setFrameDelay(100); // 10帧（这个设置好像是无效的）
        val frames = 60
        stepByStep = (per - perOld) / frames;
        valueAnimator = ValueAnimator.ofInt(0, frames)
        valueAnimator!!.run {
            duration = 2500
            // interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                alpha = perOld + (animation.animatedValue as Int) * stepByStep
                // invalidate()
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

    fun setFilterColor(alpha: Int) {
        var effectiveValue = alpha
        if (effectiveValue < 0) {
            effectiveValue = 0
        } else if (effectiveValue > FilterViewConfig.FILTER_MAX_ALPHA) {
            effectiveValue = FilterViewConfig.FILTER_MAX_ALPHA
        }

        if (this.toAlpha != effectiveValue / 1000f) {
            this.toAlpha = effectiveValue / 1000f
            // val distance = abs(this.toAlpha - this.alpha)
            // stepByStep = distance / 10
            if (isHardwareAccelerated) {
                cgangePer(this.toAlpha)
            } else {
                invalidate()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRGB(red, green, blue)
        if (!isHardwareAccelerated) {
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
            if (this.alpha != this.toAlpha) {
                invalidate()
            }
        }
    }
}
