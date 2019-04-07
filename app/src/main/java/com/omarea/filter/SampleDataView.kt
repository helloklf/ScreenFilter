package com.omarea.filter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * TODO: document your custom view class.
 */
class SampleDataView : View {
    constructor(context: Context) : super(context) {
        init(null, 0)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        init(attrs, defStyle)
    }

    private fun init(attrs: AttributeSet?, defStyle: Int) {
        // Load attributes
        val a = context.obtainStyledAttributes(attrs, R.styleable.MyView, defStyle, 0)
        a.recycle()
        invalidateTextPaintAndMeasurements()
    }

    private fun invalidateTextPaintAndMeasurements() {}

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val potintRadius = 6f
        val paint = Paint()

        val samples = GlobalStatus.sampleData!!.getAllSamples()
        val xPoints = samples.keys.sorted()

        val innerPadding = 60f

        val ratioX = (this.width - innerPadding - innerPadding) * 1.0 / (xPoints.max() as Int) // 横向比率
        val ratioY = ((this.height - innerPadding - innerPadding) * 1.0 / 256).toFloat() // 纵向比率
        val stratY = height - innerPadding

        val pathFilterAlpha = Path()
        var isFirstPoint = true

        for (point in xPoints) {
            val pointX = (point * ratioX).toFloat() + innerPadding
            val sample = samples.get(point)!!

            if (isFirstPoint) {
                pathFilterAlpha.moveTo(pointX, stratY - (sample * ratioY))
                isFirstPoint = false
                canvas.drawLine(innerPadding, innerPadding / 2, innerPadding, this.height - innerPadding, paint)
                canvas.drawLine(innerPadding, this.height - innerPadding, this.width - innerPadding / 2, this.height - innerPadding, paint)
            } else {
                pathFilterAlpha.lineTo(pointX, stratY - (sample * ratioY))

                paint.color = Color.parseColor("#8BC34A")
                canvas.drawCircle(pointX, stratY - (sample * ratioY), potintRadius, paint)
            }

            paint.textSize = 30f
            paint.color = Color.parseColor("#000000")
            canvas.drawText(point.toString(), pointX - 30, this.height - innerPadding + 35, paint)
            canvas.drawCircle(pointX, this.height - innerPadding, potintRadius, paint)
        }

        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f

        paint.color = Color.parseColor("#8BC34A")
        canvas.drawPath(pathFilterAlpha, paint)
    }
}
