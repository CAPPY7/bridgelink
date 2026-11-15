package app.bridgelink.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import app.bridgelink.android.protocol.StageSix

/** Screen buttons for mirror, camera, and the away relay. */
class SixWork(private val activity: MainActivity) {
    private val model get() = activity.model

    fun load() {
        model.relayUrl = Prefs.open(activity).getString("relayUrl", "") ?: ""
    }

    fun shareScreen() {
        val manager = activity.getSystemService(MediaProjectionManager::class.java)
        activity.screenConsent.launch(manager.createScreenCaptureIntent())
    }

    fun onScreen(code: Int, data: Intent?) {
        if (code != android.app.Activity.RESULT_OK || data == null) {
            model.mirrorStatus = "Screen share was not allowed."
            model.status = model.mirrorStatus
            return
        }
        val intent = Intent(activity, MirrorService::class.java)
        intent.putExtra("code", code)
        intent.putExtra("data", data)
        intent.putExtra("quality", StageSix.qualityName(model.mirrorQuality.trim()))
        ContextCompat.startForegroundService(activity, intent)
        model.mirrorStatus = "Sharing the screen with the Mac."
        model.status = model.mirrorStatus
    }

    fun stopShare() {
        activity.stopService(Intent(activity, MirrorService::class.java))
        send("mirror_stop", "")
        model.mirrorStatus = "Screen sharing is off."
        model.status = model.mirrorStatus
    }

    fun startCamera() {
        val need = mutableListOf(Manifest.permission.CAMERA)
        if (model.camMic) need.add(Manifest.permission.RECORD_AUDIO)
        val missing = need.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            activity.camPermission.launch(missing.toTypedArray())
            return
        }
        openCamera()
    }

    fun openCamera() {
        val zoom = model.camZoom.toIntOrNull() ?: 1
        val intent = Intent(activity, CamService::class.java)
        intent.putExtra("facing", StageSix.facing(model.camFacing))
        intent.putExtra("zoom", StageSix.zoom(zoom))
        intent.putExtra("flash", model.camFlash)
        intent.putExtra("mic", model.camMic)
        ContextCompat.startForegroundService(activity, intent)
        model.status = "The Mac can see the camera."
    }

    fun stopCamera() {
        activity.stopService(Intent(activity, CamService::class.java))
        send("cam_stop", "")
        model.status = "Camera is off."
    }

    fun saveRelay() {
        val url = model.relayUrl.trim()
        if (!StageSix.relayUrl(url)) {
            model.status = "The relay address must start with wss://"
            return
        }
        Prefs.open(activity).edit().putString("relayUrl", url).apply()
        model.status = "Saved the relay address."
        send("relay_set", url)
    }

    fun useRelay() {
        send("relay_use", "")
    }

    fun controlSteps() {
        AlertDialog.Builder(activity)
            .setTitle("Let the Mac control this phone")
            .setMessage(StageSix.CONTROL_STEPS)
            .setPositiveButton("Open Accessibility") { _, _ ->
                activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Not now", null)
            .show()
    }

    fun onAsk(status: String) {
        when (status) {
            "Allow screen share" -> shareScreen()
            "Allow the camera" -> startCamera()
            "The Mac closed the mirror." -> activity.stopService(Intent(activity, MirrorService::class.java))
            "The Mac closed the camera." -> activity.stopService(Intent(activity, CamService::class.java))
        }
        if (status.startsWith("Sharing") || status.startsWith("The Mac")) model.mirrorStatus = status
    }

    private fun send(kind: String, body: String) {
        val intent = Intent(activity, LinkService::class.java)
        intent.action = LinkService.ACTION_SEND
        intent.putExtra(LinkService.EXTRA_KIND, kind)
        intent.putExtra(LinkService.EXTRA_BODY, body)
        ContextCompat.startForegroundService(activity, intent)
    }
}
