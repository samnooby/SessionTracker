package com.sessiontracker.adapter.runelite;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import net.runelite.client.callback.ClientThread;

/**
 * Headless Swing helpers for panel tests: run work on the EDT, read the visible text out of a
 * component tree, and drive buttons and clickable rows the way a user would.
 */
final class Swing {

    private Swing() {
    }

    /** Wait for everything already queued on the EDT (e.g. panel refreshes) to finish. */
    static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    static void onEdt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    /** A ClientThread whose {@code invoke(Runnable)} runs the task immediately on the caller. */
    static ClientThread inlineClientThread() {
        ClientThread clientThread = mock(ClientThread.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(clientThread).invoke(any(Runnable.class));
        return clientThread;
    }

    /** Every non-empty label, button and text-field string in the visible part of the tree, in order. */
    static List<String> texts(Component root) {
        List<String> out = new ArrayList<>();
        collect(root, out);
        return out;
    }

    private static void collect(Component c, List<String> out) {
        if (!c.isVisible()) {
            return;
        }
        String text = null;
        if (c instanceof JLabel) {
            text = ((JLabel) c).getText();
        } else if (c instanceof AbstractButton) {
            text = ((AbstractButton) c).getText();
        } else if (c instanceof JTextComponent) {
            text = ((JTextComponent) c).getText();
        }
        if (text != null && !text.isEmpty()) {
            out.add(text);
        }
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                collect(child, out);
            }
        }
    }

    /** Every tooltip in the visible part of the tree, in order. */
    static List<String> tooltips(Component root) {
        List<String> out = new ArrayList<>();
        collectTooltips(root, out);
        return out;
    }

    private static void collectTooltips(Component c, List<String> out) {
        if (!c.isVisible()) {
            return;
        }
        if (c instanceof javax.swing.JComponent) {
            String tip = ((javax.swing.JComponent) c).getToolTipText();
            if (tip != null && !tip.isEmpty()) {
                out.add(tip);
            }
        }
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                collectTooltips(child, out);
            }
        }
    }

    static void assertHasTooltip(Component root, String needle) {
        for (String t : tooltips(root)) {
            if (t.contains(needle)) {
                return;
            }
        }
        fail("Expected a tooltip containing \"" + needle + "\" but they were " + tooltips(root));
    }

    static boolean hasText(Component root, String needle) {
        for (String t : texts(root)) {
            if (t.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    static void assertHasText(Component root, String needle) {
        if (!hasText(root, needle)) {
            fail("Expected text containing \"" + needle + "\" but the visible texts were " + texts(root));
        }
    }

    static void assertNoText(Component root, String needle) {
        if (hasText(root, needle)) {
            fail("Did not expect text containing \"" + needle + "\" but the visible texts were " + texts(root));
        }
    }

    static <T extends Component> List<T> findAll(Component root, Class<T> type, Predicate<T> filter) {
        List<T> out = new ArrayList<>();
        findInto(root, type, filter, out);
        return out;
    }

    private static <T extends Component> void findInto(Component c, Class<T> type, Predicate<T> filter,
                                                       List<T> out) {
        if (!c.isVisible()) {
            return;
        }
        if (type.isInstance(c) && filter.test(type.cast(c))) {
            out.add(type.cast(c));
        }
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                findInto(child, type, filter, out);
            }
        }
    }

    static <T extends Component> T find(Component root, Class<T> type, Predicate<T> filter) {
        List<T> all = findAll(root, type, filter);
        if (all.isEmpty()) {
            fail("No visible " + type.getSimpleName() + " matched; visible texts were " + texts(root));
        }
        return all.get(0);
    }

    static JLabel label(Component root, String exactText) {
        return find(root, JLabel.class, l -> exactText.equals(l.getText()));
    }

    static JButton button(Component root, String exactText) {
        return find(root, JButton.class, b -> exactText.equals(b.getText()));
    }

    /** Click a button on the EDT, as a user would. */
    static void click(JButton button) throws Exception {
        onEdt(() -> button.doClick(0));
    }

    /** Press on a component wired with {@link Styles#clickable}: fires its mouse listeners on the EDT. */
    static void press(Component c) throws Exception {
        onEdt(() -> {
            MouseEvent e = new MouseEvent(c, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                    0, 1, 1, 1, false, MouseEvent.BUTTON1);
            for (MouseListener l : c.getMouseListeners()) {
                l.mousePressed(e);
            }
        });
    }
}
