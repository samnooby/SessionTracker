package com.sessiontracker.adapter.runelite;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.function.BooleanSupplier;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Resolves item sprites on the client thread and draws the quantity over the corner on the EDT,
 * the way the game's inventory does: yellow up to 100K, white into the millions, green past 10M.
 */
final class ItemManagerIconProvider implements ItemIconProvider {

    private static final Color STACK_SMALL = new Color(0xff, 0xff, 0x00);
    private static final Color STACK_THOUSANDS = new Color(0xff, 0xff, 0xff);
    private static final Color STACK_MILLIONS = new Color(0x00, 0xff, 0x80);

    private final ItemManager itemManager;
    private final ClientThread clientThread;
    private final BooleanSupplier enabled;

    ItemManagerIconProvider(ItemManager itemManager, ClientThread clientThread, BooleanSupplier enabled) {
        this.itemManager = itemManager;
        this.clientThread = clientThread;
        this.enabled = enabled;
    }

    @Override
    public boolean enabled() {
        return enabled.getAsBoolean();
    }

    @Override
    public void apply(JLabel label, int itemId, String quantityText) {
        clientThread.invoke(() -> {
            AsyncBufferedImage image = itemManager.getImage(itemId);
            image.onLoaded(() -> SwingUtilities.invokeLater(() -> {
                label.setIcon(new ImageIcon(withQuantity(image, quantityText)));
                label.revalidate();
                label.repaint();
            }));
        });
    }

    /** Copies the sprite and stamps the count into its top-left corner, with the game's shadow. */
    static BufferedImage withQuantity(BufferedImage sprite, String quantityText) {
        BufferedImage out = new BufferedImage(sprite.getWidth(), sprite.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(sprite, 0, 0, null);
        if (quantityText != null && !quantityText.isEmpty()) {
            g.setFont(FontManager.getRunescapeSmallFont());
            g.setColor(Color.BLACK);
            g.drawString(quantityText, 1, 11);
            g.setColor(stackColor(quantityText));
            g.drawString(quantityText, 0, 10);
        }
        g.dispose();
        return out;
    }

    /**
     * The game colours a stack by magnitude. The suffix says which band we're in, because the
     * shortening thresholds and the colour thresholds are the same numbers.
     */
    static Color stackColor(String quantityText) {
        if (quantityText.endsWith("M")) {
            return STACK_MILLIONS;
        }
        if (quantityText.endsWith("K")) {
            return STACK_THOUSANDS;
        }
        return STACK_SMALL;
    }
}
