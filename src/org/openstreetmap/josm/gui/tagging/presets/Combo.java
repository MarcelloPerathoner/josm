// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Comparator;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JLabel;

import org.openstreetmap.josm.data.tagging.ac.AutoCompletionItem;
import org.openstreetmap.josm.data.tagging.ac.AutoCompletionPriority;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.mappaint.mapcss.CSSColors;
import org.openstreetmap.josm.gui.tagging.ac.AutoCompComboBoxEditor;
import org.openstreetmap.josm.gui.tagging.ac.AutoCompComboBoxModel;
import org.openstreetmap.josm.gui.tagging.ac.AutoCompTextField;
import org.openstreetmap.josm.gui.tagging.ac.AutoCompletionManager;
import org.openstreetmap.josm.gui.widgets.JosmComboBox;
import org.openstreetmap.josm.gui.widgets.OrientationAction;
import org.openstreetmap.josm.tools.ColorHelper;
import org.openstreetmap.josm.tools.GBC;

/**
 * Combobox type.
 */
final class Combo extends ComboMultiSelect {

    /**
     * Whether the combo box is editable, which means that the user can add other values as text.
     * Default is {@code true}. If {@code false} it is readonly, which means that the user can only select an item in the list.
     */
    private final boolean editable;

    /**
     * Private constructor. Use {@link #fromXML} instead.
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on illegal attributes
     */
    private Combo(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
        editable = Boolean.parseBoolean(attributes.getOrDefault("editable", "true"));
    }

    /**
     * Create this class from an XML element's attributes.
     * @param attributes the XML attributes
     * @return the new instance
     * @throws IllegalArgumentException on invalid attributes
     */
    static Combo fromXML(Map<String, String> attributes) throws IllegalArgumentException {
        return new Combo(attributes);
    }

    static class ComponentListener extends ComponentAdapter {
        JosmComboBox<PresetListEntry.Instance> combobox;

        ComponentListener(JosmComboBox<PresetListEntry.Instance> combobox) {
            this.combobox = combobox;
        }

        @Override
        public void componentResized(ComponentEvent e) {
            // Make multi-line JLabels the correct size
            // Only needed if there is any short_description
            JComponent component = (JComponent) e.getSource();
            int width = component.getWidth();
            if (width == 0)
                width = 200;
            Insets insets = component.getInsets();
            width -= insets.left + insets.right + 10;
            PresetListEntry.CellRenderer renderer = (PresetListEntry.CellRenderer) combobox.getRenderer();
            renderer.setWidth(width);
            combobox.setRenderer(null); // needed to make prop change fire
            combobox.setRenderer(renderer);
        }
    }

    @Override
    String getDefaultDelimiter() {
        return ",";
    }

    private void addEntry(AutoCompComboBoxModel<PresetListEntry.Instance> model, PresetListEntry.Instance instance) {
        if (!seenValues.containsKey(instance.getValue())) {
            model.addElement(instance);
            seenValues.put(instance.getValue(), instance);
        }
    }

    @Override
    boolean addToPanel(JComponent p, Composite.Instance parentInstance) {
        TaggingPreset.Instance presetInstance = parentInstance.getPresetInstance();
        Usage usage = Usage.determineTextUsage(presetInstance.getSelected(), key);
        seenValues.clear();

        // init the model
        AutoCompComboBoxModel<PresetListEntry.Instance> dropDownModel =
            new AutoCompComboBoxModel<>(Comparator.<PresetListEntry.Instance>naturalOrder());
        JosmComboBox<PresetListEntry.Instance> combobox = new JosmComboBox<>(dropDownModel);
        Instance instance = new Instance(this, parentInstance, combobox, usage);

        if (!usage.hasUniqueValue() && !usage.unused()) {
            addEntry(dropDownModel, PresetListEntry.ENTRY_DIFFERENT.newInstance(instance));
        }
        presetListEntries.forEach(e -> addEntry(dropDownModel, e.newInstance(instance)));
        if (defaultValue != null) {
            addEntry(dropDownModel, new PresetListEntry(defaultValue).newInstance(instance));
        }
        addEntry(dropDownModel, PresetListEntry.ENTRY_EMPTY.newInstance(instance));

        usage.map.forEach((value, count) ->
            addEntry(dropDownModel, new PresetListEntry(value).newInstance(instance))
        );

        AutoCompComboBoxEditor<AutoCompletionItem> editor = new AutoCompComboBoxEditor<>();
        combobox.setEditor(editor);

        // The default behaviour of JComboBox is to size the editor according to the tallest item in
        // the dropdown list.  We don't want that to happen because we want to show taller items in
        // the list than in the editor.  We can't use
        // {@code combobox.setPrototypeDisplayValue(PresetListEntry.ENTRY_EMPTY);} because that would
        // set a fixed cell height in JList.
        combobox.setPreferredHeight(combobox.getPreferredSize().height);

        // a custom cell renderer capable of displaying a short description text along with the
        // value
        combobox.setRenderer(new PresetListEntry.CellRenderer(combobox, combobox.getRenderer(), 200));
        combobox.setEditable(editable);

        AutoCompComboBoxModel<AutoCompletionItem> autoCompModel = new AutoCompComboBoxModel<>(
            AutoCompletionManager.ALPHABETIC_COMPARATOR);
        // TaggingPresetUtils.getAllForKeys(Arrays.asList(key)).forEach(autoCompModel::addElement);
        getDisplayValues().forEach(s -> autoCompModel.addElement(
            new AutoCompletionItem(s, AutoCompletionPriority.IS_IN_STANDARD)));

        AutoCompTextField<AutoCompletionItem> tf = editor.getEditorComponent();
        tf.setModel(autoCompModel);

        if (Item.DISPLAY_KEYS_AS_HINT.get()) {
            combobox.setHint(key);
        }
        if (length > 0) {
            tf.setMaxTextLength(length);
        }

        parentInstance.putInstance(this, instance);

        JLabel label = addLabel(p);

        if (key != null && ("colour".equals(key) || key.startsWith("colour:") || key.endsWith(":colour"))) {
            p.add(combobox, GBC.std().fill(GridBagConstraints.HORIZONTAL));
            JButton button = new JButton(new ChooseColorAction(instance));
            button.setOpaque(true);
            button.setBorderPainted(false);
            Dimension size = combobox.getPreferredSize();
            button.setPreferredSize(new Dimension(size.height, size.height));
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            p.add(button, GBC.eol());
            ActionListener updateColor = ignore -> button.setBackground(instance.getColor());
            updateColor.actionPerformed(null);
            combobox.addActionListener(updateColor);
        } else {
            p.add(combobox, GBC.eol().fill(GridBagConstraints.HORIZONTAL));
        }

        instance.setValue(instance.getInitialValue());

        combobox.addActionListener(l -> presetInstance.fireChangeEvent(instance));
        combobox.addComponentListener(new ComponentListener(combobox));

        label.setLabelFor(combobox);
        combobox.setToolTipText(getKeyTooltipText());
        combobox.applyComponentOrientation(OrientationAction.getValueOrientation(key));

        seenValues.clear();
        return true;
    }

    @Override
    boolean isInteractive() {
        return true;
    }

    class Instance extends ComboMultiSelect.Instance {
        JosmComboBox<PresetListEntry.Instance> combobox;

        Instance(Item item, Composite.Instance parent, JosmComboBox<PresetListEntry.Instance> combobox, Usage usage) {
            super(item, parent, combobox, usage);
            this.combobox = combobox;
        }

        /**
         * Returns the value selected in the combobox or a synthetic value if a multiselect.
         *
         * @return the value
         */
        @Override
        PresetListEntry.Instance getSelectedItem() {
            Object sel = combobox.getSelectedItem();
            if (sel instanceof PresetListEntry.Instance)
                // selected from the dropdown
                return (PresetListEntry.Instance) sel;
            if (sel instanceof String) {
                // free edit.  If the free edit corresponds to a known entry, use that entry.  This is
                // to avoid that we write a display_value to the tag's value, eg. if the user did an
                // undo.
                PresetListEntry.Instance selItem = find((String) sel);
                if (selItem != null)
                    return selItem;
                return new PresetListEntry((String) sel).newInstance(this);
            }
            return PresetListEntry.ENTRY_EMPTY.newInstance(this);
        }

        @Override
        void setValue(String newValue) {
            PresetListEntry.Instance selItem = find(newValue);
            if (selItem != null) {
                combobox.setSelectedItem(selItem);
            } else {
                combobox.setText(newValue);
            }
        }

        /**
         * Finds the PresetListEntry that matches value.
         * <p>
         * Looks in the model of the combobox for an element whose {@code value} matches {@code value}.
         *
         * @param value The value to match.
         * @return The entry or null
         */
        PresetListEntry.Instance find(String value) {
            return combobox.getModel().asCollection().stream().filter(o -> o.getValue().equals(value)).findAny().orElse(null);
        }

        void setColor(Color color) {
            if (color != null) {
                combobox.setSelectedItem(ColorHelper.color2html(color));
            }
        }

        Color getColor() {
            String colorString = getSelectedItem().getValue();
            return colorString.startsWith("#")
                    ? ColorHelper.html2color(colorString)
                    : CSSColors.get(colorString);
        }
    }

    static class ChooseColorAction extends AbstractAction {
        private transient Instance instance;

        ChooseColorAction(Instance instance) {
            this.instance = instance;
            putValue(SHORT_DESCRIPTION, tr("Choose a color"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            Color color = instance.getColor();
            color = JColorChooser.showDialog(MainApplication.getMainPanel(), tr("Choose a color"), color);
            instance.setColor(color);
        }
    }
}
