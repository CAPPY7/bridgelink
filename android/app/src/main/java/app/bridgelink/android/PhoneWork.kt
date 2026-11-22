package app.bridgelink.android

import android.app.AppOpsManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.ActivityCompat
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.bridgelink.android.protocol.Features
import app.bridgelink.android.protocol.HostMerge
import app.bridgelink.android.protocol.MacList
import app.bridgelink.android.protocol.MacTokens
import app.bridgelink.android.protocol.PairGate
import app.bridgelink.android.protocol.PairingLink
import app.bridgelink.android.protocol.RestrictedHelp
import app.bridgelink.android.protocol.StageRules
import app.bridgelink.android.ui.StatusText
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.LinkedHashSet

/** Pairing, send, and diagnostics. The service still moves the bytes. */
class PhoneWork(private val activity: MainActivity) {
    private val model get() = activity.model

    fun load() {
        val prefs = Prefs.open(activity)
        model.host = prefs.getString("host", "") ?: ""
        model.port = prefs.getString("port", "8787") ?: "8787"
        model.pin = ""
        model.version = try {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: ""
        } catch (_: Exception) {
            ""
        }
        HistoryDatabase.readLatest(activity) { lines ->
            activity.runOnUiThread {
                showHistory(lines)
                if (Prefs.keystoreFailed()) rememberOnce(Prefs.WARNING)
                val macs = MacList.names(prefs.getString("macs", "[]"))
                if (macs.isNotEmpty()) rememberOnce(activity.getString(R.string.saved_macs, macs))
            }
        }
        model.restricted = restrictedBlocked()
    }

    fun openLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "bridgelink") return
        model.pair = data.toString()
        stagePair()
    }

    fun connect() {
        val host = model.host.trim()
        val portText = model.port.trim()
        val prefs = Prefs.open(activity)
        val merged = HostMerge.merge(prefs.getString("hosts", "") ?: "", host)
        prefs.edit()
            .putString("host", host)
            .putString("hosts", merged)
            .putString("port", portText)
            .remove("pin")
            .apply()
        startLink(LinkService.ACTION_CONNECT, null)
    }

    fun applyLink() {
        stagePair()
    }

    /** Show the pair question. Does not write the key and does not connect. */
    fun stagePair() {
        val link = PairingLink.parse(model.pair)
        if (link == null) {
            model.status = activity.getString(R.string.bad_link)
            return
        }
        val prefs = Prefs.open(activity)
        val savedFp = prefs.getString("fp", "") ?: ""
        val savedToken = prefs.getString("token", "") ?: ""
        val offer = PairGate.offer(savedFp, savedToken, link.fingerprint, link.host)
        model.host = link.host
        model.port = link.port.toString()
        model.pin = link.pin
        model.pendingFp = link.fingerprint ?: ""
        model.pendingHosts = link.hosts.joinToString(",")
        model.pairTitle = offer.title
        model.pairDetail = offer.detail
        model.pairButton = offer.button
        model.pairDifferent = offer.differentMac
        model.askPair = true
        model.status = offer.title
    }

    fun acceptPair() {
        val prefs = Prefs.open(activity)
        val savedFp = prefs.getString("fp", "") ?: ""
        val savedToken = prefs.getString("token", "") ?: ""
        val offer = PairGate.offer(savedFp, savedToken, model.pendingFp, model.host)
        val editor = prefs.edit()
            .putString("host", model.host.trim())
            .putString("hosts", mergeHosts(prefs.getString("hosts", "") ?: "", model.pendingHosts))
            .putString("port", model.port.trim())
            .remove("pin")
        if (offer.differentMac) {
            var book = prefs.getString("macTokens", "") ?: ""
            if (offer.parkFingerprint.isNotEmpty() && savedToken.length >= 16) {
                book = MacTokens.put(book, offer.parkFingerprint, savedToken)
            }
            editor.putString("macTokens", book).remove("token")
        }
        if (model.pendingFp.isNotEmpty()) {
            editor.putString("fp", model.pendingFp).remove("pendingFp")
        }
        if (!editor.commit()) {
            model.status = activity.getString(R.string.bad_link)
            return
        }
        model.askPair = false
        model.status = activity.getString(R.string.link_saved)
        startLink(LinkService.ACTION_CONNECT, null)
    }

    fun cancelPair() {
        model.askPair = false
        model.pin = ""
        model.status = "Pairing cancelled. The saved Mac was not changed."
    }

    private fun mergeHosts(saved: String, extra: String): String {
        val ordered = LinkedHashSet<String>()
        for (part in (saved + "," + extra).split(",")) {
            val host = part.trim()
            if (host.isNotEmpty()) ordered.add(host)
        }
        return ordered.joinToString(",")
    }

    fun findMac() {
        if (activity.browser == null) {
            activity.browser = MacBrowser(activity, object : MacBrowser.Callback {
                override fun onHost(found: String) {
                    activity.runOnUiThread { model.host = found }
                }

                override fun onStatus(text: String) {
                    activity.runOnUiThread { model.status = text }
                }
            })
        }
        activity.browser?.start()
    }

    fun scan() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        GmsBarcodeScanning.getClient(activity, options)
            .startScan()
            .addOnSuccessListener { barcode ->
                val raw = barcode.rawValue
                if (raw.isNullOrEmpty()) {
                    model.status = activity.getString(R.string.scan_failed)
                    return@addOnSuccessListener
                }
                model.pair = raw
                applyLink()
            }
            .addOnFailureListener {
                model.status = activity.getString(R.string.scan_failed)
            }
    }

    fun confirm() {
        val confirm = Intent(activity, LinkService::class.java)
        confirm.action = LinkService.ACTION_CONFIRM
        ContextCompat.startForegroundService(activity, confirm)
    }

    fun sendNote() {
        startLink(LinkService.ACTION_SEND, model.message)
        model.message = ""
    }

    fun sendLink() {
        val intent = Intent(activity, LinkService::class.java)
        intent.action = LinkService.ACTION_SEND
        intent.putExtra(LinkService.EXTRA_KIND, "link")
        intent.putExtra(LinkService.EXTRA_BODY, model.link.trim())
        ContextCompat.startForegroundService(activity, intent)
        model.link = ""
    }

    fun sendClipboard() {
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        var text = ""
        if (clipboard != null && clipboard.hasPrimaryClip()) {
            val clip: ClipData? = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0 && clip.getItemAt(0).text != null) {
                text = clip.getItemAt(0).text.toString()
            }
        }
        val intent = Intent(activity, LinkService::class.java)
        intent.action = LinkService.ACTION_SEND
        intent.putExtra(LinkService.EXTRA_KIND, "clipboard")
        intent.putExtra(LinkService.EXTRA_BODY, text)
        ContextCompat.startForegroundService(activity, intent)
    }

    fun sendPicked(uri: Uri) {
        sendMany(listOf(uri))
    }

    fun sendMany(uris: List<Uri>) {
        if (uris.isEmpty()) return
        for (uri in uris) {
            try {
                activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
        }
        val intent = Intent(activity, LinkService::class.java)
        intent.action = LinkService.ACTION_SEND
        intent.putExtra(LinkService.EXTRA_KIND, if (uris.size == 1) "file" else "files")
        intent.putExtra(LinkService.EXTRA_BODY, uris.joinToString("\n") { it.toString() })
        ContextCompat.startForegroundService(activity, intent)
    }

    fun cancelFile() {
        val intent = Intent(activity, LinkService::class.java)
        intent.action = LinkService.ACTION_SEND
        intent.putExtra(LinkService.EXTRA_KIND, "cancel-file")
        ContextCompat.startForegroundService(activity, intent)
        model.status = "Cancelling the file send"
    }

    fun toggleClipboard() {
        val prefs = Prefs.open(activity)
        val flags = prefs.getString("flags", "") ?: ""
        val on = Features.enabled(flags, "clipboard", false)
        prefs.edit().putString("flags", StageRules.setFlag(flags, "clipboard", !on)).apply()
        model.status = if (on) "Clipboard sync is off" else "Clipboard sync is on. Sensitive text is skipped."
    }

    fun toggleLinks() {
        val prefs = Prefs.open(activity)
        val on = prefs.getString("linksAlways", "0") == "1"
        prefs.edit().putString("linksAlways", if (on) "0" else "1").apply()
        model.status = if (on) "Links open only while the screen is on" else "Links from the Mac open right away"
    }

    fun renameMac() {
        val prefs = Prefs.open(activity)
        val host = model.host.trim()
        if (host.isEmpty()) {
            model.status = "Connect a Mac before renaming it"
            return
        }
        val next = MacList.rename(prefs.getString("macs", "[]"), host, model.macName)
        prefs.edit().putString("macs", next).apply()
        model.status = "Renamed this Mac"
    }

    fun useSavedMac() {
        val prefs = Prefs.open(activity)
        val saved = MacList.findByName(prefs.getString("macs", "[]"), model.macName)
        if (saved == null) {
            model.status = "No saved Mac with that name"
            return
        }
        val token = MacTokens.tokenFor(prefs.getString("macTokens", ""), saved.fp)
        if (token.length < 16) {
            model.status = "That Mac needs to be paired again"
            return
        }
        prefs.edit()
            .putString("host", saved.host)
            .putString("hosts", saved.host)
            .putString("port", saved.port.toString())
            .putString("fp", saved.fp)
            .putString("token", token)
            .putString("deviceId", saved.deviceId)
            .commit()
        model.host = saved.host
        model.port = saved.port.toString()
        model.status = "Switching to ${saved.name}"
        startLink(LinkService.ACTION_CONNECT, null)
    }

    fun blockNotify() {
        val name = model.blockApp.trim()
        if (name.isEmpty()) {
            model.status = "Type the app package name to block"
            return
        }
        val prefs = Prefs.open(activity)
        val current = prefs.getString("notifyOff", "") ?: ""
        val next = if (current.isEmpty()) name else "$current,$name"
        prefs.edit().putString("notifyOff", next).apply()
        model.blockApp = ""
        model.status = "That app will not be mirrored"
    }

    fun repair() {
        forget()
        scan()
    }

    fun dndAccess() {
        val prefs = Prefs.open(activity)
        val flags = prefs.getString("flags", "") ?: ""
        prefs.edit().putString("flags", StageRules.setFlag(flags, "dnd", true)).apply()
        activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        model.status = "Allow Do Not Disturb access, then come back"
    }

    fun ringMac() {
        val find = Intent(activity, LinkService::class.java)
        find.action = LinkService.ACTION_FIND_MAC
        ContextCompat.startForegroundService(activity, find)
    }

    fun stopRing() {
        val stop = Intent(activity, LinkService::class.java)
        stop.action = LinkService.ACTION_STOP_RING
        ContextCompat.startForegroundService(activity, stop)
    }

    fun forget() {
        val forget = Intent(activity, LinkService::class.java)
        forget.action = LinkService.ACTION_FORGET
        ContextCompat.startForegroundService(activity, forget)
        model.status = activity.getString(R.string.forgot_mac)
    }

    fun batterySettings() {
        activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    fun mirror() {
        val prefs = Prefs.open(activity)
        var flags = prefs.getString("flags", "") ?: ""
        flags = StageRules.setFlag(flags, "notify", true)
        flags = StageRules.setFlag(flags, "otp", true)
        prefs.edit().putString("flags", flags).apply()
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECEIVE_SMS), 2)
        }
        if (restrictedBlocked()) {
            restrictedHelp()
            return
        }
        activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    fun restrictedHelp() {
        AlertDialog.Builder(activity)
            .setTitle("Allow restricted settings")
            .setMessage(RestrictedHelp.stepsText())
            .setPositiveButton("Open app info") { _, _ ->
                val settings = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + activity.packageName),
                )
                activity.startActivity(settings)
            }
            .setNegativeButton("Not now", null)
            .show()
    }

    fun diagnostics() {
        var number = 8787
        try {
            number = model.port.trim().toInt()
        } catch (_: NumberFormatException) {
        }
        val text = Features.diagnostics(
            model.version,
            "Android " + Build.VERSION.RELEASE,
            model.status.startsWith("Paired"),
            model.host.trim().isNotEmpty(),
            number,
            model.status,
        )
        try {
            val dir = File(activity.getExternalFilesDir(null), "diagnostics")
            if (!dir.mkdirs() && !dir.isDirectory) {
                model.status = "Could not save the diagnostics file."
                return
            }
            val file = File(dir, "bridgelink-diagnostics.txt")
            Files.write(file.toPath(), text.toByteArray(StandardCharsets.UTF_8))
            val uri = FileProvider.getUriForFile(activity, activity.packageName + ".files", file)
            val share = Intent(Intent.ACTION_SEND)
            share.type = "text/plain"
            share.putExtra(Intent.EXTRA_STREAM, uri)
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            activity.startActivity(Intent.createChooser(share, "Send diagnostics"))
        } catch (_: Exception) {
            model.status = "Could not save the diagnostics file."
        }
    }

    fun restrictedBlocked(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        return try {
            val ops = activity.getSystemService(AppOpsManager::class.java) ?: return false
            val mode = ops.unsafeCheckOpNoThrow("android:access_restricted_settings", Process.myUid(), activity.packageName)
            RestrictedHelp.blocked(Build.VERSION.SDK_INT, mode)
        } catch (_: RuntimeException) {
            false
        }
    }

    fun remember(line: String) {
        HistoryDatabase.addLine(activity, line) { lines ->
            activity.runOnUiThread {
                if (lines == null) {
                    model.lines = listOf(line) + model.lines
                    model.macBattery = StatusText.macBattery(model.lines)
                } else {
                    showHistory(lines)
                }
            }
        }
    }

    private fun rememberOnce(line: String) {
        if (model.lines.any { it.contains(line) }) return
        remember(line)
    }

    private fun showHistory(lines: List<String>) {
        model.lines = lines
        model.macBattery = StatusText.macBattery(lines)
    }

    private fun startLink(action: String, body: String?) {
        var number = 8787
        try {
            number = model.port.trim().toInt()
        } catch (_: NumberFormatException) {
        }
        val intent = Intent(activity, LinkService::class.java)
        intent.action = action
        intent.putExtra(LinkService.EXTRA_HOST, model.host.trim())
        intent.putExtra(LinkService.EXTRA_PORT, number)
        intent.putExtra(LinkService.EXTRA_PIN, model.pin.trim())
        intent.putExtra(LinkService.EXTRA_KIND, "text")
        intent.putExtra(LinkService.EXTRA_BODY, body)
        ContextCompat.startForegroundService(activity, intent)
    }
}
