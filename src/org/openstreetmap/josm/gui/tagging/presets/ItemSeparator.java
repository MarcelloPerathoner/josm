// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import java.util.Map;

import javax.swing.JPanel;
import javax.swing.JSeparator;

import org.openstreetmap.josm.tools.GBC;

/**
 * Class used to represent a {@link JSeparator} inside tagging preset window.
 * @since 6198
 */
final class ItemSeparator extends Item {

    private ItemSeparator(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
    }

    /**
     * Create this class from an XML element's attributes.
     * @param attributes the XML attributes (ignored)
     * @return the new instance
     * @throws IllegalArgumentException on invalid attributes
     */
    static ItemSeparator fromXML(Map<String, String> attributes) throws IllegalArgumentException {
        return new ItemSeparator(attributes);
    }

    @Override
    boolean addToPanel(JPanel p, Composite.Instance parentInstance) {
        p.add(new JSeparator(), GBC.eol().fill(GBC.HORIZONTAL).insets(0, 5, 0, 5));
        return false;
    }

    @Override
    public String toString() {
        return "ItemSeparator";
    }
}
