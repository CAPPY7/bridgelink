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
    var macName by mutableStateOf("")
    var blockApp by mutableStateOf("")
    var nowPlaying by mutableStateOf("Nothing is playing")
    var clips by mutableStateOf(listOf<ClipRow>())
    var clipHistory by mutableStateOf(false)
    var clipQuery by mutableStateOf("")
    var lockOn by mutableStateOf(false)
    var atDesk by mutableStateOf(false)
    var lockFar by mutableStateOf("70")
    var lockDelay by mutableStateOf("30")
    var volume by mutableStateOf("40")
    var messages by mutableStateOf(listOf<SmsRow>())
    var smsTo by mutableStateOf("")
    var smsBody by mutableStateOf("")
    var smsOn by mutableStateOf(false)
    var sim by mutableStateOf("0")
    var simCount by mutableStateOf(1)
    var folderOn by mutableStateOf(false)
    var folderPaused by mutableStateOf(false)
    var folderLabel by mutableStateOf("Documents/BridgeLink Shared")
    var typeText by mutableStateOf("")
    var trackpadOn by mutableStateOf(false)
    var dragMode by mutableStateOf(false)
    var mirrorQuality by mutableStateOf("medium")
    var mirrorStatus by mutableStateOf("The Mac window shows this phone after you allow the screen.")
    var camFacing by mutableStateOf("back")
    var camZoom by mutableStateOf("1")
    var camFlash by mutableStateOf(false)
    var camMic by mutableStateOf(false)
    var setupDone by mutableStateOf(true)
    var updateLine by mutableStateOf("")
    var askPair by mutableStateOf(false)
    var pairTitle by mutableStateOf("")
    var pairDetail by mutableStateOf("")
    var pairButton by mutableStateOf("Pair")
    var pairDifferent by mutableStateOf(false)
    var pendingFp by mutableStateOf("")
    var pendingHosts by mutableStateOf("")
}

data class ClipRow(val id: String, val text: String, val pinned: Boolean)

data class SmsRow(val id: String, val address: String, val body: String, val incoming: Boolean)

/** Buttons on the screen. MainActivity does the real work. */
interface PhoneActions {
    fun scan()
    fun applyLink()
    fun acceptPair()
    fun cancelPair()
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
    fun cancelFile()
    fun toggleClipboard()
    fun toggleLinks()
    fun renameMac()
    fun useSavedMac()
    fun blockNotify()
    fun repair()
    fun dndAccess()
    fun media(action: String)
    fun scanDoc()
    fun useClip(id: String)
    fun pinClip(id: String)
    fun deleteClip(id: String)
    fun toggleClipHistory()
    fun clearClips()
    fun toggleLock()
    fun toggleDesk()
    fun saveLock()
    fun enableSms()
    fun refreshSms()
    fun sendSms()
    fun smsSteps()
    fun syncFolder()
    fun chooseFolder()
    fun pauseFolder()
    fun pointer(kind: String, x: Int, y: Int, text: String)
    fun toggleTrackpad()
    fun shareScreen()
    fun stopShare()
    fun startCamera()
    fun stopCamera()
    fun controlSteps()
    fun checkUpdate()
    fun finishSetup()
    fun showSetup()
    fun resetApp()
}
