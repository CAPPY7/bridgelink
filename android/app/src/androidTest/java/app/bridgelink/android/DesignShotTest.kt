package app.bridgelink.android

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.rule.GrantPermissionRule
import app.bridgelink.android.ui.Page
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
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
        val dir = java.io.File(compose.activity.filesDir, "shots")
        dir.mkdirs()
        for (page in Page.entries) {
            compose.runOnIdle { compose.activity.model.page = page }
            compose.waitForIdle()
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            FileOutputStream(java.io.File(dir, "android-" + page.name.lowercase() + ".png")).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    }
}
