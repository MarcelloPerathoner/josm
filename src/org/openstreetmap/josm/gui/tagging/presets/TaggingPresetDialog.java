// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.openstreetmap.josm.tools.I18n.tr;
import static org.openstreetmap.josm.tools.I18n.trn;
import static org.openstreetmap.josm.tools.I18n.marktr;

import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.CheckForNull;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;

import org.openstreetmap.josm.data.preferences.NamedColorProperty;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.help.HelpBrowser;
import org.openstreetmap.josm.gui.help.HelpUtil;
import org.openstreetmap.josm.gui.preferences.ToolbarPreferences;
import org.openstreetmap.josm.gui.tagging.DataHandlers.DataSetHandler;
import org.openstreetmap.josm.gui.tagging.DataHandlers.TaggedHandler;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.gui.util.WindowGeometry;
import org.openstreetmap.josm.gui.widgets.OrientationAction;
import org.openstreetmap.josm.gui.widgets.PopupMenuLauncher;
import org.openstreetmap.josm.io.NetworkManager;
import org.openstreetmap.josm.io.OnlineResource;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.InputMapUtils;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.StreamUtils;

/**
 * A tagging preset dialog.
 * <p>
 * This dialog allows the user to edit the tags of map elements in an easy intuitive
 * way.  It is populated with the controls defined in a {@link TaggingPreset}.
 */
public class TaggingPresetDialog extends JDialog implements PropertyChangeListener {
    /** The "Apply" button */
    public static final int DIALOG_ANSWER_APPLY = 1;
    /** The "New Relation" button */
    public static final int DIALOG_ANSWER_NEW_RELATION = 2;
    /** The "Cancel" button */
    public static final int DIALOG_ANSWER_CANCEL = 3;
    /** The "Help" button. This value will actually never end in answer.  */
    public static final int DIALOG_ANSWER_HELP = 4;

    /** Same size as on JButton set from action. */
    private static final ImageProvider.ImageSizes ICONSIZE = ImageProvider.ImageSizes.LARGEICON;

    /** The user's answer */
    public int answer = DIALOG_ANSWER_CANCEL;

    /** Show the "New Relation" button */
    boolean showNewRelationButton;
    /** Disables the apply button */
    boolean disableApplyButton;

    /** The instance we are attached to. */
    final transient TaggingPreset.Instance presetInstance;

    private final Map<Integer, JButton> buttons = new HashMap<>();
    final FontMetrics fontMetrics;
    @CheckForNull private transient TaggingPresetValidator validator;
    @CheckForNull TaggingPresetValidator.EnableValidatorAction enableValidatorAction;

    /**
     * Constructs a new {@code PresetDialog}.
     * @param preset the tagging preset
     */
    TaggingPresetDialog(TaggingPreset.Instance presetInstance) {
        super(MainApplication.getMainFrame(), presetInstance.getPreset().getBaseName(), true);

        this.presetInstance = presetInstance;
        this.showNewRelationButton = false;
        this.disableApplyButton = false;
        presetInstance.dialog = this;
        fontMetrics = getFontMetrics(getFont());
        TaggingPreset preset = presetInstance.getPreset();

        setTitle(presetInstance);

        // add a validator
        // the validator crashes if a primitive is not in a dataset
        if (presetInstance.getHandler() instanceof DataSetHandler) {
            validator = buildValidator();
            validator.setEnabled(TaggingPresets.USE_VALIDATOR.get());
            enableValidatorAction = new TaggingPresetValidator.EnableValidatorAction(this, false);
        }

        Dimension border = new Dimension(fontMetrics.charWidth('m'), fontMetrics.getAscent() / 2);
        GBC gbc = GBC.eol().fill(GridBagConstraints.HORIZONTAL).insets(border.width, border.height, border.width, 0);
        JPanel contentPane = new JPanel(new GridBagLayout());
        contentPane.add(buildHeadPanel(preset), gbc);
        contentPane.add(preset.buildPresetPanel(presetInstance), gbc);
        contentPane.add(buildButtonsPanel("/Menu/Presets"),
            gbc.insets(border.width, border.height, border.width, border.height));

        JScrollPane scrollPane = new JScrollPane(contentPane);
        GuiHelper.setDefaultIncrement(scrollPane);
        scrollPane.setBorder(null);
        if (MainApplication.getMainFrame() != null) {
            scrollPane.applyComponentOrientation(MainApplication.getMainFrame().getComponentOrientation());
        }
        setContentPane(scrollPane);

        // set minimum dimensions
        // assume the main frame caption is the same height as the dialog caption
        Insets insets = MainApplication.getMainFrame().getInsets();
        Dimension d = getPreferredSize();
        int minWidth = presetInstance.getPreset().minWidth;
        if (minWidth > 0) {
            int emWidth = fontMetrics.charWidth('m');
            d.width = Math.max(d.width, emWidth * minWidth) + insets.left + insets.right;
        } else {
            d.width = Math.max(d.width, 400) + insets.left + insets.right;
        }
        d.height = Math.max(d.height, 300) + insets.top + insets.bottom;
        Logging.info("setMinimumSize: {0}x{1}", d.width, d.height);
        setMinimumSize(d);

        OrientationAction orientationAction = new OrientationAction(this);
        orientationAction.addPropertyChangeListener(this);
        orientationAction.putValue(Action.ACCELERATOR_KEY, null);
        // confusing UX because if a textfield is focused the keystroke will be consumed by the textfield
        // contentPane.getInputMap().put(OrientationAction.getShortcutKey(), orientationAction);

        // add popup
        JPopupMenu popupMenu = new JPopupMenu();
        contentPane.addMouseListener(new PopupMenuLauncher(popupMenu));
        popupMenu.add(orientationAction);
        popupMenu.add(new TaggingPresetValidator.EnableValidatorAction(this, true));

        InputMapUtils.addEscapeAction(getRootPane(), new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                // catch ESC
                closeWithAnswer(DIALOG_ANSWER_CANCEL);
            }
        });

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                // catch the X-button in the dialog caption
                closeWithAnswer(DIALOG_ANSWER_CANCEL);
            }
        });
    }

    private void closeWithAnswer(int answer) {
        this.answer = answer;
        setVisible(false);
    }

    @Override
    public void setVisible(boolean visible) {
        String positionPref = getClass().getName() + "." + presetInstance.getPreset().getName() + ".geometry";
        if (visible) {
            if (!disableApplyButton)
                getRootPane().setDefaultButton(buttons.get(DIALOG_ANSWER_APPLY));
            // trigger initial updates once
            presetInstance.setFireDataChanged(true);
            presetInstance.fireChangeEvent(presetInstance);
            // resize dialog
            WindowGeometry defaultWindowGeometry = WindowGeometry.centerInWindow(
                MainApplication.getMainFrame(), new Dimension(400, 300));
            WindowGeometry g = new WindowGeometry(positionPref, defaultWindowGeometry);
            g.applySafe(this);
        } else if (isShowing()) { // should fix #6438, #6981, #8295
            presetInstance.recalculateAll();
            new WindowGeometry(this).remember(positionPref);
        }
        super.setVisible(visible);
    }

    /**
     * Sets the dialog title according to the selected objects
     * @param instance the dialog instance
     */
    public void setTitle(TaggingPreset.Instance instance) {
        TaggingPreset preset = instance.getPreset();
        TaggedHandler handler = instance.getHandler();
        int size;
        if (handler == null) {
            setTitle(preset.getName());
        } else if ((size = handler.get().size()) > 0) {
            setTitle(trn("Change {0} object", "Change {0} objects", size, size));
            disableApplyButton = handler.isReadOnly();
        } else {
            setTitle(tr("New Relation"));
            showNewRelationButton = true;
            disableApplyButton = true;
        }
        if (preset.showPresetName)
            setIconImage(preset.getIcon(Action.SMALL_ICON).getImage());
    }

    /**
     * Build the panel at the head of the preset dialog.
     * @return the panel
     */
    JPanel buildHeadPanel(TaggingPreset preset) {
        final JPanel headPanel = new JPanel(new GridBagLayout());
        JLabel label;
        int x = 0;
        GBC gbc = GBC.std().insets(0, 0, 5, 0);

        // the preset icon
        if (preset.showPresetName) {
            headPanel.add(new JLabel(preset.getIcon(Action.LARGE_ICON_KEY)),
                gbc.grid(x++, 0));
        }
        // the supported types icons
        for (TaggingPresetType t : preset.getTypes()) {
            label = new JLabel(ImageProvider.get(t.getIconName(), ICONSIZE));
            label.setToolTipText(tr("Elements of type {0} are supported.", tr(t.getName())));
            headPanel.add(label, gbc.grid(x++, 0));
        }
        // the "also sets" icon
        final List<Key> keys = preset.getAllItems(Key.class);
        if (!keys.isEmpty()) {
            label = new JLabel(ImageProvider.get("pastetags", ICONSIZE));
            label.setToolTipText("<html>" + tr("This preset also sets: {0}",
                keys.stream().map(k -> {
                    return k.getKey() + "=" + k.getValue(); }).collect(StreamUtils.toHtmlList())));
            headPanel.add(label, gbc.grid(x++, 0));
        }
        // add horizontal glue
        headPanel.add(new JLabel(), gbc.grid(x++, 0).fill(GridBagConstraints.HORIZONTAL));

        // the "enable validator" button
        if (enableValidatorAction != null) {
            JToggleButton validatorButton = new JToggleButton(enableValidatorAction);
            validatorButton.setFocusable(false);
            headPanel.add(validatorButton, GBC.std(x++, 0).anchor(GridBagConstraints.LINE_END));
        }

        // the "pin to toolbar" button
        JToggleButton tb = new JToggleButton(new PinToolbarAction());
        tb.setFocusable(false);
        headPanel.add(tb, GBC.std(x, 0).anchor(GridBagConstraints.LINE_END));

        // the full name of the preset
        if (preset.showPresetName) {
            headPanel.add(new JLabel(preset.getName()),
                GBC.std(0, 1).insets(0, 5, 0, 0).fill(GridBagConstraints.HORIZONTAL).span(GridBagConstraints.REMAINDER));
        }
        return headPanel;
    }

    JPanel buildButtonsPanel(String helpTopic) {
        JPanel panel = new JPanel(new GridBagLayout());
        // CHECKSTYLE.OFF: SingleSpaceSeparator
        JButton ok     = new JButton(new ButtonAction(tr("Apply Preset"), "ok",            DIALOG_ANSWER_APPLY,        getTitle()));
        JButton newRel = new JButton(new ButtonAction(tr("New Relation"), "data/relation", DIALOG_ANSWER_NEW_RELATION, null));
        JButton cancel = new JButton(new ButtonAction(tr("Cancel"),       "cancel",        DIALOG_ANSWER_CANCEL,       null));
        JButton help   = new JButton(new HelpAction(helpTopic));
        // CHECKSTYLE.ON: SingleSpaceSeparator
        ok.setEnabled(!disableApplyButton);
        GBC gbc = GBC.std().insets(2, 2, 2, 2);
        panel.add(ok, gbc);
        buttons.put(DIALOG_ANSWER_APPLY, ok);
        if (showNewRelationButton) {
            buttons.put(DIALOG_ANSWER_NEW_RELATION, newRel);
            panel.add(newRel, gbc);
        }
        panel.add(cancel, gbc);
        buttons.put(DIALOG_ANSWER_CANCEL, cancel);
        if (helpTopic != null) {
            buttons.put(DIALOG_ANSWER_HELP, help);
            panel.add(help, gbc);
            HelpUtil.setHelpContext(getRootPane(), helpTopic);
        }
        return panel;
    }

    TaggingPresetValidator buildValidator() {
        final Color errorBackground = new NamedColorProperty(
            marktr("Input validation: error"), Color.RED).get();
        return new TaggingPresetValidator(presetInstance, (DataSetHandler) presetInstance.getHandler(), (errors) -> {
            presetInstance.highlight(null, null, null);
            Map<String, String> key2msg = new HashMap<>();
            for (TestError e : errors) {
                String message = e.getDescription() == null ? e.getMessage() :
                        tr("<html>{0}<br>{1}", e.getMessage(), e.getDescription());
                e.getKeys().forEach(k -> key2msg.put(k, message));
            }
            key2msg.forEach((k, msg) -> presetInstance.highlight(k, errorBackground, msg));
        });
    }

    /**
     * Returns the tagging preset instance.
     * @return the tagging preset instance
     */
    public TaggingPreset.Instance getPresetInstance() {
        return presetInstance;
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
    public static ComponentOrientation getDefaultComponentOrientation() {
        return OrientationAction.getDefaultComponentOrientation();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("orientationAction".equals(evt.getPropertyName())) {
            applyComponentOrientation((ComponentOrientation) evt.getNewValue());
        }
        if (validator != null && "enableValidator".equals(evt.getPropertyName())) {
            boolean validatorEnabled = (Boolean) evt.getNewValue();
            validator.setEnabled(validatorEnabled);
            if (validatorEnabled) {
                presetInstance.fireChangeEvent(presetInstance);
            } else {
                presetInstance.highlight(null, null, null);
            }
            firePropertyChange("enableValidator", null, validatorEnabled);
        }
    }

    private class ButtonAction extends AbstractAction {
        int answer;

        ButtonAction(String text, String iconName, int answer, String tooltip) {
            this.answer = answer;
            putValue(NAME, text);
            putValue(SHORT_DESCRIPTION, tooltip);
            new ImageProvider(iconName).getResource().attachImageIcon(this, true);
        }

        @Override
        public void actionPerformed(ActionEvent evt) {
            closeWithAnswer(answer);
        }
    }

    /**
     * Action for the help button.
     */
    private class HelpAction extends AbstractAction {
        String helpTopic;

        /**
         * Constructs a new {@code HelpAction}.
         */
        HelpAction(String helpTopic) {
            this.helpTopic = helpTopic;
            putValue(SHORT_DESCRIPTION, tr("Show help information"));
            putValue(NAME, tr("Help"));
            new ImageProvider("help").getResource().attachImageIcon(this, true);
            setEnabled(!NetworkManager.isOffline(OnlineResource.JOSM_WEBSITE));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            HelpBrowser.setUrlForHelpTopic(helpTopic);
        }
    }

    /**
     * Action that "pins" the preset to the main toolbar
     */
    private class PinToolbarAction extends AbstractAction {
        private final int toolbarIndex;

        /**
         * Constructs a new {@code ToolbarButtonAction}.
         */
        PinToolbarAction() {
            super("");
            new ImageProvider("dialogs", "pin").getResource().attachImageIcon(this, true);
            putValue(SHORT_DESCRIPTION, tr("Add or remove toolbar button"));
            List<String> t = new ArrayList<>(ToolbarPreferences.getToolString());
            toolbarIndex = t.indexOf(presetInstance.getPreset().getToolbarString());
            putValue(SELECTED_KEY, toolbarIndex >= 0);
        }

        @Override
        public void actionPerformed(ActionEvent ae) {
            MainApplication.getToolbar().addCustomButton(presetInstance.getPreset().getToolbarString(), toolbarIndex, true);
        }
    }

}
