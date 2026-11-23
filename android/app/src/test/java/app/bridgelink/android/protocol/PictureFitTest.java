package app.bridgelink.android.protocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** A portrait phone must not be squashed into a landscape box. */
public class PictureFitTest {
    @Test
    public void portraitKeepsItsShape() {
        StageSix.Picture box = StageSix.quality("medium");
        StageSix.Picture shaped = StageSix.fit(box, 1080, 2400);
        assertEquals(324, shaped.width);
        assertEquals(720, shaped.height);
        assertEquals(0, shaped.width % 2);
        assertEquals(0, shaped.height % 2);
    }

    @Test
    public void landscapeKeepsItsShape() {
        StageSix.Picture box = StageSix.quality("medium");
        StageSix.Picture shaped = StageSix.fit(box, 2400, 1080);
        assertEquals(1280, shaped.width);
        assertEquals(576, shaped.height);
        assertEquals(0, shaped.width % 2);
        assertEquals(0, shaped.height % 2);
    }

    @Test
    public void landscapeBoxStaysTheSame() {
        StageSix.Picture box = StageSix.quality("medium");
        StageSix.Picture shaped = StageSix.fit(box, 1280, 720);
        assertEquals(1280, shaped.width);
        assertEquals(720, shaped.height);
    }

    @Test
    public void bitrateFollowsPixelsAndStaysInRange() {
        assertEquals(1_000_000, StageSix.bitrateFor(100, 100, 15));
        assertEquals(6_220_800, StageSix.bitrateFor(1920, 1080, 30));
        assertEquals(8_000_000, StageSix.bitrateFor(3840, 2160, 30));
        assertEquals(720, StageSix.paneHeight(1280, 720, 1280));
        assertEquals(900, StageSix.paneHeight(1080, 2400, 720));
    }
}
