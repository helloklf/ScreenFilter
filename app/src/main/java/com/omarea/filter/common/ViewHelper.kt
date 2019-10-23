package com.omarea.filter.common

import android.content.Context

class ViewHelper(private var context: Context) {
    /**
     * dp转换成px
     */
    private fun dp2px(context: Context, dpValue: Float): Int {
        val scale = context.resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }

    /**
     * 获取导航栏高度
     * @param context
     * @return
     */
    fun getNavBarHeight(): Int {
        val height = context.resources.getDimensionPixelSize(context.resources.getIdentifier("navigation_bar_height", "dimen", "android"))
        if (height < 1) {
            return dp2px(context, 55f)
        }
        return height
    }

    /**
     * 获取状态栏高度
     */
    fun getStatusHeight(): Int {
        var result = 0
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = context.resources.getDimensionPixelSize(resourceId)
        }
        return result
    }
}
