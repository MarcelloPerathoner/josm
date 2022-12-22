// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.widgets;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagLayout;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.ImageProvider.ImageSizes;

/**
 * A label for the JOSM status bar.
 * <p>
 * This label
 * <ul>
 * <li>resizes itself according to the text length
 * <li>keeps the new width even if the text gets smaller again
 * <li>icon is left-aligned
 * <li>text is right-aligned
 * </ul>
 * Use this in the statusline to display rapidly changing information like latitude and
 * longitude of the mouse pointer.
 *
 * @since 5965
 */
public class ImageLabel extends JPanel {
    private final JLabel iconLabel;
    private final JLabel textLabel;
    private final int gap = 4;
    private int charCount = 0;

    /**
     * Constructs a new {@code ImageLabel}.
     * @param img Image name (without extension) to find in {@code statusline} directory
     * @param tooltip Tooltip text to display
     * @param charCount Character count used to compute min/preferred size
     * @param background The background color
     */
    public ImageLabel(String img, String tooltip, int charCount, Color background) {
        setLayout(new GridBagLayout());

        iconLabel = new JLabel();
        textLabel = new JLabel();
        add(iconLabel, GBC.std().grid(0, 0).weight(0, 0));
        add(textLabel, GBC.eol().grid(1, 0).weight(1, 0).fill(GBC.HORIZONTAL).insets(gap, 0, 0, 0));

        setBackground(background);
        setOpaque(true);
        setBorder(BorderFactory.createEmptyBorder(gap / 2, gap, gap / 2, gap));
        setIcon(img);
        setToolTipText(tooltip);
        setCharCount(charCount);
    }

    @Override
    public Dimension getPreferredSize() {
        return maxDimension(getMinimumSize(), super.getPreferredSize());
    }

    /**
     * Sets the image to display.
     * @param img Image name (without extension) to find in {@code statusline} directory
     */
    public void setIcon(String img) {
        iconLabel.setIcon(ImageProvider.get("statusline/", img, ImageSizes.STATUSLINE));
    }

    /**
     * Set the text to display.
     * @param text the new text
     */
    public void setText(String text) {
        textLabel.setText(text);
        setMinimumSize(maxDimension(getMinimumSize(), super.getPreferredSize()));
    }

    /**
     * Set the horizontal alignment.
     * @param horizontalAlignment the horizontal alignment
     */
    public void setHorizontalAlignment(int horizontalAlignment) {
        textLabel.setHorizontalAlignment(horizontalAlignment);
    }

    /**
     * Returns the preferred char count.
     * @return the preferred char count
     * @since 10191
     */
    public final int getCharCount() {
        return charCount;
    }

    /**
     * Sets the preferred char count.
     * @param charCount the preferred char count
     * @since 10191
     */
    public final void setCharCount(int charCount) {
        this.charCount = charCount;
        setMinimumSize(null);
        String repeatedZeroes = new String(new char[charCount]).replace('\0', '0');
        setText(repeatedZeroes);
        textLabel.setText("");
    }

    private Dimension maxDimension(Dimension a, Dimension b) {
        return new Dimension(
            Math.max(a.width, b.width),
            Math.max(a.height, b.height)
        );
    }
}
