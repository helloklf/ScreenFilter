package com.omarea.filter.service

import android.graphics.Rect
import android.util.LruCache
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

class WindowAnalyzer(private val service: FilterAccessibilityService) {
    companion object {
        private var lastParsingThread: Long = 0
        private var lastWindowChanged = 0L

        interface IWindowAnalyzerResult {
            fun onWindowAnalyzerResult(packageName: String)
        }
    }

    private val blackTypeList = arrayListOf(
            AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY,
            AccessibilityWindowInfo.TYPE_INPUT_METHOD,
            AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER,
            AccessibilityWindowInfo.TYPE_SYSTEM
    )

    // 窗口id缓存（检测到相同的窗口id时，直接读取缓存的packageName，避免重复分析窗口节点获取packageName，降低性能消耗）
    private val windowIdCaches = LruCache<Int, String>(3)
    // 新的前台应用窗口判定逻辑
    fun analysis(event: AccessibilityEvent? = null, handler: IWindowAnalyzerResult) {
        val windowsList = service.windows
        if (windowsList == null || windowsList.isEmpty()) {
            return
        } else if (windowsList.size > 1) {
            val effectiveWindows = windowsList.filter {
                // 现在不过滤画中画应用了，因为有遇到像Telegram这样的应用，从画中画切换到全屏后仍检测到处于画中画模式，并且类型是 -1（可能是MIUI魔改出来的），但对用户来说全屏就是前台应用
                !blackTypeList.contains(it.type)

                // (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && it.isInPictureInPictureMode)) && (it.type == AccessibilityWindowInfo.TYPE_APPLICATION)
            } // .sortedBy { it.layer }

            if (effectiveWindows.isNotEmpty()) {
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
                            val thread: Thread = WindowAnalyzeThread(lastWindow, lastParsingThread, handler, windowIdCaches)
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
            private val handler: IWindowAnalyzerResult,
            private val windowIdCaches: LruCache<Int, String>
    ) : Thread() {
        override fun run() {
            var root: AccessibilityNodeInfo? = null
            val windowId = windowInfo.id
            val wp = (try {
                val cache = windowIdCaches.get(windowId)
                if (cache == null) {
                    // 如果当前window锁属的APP处于未响应状态，此过程可能会等待5秒后超时返回null，因此需要在线程中异步进行此操作
                    root = (try {
                        windowInfo.root
                    } catch (ex: Exception) {
                        null
                    })
                    root?.packageName.apply {
                        if (this != null) {
                            windowIdCaches.put(windowId, toString())
                        }
                    }
                } else {
                    // Log.d("@Scene", "windowCacheHit " + cache)
                    cache
                }
            } catch (ex: Exception) {
                null
            })
            // MIUI 优化，打开MIUI多任务界面时当做没有发生应用切换
            if (wp?.equals("com.miui.home") == true) {
                /*
                val node = root?.findAccessibilityNodeInfosByText("小窗应用")?.firstOrNull()
                Log.d("Scene-MIUI", "" + node?.parent?.viewIdResourceName)
                Log.d("Scene-MIUI", "" + node?.viewIdResourceName)
                */
                val node = root?.findAccessibilityNodeInfosByViewId("com.miui.home:id/txtSmallWindowContainer")?.firstOrNull()
                if (node != null) {
                    return
                }
            }

            if (lastParsingThread == tid && wp != null) {
                handler.onWindowAnalyzerResult(wp.toString())
            }
        }
    }
}