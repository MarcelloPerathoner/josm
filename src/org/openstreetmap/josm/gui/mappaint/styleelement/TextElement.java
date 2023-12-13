// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint.styleelement;

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Objects;

import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.visitor.paint.MapPaintSettings;
import org.openstreetmap.josm.data.osm.visitor.paint.PaintColors;
import org.openstreetmap.josm.data.osm.visitor.paint.StyledMapRenderer;
import org.openstreetmap.josm.gui.draw.MapViewPositionAndRotation;
import org.openstreetmap.josm.gui.mappaint.Cascade;
import org.openstreetmap.josm.gui.mappaint.Environment;
import org.openstreetmap.josm.gui.mappaint.Keyword;
import org.openstreetmap.josm.gui.mappaint.StyleKeys;
import org.openstreetmap.josm.gui.mappaint.styleelement.placement.CompletelyInsideAreaStrategy;
import org.openstreetmap.josm.gui.mappaint.styleelement.placement.PositionForAreaStrategy;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Utils;

/**
 * The text that is drawn for a way/area. It may be drawn along the outline or onto the way.
 *
 * @since 11722
 */
public class TextElement extends StyleElement {

    private final TextLabel text;
    /**
     * The position strategy for this text label.
     */
    private final PositionForAreaStrategy labelPositionStrategy;

    /**
     * Create a new way/area text element definition
     * @param c The cascade
     * @param text The text
     * @param labelPositionStrategy The position in the area.
     */
    protected TextElement(Cascade c, TextLabel text, PositionForAreaStrategy labelPositionStrategy) {
        super(c, 4.9f);
        this.text = Objects.requireNonNull(text, "text");
        this.labelPositionStrategy = Objects.requireNonNull(labelPositionStrategy, "labelPositionStrategy");
    }

    /**
     * Gets the strategy that defines where to place the label.
     * @return The strategy. Never null.
     * @since 12475
     */
    public PositionForAreaStrategy getLabelPositionStrategy() {
        return labelPositionStrategy;
    }

    /**
     * Create a new text element
     * @param env The environment to read the text data from
     * @return The text element or <code>null</code> if it could not be created.
     */
    public static TextElement create(final Environment env) {
        TextLabel text = TextLabel.create(env, PaintColors.TEXT.get(), false);
        if (text == null)
            return null;
        final Cascade c = env.getCascade();

        Keyword positionKeyword = c.get(StyleKeys.TEXT_POSITION, null, Keyword.class);
        PositionForAreaStrategy position = PositionForAreaStrategy.forKeyword(positionKeyword);
        position = position.withAddedOffset(TextLabel.getTextOffset(c));

        return new TextElement(c, text, position);
    }

    /**
     * JOSM traditionally adds both line and content text elements if a fill style was set.
     *
     * For now, we simulate this by generating a TextElement if no text-position was provided.
     * @param env The environment to read the text data from
     * @return The text element or <code>null</code> if it could not be created.
     */
    public static TextElement createForContent(Environment env) {
        final Cascade c = env.getCascade();
        Keyword positionKeyword = c.get(StyleKeys.TEXT_POSITION, null, Keyword.class);
        if (positionKeyword != null) {
            return null; // No need for this hack.
        }

        TextLabel text = TextLabel.create(env, PaintColors.TEXT.get(), true);
        if (text == null) {
            return null;
        }
        return new TextElement(c, text, CompletelyInsideAreaStrategy.INSTANCE);
    }

    @Override
    public void paintPrimitive(IPrimitive primitive, MapPaintSettings paintSettings, StyledMapRenderer renderer, Graphics2D g,
            boolean selected, boolean outermember, boolean member) {
        renderer.drawText(g, primitive, text, getLabelPositionStrategy());
    }

    @Override
    public Rectangle getBounds(IPrimitive primitive, MapPaintSettings paintSettings, StyledMapRenderer renderer, Graphics2D g,
            boolean selected, boolean outermember, boolean member) {
        if (!renderer.isShowNames()) {
            return null;
        }
        String name = text.getString(primitive);
        if (Utils.isEmpty(name)) {
            return null;
        }

        FontMetrics fontMetrics = g.getFontMetrics(text.font); // if slow, use cache
        Rectangle2D stringBounds = fontMetrics.getStringBounds(name, g); // if slow, approximate by strlen()*maxcharbounds(font)
        Rectangle bounds = new Rectangle(0, 0, -1, -1); // a "nonexistent" {@link Rectangle}

        FontRenderContext frc = g.getFontRenderContext();
        renderer.forEachPolygon(primitive, path -> {
            MapViewPositionAndRotation center = labelPositionStrategy.findLabelPlacement(path, stringBounds);
            if (center != null) {
                AffineTransform at = renderer.getDisplayTextTransform(stringBounds, center);
                bounds.add(StyleElement.transformBounds(stringBounds.getBounds(), at));
            } else if (labelPositionStrategy.supportsGlyphVector()) {
                List<GlyphVector> gvs = Utils.getGlyphVectorsBidi(name, text.font, frc);
                for (GlyphVector gv : labelPositionStrategy.generateGlyphVectors(path, stringBounds, gvs)) {
                    bounds.add(gv.getPixelBounds(frc, 0f, 0f));
                }
            } else {
                Logging.trace("Couldn't find a correct label placement for {0} / {1}", primitive, name);
            }
        });
        return bounds;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        if (!super.equals(obj)) return false;
        TextElement that = (TextElement) obj;
        return Objects.equals(labelPositionStrategy, that.labelPositionStrategy)
            && Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), text, labelPositionStrategy);
    }

    @Override
    public String toString() {
        return "TextElement{" + super.toString() + "text=" + text + " labelPositionStrategy=" + labelPositionStrategy + '}';
    }
}
