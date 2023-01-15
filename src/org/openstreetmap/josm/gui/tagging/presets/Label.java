// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JLabel;

import org.openstreetmap.josm.tools.GBC;

/**
 * Label type.
 */
final class Label extends TextItem {

    /**
     * Private constructor. Use {@link #fromXML} instead.
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on illegal attributes
     */
    private Label(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
    }

    /**
     * Create a {@code Label} from an XML element's attributes.
     * @param attributes the XML attributes
     * @return the {@code Label}
     * @throws IllegalArgumentException on illegal attributes
     */
    static Label fromXML(Map<String, String> attributes) throws IllegalArgumentException {
        return new Label(attributes);
    }

    @Override
    boolean addToPanel(JComponent p, Composite.Instance parentInstance) {
        JLabel label = new JLabel(localeText);
        addIcon(label);
        label.applyComponentOrientation(TaggingPresetDialog.getDefaultComponentOrientation());
        p.add(label, GBC.eol().fill(GBC.HORIZONTAL));
        return true;
    }
}
