package app.bridgelink.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import app.bridgelink.android.protocol.Features
import app.bridgelink.android.protocol.StageFive
import app.bridgelink.android.ui.SmsRow
import java.io.File

/** Screen actions for texts, the shared folder, and the trackpad. */
class FiveWork(private val activity: MainActivity) {
    private val model get() = activity.model

    fun refresh() {
        val prefs = Prefs.open(activity)
        val flags = prefs.getString("flags", "") ?: ""
        model.smsOn = Features.enabled(flags, "sms", false)
        model.folderOn = Features.enabled(flags, "folder", false)
        model.folderPaused = prefs.getBoolean("folderPaused", false)
        model.folderLabel = FolderPlace.label(activity)
        model.trackpadOn = Features.enabled(flags, "input", false)
        model.simCount = PhoneFive.simCount(activity)
        model.messages = readSms()
    }

    fun enableSms() {
        val prefs = Prefs.open(activity)
        val flags = prefs.getString("flags", "") ?: ""
        prefs.edit().putString("flags", app.bridgelink.android.protocol.StageRules.setFlag(flags, "sms", true)).apply()
        model.smsOn = true
        val needed = arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_PHONE_STATE,
        ).filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, needed.toTypedArray(), 44)
        }
        if (activity.work.restrictedBlocked()) {
            smsSteps()
            return
        }
        send("sms_push", "")
    }

    fun refreshSms() {
        send("sms_push", "")
    }

    fun sendSms() {
        val to = model.smsTo.trim()
        val body = model.smsBody
        if (!StageFive.addressOk(to)) {
            model.status = "That number looks wrong."
            return
        }
        if (body.isBlank()) {
            model.status = "Write a message first."
            return
        }
        val sim = model.sim.toIntOrNull() ?: 0
        val direct = Intent(activity, LinkService::class.java)
        direct.action = LinkService.ACTION_SEND
        direct.putExtra(LinkService.EXTRA_KIND, "sms_local")
        direct.putExtra("address", to)
        direct.putExtra(LinkService.EXTRA_BODY, body)
        direct.putExtra("sim", sim.toString())
        ContextCompat.startForegroundService(activity, direct)
        model.smsBody = ""
        model.status = "Sending the text"
    }

    fun smsSteps() {
        AlertDialog.Builder(activity)
            .setTitle("Allow text messages")
            .setMessage(StageFive.SMS_STEPS)
            .setPositiveButton("Open app info") { _, _ ->
                val settings = Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + activity.packageName),
                )
                activity.startActivity(settings)
            }
            .setNegativeButton("Not now", null)
            .show()
    }

    fun syncFolder() {
        val prefs = Prefs.open(activity)
        val flags = prefs.getString("flags", "") ?: ""
        prefs.edit().putString("flags", app.bridgelink.android.protocol.StageRules.setFlag(flags, "folder", true)).apply()
        model.folderOn = true
        model.folderPaused = false
        send("folder_sync", "")
        model.folderLabel = FolderPlace.label(activity)
        model.status = "Shared folder is on. " + model.folderLabel
    }

    fun chooseFolder() {
        model.folderLabel = FolderPlace.label(activity)
        model.status = "Shared folder is " + model.folderLabel
        send("folder_root", "")
    }

    fun pauseFolder() {
        val next = !model.folderPaused
        model.folderPaused = next
        send("folder_pause", if (next) "1" else "0")
    }

    fun pointer(kind: String, x: Int, y: Int, text: String) {
        if (!model.trackpadOn) {
            model.status = "Turn the trackpad on first."
            return
        }
        send("pointer", "$kind\t$x\t$y\t$text")
    }

    fun toggleTrackpad() {
        val on = !model.trackpadOn
        model.trackpadOn = on
        send("input_on", if (on) "1" else "0")
        if (on) model.status = "Trackpad is on. The Mac needs Accessibility the first time."
    }

    private fun readSms(): List<SmsRow> {
        return try {
            val file = File(activity.filesDir, "sms.txt")
            if (!file.isFile) return emptyList()
            StageFive.decodeSms(file.readText()).map { sms -> SmsRow(sms.id, sms.address, sms.body, sms.incoming) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun send(kind: String, body: String) {
        val intent = Intent(activity, LinkService::class.java)
        intent.action = LinkService.ACTION_SEND
        intent.putExtra(LinkService.EXTRA_KIND, kind)
        intent.putExtra(LinkService.EXTRA_BODY, body)
        ContextCompat.startForegroundService(activity, intent)
    }
}
