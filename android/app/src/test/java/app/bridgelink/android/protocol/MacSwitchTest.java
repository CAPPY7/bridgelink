package app.bridgelink.android.protocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MacSwitchTest {
    @Test
    public void switchingUsesTheSavedTokenAndKeepsTheOtherMac() {
        String book = MacTokens.put("{}", "aa11aa11aa11aa11", "tokentokentoken1");
        book = MacTokens.put(book, "bb22bb22bb22bb22", "othertokentoken2");
        MacSwitch choice = MacSwitch.choose("aa11aa11aa11aa11", "tokentokentoken1", book, "BB22BB22BB22BB22");
        assertTrue(choice.switched);
        assertEquals("BB22BB22BB22BB22", choice.fingerprint);
        assertEquals("othertokentoken2", choice.token);
        HelloPlan plan = HelloPlan.decide("Pixel", "", choice.token, choice.fingerprint, "bb22bb22bb22bb22", "n");
        assertNull(plan.kind);
        assertEquals("othertokentoken2", plan.payload.get("token").getAsString());
        assertEquals("tokentokentoken1", MacTokens.tokenFor(book, "aa11aa11aa11aa11"));
        String left = MacSwitch.forget(book, "bb22bb22bb22bb22");
        assertEquals("", MacTokens.tokenFor(left, "bb22bb22bb22bb22"));
        assertEquals("tokentokentoken1", MacTokens.tokenFor(left, "aa11aa11aa11aa11"));
    }

    @Test
    public void anUnknownCertificateDoesNotEraseTheSavedToken() {
        String book = MacTokens.put("{}", "aa11aa11aa11aa11", "tokentokentoken1");
        MacSwitch choice = MacSwitch.choose("aa11aa11aa11aa11", "tokentokentoken1", book, "cc33cc33cc33cc33");
        assertFalse(choice.switched);
        assertEquals("tokentokentoken1", choice.token);
        HelloPlan plan = HelloPlan.decide("Pixel", "", choice.token, choice.fingerprint, "cc33cc33cc33cc33", "n");
        assertEquals(LinkFailure.Kind.KEY_CHANGED, plan.kind);
        assertEquals("tokentokentoken1", MacTokens.tokenFor(book, "aa11aa11aa11aa11"));
    }
}
