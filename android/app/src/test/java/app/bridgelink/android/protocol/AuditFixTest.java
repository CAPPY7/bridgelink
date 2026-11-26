package app.bridgelink.android.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class AuditFixTest {
    @Test
    public void siblingPrefixIsOutsideTheFolder() throws Exception {
        File parent = Files.createTempDirectory("bl-safe").toFile();
        File root = new File(parent, "share");
        File evil = new File(parent, "share-evil");
        assertTrue(root.mkdirs());
        assertTrue(evil.mkdirs());
        assertTrue(SafePath.inside(root, new File(root, "photo.jpg")));
        assertFalse(SafePath.inside(root, new File(evil, "photo.jpg")));
    }

    @Test
    public void oldPhoneResumesOnlyWhenTheSavedBytesMatch() {
        assertTrue(FileResume.allow(20, 20, 40));
        assertFalse(FileResume.allow(0, 0, 40));
        assertFalse(FileResume.allow(20, 10, 40));
        assertFalse(FileResume.allow(50, 50, 40));
    }

    @Test
    public void foregroundStartWaitsForARealGrant() {
        assertTrue(ServiceStart.stopFirst("app.bridgelink.android.MIRROR_STOP", "app.bridgelink.android.MIRROR_STOP"));
        assertFalse(ServiceStart.stopFirst(null, "app.bridgelink.android.MIRROR_STOP"));
        assertFalse(ServiceStart.hasPayload(false));
        assertTrue(ServiceStart.hasPayload(true));
        assertFalse(ServiceStart.cameraReady(false, false, false));
        assertTrue(ServiceStart.cameraReady(true, false, false));
        assertFalse(ServiceStart.cameraReady(true, true, false));
        assertTrue(ServiceStart.cameraReady(true, true, true));
    }

    @Test
    public void missingSmsListIsNotLooped() {
        assertTrue(SmsBatch.missing(null));
        assertFalse(SmsBatch.missing(new Object[0]));
    }

    @Test
    public void folderPathIsCheckedBeforeADirectoryIsMade() throws Exception {
        File parent = Files.createTempDirectory("bl-folder").toFile();
        File root = new File(parent, "share");
        File outside = new File(parent, "outside");
        assertTrue(root.mkdirs());
        assertTrue(outside.mkdirs());
        Files.createSymbolicLink(new File(root, "link").toPath(), outside.toPath());
        FolderFiles files = new FolderFiles(root);
        File tmp = File.createTempFile("bl", ".bin");
        Files.write(tmp.toPath(), "hi".getBytes(StandardCharsets.UTF_8));
        String stored = files.storeFile("link/newdir/note.txt", tmp);
        assertEquals("Refused a shared file.", stored);
        assertFalse(new File(outside, "newdir").exists());
    }

    @Test
    public void namesDropDirectionMarksAndKeepTheExtension() {
        assertEquals("report_.pdf", Transfers.sanitizeName("report\u202E.pdf"));
        assertEquals("note_.txt", Transfers.sanitizeName("note\u2066.txt"));
        String longName = "a".repeat(200) + ".pdf";
        String clipped = Transfers.sanitizeName(longName);
        assertEquals(120, clipped.length());
        assertTrue(clipped.endsWith(".pdf"));
    }

    @Test
    public void oneMegabyteArrivesInChunks() throws Exception {
        byte[] payload = new byte[Frame.MAX_BYTES];
        payload[0] = 7;
        payload[payload.length - 1] = 9;
        byte[] encoded = Frame.encode(Frame.BINARY, payload);
        Frame.Parser parser = new Frame.Parser();
        int offset = 0;
        java.util.List<Frame> frames = java.util.List.of();
        while (offset < encoded.length) {
            int n = Math.min(64 * 1024, encoded.length - offset);
            frames = parser.push(java.util.Arrays.copyOfRange(encoded, offset, offset + n));
            offset += n;
        }
        assertEquals(1, frames.size());
        assertEquals(Frame.MAX_BYTES, frames.get(0).payload.length);
        assertEquals(7, frames.get(0).payload[0]);
        assertEquals(9, frames.get(0).payload[Frame.MAX_BYTES - 1]);
    }
}
