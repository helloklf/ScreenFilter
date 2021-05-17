package com.omarea.filter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler

/**
 * 监听屏幕开关事件
 * Created by Hello on 2018/01/23.
 */

class ReceiverLock(private var callbacks: Handler) : BroadcastReceiver() {
    override fun onReceive(p0: Context?, p1: Intent?) {
        if (p1 == null) {
            return
        }
        when (p1.action) {
            Intent.ACTION_SCREEN_OFF -> {
                try {
                    callbacks.sendMessage(callbacks.obtainMessage(8))
                } catch (ex: Exception) {
                }
            }
            Intent.ACTION_USER_PRESENT,
            Intent.ACTION_USER_UNLOCKED,
            Intent.ACTION_SCREEN_ON -> {
                try {
                    callbacks.sendMessage(callbacks.obtainMessage(7))
                } catch (ex: Exception) {
                }
            }
        }
    }

    companion object {
        private var receiver: ReceiverLock? = null
        fun autoRegister(context: Context, callbacks: Handler): ReceiverLock? {
            if (receiver != null) {
                unRegister(context)
            }

            receiver = ReceiverLock(callbacks)

            context.applicationContext.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.applicationContext.registerReceiver(receiver, IntentFilter(Intent.ACTION_USER_UNLOCKED))
            }
            context.applicationContext.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_ON))
            context.applicationContext.registerReceiver(receiver, IntentFilter(Intent.ACTION_USER_PRESENT))



            return receiver
        }

        fun unRegister(context: Context) {
            if (receiver == null) {
                return
            }
            try {
                context.applicationContext.unregisterReceiver(receiver)
                receiver = null
            } catch (ex: java.lang.Exception) {
            }
        }
    }
}
