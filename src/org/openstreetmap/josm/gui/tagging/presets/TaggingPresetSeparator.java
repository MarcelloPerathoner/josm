// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import java.util.Map;

import javax.swing.JMenu;
import javax.swing.JSeparator;

/**
 * Class used to represent a {@link JSeparator} inside tagging preset menu.
 * @since 895
 */
final class TaggingPresetSeparator extends Item {

    private TaggingPresetSeparator(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
    }

    /**
     * Create a {@code TaggingPresetSeparator} from an XML element's attributes.
     * @param attributes the XML attributes
     * @return the {@code TaggingPresetSeparator}
     * @throws IllegalArgumentException on attribute errors
     */
    public static TaggingPresetSeparator fromXML(Map<String, String> attributes) throws IllegalArgumentException {
        return new TaggingPresetSeparator(attributes);
    }

    @Override
    public void addToMenu(JMenu parentMenu) {
        parentMenu.add(new JSeparator());
    }

    @Override
    public String toString() {
        return "TaggingPresetSeparator";
    }
}
