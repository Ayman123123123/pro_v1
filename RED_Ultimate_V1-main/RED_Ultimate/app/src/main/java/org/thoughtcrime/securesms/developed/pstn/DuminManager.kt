package com.red.sovereign.developed.pstn

class DuminManager(val gatewayIp: String) {
    fun connect() {
        println("Connecting to Dumin PSTN Gateway at $gatewayIp")
    }
}
