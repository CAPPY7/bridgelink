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
    public void landscapeBoxStaysTheSame() {
        StageSix.Picture box = StageSix.quality("medium");
        StageSix.Picture shaped = StageSix.fit(box, 1280, 720);
        assertEquals(1280, shaped.width);
        assertEquals(720, shaped.height);
    }
}
