// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import java.awt.GridBagLayout;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/**
 * A tab in a tabbed pane.
 */
class Tab extends Composite {
    /** The text to display */
    final String text;
    /** The context used for translating {@link #text} */
    final String textContext;
    /** The localized version of {@link #text} */
    final String localeText;
    /** The tooltip to show */
    final String toolTip;
    /** The location of icon file to display */
    final String icon;
    /** The size of displayed icon. If not set, default is 16px */
    final int iconSize;

    /**
     * Constructor.
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on illegal attributes
     */
    Tab(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
        text = attributes.get("text");
        textContext = attributes.get("text_context");
        localeText = TaggingPresetUtils.buildLocaleString(attributes.get("locale_text"), text, textContext);
        toolTip = attributes.get("tooltip");
        icon = attributes.get("icon");
        iconSize = Integer.parseInt(attributes.getOrDefault("icon_size", "16"));
    }

    /**
     * Create this class from an XML element's attributes.
     * @param attributes the XML attributes
     * @return the new instance
     * @throws IllegalArgumentException on illegal attributes
     */
    static Tab fromXML(Map<String, String> attributes) throws IllegalArgumentException {
        return new Tab(attributes);
    }

    @Override
    boolean addToPanel(JComponent p, Composite.Instance parentInstance) {
        assert p instanceof JTabbedPane;
        if (items.isEmpty())
            // do not add an empty tab
            return false;
        JPanel panel = new JPanel(new GridBagLayout());
        // Make the panel transparent to match the background of the selected tab.
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        boolean hasElements = super.addToPanel(panel, parentInstance);
        panel.applyComponentOrientation(TaggingPresetDialog.getDefaultComponentOrientation());
        if (icon != null) {
            ImageIcon ico = TaggingPresetUtils.loadImageIcon(icon, TaggingPresetReader.getZipIcons(), iconSize);
            ((JTabbedPane) p).addTab(text, ico, panel, toolTip);
        } else {
            ((JTabbedPane) p).addTab(text, null, panel, toolTip);
        }
        return hasElements;
    }

    @Override
    public String toString() {
        return "Tab";
    }
}
