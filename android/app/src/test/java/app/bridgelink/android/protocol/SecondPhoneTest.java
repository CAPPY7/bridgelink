package app.bridgelink.android.protocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SecondPhoneTest {
    @Test
    public void aDifferentPhoneWaitsFiveMinutes() {
        assertTrue(SecondPhone.allow("", "b", false));
        assertTrue(SecondPhone.allow("a", "a", true));
        assertFalse(SecondPhone.allow("a", "b", true));
        long until = SecondPhone.until(1_000);
        assertEquals(1_000 + SecondPhone.HOLD_MS, until);
        assertFalse(SecondPhone.retry(until - 1, until));
        assertTrue(SecondPhone.retry(until, until));
    }
}
