// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.font.TextAttribute;
import java.util.Collections;

import javax.swing.JLabel;

import org.openstreetmap.josm.gui.tagging.DataHandlers.DataSetHandler;
import org.openstreetmap.josm.gui.tagging.DataHandlers.TaggedHandler;
import org.openstreetmap.josm.gui.tagging.ac.AutoCompletionManager;

/**
 * A hyperlink {@link JLabel} that opens a preset dialog.
 * <p>
 * On mouse hover it displays an appropriate mouse cursor and a dotted underline.
 * <p>
 * Works in 3 environments:
 * <ul>
 * <li>in the {@link PresetListPanel} in the {@link PropertiesDialog}
 * <li>in the {@link PresetListPanel} in a {@link GenericRelationEditor}
 * <li>inside a {@link Link} element in a {@link TaggingPreset} dialog
 * </ul>
 * When called from a preset dialog, it will carry the yet unsaved changes into
 * the next dialog.
 */
public class TaggingPresetLabel extends JLabel {
    private final TaggingPreset preset;
    private final TaggingPreset.Instance presetInstance;
    private final TaggedHandler handler;
    private final AutoCompletionManager manager;

    /**
     * Constructor
     *
     * @param handler the data handler to pass to the new dialog
     * @param preset the tagging preset to open in the new dialog when clicked
     * @param presetInstance the preset instance of the old dialog
     */
    public TaggingPresetLabel(TaggedHandler handler, TaggingPreset preset, TaggingPreset.Instance presetInstance) {
        super(preset.getName() + " …", preset.getSmallIcon(), LEADING);
        this.preset = preset;
        this.presetInstance = presetInstance;
        this.handler = handler;
        this.manager = presetInstance.getAutoCompletionManager();
        addMouseListener(new TaggingPresetMouseListener(this));
    }

    /**
     * Constructor
     *
     * @param handler the data handler to pass to the new dialog
     * @param preset the tagging preset to open in the new dialog when clicked
     * @param manager the autocompletion manager to pass to the new dialog
     */
    public TaggingPresetLabel(TaggedHandler handler, TaggingPreset preset, AutoCompletionManager manager) {
        super(preset.getName() + " …", preset.getSmallIcon(), LEADING);
        this.preset = preset;
        this.presetInstance = null;
        this.handler = handler;
        this.manager = manager;
        addMouseListener(new TaggingPresetMouseListener(this));
    }

    /**
     * Underlines the label when hovered
     */
    class TaggingPresetMouseListener implements MouseListener {
        protected final JLabel label;
        protected final Font hover;
        protected final Font normal;

        /**
         * Constructs a new {@code PresetLabelMouseListener}.
         * @param lbl Label to highlight
         */
        TaggingPresetMouseListener(JLabel lbl) {
            label = lbl;
            lbl.setCursor(new Cursor(Cursor.HAND_CURSOR));
            normal = label.getFont();
            hover = normal.deriveFont(Collections.singletonMap(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_LOW_DOTTED));
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            preset.showDialog(presetInstance != null ?
                new TaggingPreset.PresetLinkHandler((DataSetHandler) handler, presetInstance) : handler, manager);
        }

        @Override
        public void mouseEntered(MouseEvent e) {
            label.setFont(hover);
        }

        @Override
        public void mouseExited(MouseEvent e) {
            label.setFont(normal);
        }

        @Override
        public void mousePressed(MouseEvent e) {
            // Do nothing
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            // Do nothing
        }
    }
}
