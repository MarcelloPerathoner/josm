// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JLabel;

import org.openstreetmap.josm.tools.GBC;

/**
 * A horizontal spacer.
 */
final class Space extends Item {
    private Space(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
    }

    /**
     * Create this class from an XML element's attributes.
     * @param attributes the XML attributes (ignored)
     * @return the new instance
     * @throws IllegalArgumentException on invalid attributes
     */
    static Space fromXML(Map<String, String> attributes) throws IllegalArgumentException {
        return new Space(attributes);
    }

    @Override
    boolean addToPanel(JComponent p, Composite.Instance parentInstance) {
        p.add(new JLabel(" "), GBC.eol()); // space
        return false;
    }

    @Override
    public String toString() {
        return "Space";
    }
}
