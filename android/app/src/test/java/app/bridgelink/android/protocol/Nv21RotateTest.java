package app.bridgelink.android.protocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Camera pixels turn once, before they become a JPEG. */
public class Nv21RotateTest {
    @Test
    public void ninetyTurnsTheBrightness() {
        byte[] src = new byte[] {1, 2, 3, 4, 9, 8};
        byte[] turned = StageSix.rotateNv21(src, 2, 2, 90, false);
        assertEquals(3, turned[0]);
        assertEquals(1, turned[1]);
        assertEquals(4, turned[2]);
        assertEquals(2, turned[3]);
    }

    @Test
    public void oneEightyAndTwoSeventy() {
        byte[] src = new byte[] {1, 2, 3, 4, 9, 8};
        byte[] half = StageSix.rotateNv21(src, 2, 2, 180, false);
        assertEquals(4, half[0]);
        assertEquals(3, half[1]);
        assertEquals(2, half[2]);
        assertEquals(1, half[3]);
        byte[] three = StageSix.rotateNv21(src, 2, 2, 270, false);
        assertEquals(2, three[0]);
        assertEquals(4, three[1]);
        assertEquals(1, three[2]);
        assertEquals(3, three[3]);
    }

    @Test
    public void frontAddsTheDisplayTurn() {
        assertEquals(90, StageSix.cameraAngle(90, 0, false));
        assertEquals(0, StageSix.cameraAngle(90, 90, false));
        assertEquals(180, StageSix.cameraAngle(90, 90, true));
    }
}
