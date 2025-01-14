// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.tools;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.Insets;

import javax.swing.Box;

/**
 * A wrapper for GridBagConstraints which has sane default static creators and
 * member functions to chain calling.
 *
 * @author imi
 */
public final class GBC extends GridBagConstraints {

    /**
     * Use public static creator functions to create an GBC.
     */
    private GBC() {}

    /**
     * Create a standard constraint (which is not the last).
     * @return A standard constraint with no filling.
     */
    public static GBC std() {
        GBC c = new GBC();
        c.anchor = GridBagConstraints.LINE_START;
        return c;
    }

    /**
     * Create the constraint for the last elements on a line.
     * @return A constraint which indicates the last item on a line.
     */
    public static GBC eol() {
        GBC c = std();
        c.gridwidth = REMAINDER;
        return c;
    }

    /**
     * Create the constraint for the last elements on a line and on a paragraph.
     * This is merely a shortcut for eol().insets(0,0,0,10)
     * @return A constraint which indicates the last item on a line.
     */
    public static GBC eop() {
        return eol().insets(0, 0, 0, 10);
    }

    /**
     * Try to fill both, horizontal and vertical
     * @return This constraint for chaining.
     */
    public GBC fill() {
        return fill(BOTH);
    }

    /**
     * Try to fill horizontally
     * @return This constraint for chaining.
     */
    public GBC hfill() {
        return fill(HORIZONTAL);
    }

    /**
     * Try to fill vertically
     * @return This constraint for chaining.
     */
    public GBC vfill() {
        return fill(VERTICAL);
    }

    /**
     * Set fill to the given value
     * @param value The filling value, either NONE, HORIZONTAL, VERTICAL or BOTH
     * @return This constraint for chaining.
     */
    public GBC fill(int value) {
        fill = value;
        if (value == HORIZONTAL || value == BOTH)
            weightx = 1.0;
        if (value == VERTICAL || value == BOTH)
            weighty = 1.0;
        return this;
    }

    /**
     * Set the anchor of this GBC to a.
     * @param a The new anchor, e.g. GBC.CENTER or GBC.EAST.
     * @return This constraint for chaining.
     */
    public GBC anchor(int a) {
        anchor = a;
        return this;
    }

    /**
     * Adds insets to this GBC.
     * @param left      The left space of the insets
     * @param top       The top space of the insets
     * @param right     The right space of the insets
     * @param bottom    The bottom space of the insets
     * @return This constraint for chaining.
     */
    public GBC insets(int left, int top, int right, int bottom) {
        insets = new Insets(top, left, bottom, right);
        return this;
    }

    /**
     * Adds insets to this GBC.
     * @param insets The insets in all directions.
     * @return This constraint for chaining.
     * @since 10649
     */
    public GBC insets(int insets) {
        insets(insets, insets, insets, insets);
        return this;
    }

    /**
     * Adds insets to this GBC.
     * @param insets The insets in all directions.
     * @return This constraint for chaining.
     * @since xxx
     */
    public GBC insets(Insets insets) {
        this.insets = insets;
        return this;
    }

    /**
     * Specifies how to distribute extra horizontal space.
     * @param weightx   Weight in horizontal direction
     * @param weighty   Weight in vertical direction
     * @return This constraint for chaining.
     */
    public GBC weight(double weightx, double weighty) {
        this.weightx = weightx;
        this.weighty = weighty;
        return this;
    }

    /**
     * This is a helper to easily create a glue with a minimum default value.
     * @param x If higher than 0, this will be a horizontal glue with x as minimum
     *      horizontal strut.
     * @param y If higher than 0, this will be a vertical glue with y as minimum
     *      vertical strut.
     * @return the glue component
     */
    public static Component glue(int x, int y) {
        short maxx = x > 0 ? Short.MAX_VALUE : 0;
        short maxy = y > 0 ? Short.MAX_VALUE : 0;
        return new Box.Filler(new Dimension(x, y), new Dimension(x, y), new Dimension(maxx, maxy));
    }

    /**
     * This is a helper to easily create a skip with a fixed value.
     * @param x The width
     * @param y The height
     * @return the skip component
     */
    public static Component skip(int x, int y) {
        Dimension dimen = new Dimension(x, y);
        return new Box.Filler(dimen, dimen, dimen);
    }

    /**
     * Sets the constraint's {@code gridx}, {@code gridy}.
     * @param gridx cell containing the leading edge of the component's display area
     * @param gridy cell at the top of the component's display area
     * @return This constraint for chaining.
     * @see #gridx
     * @see #gridy
     */
    public GBC grid(int gridx, int gridy) {
        this.gridx = gridx;
        this.gridy = gridy;
        return this;
    }

    /**
     * Sets the constraint's {@code gridwidth}, {@code gridheight}.
     * @param gridwidth number of cells in a row for the component's display area
     * @param gridheight number of cells in a column for the component's display area
     * @return This constraint for chaining.
     * @see #gridwidth
     * @see #gridheight
     */
    public GBC span(int gridwidth, int gridheight) {
        this.gridwidth = gridwidth;
        this.gridheight = gridheight;
        return this;
    }

    /**
     * Sets the constraint's {@code gridwidth}.
     * @param gridwidth number of cells in a row for the component's display area
     * @return This constraint for chaining.
     * @see #gridwidth
     */
    public GBC span(int gridwidth) {
        this.gridwidth = gridwidth;
        return this;
    }

    /**
     * Create a standard constraint with the {@code gridx}, {@code gridy} set.
     *
     * Is equivalent to {@code std().grid(gridx, gridy)}
     * @param gridx cell containing the leading edge of the component's display area
     * @param gridy cell at the top of the component's display area
     * @return A standard constraint.
     * @see #std()
     * @see #grid(int, int)
     * @see #gridx
     * @see #gridy
     */
    public static GBC std(int gridx, int gridy) {
        return std().grid(gridx, gridy);
    }
}
