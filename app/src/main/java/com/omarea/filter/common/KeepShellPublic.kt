package com.omarea.filter.common

/**
 * Created by Hello on 2018/01/23.
 */
object KeepShellPublic {
    private var keepShell: KeepShell? = null

    //执行脚本
    fun doCmdSync(cmd: String): String {
        if (keepShell == null) {
            keepShell = KeepShell()
        }
        return keepShell!!.doCmdSync(cmd)
    }

    //执行脚本
    fun checkRoot(): Boolean {
        if (keepShell == null) {
            keepShell = KeepShell()
        }
        return keepShell!!.checkRoot()
    }

    fun tryExit() {
        if (keepShell != null) {
            keepShell!!.tryExit()
            keepShell = null
        }
    }
}
