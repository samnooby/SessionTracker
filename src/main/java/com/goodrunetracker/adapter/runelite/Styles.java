package com.goodrunetracker.adapter.runelite;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** Shared dark-theme styling helpers so the Now / Sessions / Stats tabs look consistent. */
final class Styles {

    private Styles() {
    }

    static final Color PANEL = ColorScheme.DARK_GRAY_COLOR;
    static final Color CARD = new Color(0x36, 0x36, 0x36);
    static final Color TILE = new Color(0x2b, 0x2b, 0x2b);
    static final Color ORANGE = ColorScheme.BRAND_ORANGE;
    static final Color TEXT = Color.WHITE;
    static final Color SUBTEXT = new Color(0x9a, 0x9a, 0x9a);
    static final Color GP = new Color(0x5f, 0xd3, 0x5f);
    static final Color XP = new Color(0x7f, 0xbf, 0xff);
    static final Color MISSED = new Color(0xe0, 0xa4, 0x4c);
    static final Color NEG = new Color(0xe0, 0x6c, 0x6c);

    /** A small uppercase section heading. */
    static JLabel sectionHeader(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setForeground(SUBTEXT);
        l.setBorder(new EmptyBorder(9, 1, 3, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    /** A full-width card; caps its height to preferred so a vertical BoxLayout won't stretch it. */
    static JPanel card() {
        JPanel p = new JPanel() {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(CARD);
        p.setBorder(new EmptyBorder(8, 9, 8, 9));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    static JButton button(String text, Color bg, Color fg) {
        JButton b = new JButton(text);
        b.setFont(FontManager.getRunescapeSmallFont());
        b.setBackground(bg);
        b.setForeground(fg);
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(7, 8, 7, 8));
        return b;
    }

    /** A stat tile: a bold colored value over a small caption. The caller keeps {@code value} to update it. */
    static JPanel tile(JLabel value, String caption, Color valueColor) {
        JPanel t = new JPanel();
        t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
        t.setBackground(TILE);
        t.setBorder(new EmptyBorder(5, 7, 5, 7));
        value.setFont(FontManager.getRunescapeBoldFont());
        value.setForeground(valueColor);
        value.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel cap = new JLabel(caption.toUpperCase());
        cap.setFont(FontManager.getRunescapeSmallFont());
        cap.setForeground(SUBTEXT);
        cap.setAlignmentX(Component.LEFT_ALIGNMENT);
        t.add(value);
        t.add(cap);
        return t;
    }

    static JLabel keyLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setForeground(SUBTEXT);
        return l;
    }

    static JLabel valueLabel(Color color) {
        JLabel l = new JLabel("-");
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setForeground(color);
        l.setHorizontalAlignment(SwingConstants.RIGHT);
        return l;
    }

    /** Pins a component's max height to its preferred height (full width still allowed). */
    static void capHeight(JComponent c) {
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
    }
}
