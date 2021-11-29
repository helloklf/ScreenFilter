package com.omarea.filter

import android.os.Handler
import android.os.Looper
import android.os.Message

/**
 * 处理屏幕开关事件的Handler
 * Created by Hello on 2018/01/23.
 */

internal class ScreenEventHandler(private var onSceenOff: () -> Unit, private var onSceenOn: () -> Unit) : Handler(Looper.getMainLooper()) {
    override fun handleMessage(msg: Message) {
        super.handleMessage(msg)

        when (msg.what) {
            7 -> onSceenOn()    //屏幕已解锁
            8 -> onSceenOff()   //屏幕已关闭
        }
    }
}
