package com.myaiaassistant.v4.security

object SecurityGate {
    private val blocked = setOf("send_money","delete_files","change_password","factory_reset")
    fun requiresConfirmation(action: String): Boolean = action in blocked
}
