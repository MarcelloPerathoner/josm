// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import java.util.Map;

/**
 * A {@code <case>} element.
 */
class Case extends ConditionalMapCSS {

    /**
     * Constructor.
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on illegal attributes
     */
    Case(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
    }

    /**
     * Create this class from an XML element's attributes.
     * @param attributes the XML attributes
     * @return the new instance
     * @throws IllegalArgumentException on illegal attributes
     */
    static Case fromXML(Map<String, String> attributes) throws IllegalArgumentException {
        return new Case(attributes);
    }

    @Override
    public String toString() {
        return "Case";
    }
}
