// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.Map;

import javax.swing.JLabel;
import javax.swing.JPanel;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.tagging.DataHandlers.DataSetHandler;
import org.openstreetmap.josm.gui.tagging.DataHandlers.TaggedHandler;
import org.openstreetmap.josm.tools.GBC;

/**
 * Adds a link to another preset.
 * @since 8863
 */
final class PresetLink extends TextItem {

    /** The exact name of the preset to link to. Required. */
    private final String presetName;

    /**
     * Private constructor. Use {@link #fromXML} instead.
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on illegal attributes
     */
    private PresetLink(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
        presetName = attributes.get("preset_name");
        if (presetName == null)
            throw new IllegalArgumentException("attribute preset_name is required");
    }

    /**
     * Create a {@code PresetLink} from an XML element's attributes.
     * @param attributes the XML attributes
     * @return the {@code PresetLink}
     * @throws IllegalArgumentException on illegal attributes
     */
    static PresetLink fromXML(Map<String, String> attributes) throws IllegalArgumentException {
        return new PresetLink(attributes);
    }

    @Override
    protected String getDefaultText() {
        return tr("Edit also …");
    }

    /**
     * Creates a label to be inserted above this link
     * @return a label
     */
    JLabel createLabel() {
        return new JLabel(localeText);
    }

    @Override
    boolean addToPanel(JPanel p, Composite.Instance parentInstance) {
        TaggingPreset.Instance presetInstance = parentInstance.getPresetInstance();
        TaggingPresetDialog dialog = presetInstance.getDialog();
        TaggedHandler handler = presetInstance.getHandler();
        if (handler instanceof DataSetHandler) {
            for (TaggingPreset preset : MainApplication.getTaggingPresets().getAllPresets()) {
                if (presetName.equals(preset.getBaseName())) {
                    JLabel lbl = new TaggingPresetLabel(handler, preset, presetInstance);
                    lbl.applyComponentOrientation(dialog.getDefaultComponentOrientation());
                    p.add(lbl, GBC.eol());
                    break;
                }
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "PresetLink [preset_name=" + presetName + ']';
    }
}
