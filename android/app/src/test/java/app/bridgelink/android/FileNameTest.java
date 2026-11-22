package app.bridgelink.android;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import app.bridgelink.android.protocol.Transfers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/** Android 8 and 9 must not overwrite a download that already has this name. */
public class FileNameTest {
    @Test
    public void aSecondFileGetsANumber() throws Exception {
        File dir = Files.createTempDirectory("bridgelink-names").toFile();
        File first = new File(dir, "photo.jpg");
        assertEquals(true, first.createNewFile());
        File next = FileBridge.uniqueFile(dir, "photo.jpg");
        assertEquals("photo (1).jpg", next.getName());
        assertEquals(true, next.createNewFile());
        assertEquals("photo (2).jpg", FileBridge.uniqueFile(dir, "photo.jpg").getName());
    }

    @Test
    public void aRenamedDownloadUsesTheStoredName() {
        assertEquals("notes (1).txt", FileBridge.chosenName("notes.txt", "notes (1).txt"));
        assertEquals("notes.txt", FileBridge.chosenName("notes.txt", ""));
    }

    @Test
    public void aResumeHashesEveryPieceAlreadyOnDisk() {
        Transfers.Incoming transfer = Transfers.Incoming.begin("id-1", "notes.txt", 11);
        transfer.seed("hello".getBytes(StandardCharsets.UTF_8));
        transfer.seed(" world".getBytes(StandardCharsets.UTF_8));
        assertFalse(transfer.failed());
        assertEquals(11L, transfer.written());
    }
}
