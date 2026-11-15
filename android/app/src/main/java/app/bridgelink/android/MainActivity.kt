package app.bridgelink.android

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import app.bridgelink.android.ui.BridgeApp
import app.bridgelink.android.ui.PhoneActions
import app.bridgelink.android.ui.PhoneModel
import app.bridgelink.android.ui.StatusText
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/** Compose shell. LinkService still owns the socket. */
class MainActivity : AppCompatActivity(), PhoneActions {
    val model = PhoneModel()
    val work by lazy { PhoneWork(this) }
    val easy by lazy { EasyWork(this) }
    val five by lazy { FiveWork(this) }
    val six by lazy { SixWork(this) }
    val polish by lazy { Polish(this) }
    var browser: MacBrowser? = null

    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (!uris.isNullOrEmpty()) work.sendMany(uris)
    }

    private val scanPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) easy.sendScan(uri)
    }

    private val scanPdf = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val uri = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pdf?.uri
            ?: GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages?.firstOrNull()?.imageUri
        if (uri != null) easy.sendScan(uri) else model.status = "No scan was saved"
    }

    private val blePermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) easy.saveLock(true) else model.status = "Bluetooth was not allowed, so the Mac will not lock when you leave."
    }

    val screenConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        six.onScreen(result.resultCode, result.data)
    }

    val camPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) six.openCamera() else model.status = "Camera was not allowed."
    }

    private val events = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val next = intent?.getStringExtra(LinkService.EXTRA_STATUS)
            val line = intent?.getStringExtra(LinkService.EXTRA_LINE)
            if (next != null) {
                model.status = next
                six.onAsk(next)
                if (next.startsWith("Playing ") || next.startsWith("Paused ") || next == "Nothing is playing") {
                    model.nowPlaying = next
                }
            }
            if (!line.isNullOrEmpty()) work.remember(line)
            easy.refreshClips()
            five.refresh()
        }
    }

    private val batteryWatch = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            readBattery(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        work.load()
        easy.load()
        five.refresh()
        six.load()
        polish.load()
        setContent { BridgeApp(model, this) }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        work.openLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        work.openLink(intent)
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, events, IntentFilter(LinkService.EVENT), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(
            this,
            batteryWatch,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        model.restricted = work.restrictedBlocked()
    }

    override fun onStop() {
        unregisterReceiver(events)
        unregisterReceiver(batteryWatch)
        super.onStop()
    }

    override fun onDestroy() {
        browser?.stop()
        super.onDestroy()
    }

    fun launchPicker() {
        picker.launch(arrayOf("*/*"))
    }

    fun uiSendNote(text: String) {
        model.message = text
        sendNote()
    }

    fun uiForget() = forget()

    fun uiConnectWithPin(value: String) {
        model.pin = value
        connect()
    }

    fun uiConfirm() = confirm()

    override fun scan() = work.scan()
    override fun applyLink() = work.applyLink()
    override fun acceptPair() = work.acceptPair()
    override fun cancelPair() = work.cancelPair()
    override fun findMac() = work.findMac()
    override fun connect() = work.connect()
    override fun confirm() = work.confirm()
    override fun sendNote() = work.sendNote()
    override fun pickFile() = launchPicker()
    override fun sendLink() = work.sendLink()
    override fun sendClipboard() = work.sendClipboard()
    override fun ringMac() = work.ringMac()
    override fun stopRing() = work.stopRing()
    override fun forget() = work.forget()
    override fun batterySettings() = work.batterySettings()
    override fun diagnostics() = work.diagnostics()
    override fun mirror() = work.mirror()
    override fun restrictedHelp() = work.restrictedHelp()
    override fun cancelFile() = work.cancelFile()
    override fun toggleClipboard() = work.toggleClipboard()
    override fun toggleLinks() = work.toggleLinks()
    override fun renameMac() = work.renameMac()
    override fun blockNotify() = work.blockNotify()
    override fun repair() = work.repair()
    override fun dndAccess() = work.dndAccess()
    override fun media(action: String) = easy.media(action)
    override fun scanDoc() = startScan()
    override fun useClip(id: String) = easy.useClip(id)
    override fun pinClip(id: String) = easy.pinClip(id)
    override fun deleteClip(id: String) = easy.deleteClip(id)
    override fun toggleLock() = requestLock()
    override fun toggleDesk() = easy.toggleDesk()
    override fun saveLock() = easy.saveLock()
    override fun enableSms() = five.enableSms()
    override fun refreshSms() = five.refreshSms()
    override fun sendSms() = five.sendSms()
    override fun smsSteps() = five.smsSteps()
    override fun syncFolder() = five.syncFolder()
    override fun pauseFolder() = five.pauseFolder()
    override fun addRule() = five.addRule()
    override fun toggleRule(id: String) = five.toggleRule(id)
    override fun deleteRule(id: String) = five.deleteRule(id)
    override fun pointer(kind: String, x: Int, y: Int, text: String) = five.pointer(kind, x, y, text)
    override fun toggleTrackpad() = five.toggleTrackpad()
    override fun shareScreen() = six.shareScreen()
    override fun stopShare() = six.stopShare()
    override fun startCamera() = six.startCamera()
    override fun stopCamera() = six.stopCamera()
    override fun saveRelay() = six.saveRelay()
    override fun useRelay() = six.useRelay()
    override fun controlSteps() = six.controlSteps()
    override fun checkUpdate() = polish.checkUpdate()
    override fun finishSetup() = polish.finishSetup()
    override fun showSetup() = polish.showSetup()
    override fun resetApp() = polish.reset()

    private fun startScan() {
        try {
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(8)
                .setResultFormats(
                    GmsDocumentScannerOptions.RESULT_FORMAT_PDF,
                    GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                )
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
            GmsDocumentScanning.getClient(options).getStartScanIntent(this)
                .addOnSuccessListener { sender ->
                    scanPdf.launch(IntentSenderRequest.Builder(sender).build())
                }
                .addOnFailureListener {
                    model.status = "Document scanner is not on this phone. Pick a photo instead."
                    scanPhoto.launch("image/*")
                }
        } catch (_: Throwable) {
            model.status = "Document scanner is not on this phone. Pick a photo instead."
            scanPhoto.launch("image/*")
        }
    }

    private fun requestLock() {
        if (model.lockOn) {
            easy.saveLock(false)
            return
        }
        val needed = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH,
            )
        }
        val missing = needed.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing) blePermission.launch(needed) else easy.saveLock(true)
    }

    private fun readBattery(intent: Intent?) {
        if (intent == null) return
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return
        val percent = (level * 100) / scale
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        model.phoneBattery = StatusText.phoneBattery(percent, charging)
    }
}
