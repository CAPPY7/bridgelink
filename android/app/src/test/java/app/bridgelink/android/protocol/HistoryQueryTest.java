package app.bridgelink.android.protocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HistoryQueryTest {
    @Test
    public void searchEscapesWildcardsAndSkipsSecrets() {
        assertEquals("%100\\% off%", HistoryQuery.like("100% off"));
        assertEquals("%a\\_b%", HistoryQuery.like("a_b"));
        assertTrue(HistoryQuery.SEARCH.contains("ESCAPE"));
        assertTrue(HistoryQuery.SEARCH.contains("ORDER BY id DESC"));
        assertFalse(HistoryQuery.keep("password: x", false));
        assertFalse(HistoryQuery.keep("hello", true));
        assertTrue(HistoryQuery.keep("hello", false));
        assertEquals("view", HistoryQuery.openPlan("file"));
        assertEquals("browse", HistoryQuery.openPlan("link"));
        assertEquals("copy", HistoryQuery.openPlan("text"));
    }
}
