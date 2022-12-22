// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import java.util.Map;

/**
 * A group of {@link Check}s.
 * @since 6114
 */
final class CheckGroup extends Container {
    /**
     * Private constructor. Use {@link #fromXML} instead.
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on illegal attributes
     */
    private CheckGroup(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
    }

    /**
     * Create this class from an XML element's attributes.
     * @param attributes the XML attributes
     * @return the new instance
     * @throws IllegalArgumentException on illegal attributes
     */
    static CheckGroup fromXML(Map<String, String> attributes) throws IllegalArgumentException {
        return new CheckGroup(attributes);
    }

    @Override
    public String toString() {
        return "CheckGroup [columns=" + columns + ']';
    }
}
