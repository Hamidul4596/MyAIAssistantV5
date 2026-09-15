package com.myaiaassistant.v4.security

object KillSwitch {
    @Volatile var enabled: Boolean = false
    fun stop() { enabled = true }
    fun resume() { enabled = false }
    fun canExecute(): Boolean = !enabled
}
