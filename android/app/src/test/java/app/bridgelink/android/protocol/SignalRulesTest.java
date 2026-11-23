package app.bridgelink.android.protocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SignalRulesTest {
    @Test
    public void signalIsSentOnlyWhenTheBarsChange() {
        assertEquals(4, SignalRules.bars(9));
        assertEquals(0, SignalRules.bars(-1));
        assertTrue(SignalRules.changed(0, false, 2));
        assertFalse(SignalRules.changed(2, true, 2));
        assertTrue(SignalRules.changed(2, true, 3));
    }
}
