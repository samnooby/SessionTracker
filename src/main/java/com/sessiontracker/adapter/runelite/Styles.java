package com.sessiontracker.adapter.runelite;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import com.sessiontracker.adapter.PotionFormat;
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

    /** A skill row label: the skill name, with its icon if one was supplied. */
    static JLabel skillLabel(String skill, Icon icon) {
        JLabel l = new JLabel(skill);
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setForeground(SUBTEXT);
        if (icon != null) {
            l.setIcon(icon);
            l.setIconTextGap(5);
        }
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

    /** Appends a bold key + bold right-aligned value (the "Total" footer row) to a 2-column grid. */
    static void addBoldRow(JPanel grid, String key, String value, Color valueColor) {
        JLabel k = new JLabel(key);
        k.setFont(FontManager.getRunescapeBoldFont());
        k.setForeground(TEXT);
        JLabel v = new JLabel(value);
        v.setFont(FontManager.getRunescapeBoldFont());
        v.setForeground(valueColor);
        v.setHorizontalAlignment(SwingConstants.RIGHT);
        grid.add(k);
        grid.add(v);
    }

    static final int CARET_RIGHT = 0;
    static final int CARET_DOWN = 1;
    static final int CARET_LEFT = 2;

    /** A small drawn triangle caret (the pixel font has no arrow/chevron glyphs). */
    static ImageIcon caret(int direction, Color color) {
        int s = 8;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(color);
        if (direction == CARET_DOWN) {
            g.fillPolygon(new int[]{0, s, s / 2}, new int[]{1, 1, s - 1}, 3);
        } else if (direction == CARET_LEFT) {
            g.fillPolygon(new int[]{s - 1, s - 1, 1}, new int[]{0, s, s / 2}, 3);
        } else {
            g.fillPolygon(new int[]{1, 1, s - 1}, new int[]{0, s, s / 2}, 3);
        }
        g.dispose();
        return new ImageIcon(img);
    }

    /** A borderless, transparent text button for inline "Edit"/"Back"-style affordances. */
    static JButton linkButton(String text, Color fg) {
        JButton b = new JButton(text);
        b.setFont(FontManager.getRunescapeSmallFont());
        b.setForeground(fg);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    /** A small pill-style button for reusing an existing category. */
    static JButton chip(String text) {
        JButton b = new JButton(text);
        b.setFont(FontManager.getRunescapeSmallFont());
        b.setBackground(TILE);
        b.setForeground(SUBTEXT);
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(2, 7, 2, 7));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    /**
     * Makes a card-like row behave as one big button: a press anywhere on the row — its
     * background, padding, or any label — fires {@code onClick}. {@link JButton} children
     * (and their subtrees) are skipped, so e.g. an inline "Edit" button keeps its own action.
     *
     * <p>Uses {@code mousePressed} (not {@code mouseClicked}) so a press/release on different
     * child labels still registers, and attaches the listener exactly once per component so a
     * press is never counted twice.
     */
    static void clickable(JPanel row, Runnable onClick) {
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                onClick.run();
            }
        };
        attach(row, ma);
    }

    private static void attach(Component c, MouseAdapter ma) {
        if (c instanceof JButton) {
            return;
        }
        c.addMouseListener(ma);
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                attach(child, ma);
            }
        }
    }

    /** Full-label tooltip; for potions adds a dose→potion breakdown line. {@code doses} may be fractional. */
    static String itemTooltip(String label, double doses, boolean isPotion, int dosesPerPotion) {
        if (!isPotion) {
            return label;
        }
        String doseStr = doses == Math.rint(doses)
                ? Long.toString((long) doses)
                : String.format(java.util.Locale.US, "%.1f", doses);
        return "<html>" + escapeHtml(label) + "<br>" + doseStr + " doses ≈ "
                + PotionFormat.potions(doses, dosesPerPotion) + "</html>";
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
