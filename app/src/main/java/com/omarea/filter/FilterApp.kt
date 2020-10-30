package com.omarea.filter

import android.app.Application
import android.content.Context

public class FilterApp : Application() {
    companion object {
        public lateinit var context: Context
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        context = this
    }
}