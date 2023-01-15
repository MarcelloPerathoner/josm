// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.widgets;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;

import javax.swing.Icon;
import javax.swing.JCheckBox;

/**
 * A checkbox with an icon and text.
 */
public class JosmCheckBox extends JCheckBox {
    final transient Icon icon;
    private static Dimension minCheckBoxSize = null;

    /**
     * Creates a JosmCheckBox
     * @param text the CheckBox label or null
     * @param icon the CheckBox icon or null
     */
    public JosmCheckBox(String text, Icon icon) {
        super(text);
        this.icon = icon;
        if (icon != null)
            setIconTextGap(2 * icon.getIconWidth());
        if (minCheckBoxSize == null)
            minCheckBoxSize = new JCheckBox().getMinimumSize(); // NOSONAR
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (icon != null) {
            Insets i = getInsets();
            int h = getHeight() - i.top - i.bottom;
            int w = minCheckBoxSize.width - i.left - i.right;

            if (getComponentOrientation().isLeftToRight()) {
                icon.paintIcon(this, g,
                    i.left + w + icon.getIconWidth() / 2,
                    i.top + (h - icon.getIconHeight()) / 2);
            } else {
                icon.paintIcon(this, g,
                    getWidth() - i.right - w - icon.getIconWidth() * 3 / 2,
                    i.top + (h - icon.getIconHeight()) / 2);
            }
        }
    }
}
