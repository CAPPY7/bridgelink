package app.bridgelink.android

import android.content.Intent
import android.net.Uri
import app.bridgelink.android.protocol.ReleaseCheck
import java.net.HttpURLConnection
import java.net.URL

/** First-run, update check, and reset. The link itself stays in LinkService. */
class Polish(private val activity: MainActivity) {
    fun load() {
        val prefs = Prefs.open(activity)
        activity.model.setupDone = prefs.contains("setup_done") && prefs.getBoolean("setup_done", false)
    }

    fun finishSetup() {
        Prefs.open(activity).edit().putBoolean("setup_done", true).apply()
        activity.model.setupDone = true
        activity.model.status = "Setup finished. Scan the Mac QR to pair."
    }

    fun showSetup() {
        activity.model.setupDone = false
        activity.model.page = app.bridgelink.android.ui.Page.Home
    }

    fun checkUpdate() {
        activity.model.updateLine = "Checking…"
        val local = activity.model.version.ifEmpty { "0" }
        Thread {
            val remote = readRemote()
            activity.runOnUiThread {
                if (remote.isEmpty()) {
                    activity.model.updateLine = "Could not reach the update list."
                    activity.model.status = "Open the BridgeLink releases page and download the APK."
                    return@runOnUiThread
                }
                if (ReleaseCheck.compare(local, remote) > 0) {
                    activity.model.updateLine = "BridgeLink $remote is ready."
                    activity.model.status = "A newer version is ready. The releases page is opening."
                    val view = Intent(Intent.ACTION_VIEW, Uri.parse(ReleaseCheck.RELEASES))
                    activity.startActivity(view)
                } else {
                    activity.model.updateLine = "BridgeLink $local is the latest."
                    activity.model.status = "You already have the latest BridgeLink."
                }
            }
        }.start()
    }

    fun reset() {
        activity.work.forget()
        Prefs.open(activity).edit().clear().apply()
        activity.model.setupDone = false
        activity.model.updateLine = ""
        activity.model.page = app.bridgelink.android.ui.Page.Home
        activity.model.status = "BridgeLink was reset. Pair again."
    }

    private fun readRemote(): String {
        return try {
            val conn = URL(ReleaseCheck.FEED).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.instanceFollowRedirects = true
            conn.inputStream.bufferedReader().use { ReleaseCheck.shortVersion(it.readText()) }
        } catch (_: Exception) {
            ""
        }
    }
}
