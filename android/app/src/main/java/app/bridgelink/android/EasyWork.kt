package app.bridgelink.android

import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import app.bridgelink.android.protocol.StageFour
import app.bridgelink.android.ui.ClipRow
import java.io.File

/** Media, scans, clipboard history, and leave-lock. The link service still moves the bytes. */
class EasyWork(private val activity: MainActivity) {
    private val model get() = activity.model

    fun load() {
        val prefs = Prefs.open(activity)
        model.lockOn = prefs.getString("lockOn", "0") == "1"
        model.atDesk = prefs.getString("atDesk", "0") == "1"
        model.lockFar = prefs.getString("lockFar", "70") ?: "70"
        model.lockDelay = prefs.getString("lockDelay", "30") ?: "30"
        model.clipHistory = ClipStore.historyOn(activity)
        refreshClips()
    }

    fun refreshClips() {
        model.clips = ClipStore.load(activity).map { clip -> ClipRow(clip.id, clip.text, clip.pinned) }
    }

    fun media(action: String) {
        val intent = Intent(activity, LinkService::class.java)
        intent.action = LinkService.ACTION_SEND
        intent.putExtra(LinkService.EXTRA_KIND, "media")
        intent.putExtra(LinkService.EXTRA_BODY, action)
        ContextCompat.startForegroundService(activity, intent)
    }

    fun useClip(id: String) {
        val clip = model.clips.firstOrNull { it.id == id } ?: return
        val board = activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        board?.setPrimaryClip(android.content.ClipData.newPlainText("BridgeLink", clip.text))
        model.message = clip.text
        model.status = "Copied from history"
    }

    fun pinClip(id: String) {
        val clip = ClipStore.load(activity).firstOrNull { it.id == id } ?: return
        ClipStore.save(activity, StageFour.pin(ClipStore.load(activity), id, !clip.pinned))
        refreshClips()
    }

    fun deleteClip(id: String) {
        ClipStore.save(activity, StageFour.delete(ClipStore.load(activity), id))
        refreshClips()
    }

    fun toggleClipHistory() {
        val on = !ClipStore.historyOn(activity)
        ClipStore.setHistory(activity, on)
        model.clipHistory = on
        refreshClips()
        model.status = if (on) "Clipboard history is on. Copies older than 24 hours are removed." else "Clipboard history is off."
    }

    fun clearClips() {
        ClipStore.clear(activity)
        refreshClips()
        model.status = "Clipboard history cleared"
    }

    fun toggleLock() {
        model.lockOn = !model.lockOn
        saveLock(model.lockOn)
    }

    fun toggleDesk() {
        model.atDesk = !model.atDesk
        saveLock(model.lockOn)
        model.status = if (model.atDesk) "At the desk. The Mac will not lock." else "Away from the desk can lock the Mac."
    }

    fun saveLock(on: Boolean = model.lockOn) {
        val far = model.lockFar.toIntOrNull() ?: 70
        val delay = model.lockDelay.toIntOrNull() ?: 30
        val body = StageFour.formatSetup(on, far, delay, model.atDesk)
        val setup = StageFour.parseSetup(body) ?: return
        LeaveWatch.saveSetup(activity, setup)
        model.lockOn = setup[0] == 1
        model.lockFar = setup[1].toString()
        model.lockDelay = setup[2].toString()
        model.atDesk = setup[3] == 1
        val refresh = Intent(activity, LinkService::class.java)
        refresh.action = LinkService.ACTION_SEND
        refresh.putExtra(LinkService.EXTRA_KIND, "lock-refresh")
        ContextCompat.startForegroundService(activity, refresh)
        val send = Intent(activity, LinkService::class.java)
        send.action = LinkService.ACTION_SEND
        send.putExtra(LinkService.EXTRA_KIND, "lock_setup")
        send.putExtra(LinkService.EXTRA_BODY, body)
        ContextCompat.startForegroundService(activity, send)
        model.status = if (model.lockOn) {
            "Lock when you leave is on. The Mac never unlocks from the phone."
        } else {
            "Lock when you leave is off."
        }
    }

    fun sendScan(uri: Uri) {
        val type = activity.contentResolver.getType(uri) ?: ""
        val stamp = app.bridgelink.android.protocol.Features.scanName(System.currentTimeMillis())
        val name = if (type.contains("pdf") || uri.toString().lowercase().endsWith(".pdf")) stamp else stamp.replace(".pdf", ".jpg")
        val dir = File(activity.cacheDir, "outgoing")
        if (!dir.isDirectory && !dir.mkdirs()) {
            model.status = "Could not store the scan"
            return
        }
        val dest = File(dir, name)
        try {
            activity.contentResolver.openInputStream(uri).use { input ->
                if (input == null) {
                    model.status = "Could not read the scan"
                    return
                }
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (_: Exception) {
            model.status = "Could not read the scan"
            return
        }
        val intent = Intent(activity, LinkService::class.java)
        intent.action = LinkService.ACTION_SEND
        intent.putExtra(LinkService.EXTRA_KIND, "file")
        intent.putExtra(LinkService.EXTRA_BODY, dest.absolutePath)
        ContextCompat.startForegroundService(activity, intent)
        model.status = "Sending the scan to the Mac"
    }
}
