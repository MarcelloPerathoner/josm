// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import org.openstreetmap.josm.gui.MapView;

/**
 * This class provides layers with access to drawing on the map view.
 * <p>
 * It contains information about the state of the map view.
 * <p>
 * In the future, it may add support for parallel drawing or layer caching.
 * <p>
 * It is intended to be used during {@link MapView#paint(java.awt.Graphics)}
 * @author Michael Zangl
 * @since 10458
 */
public class MapViewGraphics {
    private final Graphics2D graphics;
    private final BufferedImage buffer;
    /**
     * The bounds of the buffer in screen (MapView) coordinates. These bounds describe
     * the position of the buffer relative to the screen. In the simplest case the size
     * of the screen and the size of the buffer and the bounds are the same.  But if we
     * are to redraw only a small portion of the screen, the bounds will describe that
     * portion and the size of the buffer will be the size of the portion only.
     */
    private final Rectangle bounds;

	/**
     * Constructs a new {@code MapViewGraphics}.
     * @param mapView map view
     * @param buffer the buffer to draw into
     * @param graphics default graphics
     * @param bounds bounds in buffer coordinates of the area to be drawn
     */
    public MapViewGraphics(BufferedImage buffer, Graphics2D graphics, Rectangle bounds) {
        this.graphics = graphics;
        this.buffer = buffer;
        this.bounds = bounds;
    }

    /**
     * Gets the {@link Graphics2D} you should use to paint on this graphics object. It may already have some data painted on it.
     * You should paint your layer data on this graphics.
     * @return The {@link Graphics2D} instance.
     */
    public Graphics2D getDefaultGraphics() {
        return graphics;
    }

    public BufferedImage getBuffer() {
		return buffer;
	}

    /**
     * Returns the area to be drawn in screen coordinates
     * @return the area to be drawn
     */
    public Rectangle getBounds() {
		return bounds;
	}

    @Override
    public String toString() {
        return "MapViewGraphics [graphics=" + graphics + ", bounds=" + bounds + ']';
    }
}
