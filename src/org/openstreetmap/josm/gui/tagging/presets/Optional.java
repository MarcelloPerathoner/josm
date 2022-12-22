// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.GridBagLayout;
import java.util.Map;

import javax.swing.JPanel;

/**
 * Used to group optional attributes.
 * @since 8863
 */
final class Optional extends Container {
    /**
     * Private constructor.
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on illegal attributes
     */
    private Optional(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
    }

    /**
     * Create this class from an XML element's attributes.
     * @param attributes the XML attributes
     * @return the new instance
     * @throws IllegalArgumentException on invalid attributes
     */
    static Optional fromXML(Map<String, String> attributes) throws IllegalArgumentException {
        return new Optional(attributes);
    }

    @Override
    String getDefaultText() {
        return tr("Optional Attributes:");
    }

    @Override
    JPanel getPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        addBorder(panel, localeText);
        return panel;
    }

    @Override
    public String toString() {
        return "Optional";
    }
}
