// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint;

import java.util.Locale;

/**
 * A MapCSS keyword.
 * <p>
 * For example "<code>round</code>" is a keyword in
 * <pre>linecap: round;</pre>
 */
public enum Keyword {
    ABOVE,
    AUTO,
    BELOW,
    BEVEL,
    BOLD,
    BOTTOM,
    CENTER,
    CIRCLE,
    DECAGON,
    DEFAULT,
    HEPTAGON,
    HEXAGON,
    INSIDE,
    ITALIC,
    LEFT,
    LINE,
    MITER,
    NONAGON,
    NONE,
    NORMAL,
    OCTAGON,
    PENTAGON,
    RIGHT,
    ROUND,
    SQUARE,
    THINNEST,
    TOP,
    TRIANGLE,
    WAY;

    /**
     * Returns the Keyword corresponding to the given string.
     * <p>
     * {@code Keyword.create("left")} returns {@code Keyword.LEFT}.
     * <p>
     * Used by the MapCSS parser.
     *
     * @return the Keyword or null
     */
    public static Keyword create(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "Keyword{" + name().toLowerCase(Locale.ENGLISH) + '}';
    }
}
