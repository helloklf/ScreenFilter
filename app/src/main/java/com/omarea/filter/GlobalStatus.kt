package com.omarea.filter

object GlobalStatus {
    var filterEnabled = false

    var currentLux: Int = 0
    var currentSystemBrightness: Int = 0
    var currentFilterAlpah: Int = 0
    var sampleData:SampleData? = null

    var filterOpen:Runnable? = null
    var filterClose:Runnable? = null
    var filterRefresh:Runnable? = null
}
