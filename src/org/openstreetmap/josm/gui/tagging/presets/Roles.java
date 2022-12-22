// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.Map;

import javax.swing.JLabel;
import javax.swing.JPanel;

import org.openstreetmap.josm.tools.GBC;

/**
 * The <code>roles</code> element in tagging presets definition.
 * <p>
 * A list of {@link Role} elements. Describes the roles that are expected for
 * the members of a relation.
 * <p>
 * Used for data validation, auto completion, among others.
 */
final class Roles extends Container {
    /** the right margin */
    private static final int right = 10;

    /**
     * Private constructor. Use {@link #fromXML} instead.
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on illegal attributes
     */
    private Roles(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
    }

    /**
     * Create this class from an XML element's attributes.
     * @param attributes the XML attributes
     * @return the new instance
     * @throws IllegalArgumentException on invalid attributes
     */
    static Roles fromXML(Map<String, String> attributes) throws IllegalArgumentException {
        return new Roles(attributes);
    }

    @Override
    String getDefaultText() {
        return tr("Roles:");
    }

    @Override
    JPanel getPanel() {
        GBC std = GBC.std().insets(0, 0, right, 10);
        GBC eol = GBC.eol().insets(0, 0, right, 10).fill(GridBagConstraints.HORIZONTAL);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setAlignmentX(0);
        addBorder(panel, localeText);
        panel.add(new JLabel(tr("Available roles")), std);
        panel.add(new JLabel(tr("role")), std);
        panel.add(new JLabel(tr("count")), std);
        panel.add(new JLabel(tr("elements")), eol);
        return panel;
    }

    @Override
    public String toString() {
        return "Roles";
    }
}
