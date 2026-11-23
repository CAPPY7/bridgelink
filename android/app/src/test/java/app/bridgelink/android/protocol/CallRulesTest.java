package app.bridgelink.android.protocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CallRulesTest {
    @Test
    public void callNoticeBecomesANameAndKey() {
        CallRules.Call call = CallRules.fromNotice("call", "Ada", "+15551212", "n1");
        assertEquals("Ada", call.name);
        assertEquals("+15551212", call.number);
        assertEquals("n1", call.key);
        assertEquals("Call from Ada", CallRules.label(call));
    }

    @Test
    public void emptyCallNoticeSaysIncomingCall() {
        CallRules.Call call = CallRules.fromNotice("call", "  ", "", "n2");
        assertEquals("", call.name);
        assertEquals("Incoming call", CallRules.label(call));
    }

    @Test
    public void otherNoticesAreSkipped() {
        assertNull(CallRules.fromNotice("msg", "Ada", "hi", "n3"));
        assertNull(CallRules.fromNotice(null, "Ada", "hi", "n3"));
        assertFalse(CallRules.isCall("email"));
    }

    @Test
    public void listenerStopsWhenTheFeatureIsOffOrTheLinkIsDown() {
        assertFalse(CallRules.listen(false, true));
        assertFalse(CallRules.listen(true, false));
        assertTrue(CallRules.listen(true, true));
    }
}
