package org.openstreetmap.josm.data.osm.visitor.paint;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Objects;

import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.gui.mappaint.styleelement.DefaultStyles;
import org.openstreetmap.josm.gui.mappaint.styleelement.StyleElement;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Logging;

/**
 * Stores a primitive along with a style.
 */
public class StyleRecord implements Comparable<StyleRecord> {
    private static boolean debugBounds = Config.getPref().getBoolean("mappaint.render.debug-bounds", false);

    private final StyleElement style;
    private final IPrimitive osm;
    private final int flags;
    private final long order;
    private Rectangle bounds;

    StyleRecord(StyleElement style, IPrimitive osm, int flags) {
        this.style = style;
        this.osm = osm;
        this.flags = flags;

        long styleOrder = 0;
        if ((this.flags & StyledMapRenderer.FLAG_DISABLED) == 0) {
            styleOrder |= 1;
        }

        styleOrder <<= 24;
        styleOrder |= floatToFixed(this.style.majorZIndex, 24);

        // selected on top of member of selected on top of unselected
        // FLAG_DISABLED bit is the same at this point, but we simply ignore it
        styleOrder <<= 4;
        styleOrder |= this.flags & 0xf;

        styleOrder <<= 24;
        styleOrder |= floatToFixed(this.style.zIndex, 24);

        styleOrder <<= 1;
        // simple node on top of icons and shapes
        if (DefaultStyles.SIMPLE_NODE_ELEMSTYLE.equals(this.style)) {
            styleOrder |= 1;
        }

        this.order = styleOrder;
    }

    /**
     * Converts a float to a fixed point decimal so that the order stays the same.
     *
     * @param number The float to convert
     * @param totalBits
     *            Total number of bits. 1 sign bit. There should be at least 15 bits.
     * @return The float converted to an integer.
     */
    protected static long floatToFixed(float number, int totalBits) {
        long value = Float.floatToIntBits(number) & 0xffffffffL;

        boolean negative = (value & 0x80000000L) != 0;
        // Invert the sign bit, so that negative numbers are lower
        value ^= 0x80000000L;
        // Now do the shift. Do it before accounting for negative numbers (symmetry)
        if (totalBits < 32) {
            value >>= (32 - totalBits);
        }
        // positive numbers are sorted now. Negative ones the wrong way.
        if (negative) {
            // Negative number: re-map it
            value = (1L << (totalBits - 1)) - value;
        }
        return value;
    }

    @Override
    public int compareTo(StyleRecord other) {
        int d = Long.compare(order, other.order);
        if (d != 0)
            return d;

        // newer primitives to the front
        d = Long.compare(osm.getUniqueId(), other.osm.getUniqueId());
        if (d != 0)
            return d;

        d = Float.compare(style.objectZIndex, other.style.objectZIndex);
        if (d != 0)
            return d;

        return System.identityHashCode(this) - System.identityHashCode(other);
    }

    @Override
    public int hashCode() {
        return Objects.hash(order, osm, style, flags);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        StyleRecord other = (StyleRecord) obj;
        return flags == other.flags
            && order == other.order
            && Objects.equals(osm, other.osm)
            && Objects.equals(style, other.style);
    }

    /**
     * Gets the style for this style element.
     */
    public StyleElement getStyle() {
        return style;
    }

    /**
     * Gets the primitive for this style element.
     */
    public IPrimitive getPrimitive() {
        return osm;
    }

    /**
     * Gets the screen bounds for this style element.
     */
    public Rectangle getBounds() {
        return bounds;
    }

    /**
     * Returns true if this style element intersects the given region.
     * @param bounds the given bounds
     * @return true if the style intersects the given bounds
     */
    public boolean intersects(Rectangle bounds) {
        if (this.bounds == null) {
            // this primitive does not paint anything with this style
            return false;
        }
        return bounds.intersects(this.bounds);
    }

    /**
     * Calculates the bounds of this primitive with this style.
     * @param paintSettings The settings to use.
     * @param renderer The painter to paint the style.
     */
    public Rectangle getBounds(MapPaintSettings paintSettings, StyledMapRenderer renderer, Graphics2D g) {
        bounds = style.getBounds(
                osm,
                paintSettings,
                renderer,
                g,
                (flags & StyledMapRenderer.FLAG_SELECTED) != 0,
                (flags & StyledMapRenderer.FLAG_OUTERMEMBER_OF_SELECTED) != 0,
                (flags & StyledMapRenderer.FLAG_MEMBER_OF_SELECTED) != 0
        );
        if (debugBounds && bounds != null) {
            g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }
        return bounds;
    }

    /**
     * Paints the primitive with the style.
     * @param paintSettings The settings to use.
     * @param renderer The painter to paint the style.
     */
    public void paintPrimitive(MapPaintSettings paintSettings, StyledMapRenderer renderer, Graphics2D g) {
        style.paintPrimitive(
                osm,
                paintSettings,
                renderer,
                g,
                (flags & StyledMapRenderer.FLAG_SELECTED) != 0,
                (flags & StyledMapRenderer.FLAG_OUTERMEMBER_OF_SELECTED) != 0,
                (flags & StyledMapRenderer.FLAG_MEMBER_OF_SELECTED) != 0
        );
    }

    @Override
    public String toString() {
        return "StyleRecord [style=" + style + ", osm=" + osm + ", flags=" + flags + "]";
    }
}
