// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import java.util.Map;

import org.openstreetmap.josm.gui.tagging.DataHandlers.TaggedHandler;

/**
 * An {@code <otherwise>} element.
 *
 * This element always matches.
 */
final class Otherwise extends Conditional {

    /**
     * Constructor.
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on illegal attributes
     */
    Otherwise(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
    }

    /**
     * Create this class from an XML element's attributes.
     * @param attributes the XML attributes
     * @return the new instance
     * @throws IllegalArgumentException on illegal attributes
     */
    static Otherwise fromXML(Map<String, String> attributes) throws IllegalArgumentException {
        return new Otherwise(attributes);
    }

    @Override
    boolean matches(TaggedHandler handler) {
        return true;
    }

    @Override
    public String toString() {
        return "Otherwise";
    }
}
