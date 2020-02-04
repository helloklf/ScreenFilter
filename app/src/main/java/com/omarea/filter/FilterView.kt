package com.omarea.filter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.view.View

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
        val filterColor = GlobalStatus.sampleData!!.getFilterColor()
        red = Color.red(filterColor)
        green = Color.green(filterColor)
        blue = Color.blue(filterColor)
    }

    fun setFilterColor(r: Int, g: Int, b: Int) {
        this.red = r
        this.green = g
        this.blue = b
        invalidate()
    }

    fun setAlpha(alpha: Int) {
        var effectiveValue = alpha
        if (effectiveValue < 0) {
            effectiveValue = 0
        } else if (effectiveValue > FilterViewConfig.FILTER_MAX_ALPHA) {
            effectiveValue = FilterViewConfig.FILTER_MAX_ALPHA
        }

        if (this.currentAlpha != (effectiveValue / 4.2).toInt()) {
            this.currentAlpha = (effectiveValue / 4.2).toInt()
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawARGB(currentAlpha, red, green, blue)
        /*
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && Build.PRODUCT == "tucana") {
            // canvas.drawRect(720f, 10f, 750f, 30f, Paint().apply { color = Color.BLACK }) // Xiaomi CC9Pro 屏下光线传感器位置
            canvas.drawRoundRect(720f, 5f, 750f, 30f, 12f, 12f, Paint().apply { color = Color.BLACK }) // Xiaomi CC9Pro 屏下光线传感器位置
        }
        */
    }
}
