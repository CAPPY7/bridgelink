package app.bridgelink.android.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Page(val label: String, val title: String) {
    Home("Home", "Home"),
    Send("Send", "Send"),
    Notifications("Alerts", "Notifications"),
    Messages("Texts", "Messages"),
    Files("Files", "Files"),
    Remote("Remote", "Remote"),
    Mirror("Mirror", "Mirror"),
    Settings("Settings", "Settings"),
}

/** What the screen shows. The activity owns this object and writes it from the link service. */
class PhoneModel {
    var page by mutableStateOf(Page.Home)
    var status by mutableStateOf("Not connected")
    var lines by mutableStateOf(listOf<String>())
    var host by mutableStateOf("")
    var port by mutableStateOf("8787")
    var pin by mutableStateOf("")
    var pair by mutableStateOf("")
    var message by mutableStateOf("")
    var link by mutableStateOf("")
    var phoneBattery by mutableStateOf("—")
    var macBattery by mutableStateOf("—")
    var restricted by mutableStateOf(false)
    var version by mutableStateOf("")
}

/** Buttons on the screen. MainActivity does the real work. */
interface PhoneActions {
    fun scan()
    fun applyLink()
    fun findMac()
    fun connect()
    fun confirm()
    fun sendNote()
    fun pickFile()
    fun sendLink()
    fun sendClipboard()
    fun ringMac()
    fun stopRing()
    fun forget()
    fun batterySettings()
    fun diagnostics()
    fun mirror()
    fun restrictedHelp()
}
