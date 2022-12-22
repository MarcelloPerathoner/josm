// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import java.util.Map;

import org.openstreetmap.josm.gui.tagging.DataHandlers.TaggedHandler;

/**
 * Base class for elements that display their children conditionally.
 */
abstract class Conditional extends Composite {

    /**
     * Constructor.
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on illegal attributes
     */
    Conditional(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
    }

    /**
     * Returns true if the condition is satisfied.
     * @param handler
     *
     * @return true if the condition matches
     */
    abstract boolean matches(TaggedHandler handler);

    @Override
    public String toString() {
        return "Conditional";
    }
}
