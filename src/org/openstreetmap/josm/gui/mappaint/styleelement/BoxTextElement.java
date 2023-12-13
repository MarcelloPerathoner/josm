// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint.styleelement;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.font.LineMetrics;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Objects;

import org.openstreetmap.josm.data.osm.INode;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.visitor.paint.MapPaintSettings;
import org.openstreetmap.josm.data.osm.visitor.paint.StyledMapRenderer;
import org.openstreetmap.josm.gui.mappaint.Cascade;
import org.openstreetmap.josm.gui.mappaint.Environment;
import org.openstreetmap.josm.gui.mappaint.Keyword;
import org.openstreetmap.josm.tools.CheckParameterUtil;

/**
 * Text style attached to a style with a bounding box, like an icon or a symbol.
 */
public class BoxTextElement extends StyleElement {
    /**
     * The text this element should display.
     */
    public TextLabel text;
    /**
     * The x offset of the text.
     */
    public int xOffset;
    /**
     * The y offset of the text. In screen space (inverted to user space)
     */
    public int yOffset;
    /**
     * The horizontal text alignment for this text.
     */
    public Keyword hAlign;
    /**
     * The vertical text alignment for this text.
     */
    public Keyword vAlign;

    /** The bounding box of the node's symbol or icon */
    protected Rectangle symbolBounds;

    private static Cascade emptyCascade = new Cascade();

    /**
     * Create a new {@link BoxTextElement}
     * @param c The current cascade
     * @param text The text to display
     * @param symbolBounds The bounding box of the node's symbol or icon
     * @param offsetX x offset, in screen space
     * @param offsetY y offset, in screen space
     * @param hAlign The horizontal alignment {@link Keyword}
     * @param vAlign The vertical alignment {@link Keyword}
     */
    public BoxTextElement(Cascade c, TextLabel text, Rectangle symbolBounds,
            int offsetX, int offsetY, Keyword hAlign, Keyword vAlign) {
        super(c, 5f);
        xOffset = offsetX;
        yOffset = offsetY;
        CheckParameterUtil.ensureParameterNotNull(text);
        CheckParameterUtil.ensureParameterNotNull(hAlign);
        CheckParameterUtil.ensureParameterNotNull(vAlign);
        this.text = text;
        this.symbolBounds = symbolBounds;
        this.hAlign = hAlign;
        this.vAlign = vAlign;
    }

    /**
     * Create a new {@link BoxTextElement}
     * @param env The MapCSS environment
     * @param bbox The bounding box
     * @return A new {@link BoxTextElement} or <code>null</code> if the creation failed.
     */
    public static BoxTextElement create(Environment env, Rectangle bbox) {
        TextLabel text = TextLabel.create(env, DefaultStyles.PreferenceChangeListener.getTextColor(), false);
        if (text == null) return null;
        // Skip any primitives that don't have text to draw. (Styles are recreated for any tag change.)
        // The concrete text to render is not cached in this object, but computed for each
        // repaint. This way, one BoxTextElement object can be used by multiple primitives (to save memory).
        if (text.text == null) return null;

        Cascade c = env.getCascade();
        Keyword hAlign = c.get(TEXT_ANCHOR_HORIZONTAL, Keyword.RIGHT, Keyword.class);
        Keyword vAlign = c.get(TEXT_ANCHOR_VERTICAL, Keyword.BOTTOM, Keyword.class);

        Point2D offset = TextLabel.getTextOffset(c);

        return new BoxTextElement(c, text, bbox, (int) offset.getX(), (int) -offset.getY(), hAlign, vAlign);
    }

    /**
     * Creates a new {@link BoxTextElement} for a node without styles.
     *
     * @param text the node text
     * @return A new {@link BoxTextElement} or <code>null</code> if the creation failed.
     */
    public static BoxTextElement createNoStyle(String text) {
        return new BoxTextElement(emptyCascade, TextLabel.createNoStyle(text),
            DefaultStyles.PreferenceChangeListener.getRect(), 0, 0, Keyword.RIGHT, Keyword.BOTTOM);
    }

    /**
     * Anchors the text to the node symbol or icon.
     * <p>
     * <pre>
     *       left-above __center-above___ right-above
     *         left-top|                 |right-top
     *                 |                 |
     *      left-center|  center-center  |right-center
     *                 |                 |
     *      left-bottom|_________________|right-bottom
     *       left-below   center-below    right-below
     * </pre>
     *
     * @param stringBounds the size of the text when drawn
     * @param metrics the line metrics of the text
     * @return the top-left point of the text in screen coordinates
     */
    public Point anchor(Rectangle2D stringBounds, LineMetrics metrics) {
        int x = xOffset;
        int y = yOffset;
        Rectangle box = symbolBounds;
        if (hAlign == Keyword.RIGHT) {
            x += box.x + box.width + 2;
        } else {
            int textWidth = (int) stringBounds.getWidth();
            if (hAlign == Keyword.CENTER) {
                x -= textWidth / 2d;
            } else if (hAlign == Keyword.LEFT) {
                x -= -box.x + 4 + textWidth;
            } else throw new AssertionError();
        }

        if (vAlign == Keyword.BOTTOM) {
            y += box.y + box.height;
        } else {
            if (vAlign == Keyword.ABOVE) {
                y -= -box.y + (int) metrics.getDescent();
            } else if (vAlign == Keyword.TOP) {
                y -= -box.y - (int) metrics.getAscent();
            } else if (vAlign == Keyword.CENTER) {
                y += (int) ((metrics.getAscent() - metrics.getDescent()) / 2);
            } else if (vAlign == Keyword.BELOW) {
                y += box.y + box.height + (int) metrics.getAscent() + 2;
            } else throw new AssertionError();
        }
        return new Point(x, y);
    }

    @Override
    public Rectangle getBounds(IPrimitive osm, MapPaintSettings settings, StyledMapRenderer renderer, Graphics2D g,
            boolean selected, boolean outermember, boolean member) {
        if (osm instanceof INode) {
            return renderer.getBoxTextBounds((INode) osm, this, g);
        }
        return null;
    }

    @Override
    public void paintPrimitive(IPrimitive osm, MapPaintSettings settings, StyledMapRenderer renderer, Graphics2D g,
            boolean selected, boolean outermember, boolean member) {
        if (osm instanceof INode) {
            renderer.drawBoxText(g, (INode) osm, this);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        if (!super.equals(obj)) return false;
        BoxTextElement that = (BoxTextElement) obj;
        return hAlign == that.hAlign &&
               vAlign == that.vAlign &&
               xOffset == that.xOffset &&
               yOffset == that.yOffset &&
               Objects.equals(text, that.text) &&
               Objects.equals(symbolBounds, that.symbolBounds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), text, symbolBounds, hAlign, vAlign, xOffset, yOffset);
    }

    @Override
    public String toString() {
        return "BoxTextElement{" + super.toString() + ' ' + text.toStringImpl()
                + " symbolBounds=" + symbolBounds + " hAlign=" + hAlign + " vAlign=" + vAlign + " xOffset=" + xOffset + " yOffset=" + yOffset + '}';
    }
}
