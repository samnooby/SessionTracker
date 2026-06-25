package com.sessiontracker.adapter.runelite;

import java.awt.Image;
import java.util.function.BooleanSupplier;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;

/** Resolves item images on the client thread, scales them, and sets them on the EDT. */
final class ItemManagerIconProvider implements ItemIconProvider {

    private static final int ICON_W = 18; // 36x32 source kept in aspect at ~16px tall
    private static final int ICON_H = 16;

    private final ItemManager itemManager;
    private final ClientThread clientThread;
    private final BooleanSupplier enabled;

    ItemManagerIconProvider(ItemManager itemManager, ClientThread clientThread, BooleanSupplier enabled) {
        this.itemManager = itemManager;
        this.clientThread = clientThread;
        this.enabled = enabled;
    }

    @Override
    public void apply(JLabel label, int itemId) {
        if (!enabled.getAsBoolean()) {
            return;
        }
        clientThread.invoke(() -> {
            AsyncBufferedImage image = itemManager.getImage(itemId);
            image.onLoaded(() -> {
                Image scaled = image.getScaledInstance(ICON_W, ICON_H, Image.SCALE_SMOOTH);
                SwingUtilities.invokeLater(() -> {
                    label.setIcon(new ImageIcon(scaled));
                    label.setIconTextGap(5);
                    label.revalidate();
                    label.repaint();
                });
            });
        });
    }
}
