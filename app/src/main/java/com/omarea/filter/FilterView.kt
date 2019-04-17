package com.omarea.filter

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import java.lang.Exception


/**
 * TODO: document your custom view class.
 */
class FilterView : View {
    private var alpha = 0
    private var red = 0
    private var green = 0
    private var blue = 0
    private var pixelFilter = false
    private var pixelAlternate = false // 像素交替 避免开启像素滤镜后烧屏

    private var pointPaint:Paint? = null
    private var bufferCanvas: Canvas? = null
    private var bufferBitmap: Bitmap? = null

    private var valueAnimator: ValueAnimator? = null

    constructor(context: Context) : super(context) {
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
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

    fun setFilterColor(alpha: Int, red: Int = 0, green: Int = 0, blue: Int = 0, soomth: Boolean = false, pixelFilter: Boolean = false) {
        var effectiveValue = alpha
        if (effectiveValue < 0) {
            effectiveValue = 0
        } else if (effectiveValue > 250) {
            effectiveValue = 250
        }
        if (this.alpha != effectiveValue || this.red != red || this.blue != blue || this.green != green || this.pixelFilter != pixelFilter) {
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.pixelFilter  = pixelFilter;
            if (soomth) {
                cgangePer(effectiveValue)
            } else {
                this.alpha = effectiveValue
                invalidate()
            }
        }
    }

    private var cachedPixelAlternate:Boolean? = null
    private var cacheTime:Long = 0L

    private fun drawBuffer(): Canvas {
        if (cachedPixelAlternate == pixelAlternate && (System.currentTimeMillis() - cacheTime < 120000)) {
            return bufferCanvas!!
        } else {
            pixelAlternate = !pixelAlternate
            bufferBitmap = Bitmap.createBitmap(this.width,this.height, Bitmap.Config.ARGB_8888);
            bufferCanvas = Canvas(bufferBitmap!!)
            pointPaint = Paint()
            pointPaint!!.color = Color.BLACK
            val canvas = bufferCanvas!!

            for (x in 0..this.width) {
                for (y in 0..this.height) {
                    if (pixelAlternate) {
                        if ((x + y) % 2 == 0) {
                            canvas.drawPoint(x.toFloat(), y.toFloat(), pointPaint!!)
                        }
                    } else {
                        if ((x + y) % 2 == 1) {
                            canvas.drawPoint(x.toFloat(), y.toFloat(), pointPaint!!)
                        }
                    }
                }
            }
            cacheTime = System.currentTimeMillis()
            cachedPixelAlternate = pixelAlternate
            Toast.makeText(this.context, "PixelFilter Updated！", Toast.LENGTH_LONG).show()
            return canvas
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pixelFilter) {
            // pixelAlternate = !pixelAlternate
            drawBuffer()
            canvas.drawBitmap(bufferBitmap, 0f, 0f, Paint())
        }
        canvas.drawARGB(alpha, red, green, blue)
    }
}
