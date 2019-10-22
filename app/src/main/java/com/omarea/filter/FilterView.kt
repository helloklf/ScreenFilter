package com.omarea.filter

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import com.omarea.filter.FilterViewConfig

class FilterView : View {
    private var red = 0
    private var green = 0
    private var blue = 0
    private var currentAlpha: Int = 0

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        init()
    }

    private fun init() {
        currentAlpha = 0
    }

    fun setFilterColorNow(alpha: Int) {
        var effectiveValue = alpha
        if (effectiveValue < 0) {
            effectiveValue = 0
        } else if (effectiveValue > FilterViewConfig.FILTER_MAX_ALPHA) {
            effectiveValue = FilterViewConfig.FILTER_MAX_ALPHA
        }

        if (this.currentAlpha != effectiveValue / 4) {
            this.currentAlpha = effectiveValue / 4
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawARGB(currentAlpha, red, green, blue)
    }
}
