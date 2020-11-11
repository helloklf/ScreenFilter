package com.omarea.filter

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class FilterView : View {
    private var red = 0
    private var green = 0
    private var blue = 0
    private var currentAlpha: Int = 0

    // 磨砂纹理（缓存）
    private var texture: Bitmap? = null

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

    // 设置纹理
    fun setTexture(textureSand: Bitmap?) {
        this.texture?.recycle()
        // 保存纹理资源文件
        this.texture = textureSand
        // 计算纹理资源尺寸
        if (textureSand != null) {
            textureRect = Rect(0, 0, textureSand.width, textureSand.height)
        }
        // 清空纹理全屏缓存
        textureCache?.recycle()
        textureCache = null

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

    private var textureRect = Rect(0, 0, 512, 512)
    private var textureCache: Bitmap? = null
    private var fullSizeRect = Rect(0, 0, this.width, this.height)
    private val emptyPaint = Paint()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        fullSizeRect = Rect(0, 0, w, h)
        // 清空纹理全屏缓存
        textureCache?.recycle()
        textureCache = null
    }

    private fun ceiling(value: Double): Int {
        val i = value.toInt()
        if (value > i) {
            return i + 1
        }
        return i
    }

    override fun onDetachedFromWindow() {
        texture?.recycle()
        texture = null
        textureCache?.recycle()
        textureCache = null

        super.onDetachedFromWindow()
    }

    private fun repeatDrawTextureSand(canvas: Canvas) {
        val textureSand = this.texture ?: return

        if (textureCache == null) {
            textureCache = Bitmap.createBitmap(this.width, this.height, Bitmap.Config.ARGB_4444)
            val cacheCanvas = Canvas(textureCache!!)

            val width = canvas.width.toDouble()
            val height = canvas.height.toDouble()

            val textureWidth = textureRect.width()
            val textureHeight = textureRect.height()
            val rows = ceiling(height / textureHeight)
            val cols = ceiling(width / textureWidth)

            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    cacheCanvas.drawBitmap(
                        textureSand,
                        textureRect,
                        Rect(
                            col * textureWidth,
                            row * textureHeight,
                            (col + 1) * textureWidth,
                            (row + 1) * textureHeight
                        ),
                        emptyPaint
                    )
                }
            }
            System.gc()
        }

        canvas.drawBitmap(textureCache!!, fullSizeRect, fullSizeRect, emptyPaint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        repeatDrawTextureSand(canvas)

        canvas.drawARGB(currentAlpha, red, green, blue)
        /*
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && Build.PRODUCT == "tucana") {
            // canvas.drawRect(720f, 10f, 750f, 30f, Paint().apply { color = Color.BLACK }) // Xiaomi CC9Pro 屏下光线传感器位置
            canvas.drawRoundRect(720f, 5f, 750f, 30f, 12f, 12f, Paint().apply { color = Color.BLACK }) // Xiaomi CC9Pro 屏下光线传感器位置
        }
        */
    }
}
