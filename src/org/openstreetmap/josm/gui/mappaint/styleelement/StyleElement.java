// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint.styleelement;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.visitor.paint.MapPaintSettings;
import org.openstreetmap.josm.data.osm.visitor.paint.StyledMapRenderer;
import org.openstreetmap.josm.gui.mappaint.Cascade;
import org.openstreetmap.josm.gui.mappaint.Keyword;
import org.openstreetmap.josm.gui.mappaint.StyleKeys;
import org.openstreetmap.josm.spi.preferences.Config;

/**
 * Class that defines how objects ({@link OsmPrimitive}) should be drawn on the map.
 *
 * Several subclasses of this abstract class implement different drawing features,
 * like icons for a node or area fill. This class and all its subclasses are immutable
 * and tend to get shared when multiple objects have the same style (in order to
 * save memory, see {@link org.openstreetmap.josm.gui.mappaint.StyleCache#intern()}).
 */
public abstract class StyleElement implements StyleKeys {

    protected static final int ICON_IMAGE_IDX = 0;
    protected static final int ICON_WIDTH_IDX = 1;
    protected static final int ICON_HEIGHT_IDX = 2;
    protected static final int ICON_OPACITY_IDX = 3;
    protected static final int ICON_OFFSET_X_IDX = 4;
    protected static final int ICON_OFFSET_Y_IDX = 5;

    /**
     * The major z index of this style element
     */
    public float majorZIndex;
    /**
     * The z index as set by the user
     */
    public float zIndex;
    /**
     * The object z index
     */
    public float objectZIndex;
    /**
     * false, if style can serve as main style for the primitive;
     * true, if it is a highlight or modifier
     */
    public boolean isModifier;
    /**
     * A flag indicating that the selection color handling should be done automatically
     */
    public boolean defaultSelectedHandling;

    /**
     * Convenience function to transform bounds
     * <p>
     * This function returns a bounds rectangle that contains the transformed bounds.
     * The returned rectangle will be axis-aligned even if the transform contains a
     * rotation.
     *
     * @param bbox the untransformed bounds
     * @param at the affine transformation
     * @return the transformed bounds
     */
    public static Rectangle transformBounds(Rectangle bbox, AffineTransform at) {
        Point[] pts = new Point[4];
        pts[0] = new Point(bbox.x,              bbox.y);
        pts[1] = new Point(bbox.x + bbox.width, bbox.y);
        pts[2] = new Point(bbox.x,              bbox.y + bbox.height);
        pts[3] = new Point(bbox.x + bbox.width, bbox.y + bbox.height);
        at.transform(pts, 0, pts, 0, 4);
        Rectangle bounds = new Rectangle(pts[0]);
        bounds.add(pts[1]);
        bounds.add(pts[2]);
        bounds.add(pts[3]);
        return bounds;
    }

    /**
     * Returns screen bounds for the element rendered with the style
     * <p>
     * An osm node has a bbox containing a single point, but with styles applied the
     * bounds may well be much bigger, ie. the size of the symbol or the size of the
     * node text.  This default implementation, which you should override, returns the
     * primitive's bbox inflated by a small fixed amount.
     * <p>
     * The bbox is in screen coordinates relative to the mapview origin.
     * <p>
     * If the exact bounds are expensive to calculate you may return inexact bounds that
     * are guaranteed to contain the exact bounds, ie. return a bigger rectangle.
     */
    public abstract Rectangle getBounds(IPrimitive osm, MapPaintSettings settings, StyledMapRenderer renderer, Graphics2D g,
            boolean selected, boolean outermember, boolean member);

    /**
     * Construct a new StyleElement
     * @param majorZIndex like z-index, but higher priority
     * @param zIndex order the objects are drawn
     * @param objectZIndex like z-index, but lower priority
     * @param isModifier if false, a default line or node symbol is generated
     * @param defaultSelectedHandling true if default behavior for selected objects
     * is enabled, false if a style for selected state is given explicitly
     */
    protected StyleElement(float majorZIndex, float zIndex, float objectZIndex, boolean isModifier, boolean defaultSelectedHandling) {
        this.majorZIndex = majorZIndex;
        this.zIndex = zIndex;
        this.objectZIndex = objectZIndex;
        this.isModifier = isModifier;
        this.defaultSelectedHandling = defaultSelectedHandling;
    }

    protected StyleElement(Cascade c, float defaultMajorZindex) {
        majorZIndex = c.get(MAJOR_Z_INDEX, defaultMajorZindex, Float.class);
        zIndex = c.get(Z_INDEX, 0f, Float.class);
        objectZIndex = c.get(OBJECT_Z_INDEX, 0f, Float.class);
        isModifier = c.get(MODIFIER, Boolean.FALSE, Boolean.class);
        defaultSelectedHandling = c.isDefaultSelectedHandling();
    }

    /**
     * draws a primitive
     * @param primitive primitive to draw
     * @param paintSettings paint settings
     * @param painter painter
     * @param selected true, if primitive is selected
     * @param outermember true, if primitive is not selected and outer member of a selected multipolygon relation
     * @param member true, if primitive is not selected and member of a selected relation
     * @since 13662 (signature)
     */
    public abstract void paintPrimitive(IPrimitive primitive, MapPaintSettings paintSettings, StyledMapRenderer painter, Graphics2D g,
            boolean selected, boolean outermember, boolean member);

    /**
     * Check if this is a style that makes the line visible to the user
     * @return <code>true</code> for line styles
     */
    public boolean isProperLineStyle() {
        return false;
    }

    /* ------------------------------------------------------------------------------- */
    /* cached values                                                                   */
    /* ------------------------------------------------------------------------------- */
    /*
     * Two preference values and the set of created fonts are cached in order to avoid
     * expensive lookups and to avoid too many font objects
     *
     * FIXME: cached preference values are not updated if the user changes them during
     * a JOSM session. Should have a listener listening to preference changes.
     */
    private static volatile String defaultFontName;
    private static volatile Float defaultFontSize;
    private static final Object lock = new Object();

    // thread save access (double-checked locking)
    private static Float getDefaultFontSize() {
        Float s = defaultFontSize;
        if (s == null) {
            synchronized (lock) {
                s = defaultFontSize;
                if (s == null) {
                    defaultFontSize = s = (float) Config.getPref().getInt("mappaint.fontsize", 8);
                }
            }
        }
        return s;
    }

    private static String getDefaultFontName() {
        String n = defaultFontName;
        if (n == null) {
            synchronized (lock) {
                n = defaultFontName;
                if (n == null) {
                    defaultFontName = n = Config.getPref().get("mappaint.font", "Droid Sans");
                }
            }
        }
        return n;
    }

    private static class FontDescriptor {
        final String name;
        final int style;
        final int size;

        FontDescriptor(String name, int style, int size) {
            this.name = name;
            this.style = style;
            this.size = size;
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, style, size);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            FontDescriptor that = (FontDescriptor) obj;
            return style == that.style &&
                    size == that.size &&
                    Objects.equals(name, that.name);
        }
    }

    private static final Map<FontDescriptor, Font> FONT_MAP = new ConcurrentHashMap<>();

    private static Font getCachedFont(String name, int style, int size) {
        return FONT_MAP.computeIfAbsent(new FontDescriptor(name, style, size), fd -> new Font(fd.name, fd.style, fd.size));
    }

    /**
     * Font for nodes without style
     */
    static Font noStyleFont = getCachedFont(
        getDefaultFontName(),
        Font.PLAIN,
        Math.round(getDefaultFontSize())
    );

    protected static Font getFont(Cascade c, String s) {
        String name = c.get(FONT_FAMILY, getDefaultFontName(), String.class);
        float size = c.get(FONT_SIZE, getDefaultFontSize(), Float.class);
        int weight = Font.PLAIN;
        if (Keyword.BOLD == c.get(FONT_WEIGHT, null, Keyword.class)) {
            weight = Font.BOLD;
        }
        int style = Font.PLAIN;
        if (Keyword.ITALIC == c.get(FONT_STYLE, null, Keyword.class)) {
            style = Font.ITALIC;
        }
        Font f = getCachedFont(name, style | weight, Math.round(size));
        if (s != null && f.canDisplayUpTo(s) == -1)
            return f;
        else {
            // fallback if the string contains characters that cannot be
            // rendered by the selected font
            return getCachedFont("SansSerif", style | weight, Math.round(size));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StyleElement that = (StyleElement) o;
        return isModifier == that.isModifier &&
               Float.compare(that.majorZIndex, majorZIndex) == 0 &&
               Float.compare(that.zIndex, zIndex) == 0 &&
               Float.compare(that.objectZIndex, objectZIndex) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(majorZIndex, zIndex, objectZIndex, isModifier);
    }

    @Override
    public String toString() {
        return String.format("z_idx=[%s/%s/%s] ", majorZIndex, zIndex, objectZIndex) + (isModifier ? "modifier " : "");
    }
}
