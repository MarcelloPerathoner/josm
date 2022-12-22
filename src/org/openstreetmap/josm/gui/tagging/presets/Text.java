// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.awt.Color;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

import org.openstreetmap.josm.data.tagging.ac.AutoCompletionItem;
import org.openstreetmap.josm.gui.tagging.ac.AutoCompComboBoxEditor;
import org.openstreetmap.josm.gui.tagging.ac.AutoCompTextField;
import org.openstreetmap.josm.gui.util.DocumentAdapter;
import org.openstreetmap.josm.gui.widgets.JosmComboBox;
import org.openstreetmap.josm.gui.widgets.OrientationAction;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Utils;
import org.openstreetmap.josm.tools.template_engine.TemplateEntry;

/**
 * Text field type.
 */
final class Text extends InteractiveItem {
    /**
     * May contain a comma separated list of integer increments or decrements, e.g. "-2,-1,+1,+2".
     * A button will be shown next to the text field for each value, allowing the user to select auto-increment with the given stepping.
     * Auto-increment only happens if the user selects it. There is also a button to deselect auto-increment.
     * Default is no auto-increment. Mutually exclusive with {@link InteractiveItem#useLastAsDefault}.
     */
    private final String autoIncrement;
    /** A comma separated list of alternative keys to use for autocompletion. */
    private final String alternativeAutocompleteKeys;
    /** A value template */
    private final TemplateEntry valueTemplate;
    /** The default value for the item. If not specified, the current value of the key is chosen as
     * default (if applicable). Defaults to "". */
    private final String default_;

    /** Calculates the field's value once when pressed */
    private JButton calcButton;
    /** Automatically calculate the field's value */
    private JCheckBox calcLockButton;

    /**
     * Private constructor. Use {@link #fromXML} instead.
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on illegal attributes
     */
    private Text(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
        valueTemplate = TaggingPresetUtils.parseTemplate(attributes.get("value_template"));
        autoIncrement = attributes.get("auto_increment");
        alternativeAutocompleteKeys = attributes.get("alternative_autocomplete_keys");
        default_ = attributes.get("default");
    }

    /**
     * Create a {@code Text} from an XML element's attributes.
     * @param attributes the XML attributes
     * @return the {@code Text}
     * @throws IllegalArgumentException on invalid attributes
     */
    static Text fromXML(Map<String, String> attributes) throws IllegalArgumentException {
        return new Text(attributes);
    }

    @Override
    boolean addToPanel(JPanel p, Composite.Instance parentInstance) {
        TaggingPreset.Instance presetInstance = parentInstance.getPresetInstance();
        TaggingPresetDialog dialog = presetInstance.getDialog();

        AutoCompTextField<AutoCompletionItem> textField;
        AutoCompComboBoxEditor<AutoCompletionItem> editor = null;

        // find out if our key is already used in the selection.
        Usage usage = Usage.determineTextUsage(presetInstance.getSelected(), key);

        JComponent component;
        if (usage.unused() || usage.hasUniqueValue()) {
            textField = new AutoCompTextField<>();
            component = textField;
        } else {
            // The selected primitives have different values for this key.   <b>Note:</b> this
            // cannot be an AutoCompComboBox because the values in the dropdown are different from
            // those we autocomplete on.
            JosmComboBox<String> comboBox = new JosmComboBox<>();
            comboBox.getModel().addAllElements(usage.map.keySet());
            comboBox.setEditable(true);
            editor = new AutoCompComboBoxEditor<>();
            comboBox.setEditor(editor);
            comboBox.getEditor().setItem(DIFFERENT_I18N);
            textField = editor.getEditorComponent();
            component = comboBox;
        }

        List<String> keys = getAlternativeAutocompleteKeys();
        if (presetInstance.getAutoCompletionManager() != null) {
            textField.addAutoCompListener(presetInstance.getAutoCompletionManager().new NaiveValueAutoCompManager(keys));
        }
        if (length > 0) {
            textField.setMaxTextLength(length);
        }
        if (Item.DISPLAY_KEYS_AS_HINT.get()) {
            textField.setHint(key);
        }

        Instance instance = new Instance(this, parentInstance, textField);
        parentInstance.putInstance(this, instance);
        instance.setInitialValue(usage, presetInstance);

        instance.setupListeners(textField, presetInstance);

        // if there's an auto_increment setting, then wrap the text field
        // into a panel, appending a number of buttons.
        // auto_increment has a format like -2,-1,1,2
        // the text box being the first component in the panel is relied
        // on in a rather ugly fashion further down.
        if (autoIncrement != null) {
            int autoIncrementSelected = instance.getAutoIncrementValue();
            ButtonGroup bg = new ButtonGroup();
            JPanel pnl = new JPanel(new GridBagLayout());
            pnl.add(component, GBC.std().fill(GBC.HORIZONTAL));

            // first, one button for each auto_increment value
            for (final String ai : autoIncrement.split(",", -1)) {
                JToggleButton aibutton = new JToggleButton(ai);
                aibutton.setToolTipText(tr("Select auto-increment of {0} for this field", ai));
                aibutton.setFocusable(false);
                minimizeMargins(aibutton);
                bg.add(aibutton);
                try {
                    // TODO there must be a better way to parse a number like "+3" than this.
                    final int buttonvalue = NumberFormat.getIntegerInstance().parse(ai.replace("+", "")).intValue();
                    if (autoIncrementSelected == buttonvalue) aibutton.setSelected(true);
                    aibutton.addActionListener(e -> instance.setAutoIncrementValue(buttonvalue));
                    pnl.add(aibutton, GBC.std());
                } catch (ParseException ex) {
                    Logging.error("Cannot parse auto-increment value of '" + ai + "' into an integer");
                }
            }

            // an invisible toggle button for "release" of the button group
            final JToggleButton clearbutton = new JToggleButton("X");
            clearbutton.setVisible(false);
            clearbutton.setFocusable(false);
            bg.add(clearbutton);
            // and its visible counterpart. - this mechanism allows us to
            // have *no* button selected after the X is clicked, instead
            // of the X remaining selected
            JButton releasebutton = new JButton("X");
            releasebutton.setToolTipText(tr("Cancel auto-increment for this field"));
            releasebutton.setFocusable(false);
            releasebutton.addActionListener(e -> {
                instance.setAutoIncrementValue(0);
                clearbutton.setSelected(true);
            });
            minimizeMargins(releasebutton);
            pnl.add(releasebutton, GBC.eol());
            component = pnl;
        }

        //
        // If this is a calculated field, then wrap the text field into a panel and
        // append buttons to recalculate and to enable automatic calculation.
        //
        if (valueTemplate != null) {
            JPanel pnl = new JPanel(new GridBagLayout());
            pnl.add(component, GBC.std().fill(GBC.BOTH).weight(1.0, 0.0));
            component = pnl;

            calcButton = new JButton("Σ");
            calcButton.setToolTipText(tr("Calculate the value"));
            calcButton.setFocusable(false);
            minimizeMargins(calcButton);
            calcButton.addActionListener(e -> {
                instance.calculate(presetInstance);
            });
            pnl.add(calcButton, GBC.std().fill(GBC.VERTICAL));

            calcLockButton = new JCheckBox("auto", instance.getCalcLockValue());
            calcLockButton.setToolTipText(tr("Enable automatic calculation for this field"));
            calcLockButton.setFocusable(false);
            minimizeMargins(calcLockButton);
            calcLockButton.addActionListener(e -> {
                instance.setCalcLockValue(calcLockButton.isSelected());
            });
            pnl.add(calcLockButton, GBC.std().fill(GBC.VERTICAL));
        }

        final JLabel label = new JLabel(tr("{0}:", localeText));
        addIcon(label);
        label.setToolTipText(getKeyTooltipText());
        label.setComponentPopupMenu(getPopupMenu());
        label.setLabelFor(component);
        p.add(label, GBC.std().insets(0, 0, 10, 0).weight(0, 0));
        p.add(component, GBC.eol().fill(GBC.HORIZONTAL).weight(1, 0));
        label.applyComponentOrientation(dialog.getDefaultComponentOrientation());
        component.setToolTipText(getKeyTooltipText());
        component.applyComponentOrientation(OrientationAction.getNamelikeOrientation(key));

        return true;
    }

    @Override
    boolean isInteractive() {
        return true;
    }

    private static void minimizeMargins(AbstractButton button) {
        button.setMargin(new Insets(0, 0, 0, 0));
        Insets insets = button.getBorder().getBorderInsets(button);
        // Ensure the current look&feel does not waste horizontal space (as seen in Nimbus & Aqua)
        if (insets != null && insets.left+insets.right > insets.top+insets.bottom) {
            int min = Math.min(insets.top, insets.bottom);
            button.setBorder(BorderFactory.createEmptyBorder(insets.top, min, insets.bottom, min));
        }
    }

    @Override
    MatchType getDefaultMatch() {
        return MatchType.NONE;
    }

    @Override
    public List<String> getValues() {
        if (Utils.isEmpty(default_))
            return Collections.emptyList();
        return Collections.singletonList(default_);
    }

    public List<String> getAlternativeAutocompleteKeys() {
        List<String> keys = new ArrayList<>();
        keys.add(key);
        if (alternativeAutocompleteKeys != null) {
            for (String k : alternativeAutocompleteKeys.split(",", -1)) {
                keys.add(k);
            }
        }
        return keys;
    }

    class Instance extends InteractiveItem.Instance {
        private AutoCompTextField<AutoCompletionItem> textField;
        private String originalValue;
        private Integer autoIncrementSelected;

        Instance(Item item, Composite.Instance parent, AutoCompTextField<AutoCompletionItem> textField) {
            super(item, parent, textField);
            this.textField = textField;
            this.autoIncrementSelected = (Integer) parent.getPresetInstance().getPresetProperty(key + ".autoincrement", 0);
        }

        @Override
        void addChangedTag(Map<String, String> changedTags) {
            // return if unchanged
            String v = getValue();
            if (v == null) {
                Logging.error("No 'last value' support for component " + textField);
                return;
            }

            if (isUseLastAsDefault() || autoIncrement != null) {
                LAST_VALUES.put(key, v);
            }
            if (v.equals(originalValue) || (originalValue == null && v.isEmpty()))
                return;

            changedTags.put(key, v);
            // AutoCompletionManager.rememberUserInput(key, v, true);
        }

        @Override
        void addCurrentTag(Map<String, String> currentTags) {
            currentTags.put(key, getValue());
        }

        @Override
        String getValue() {
            return Utils.removeWhiteSpaces(textField.getText());
        }

        @Override
        void setValue(String newValue) {
            textField.setText(newValue);
        }

        @Override
        void recalculate() {
            if (calcLockButton != null && calcLockButton.isSelected())
                calculate(getPresetInstance());
        }

        private void setInitialValue(Usage usage, TaggingPreset.Instance presetInstance) {
            if (usage.unused()) {
                if (autoIncrementSelected != 0 && autoIncrement != null) {
                    try {
                        textField.setText(Integer.toString(Integer.parseInt(
                                LAST_VALUES.get(key)) + autoIncrementSelected));
                    } catch (NumberFormatException ex) {
                        // Ignore - cannot auto-increment if last was non-numeric
                        Logging.trace(ex);
                    }
                } else if (!usage.hadKeys() || PROP_FILL_DEFAULT.get() || isForceUseLastAsDefault()) {
                    // selected osm primitives are untagged or filling default values feature is enabled
                    if (!presetInstance.isPresetInitiallyMatches() && isUseLastAsDefault() && LAST_VALUES.containsKey(key)) {
                        textField.setText(LAST_VALUES.get(key));
                    } else {
                        textField.setText(default_);
                    }
                } else {
                    // selected osm primitives are tagged and filling default values feature is disabled
                    textField.setText("");
                }
                originalValue = null;
            } else if (usage.hasUniqueValue()) {
                // all objects use the same value
                textField.setText(usage.getFirst());
                originalValue = usage.getFirst();
            } else {
                originalValue = DIFFERENT_I18N;
            }
        }

        /**
         * Calculate and set the field's value from the {@code valueTemplate}
         * @param presetInstance the preset instance (that knows the value of all other fields)
         */
        private void calculate(TaggingPreset.Instance presetInstance) {
            String valueTemplateText = valueTemplate.getText(presetInstance);
            textField.setText(valueTemplateText);
            if (originalValue != null && !originalValue.equals(valueTemplateText)) {
                textField.setForeground(Color.RED);
            } else {
                textField.setForeground(Color.BLUE);
            }
        }

        private void setupListeners(AutoCompTextField<AutoCompletionItem> textField, TaggingPreset.Instance presetInstance) {
            // value_templates don't work well with multiple selected items because,
            // as the command queue is currently implemented, we can only save
            // the same value to all selected primitives, which is probably not
            // what you want.
            if (valueTemplate == null) {  // only fire on normal fields
                textField.getDocument().addDocumentListener(DocumentAdapter.create(ignore ->
                    presetInstance.fireChangeEvent(this)));
            } else {  // only listen on calculated fields
                presetInstance.addListener(new TaggingPreset.DebouncedChangeListener((event) -> {
                    if (calcLockButton.isSelected()) {
                        calculate(presetInstance);
                    }
                }, 250));
            }
        }

        private Integer getAutoIncrementValue() {
            return (Integer) getPresetInstance().getPresetProperty(key + ".autoincrement", 0);
        }

        private void setAutoIncrementValue(Integer i) {
            getPresetInstance().putPresetProperty(key + ".autoincrement", i);
        }

        private String getCalcLockPrefName() {
            return "taggingpreset.calclock." + getPresetInstance().getPreset().getRawName() + "." + key;
        }

        private Boolean getCalcLockValue() {
            return Config.getPref().getBoolean(getCalcLockPrefName(), false);
        }

        private void setCalcLockValue(Boolean b) {
            Config.getPref().putBoolean(getCalcLockPrefName(), b);
        }
    }
}
