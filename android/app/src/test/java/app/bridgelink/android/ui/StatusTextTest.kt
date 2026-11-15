package app.bridgelink.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusTextTest {
    @Test
    fun dotTitleAndBatteries() {
        assertEquals("on", StatusText.dot("Paired with the Mac"))
        assertEquals("the Mac", StatusText.title("Paired with the Mac"))
        assertEquals("off", StatusText.dot("Not connected"))
        assertEquals("off", StatusText.dot("Forgot this Mac"))
        assertEquals("wait", StatusText.dot("Match code 123456"))
        assertEquals("80%", StatusText.macBattery(listOf("Sent", "Mac battery 80%", "Mac battery 10%")))
        assertEquals("—", StatusText.macBattery(emptyList()))
        assertEquals("40% charging", StatusText.phoneBattery(40, true))
        assertEquals("—", StatusText.phoneBattery(-1, false))
    }
}
