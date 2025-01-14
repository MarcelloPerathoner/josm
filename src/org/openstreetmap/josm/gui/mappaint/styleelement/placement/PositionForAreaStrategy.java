// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint.styleelement.placement;

import java.awt.font.GlyphVector;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import org.openstreetmap.josm.gui.draw.MapViewPath;
import org.openstreetmap.josm.gui.draw.MapViewPositionAndRotation;
import org.openstreetmap.josm.gui.mappaint.Keyword;

/**
 * This strategy defines how to place a label or icon inside the area.
 *
 * @author Michael Zangl
 * @since 11722
 * @since 11748 moved to own file
 */
public interface PositionForAreaStrategy {
    /**
     * Finds the correct position of a label / icon inside the area.
     * @param path The area to search in
     * @param nb The bounding box of the thing we are searching a place for.
     * @return The position as rectangle with the same dimension as nb. <code>null</code> if none was found.
     */
    MapViewPositionAndRotation findLabelPlacement(MapViewPath path, Rectangle2D nb);

    /**
     * Checks whether this placement strategy supports more detailed (rotation / ...) placement using a glyph vector.
     * @return <code>true</code> if it is supported.
     */
    boolean supportsGlyphVector();

    /**
     * Aligns glyphs along a path
     * <p>
     * The input text may consist of multiple segments of bidirectional text. The way
     * may consist of multiple segments.
     *
     * @param path The path to place the text along
     * @param stringBounds The bounds of the text as returned by fontMetrics.getStringBounds(text, g)
     * @param gvList a list of {@link GlyphVector} each vector containing a segment of
     * the text in either ltr or rtl direction.
     *
     * @return The glyph vectors.
     * @throws UnsupportedOperationException if {@link #supportsGlyphVector()} returns false
     */
    default List<GlyphVector> generateGlyphVectors(
            MapViewPath path, Rectangle2D stringBounds, List<GlyphVector> gvList) {
        throw new UnsupportedOperationException("Single glyph transformation is not supported by this implementation");
    }

    /**
     * Gets a strategy for the given keyword.
     * @param keyword The text position keyword.
     * @return The strategy or line if none was specified.
     * @since 11722
     */
    static PositionForAreaStrategy forKeyword(Keyword keyword) {
        return forKeyword(keyword, OnLineStrategy.INSTANCE);
    }

    /**
     * Gets a strategy for the given keyword.
     * @param keyword The text position keyword.
     * @param defaultStrategy The default if no strategy was recognized.
     * @return The strategy or line if none was specified.
     * @since 11722
     */
    static PositionForAreaStrategy forKeyword(Keyword keyword, PositionForAreaStrategy defaultStrategy) {
        if (keyword == null)
            return defaultStrategy;
        if (Keyword.CENTER == keyword)
            return PartiallyInsideAreaStrategy.INSTANCE;
        if (Keyword.INSIDE == keyword)
            return CompletelyInsideAreaStrategy.INSTANCE;
        if (Keyword.LINE == keyword)
            return OnLineStrategy.INSTANCE;
        return defaultStrategy;
    }

    /**
     * Create a new instance of the same strategy adding a offset
     * @param addToOffset The offset to add
     * @return The new strategy
     * @since 12476
     */
    PositionForAreaStrategy withAddedOffset(Point2D addToOffset);
}
