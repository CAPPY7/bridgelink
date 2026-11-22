package app.bridgelink.android.protocol;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class NoteIdTest {
    @Test
    public void alertIdsNeverReuseFileNoticeIds() {
        for (int i = 1; i <= 500; i++) {
            int alert = StageRules.alertId(i);
            int file = StageRules.fileNoteId(i);
            assertTrue(alert >= 100 && alert <= 189);
            assertTrue(file >= 200);
            assertTrue(alert != file);
        }
    }
}
