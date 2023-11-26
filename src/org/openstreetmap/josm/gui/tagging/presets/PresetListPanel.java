// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.Collection;
import java.util.Map;

import javax.swing.JLabel;
import javax.swing.JPanel;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.tagging.DataHandlers;
import org.openstreetmap.josm.gui.tagging.ac.AutoCompletionManager;
import org.openstreetmap.josm.tools.GBC;

/**
 * The list of matching presets shown in the preset dialogs, properties dialog and the
 * relation editor.
 */
public class PresetListPanel extends JPanel {

    /**
     * Constructs a new {@code PresetListPanel}.
     */
    public PresetListPanel() {
        super(new GridBagLayout());
    }

    /**
     * Updates the preset list
     * <p>
     * Displays a list of all presets that match both {@code types} and {@code tags}. A
     * click on the item opens a preset dialog connected to the {@link DataHandlers.TaggedHandler}
     * and the {@link AutoCompletionManager}.
     *
     * @param types collection of tagging presets types
     * @param tags collection of tags
     * @param handler the data handler for dialogs opened from here
     * @param autoCompletionManager the autocompletion manager or null
     */
    public void updatePresets(final Collection<TaggingPresetType> types, final Map<String, String> tags,
            final DataHandlers.DataSetHandler handler, final AutoCompletionManager autoCompletionManager) {

        removeAll();
        if (types.isEmpty()) {
            setVisible(false);
            return;
        }

        for (final TaggingPreset preset : MainApplication.getTaggingPresets().getMatchingPresets(types, tags, true)) {
            final JLabel lbl = new TaggingPresetLabel(preset, handler, autoCompletionManager);
            add(lbl, GBC.eol().fill(GridBagConstraints.HORIZONTAL));
        }

        setVisible(getComponentCount() > 0);
    }
}
