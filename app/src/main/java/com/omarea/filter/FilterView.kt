package com.omarea.filter

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * TODO: document your custom view class.
 */
class FilterView : View {
    private var alpha = 0
    private var red = 0
    private var green = 0
    private var blue = 0

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
        val perOld = this.alpha
        if (valueAnimator != null && valueAnimator!!.isRunning) {
            valueAnimator!!.cancel()
        }
        valueAnimator = ValueAnimator.ofInt(perOld, per)
        valueAnimator!!.run {
            duration = 1000
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                alpha = animation.animatedValue as Int
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
    }

    private fun invalidateTextPaintAndMeasurements() {}

    fun setFilterColor(alpha: Int, red: Int = 0, green: Int = 0, blue: Int = 0, soomth: Boolean = false) {
        var effectiveValue = alpha
        if (effectiveValue < 0) {
            effectiveValue = 0
        } else if (effectiveValue > 240) {
            effectiveValue = 240
        }
        if (this.alpha != effectiveValue || this.red != red || this.blue != blue || this.green != green) {
            this.red = red;
            this.green = green;
            this.blue = blue;
            if (soomth) {
                cgangePer(effectiveValue)
            } else {
                this.alpha = effectiveValue
                invalidate()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawARGB(alpha, red, green, blue)
    }
}
