package app.bridgelink.android.protocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TextCapTest {
    @Test
    public void trimsAndCapsAtOneHundredThousand() {
        assertEquals("hello", TextCap.trimCap("  hello  "));
        assertEquals("", TextCap.trimCap(null));
        String huge = "a".repeat(TextCap.MAX + 20);
        assertEquals(TextCap.MAX, TextCap.trimCap(huge).length());
    }
}
