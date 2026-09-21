package com.sessiontracker.adapter.runelite;

import static org.junit.Assert.assertEquals;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.Test;

/** The drawing half of the icon provider: stamping a count onto a sprite, in the game's colours. */
public class ItemManagerIconProviderTest {

    private static BufferedImage sprite() {
        BufferedImage img = new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 36, 32);
        g.dispose();
        return img;
    }

    @Test
    public void stampingTheCountKeepsTheSpriteSizeSoTheGridDoesNotShift() {
        BufferedImage out = ItemManagerIconProvider.withQuantity(sprite(), "25");

        assertEquals(36, out.getWidth());
        assertEquals(32, out.getHeight());
    }

    @Test
    public void theSpriteShowsThroughWhereTheCountIsNotDrawn() {
        BufferedImage out = ItemManagerIconProvider.withQuantity(sprite(), "25");

        assertEquals(Color.RED.getRGB(), out.getRGB(30, 28));
    }

    @Test
    public void smallCountsAreYellowLikeTheGame() {
        assertEquals(new Color(0xff, 0xff, 0x00), ItemManagerIconProvider.stackColor("2.0"));
    }

    @Test
    public void thousandsTurnWhite() {
        assertEquals(new Color(0xff, 0xff, 0xff), ItemManagerIconProvider.stackColor("150K"));
    }

    @Test
    public void millionsTurnGreen() {
        assertEquals(new Color(0x00, 0xff, 0x80), ItemManagerIconProvider.stackColor("12M"));
    }
}
