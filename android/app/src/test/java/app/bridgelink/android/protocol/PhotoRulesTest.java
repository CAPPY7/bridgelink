package app.bridgelink.android.protocol;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PhotoRulesTest {
    @Test
    public void pagesAreNewestFirstWithNoOverlap() {
        List<PhotoRules.Shot> raw = List.of(
                new PhotoRules.Shot("old", 1),
                new PhotoRules.Shot("new", 50),
                new PhotoRules.Shot("mid", 20),
                new PhotoRules.Shot("new", 50)
        );
        List<PhotoRules.Shot> ordered = PhotoRules.newestFirst(raw);
        assertEquals("new", ordered.get(0).id);
        assertEquals("mid", ordered.get(1).id);
        List<PhotoRules.Shot> first = PhotoRules.page(ordered, 0, 2);
        List<PhotoRules.Shot> second = PhotoRules.page(ordered, 2, 2);
        assertEquals(2, first.size());
        assertEquals("new", first.get(0).id);
        assertEquals("old", second.get(0).id);
        assertTrue(PhotoRules.noDuplicates(first));
        assertTrue(!first.get(0).id.equals(second.get(0).id));
    }
}
