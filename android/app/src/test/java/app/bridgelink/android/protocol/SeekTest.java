package app.bridgelink.android.protocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SeekTest {
    @Test
    public void seekKeepsTheRealPosition() {
        assertEquals("90000", StageFour.splitCommand("seek:90000")[1]);
        assertEquals(90000L, StageFour.seekMs("90000"));
        assertEquals(-1L, StageFour.seekMs("-1"));
        assertNull(StageFour.splitCommand("seek:nope"));
        assertEquals("100", StageFour.splitCommand("volume:140")[1]);
    }
}
