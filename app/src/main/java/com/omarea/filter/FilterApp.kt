package com.omarea.filter

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper

class FilterApp : Application() {
    companion object {
        lateinit var context: Context
        val handler = Handler(Looper.getMainLooper())
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        context = this
    }
}