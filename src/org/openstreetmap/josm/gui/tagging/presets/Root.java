// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JMenu;

/**
 * The XML root element.  Corresponds to {@code <presets>}.
 * <p>
 * Note: May contain another root if {@code <xi:include>} is used.
 */
final class Root extends TaggingPresetMenu {
    /** The url of the XML resource. */
    String url;

    final String author;
    final String version;
    final String description;
    final String shortDescription;
    final String link;
    final String iconName;
    final String baseLanguage;

    /**
     * Constructor.
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on illegal attributes
     */
    Root(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
        author = attributes.get("author");
        version = attributes.get("version");
        description = attributes.get("description");
        shortDescription = attributes.get("shortdescription");
        link = attributes.get("link");
        iconName = attributes.get("icon");
        baseLanguage = attributes.get("baselanguage");
    }

    /**
     * Create a {@code Root}
     * @param attributes the XML attributes
     * @return the {@code Chunk}
     * @throws IllegalArgumentException on invalid attributes
     */
    static Root fromXML(Map<String, String> attributes) throws IllegalArgumentException {
        return new Root(attributes);
    }

    @Override
    boolean addToPanel(JComponent p, Composite.Instance parentInstance) {
        return false;
    }

    @Override
    void addToMenu(JMenu parentMenu) {
        // don't add ourselves, just the children
        items.forEach(item -> item.addToMenu(parentMenu));
    }

    @Override
    public String toString() {
        return "Root [" + url + "]";
    }
}
