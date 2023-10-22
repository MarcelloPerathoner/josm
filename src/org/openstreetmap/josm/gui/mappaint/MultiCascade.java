// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.openstreetmap.josm.tools.CheckParameterUtil;

/**
 * Several layers / cascades, e.g. one for the main Line and one for each overlay.
 * The range is (0,Infinity) at first and it shrinks in the process when
 * StyleSources apply zoom level dependent properties.
 */
public class MultiCascade implements StyleKeys {
    /** The default layer of a MultiCascade. */
    public static final String DEFAULT = "default";
    /** The template layer of a MultiCascade */
    public static final String TEMPLATE = "*";
    /** The subpart id that matches all subparts (but not the default subpart!).  */
    public static final String WILDCARD = "*";

    private final Map<String, Cascade> layers;
    /**
     * The scale range this cascade is valid for
     */
    public Range range;

    /**
     * Constructs a new {@code MultiCascade}.
     */
    public MultiCascade() {
        layers = new HashMap<>();
        range = Range.ZERO_TO_INFINITY;
    }

    /**
     * Return the cascade with the given name. If it doesn't exist, create
     * a new layer with that name and return it. The new layer will be
     * a clone of the TEMPLATE layer, if it exists.
     * @param layer layer
     * @return cascade
     */
    public Cascade getOrCreateCascade(String layer) {
        CheckParameterUtil.ensureParameterNotNull(layer);
        Cascade c = layers.get(layer);
        if (c == null) {
            if (layers.containsKey(TEMPLATE)) {
                c = new Cascade(layers.get(TEMPLATE));
            } else {
                c = new Cascade();
                // Everything that is not on the default layer is assumed to
                // be a modifier. Can be overridden in style definition.
                if (!DEFAULT.equals(layer) && !TEMPLATE.equals(layer)) {
                    c.put(MODIFIER, true);
                }
            }
            layers.put(layer, c);
        }
        return c;
    }

    /**
     * Read-only version of {@link #getOrCreateCascade}. For convenience, it returns an
     * empty cascade for non-existing layers. However this empty (read-only) cascade
     * is not added to this MultiCascade object.
     * @param layer layer
     * @return cascade
     */
    public Cascade getCascade(String layer) {
        if (layer == null) {
            layer = DEFAULT;
        }
        Cascade c = layers.get(layer);
        if (c == null) {
            c = new Cascade();
            if (!DEFAULT.equals(layer) && !TEMPLATE.equals(layer)) {
                c.put(MODIFIER, true);
            }
        }
        return c;
    }

    /**
     * Gets all cascades for the known layers
     * @return The cascades for the layers
     */
    public Collection<Entry<String, Cascade>> getLayers() {
        return layers.entrySet();
    }

    /**
     * Check whether this cascade has a given layer
     * @param layer The layer to check for
     * @return <code>true</code> if it has that layer
     */
    public boolean hasLayer(String layer) {
        return layers.containsKey(layer);
    }
}
