package com.omarea.filter

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.widget.Toast

class HelpActivity : AppCompatActivity() {
    private var filterEnabled = GlobalStatus.filterEnabled
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = this.intent
        if (intent != null && intent.action != null) {
            val action = intent.action
            if (action == "open") {
                if (!GlobalStatus.filterEnabled && GlobalStatus.filterOpen != null) {
                    GlobalStatus.filterOpen?.run()
                }
                Toast.makeText(this, "滤镜已开启", Toast.LENGTH_SHORT).show()
                finish()
                return
            } else if (action == "close") {
                if (GlobalStatus.filterEnabled && GlobalStatus.filterClose != null) {
                    GlobalStatus.filterClose?.run()
                }
                Toast.makeText(this, "滤镜已关闭", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        }

        setTheme(if (filterEnabled) R.style.AppTheme_Default else R.style.AppTheme_OFF)

        setContentView(R.layout.activity_help)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
    }
}
