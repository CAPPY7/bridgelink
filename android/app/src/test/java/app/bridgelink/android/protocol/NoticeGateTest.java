package app.bridgelink.android.protocol;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NoticeGateTest {
    @Test
    public void theSameNoteIsSkippedAndProgressUpdatesStop() {
        String first = NoticeGate.stamp("k", "Chat", "Hi");
        assertFalse(NoticeGate.skipUpdate(false, null, first));
        assertTrue(NoticeGate.skipUpdate(false, first, first));
        assertFalse(NoticeGate.skipUpdate(false, first, NoticeGate.stamp("k", "Chat", "Next")));
        assertFalse(NoticeGate.skipUpdate(true, null, first));
        assertTrue(NoticeGate.skipUpdate(true, first, NoticeGate.stamp("k", "Chat", "40%")));
    }
}
