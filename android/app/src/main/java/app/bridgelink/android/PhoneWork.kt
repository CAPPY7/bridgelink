package app.bridgelink.android

import android.app.AppOpsManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.bridgelink.android.protocol.Features
import app.bridgelink.android.protocol.MacList
import app.bridgelink.android.protocol.PairingLink
import app.bridgelink.android.protocol.RestrictedHelp
import app.bridgelink.android.ui.StatusText
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

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
        showHistory(HistoryDatabase.get(activity).history().latest(200))
        if (Prefs.keystoreFailed()) rememberOnce(Prefs.WARNING)
        val macs = MacList.names(prefs.getString("macs", "[]"))
        if (macs.isNotEmpty()) rememberOnce(activity.getString(R.string.saved_macs, macs))
        model.restricted = restrictedBlocked()
    }

    fun openLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "bridgelink") return
        model.pair = data.toString()
        applyLink()
    }

    fun connect() {
        val host = model.host.trim()
        val portText = model.port.trim()
        Prefs.open(activity).edit()
            .putString("host", host)
            .putString("hosts", host)
            .putString("port", portText)
            .remove("pin")
            .apply()
        startLink(LinkService.ACTION_CONNECT, null)
    }

    fun applyLink() {
        val link = PairingLink.parse(model.pair)
        if (link == null) {
            model.status = activity.getString(R.string.bad_link)
            return
        }
        model.host = link.host
        model.port = link.port.toString()
        model.pin = link.pin
        val editor = Prefs.open(activity).edit()
            .putString("host", link.host)
            .putString("hosts", link.hosts.joinToString(","))
            .putString("port", link.port.toString())
            .remove("pin")
        if (!link.fingerprint.isNullOrEmpty()) {
            editor.putString("fp", link.fingerprint).remove("pendingFp")
        }
        if (!editor.commit()) {
            model.status = activity.getString(R.string.bad_link)
            return
        }
        model.status = activity.getString(R.string.link_saved)
        connect()
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
        try {
            activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
        val intent = Intent(activity, LinkService::class.java)
        intent.action = LinkService.ACTION_SEND
        intent.putExtra(LinkService.EXTRA_KIND, "file")
        intent.putExtra(LinkService.EXTRA_BODY, uri.toString())
        ContextCompat.startForegroundService(activity, intent)
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
        Prefs.open(activity).edit().putString("flags", "notify=1,otp=1").apply()
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
        try {
            val dao = HistoryDatabase.get(activity).history()
            dao.insert(HistoryEntry(0, line))
            dao.trim(200)
            showHistory(dao.latest(200))
        } catch (_: Exception) {
            model.lines = listOf(line) + model.lines
            model.macBattery = StatusText.macBattery(model.lines)
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
