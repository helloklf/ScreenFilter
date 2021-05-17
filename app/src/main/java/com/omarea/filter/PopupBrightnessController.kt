package com.omarea.filter

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.view.*
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import com.omarea.filter.GlobalStatus.filterClose
import com.omarea.filter.GlobalStatus.filterOpen
import com.omarea.filter.GlobalStatus.filterRefresh

class PopupBrightnessController(private val context: Context) {
    private val mContext: Context = context.applicationContext
    private var mView: View? = null

    /**
     * 显示弹出框
     *
     * @param context
     */
    fun open() {
        if (isShown!!) {
            return
        }

        isShown = true
        // 获取WindowManager
        mWindowManager = mContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        setUpView()

        val params = WindowManager.LayoutParams()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this.mContext)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {//6.0+
                    params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                }
            } else {
                params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            }
        } else {
            params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        // 设置flag

        val flags = WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        flags.and(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        // | LayoutParams.FLAG_NOT_FOCUSABLE;
        // 如果设置了LayoutParams.FLAG_NOT_FOCUSABLE，弹出的View收不到Back键的事件
        params.flags = flags
        // 不设置这个弹出框的透明遮罩显示为黑色
        params.format = PixelFormat.TRANSLUCENT
        // FLAG_NOT_TOUCH_MODAL不阻塞事件传递到后面的窗口
        // 设置 FLAG_NOT_FOCUSABLE 悬浮窗口较小时，后面的应用图标由不可长按变为可长按
        // 不设置这个flag的话，home页的划屏会有问题

        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = WindowManager.LayoutParams.MATCH_PARENT

        params.gravity = Gravity.CENTER
        params.windowAnimations = android.R.style.Animation_Dialog // R.style.windowAnim

        mWindowManager!!.addView(mView, params)
    }

    /**
     * 隐藏弹出框
     */
    fun close() {
        if (isShown!! && null != this.mView) {
            mWindowManager?.removeView(mView)
            isShown = false
            mWindowManager = null
        }
    }

    private fun checkWriteSettings(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(context)
    }

    private fun setUpView() {
        if (this.mView == null) {
            this.mView = LayoutInflater.from(context).inflate(R.layout.popup_controller, null)
            // 设置悬浮窗状态
            setDialogState(this.mView!!)
        }
        val view = this.mView!!

        // view.findViewById<LinearLayout>(R.id.popup_window).setBackgroundColor(Color.WHITE)

        val config = context.getSharedPreferences(SpfConfig.FILTER_SPF, Context.MODE_PRIVATE)
        val cr = context.contentResolver
        var autoMode = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        val modeSwitch = view.findViewById<ImageView>(R.id.brightness_auto_manual)

        val updateView = Runnable {
            if (GlobalStatus.filterEnabled) {
                if (autoMode) {
                    // 切换为手动模式按钮
                    modeSwitch.setImageResource(R.drawable.icon_brightness_auto)

                    val levels = SpfConfig.BRIGTHNESS_OFFSET_LEVELS
                    val offset = config.getInt(SpfConfig.BRIGTHNESS_OFFSET, SpfConfig.BRIGTHNESS_OFFSET_DEFAULT)
                    val brightnessValueView = view.findViewById<TextView>(R.id.brightness_value).apply {
                        text = "" + (if (offset > 0) {
                            "+$offset%"
                        } else if (offset < 0) {
                            "$offset%"
                        } else {
                            "--"
                        })
                    }
                    view.findViewById<SeekBar>(R.id.brightness_current).run {
                        setOnSeekBarChangeListener(null)
                        max = levels
                        val centerValue = (levels / 2)
                        progress = offset + centerValue
                        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                                val offset = progress - centerValue
                                brightnessValueView.text = "" + (if (offset > 0) {
                                    "+$offset%"
                                } else if (offset < 0) {
                                    "$offset%"
                                } else {
                                    "--"
                                })
                                config.edit().putInt(SpfConfig.BRIGTHNESS_OFFSET, offset).apply()
                                val runnable = filterRefresh
                                runnable?.run()
                            }

                            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                            }

                            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                            }
                        })
                    }
                } else {
                    // 切换为自动模式按钮
                    modeSwitch.setImageResource(R.drawable.icon_brightness_manual)

                    var manualCurrent = 0
                    var manualMax = config.getInt(SpfConfig.SCREENT_MAX_LIGHT, SpfConfig.SCREENT_MAX_LIGHT_DEFAULT)

                    try {
                        val contentResolver = context.contentResolver
                        manualCurrent = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                    } catch (ex: Exception) {
                    }


                    if (manualCurrent > manualMax) {
                        config.edit().putInt(SpfConfig.SCREENT_MAX_LIGHT, manualCurrent).apply()
                        manualMax = manualCurrent
                    }

                    val valueMax = if (manualCurrent > manualMax) manualCurrent else manualMax

                    val brightnessValueView = view.findViewById<TextView>(R.id.brightness_value).apply {
                        text = "" + String.format("%.1f%%", manualCurrent * 100f / valueMax)
                    }
                    view.findViewById<SeekBar>(R.id.brightness_current).run {
                        setOnSeekBarChangeListener(null)
                        max = valueMax
                        progress = manualCurrent
                        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, progress)
                                brightnessValueView.text = "" + String.format("%.1f%%", progress * 100f / valueMax)
                            }

                            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                            }

                            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                            }
                        })
                    }
                }

                /*
                view.findViewById<ImageView>(R.id.brightness_power_switch).apply {
                    setImageResource(R.drawable.icon_power_off)
                }.setOnClickListener {
                    val runnable = filterClose
                    if (runnable != null) {
                        runnable.run()
                        config.edit().putBoolean(SpfConfig.FILTER_AUTO_START, false).apply()

                        setUpView()
                    }
                }
                view.findViewById<View>(R.id.brightness_controller).visibility = View.VISIBLE
                */
            } else {
                /*
                view.findViewById<ImageView>(R.id.brightness_power_switch).apply {
                    setImageResource(R.drawable.icon_power_off)
                }.setOnClickListener {
                    val runnable = filterOpen
                    if (runnable != null) {
                        runnable.run()
                        config.edit().putBoolean(SpfConfig.FILTER_AUTO_START, true).apply()

                        setUpView()
                    }
                }
                view.findViewById<View>(R.id.brightness_controller).visibility = View.GONE
                */
            }
        }

        modeSwitch.setOnClickListener {
            try {
                if (autoMode) {
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                } else {
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
                }
                autoMode = !autoMode
                updateView.run()
            } catch (ex: java.lang.Exception) {
            }
        }
        updateView.run()
    }

    // 设置悬浮窗状态
    private fun setDialogState(view: View) {
        // 点击窗口外部区域可消除
        // 这点的实现主要将悬浮窗设置为全屏大小，外层有个透明背景，中间一部分视为内容区域
        // 所以点击内容区域外部视为点击悬浮窗外部
        val popupWindowView = view.findViewById<View>(R.id.popup_window)// 非透明的内容区域

        view.setOnTouchListener { v, event ->
            val x = event.x.toInt()
            val y = event.y.toInt()
            val rect = Rect()
            popupWindowView.getGlobalVisibleRect(rect)
            if (!rect.contains(x, y)) {
                close()
            }
            false
        }

        // 点击back键可消除
        view.setOnKeyListener { v, keyCode, event ->
            when (keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    close()
                    true
                }
                else -> false
            }
        }
    }

    companion object {
        private var mWindowManager: WindowManager? = null
        var isShown: Boolean? = false
    }
}