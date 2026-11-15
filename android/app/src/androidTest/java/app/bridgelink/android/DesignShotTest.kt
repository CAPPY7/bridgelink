package app.bridgelink.android

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import app.bridgelink.android.ui.Page
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/** Saves one picture of each screen. The emulator job copies them out. */
@RunWith(AndroidJUnit4::class)
class DesignShotTest {
    private val grant = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(grant).around(compose)

    @Test(timeout = 180_000)
    fun captureScreens() {
        val dirs = writableDirs()
        compose.runOnIdle {
            val model = compose.activity.model
            model.smsOn = true
            model.trackpadOn = true
            model.folderOn = true
            model.messages = listOf(app.bridgelink.android.ui.SmsRow("1", "+15551212", "On my way", true))
            model.rules = listOf(app.bridgelink.android.ui.RuleRow("connect-alert-1", "Phone connects", "Show an alert", true))
            model.nowPlaying = "Playing Sunny - Ada"
        }
        for (page in Page.entries) {
            compose.runOnIdle { compose.activity.model.page = page }
            compose.waitForIdle()
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            val name = "android-" + page.name.lowercase() + ".png"
            val png = ByteArrayOutputStream().use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "$name did not encode" }
                out.toByteArray()
            }
            check(png.size > 1000) { "$name is too small (${png.size})" }
            for (dir in dirs) {
                FileOutputStream(File(dir, name)).use { it.write(png) }
            }
            copyOut(name, dirs)
            logPng(name, png)
            println("DesignShot $name ${png.size}")
        }
        // Save the log now. Later tests can fill the log buffer.
        repeat(5) {
            println(shell("logcat -d -s BLSHOT:I -f /data/local/tmp/blshot-log.txt"))
            val ends = shell("grep -c '#END#' /data/local/tmp/blshot-log.txt")
            println("DesignShot end markers $ends")
            if (ends.trim() == "8") return
            Thread.sleep(300)
        }
    }

    @Suppress("DEPRECATION")
    private fun writableDirs(): List<File> {
        val arg = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val media = compose.activity.externalMediaDirs.firstOrNull()
        val candidates = listOfNotNull(
            arg?.let { File(it) },
            media?.let { File(it, "additional_test_output") },
            media,
            compose.activity.getExternalFilesDir(null)?.let { File(it, "shots") },
            File(compose.activity.filesDir, "shots"),
        )
        val ok = ArrayList<File>()
        for (dir in candidates) {
            if (!dir.exists() && !dir.mkdirs()) continue
            try {
                val probe = File(dir, ".probe")
                probe.writeText("ok")
                probe.delete()
                ok.add(dir)
            } catch (_: Exception) {
            }
        }
        check(ok.isNotEmpty()) { "No folder accepted a screenshot" }
        println("DesignShot dirs " + ok.joinToString(" | ") { it.absolutePath })
        return ok
    }

    /** Shell can read the media folder and /data/local/tmp. Private app files stay on the phone. */
    private fun copyOut(name: String, dirs: List<File>) {
        for (dir in dirs) {
            val src = File(dir, name)
            if (!src.exists() || src.length() < 1000) continue
            val copied = shell("cp ${src.absolutePath} /data/local/tmp/$name")
            shell("chmod 644 /data/local/tmp/$name")
            println("DesignShot cp ${src.absolutePath} $copied")
        }
    }

    private fun logPng(name: String, png: ByteArray) {
        val b64 = Base64.encodeToString(png, Base64.NO_WRAP)
        var index = 0
        var part = 0
        while (index < b64.length) {
            val end = minOf(index + 800, b64.length)
            Log.i("BLSHOT", name + "#" + part + "#" + b64.substring(index, end))
            index = end
            part++
        }
        Log.i("BLSHOT", "$name#END#$part")
    }

    private fun shell(command: String): String {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(fd).use { stream ->
            stream.readBytes().toString(Charsets.UTF_8).trim()
        }
    }
}
