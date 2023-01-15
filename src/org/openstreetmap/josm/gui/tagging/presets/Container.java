// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;

import org.openstreetmap.josm.tools.GBC;

/**
 * A sequence of {@link Item}s in a panel. The panel may have a title and border.
 */
class Container extends Composite {
    /** The text to display */
    final String text;
    /** The context used for translating {@link #text} */
    final String textContext;
    /** The localized version of {@link #text} */
    final String localeText;
    /** The number of columns (integer > 0). Columns > 1 uses a GridLayout else a GridBagLayout. */
    final int columns;

    /**
     * Constructor.
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on illegal attributes
     */
    Container(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
        String t = attributes.get("text");
        text = t != null ? t : getDefaultText();
        textContext = attributes.get("text_context");
        localeText = TaggingPresetUtils.buildLocaleString(attributes.get("locale_text"), text, textContext);
        columns = Integer.parseInt(attributes.getOrDefault("columns", "1"));
    }

    /**
     * Create this class from an XML element's attributes.
     * @param attributes the XML attributes
     * @return the new instance
     * @throws IllegalArgumentException on illegal attributes
     */
    static Container fromXML(Map<String, String> attributes) throws IllegalArgumentException {
        return new Container(attributes);
    }

    String getDefaultText() {
        return null;
    }

    static void addBorder(JPanel panel, String title) {
        Border margin = BorderFactory.createEmptyBorder(10, 0, 0, 0);
        if (title != null) {
            Border border = BorderFactory.createTitledBorder(title);
            Border padding = BorderFactory.createEmptyBorder(10, 10, 10, 10);
            margin = new CompoundBorder(margin, new CompoundBorder(border, padding));
        }
        panel.setBorder(margin);
    }

    /**
     * Get a panel with a suitable border and a configured layout manager.
     * @return the panel
     */
    JPanel getPanel() {
        // Note: rows = 0 automagically fixes #20792. See {@link GridLayout#layoutContainer}
        JPanel panel = new JPanel(columns > 1 ? new GridLayout(0, columns) : new GridBagLayout());
        addBorder(panel, localeText);
        return panel;
    }

    @Override
    boolean addToPanel(JComponent p, Composite.Instance parentInstance) {
        if (items.isEmpty())
            // do not add an empty panel
            return false;
        JPanel panel = getPanel();
        boolean hasElements = super.addToPanel(panel, parentInstance);
        panel.applyComponentOrientation(TaggingPresetDialog.getDefaultComponentOrientation());
        p.add(panel, GBC.eol().fill(GridBagConstraints.HORIZONTAL));
        return hasElements;
    }

    @Override
    public String toString() {
        return "Container";
    }
}
