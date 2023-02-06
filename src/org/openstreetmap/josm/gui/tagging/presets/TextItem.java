// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import java.awt.Dimension;
import java.util.Map;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

import org.openstreetmap.josm.tools.ImageProvider;

/**
 * A tagging preset item displaying a localizable text.
 * @since 6190
 */
abstract class TextItem extends Item {

    /** The text to display */
    final String text;
    /** The context used for translating {@link #text} */
    final String textContext;
    /** The localized version of {@link #text} */
    final String localeText;
    /** The location of icon file to display */
    final String icon;
    /** The size of displayed icon. If not set, default is 16px */
    final Dimension iconSize;

    /**
     * Constructor.
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on illegal attributes
     */
    TextItem(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
        String v = attributes.get("text");
        text = v != null ? v : getDefaultText();
        textContext = attributes.get("text_context");
        localeText = TaggingPresetUtils.buildLocaleString(attributes.get("locale_text"), text, textContext);
        icon = attributes.get("icon");
        iconSize = parseIconSize(attributes, ImageProvider.ImageSizes.SMALLICON);
    }

    /**
     * Returns the text
     * @return teh text
     */
    String getText() {
        return text;
    }

    String getDefaultText() {
        return null;
    }

    String fieldsToString() {
        return (text != null ? "text=" + text + ", " : "")
                + (textContext != null ? "text_context=" + textContext + ", " : "")
                + (localeText != null ? "locale_text=" + localeText : "");
    }

    /**
     * Defines the label icon from this entry's icon
     * @param label the component
     * @since 17605
     */
    void addIcon(JLabel label) {
        label.setIcon(getIcon());
        label.setHorizontalAlignment(SwingConstants.LEADING);
    }

    /**
     * Returns the entry icon, if any.
     * @return the entry icon, or {@code null}
     * @since 17605
     */
    ImageIcon getIcon() {
        return icon == null ? null : TaggingPresetUtils.loadImageIcon(
            icon, TaggingPresetReader.getZipIcons(), iconSize);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + fieldsToString() + ']';
    }
}
