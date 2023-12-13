package org.openstreetmap.josm.gui.layer;

import org.openstreetmap.josm.gui.layer.MapViewPaintable.MapViewEvent;

/**
 * Paints a layer into a map view.
 *
 * @author Michael Zangl
 * @since 10458
 */
public interface LayerPainter {
    /**
     * Paint the layer
     */
    void paint(MapViewGraphics graphics);

    /**
     * Called when the layer is added to a map view.
     * <p>
     * @param event The event.
     */
    LayerPainter attachToMapView(MapViewEvent event);

    /**
     * Called when the layer is removed from the map view and this painter is not used any more.
     * <p>
     * This method is called once on the painter returned by {@link Layer#attachToMapView}
     * @param event The event.
     */
    default void detachFromMapView(MapViewEvent event) {}
}
