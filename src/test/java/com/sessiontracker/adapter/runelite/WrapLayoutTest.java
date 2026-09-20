package com.sessiontracker.adapter.runelite;

import static org.junit.Assert.assertEquals;

import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.junit.Test;

/** The wrapping grid has to report every row's height, or its last row gets clipped away. */
public class WrapLayoutTest {

    private static JPanel gridOf(int cells, int parentWidth) {
        JPanel parent = new JPanel();
        parent.setSize(parentWidth, 500);
        JPanel grid = new JPanel(new WrapLayout(FlowLayout.LEFT, 2, 2));
        parent.add(grid);
        for (int i = 0; i < cells; i++) {
            JLabel cell = new JLabel();
            cell.setPreferredSize(new Dimension(38, 34));
            grid.add(cell);
        }
        return grid;
    }

    @Test
    public void countsEveryRowBeforeTheGridItselfHasBeenSized() {
        // Five 38px cells fit across 207px, so seven of them need a second row.
        JPanel grid = gridOf(7, 207);

        assertEquals(74, grid.getPreferredSize().height);
    }

    @Test
    public void asingleRowIsStillOneRowTall() {
        JPanel grid = gridOf(3, 207);

        assertEquals(38, grid.getPreferredSize().height);
    }
}
