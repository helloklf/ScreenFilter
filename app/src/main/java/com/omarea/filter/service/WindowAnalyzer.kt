package com.omarea.filter.service

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

class WindowAnalyzer(private val service: FilterAccessibilityService) {
    companion object {
        private var lastParsingThread: Long = 0
        private var lastWindowChanged = 0L

        interface IWindowAnalyzerResult {
            fun onWindowAnalyzerResult(packageName: String)
        }
    }

    // 新的前台应用窗口判定逻辑
    fun analysis(event: AccessibilityEvent? = null, handler: IWindowAnalyzerResult) {
        val windowsList = service.windows
        if (windowsList == null || windowsList.isEmpty()) {
            return
        } else if (windowsList.size > 1) {
            val effectiveWindows = windowsList.filter {
                (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && it.isInPictureInPictureMode)) && (it.type == AccessibilityWindowInfo.TYPE_APPLICATION)
            } // .sortedBy { it.layer }

            if (effectiveWindows.size > 0) {
                try {
                    var lastWindow: AccessibilityWindowInfo? = null
                    // TODO:
                    //      此前在MIUI系统上测试，只判定全屏显示（即窗口大小和屏幕分辨率完全一致）的应用，逻辑非常准确
                    //      但在类原生系统上表现并不好，例如：有缺口的屏幕或有导航键的系统，报告的窗口大小则可能不包括缺口高度区域和导航键区域高度
                    //      因此，现在将逻辑调整为：从所有应用窗口中选出最接近全屏的一个，判定为前台应用
                    //      当然，这并不意味着完美，只是暂时没有更好的解决方案……
                    var lastWindowSize = 0
                    for (window in effectiveWindows) {

                        val outBounds = Rect()
                        window.getBoundsInScreen(outBounds)

                        /*
                        // 获取窗口 root节点 会有性能问题，因此去掉此判断逻辑
                        val wp = window.root?.packageName
                        if (wp == null || wp == "android" || wp == "com.android.systemui" || wp == "com.miui.freeform" || wp == "com.omarea.gesture" || wp == "com.omarea.filter" || wp == "com.android.permissioncontroller") {
                            continue
                        }
                        */

                        val size = (outBounds.right - outBounds.left) * (outBounds.bottom - outBounds.top)
                        if (size >= lastWindowSize) {
                            lastWindow = window
                            lastWindowSize = size
                        }
                    }
                    if (lastWindow != null) {
                        val eventWindowId = event?.windowId
                        val lastWindowId = lastWindow.id

                        if (eventWindowId == lastWindowId && event.packageName != null) {
                            val pa = event.packageName
                            handler.onWindowAnalyzerResult(pa.toString())
                        } else {
                            lastParsingThread = System.currentTimeMillis()
                            val thread: Thread = WindowAnalyzeThread(lastWindow, lastParsingThread, handler)
                            thread.start()
                        }
                    } else {
                        return
                    }
                } catch (ex: Exception) {
                    return
                }
            }
        }
    }

    class WindowAnalyzeThread constructor(
            private val windowInfo: AccessibilityWindowInfo,
            private val tid: Long,
            private val handler: IWindowAnalyzerResult
    ) : Thread() {
        override fun run() {
            // 如果当前window锁属的APP处于未响应状态，此过程可能会等待5秒后超时返回null，因此需要在线程中异步进行此操作
            val wp = (try {
                windowInfo.root?.packageName
            } catch (ex: Exception) {
                null
            })

            if (lastParsingThread == tid && wp != null) {
                handler.onWindowAnalyzerResult(wp.toString())
            }
        }
    }
}