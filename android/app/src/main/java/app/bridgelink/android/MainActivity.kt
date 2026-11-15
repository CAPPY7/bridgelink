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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import app.bridgelink.android.ui.BridgeApp
import app.bridgelink.android.ui.PhoneActions
import app.bridgelink.android.ui.PhoneModel
import app.bridgelink.android.ui.StatusText

/** Compose shell. LinkService still owns the socket. */
class MainActivity : AppCompatActivity(), PhoneActions {
    val model = PhoneModel()
    val work by lazy { PhoneWork(this) }
    var browser: MacBrowser? = null

    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (!uris.isNullOrEmpty()) work.sendMany(uris)
    }

    private val events = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val next = intent?.getStringExtra(LinkService.EXTRA_STATUS)
            val line = intent?.getStringExtra(LinkService.EXTRA_LINE)
            if (next != null) model.status = next
            if (!line.isNullOrEmpty()) work.remember(line)
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
