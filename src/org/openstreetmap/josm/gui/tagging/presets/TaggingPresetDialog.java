// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;

import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.util.WindowGeometry;
import org.openstreetmap.josm.gui.widgets.OrientationAction;

/**
 * A tagging preset dialog.
 * <p>
 * This dialog allows the user to edit the tags of map elements in an easy intuitive
 * way.  It is populated with the controls defined in a {@link TaggingPreset}.
 */
public class TaggingPresetDialog extends ExtendedDialog {
    /** The "Apply" button */
    public static final int DIALOG_ANSWER_APPLY = 1;
    /** The "New Relation" button */
    public static final int DIALOG_ANSWER_NEW_RELATION = 2;
    /** The "Cancel" button */
    public static final int DIALOG_ANSWER_CANCEL = 3;

    /** Show the "New Relation" button */
    boolean showNewRelationButton;
    /** Disables the apply button */
    boolean disableApplyButton;

    TaggingPreset.Instance presetInstance;

    /**
     * Constructs a new {@code PresetDialog}.
     * @param preset the tagging preset
     */
    TaggingPresetDialog(TaggingPreset preset, TaggingPreset.Instance presetInstance) {
        super(MainApplication.getMainFrame(),
            preset.getBaseName(),
            new String[] {tr("Apply Preset"), tr("New relation"), tr("Cancel")},
            true);
        this.presetInstance = presetInstance;
        this.showNewRelationButton = false;
        this.disableApplyButton = false;

        contentInsets = new Insets(10, 10, 0, 10);
        setButtonIcons("ok", "data/relation", "cancel");
        configureContextsensitiveHelp("/Menu/Presets", true);
        setDefaultButton(DIALOG_ANSWER_APPLY);

        setRememberWindowGeometry(getClass().getName() + "." + preset + ".geometry",
            WindowGeometry.centerInWindow(MainApplication.getMainFrame(), new Dimension(300, 300)));
    }

    @Override
    public void setupDialog() {
        super.setupDialog();
        buttons.get(DIALOG_ANSWER_APPLY - 1).setEnabled(!disableApplyButton);
        buttons.get(DIALOG_ANSWER_APPLY - 1).setToolTipText(getTitle());
        buttons.get(DIALOG_ANSWER_NEW_RELATION - 1).setVisible(showNewRelationButton);
    }

    @Override
    public void setSize(int width, int height) {
        super.setSize(width, height);
        // This is a bit too small on the GTK L&F (and maybe others).  HiDPI issue?
        setMinimumSize(getPreferredSize());
    }

    /**
     * Returns the tagging preset instance.
     * @return the tagging preset instance
     */
    public TaggingPreset.Instance getPresetInstance() {
        return presetInstance;
    }

    /**
     * Fills in the dialog fields from a key -> value map
     *
     * @param tags a map of key -> value to fill in
     */
    public void fill(Map<String, String> tags) {
        tags.forEach((key, value) -> {
            Item.Instance itemInstance = presetInstance.getInstance(key);
            if (itemInstance != null)
                itemInstance.setValue(value);
        });
    }

    /**
     * Gets the location of a component relative to the content panel.
     * @param c the component
     * @return the position
     */
    Rectangle getLocation(Component c) {
        Point p2 = this.getContentPane().getLocationOnScreen();
        Point p1 = c.getLocationOnScreen();
        Point p = new Point(p1.x - p2.x, p1.y - p2.y);
        return new Rectangle(p, c.getSize());
    }

    /**
     * Outlines a component.
     * <p>
     * Draws a fat border around the component to outline.  Uses the layered pane.  This
     * solution has the advantage that the fat borders do not take up any layout space,
     * and the disadvantage that it doesn't resize.
     *
     * @param c the component to outline
     * @param color the color to use
     */
    void outline(Component c, Color color) {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createLineBorder(color, 5));
        Rectangle r = getLocation(c);
        panel.setOpaque(false);
        panel.setBounds(r);
        getLayeredPane().add(panel, JLayeredPane.PALETTE_LAYER);
    }

    /**
     * Returns the default component orientation by the user's locale
     *
     * @return the default component orientation
     */
    public ComponentOrientation getDefaultComponentOrientation() {
        return OrientationAction.getDefaultComponentOrientation();
    }
}
