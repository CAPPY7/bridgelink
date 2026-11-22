package app.bridgelink.android;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;

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
}
