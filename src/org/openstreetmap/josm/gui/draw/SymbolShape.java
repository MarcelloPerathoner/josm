// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.draw;

import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;

import org.openstreetmap.josm.gui.mappaint.Keyword;

/**
 * A list of possible symbol shapes.
 * @since 10875
 */
public enum SymbolShape {
    /**
     * A square
     */
    SQUARE(4, Math.PI / 4),
    /**
     * A circle
     */
    CIRCLE(1, 0),
    /**
     * A triangle with sides of equal length
     */
    TRIANGLE(3, Math.PI / 2),
    /**
     * A pentagon
     */
    PENTAGON(5, Math.PI / 2),
    /**
     * A hexagon
     */
    HEXAGON(6, 0),
    /**
     * A heptagon
     */
    HEPTAGON(7, Math.PI / 2),
    /**
     * An octagon
     */
    OCTAGON(8, Math.PI / 8),
    /**
     * a nonagon
     */
    NONAGON(9, Math.PI / 2),
    /**
     * A decagon
     */
    DECAGON(10, 0);

    private final int sides;
    private final double rotation;

    public static SymbolShape fromKeyword(Keyword keyword) {
        try {
            return valueOf(keyword.name());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    SymbolShape(int sides, double rotation) {
        this.sides = sides;
        this.rotation = rotation;
    }

    /**
     * Create the path for this shape around the given position
     * @param x The x position
     * @param y The y position
     * @param size The size (width for rect, diameter for rest)
     * @return The symbol.
     * @since 10875
     */
    public Shape shapeAround(double x, double y, double size) {
        double radius = size / 2;
        Shape shape;
        switch (this) {
        case SQUARE:
            // optimize for performance reasons
            shape = new Rectangle2D.Double(x - radius, y - radius, size, size);
            break;
        case CIRCLE:
            shape = new Ellipse2D.Double(x - radius, y - radius, size, size);
            break;
        default:
            shape = buildPolygon(x, y, radius);
            break;
        }
        return shape;
    }

    private Shape buildPolygon(double cx, double cy, double radius) {
        GeneralPath polygon = new GeneralPath();
        for (int i = 0; i < sides; i++) {
            double angle = ((2 * Math.PI / sides) * i) - rotation;
            double x = cx + radius * Math.cos(angle);
            double y = cy + radius * Math.sin(angle);
            if (i == 0) {
                polygon.moveTo(x, y);
            } else {
                polygon.lineTo(x, y);
            }
        }
        polygon.closePath();
        return polygon;
    }

    /**
     * Gets the number of normally straight sides this symbol has. Returns 1 for a circle.
     * @return The sides of the symbol
     */
    public int getSides() {
        return sides;
    }

    /**
     * Gets the rotation of the first point of this symbol.
     * @return The rotation
     */
    public double getRotation() {
        return rotation;
    }
}
