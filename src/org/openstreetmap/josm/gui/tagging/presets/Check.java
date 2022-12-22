// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

import org.openstreetmap.josm.data.osm.OsmUtils;
import org.openstreetmap.josm.data.osm.Tagged;
import org.openstreetmap.josm.gui.widgets.IconTextCheckBox;
import org.openstreetmap.josm.gui.widgets.QuadStateCheckBox;
import org.openstreetmap.josm.tools.GBC;

/**
 * Checkbox type.
 */
final class Check extends InteractiveItem {

    /** the value to set when checked (default is "yes") */
    private final String valueOn;
    /** the value to set when unchecked (default is "no") */
    private final String valueOff;
    /** whether the off value is disabled in the dialog, i.e., only unset or yes are provided */
    private final boolean disableOff;
    /** "on" or "off" or unset (default is unset) */
    private final String default_; // only used for tagless objects

    /**
     * Private constructor. Use {@link #fromXML} instead.
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on illegal attributes
     */
    private Check(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
        valueOn = attributes.getOrDefault("value_on", OsmUtils.TRUE_VALUE);
        valueOff = attributes.getOrDefault("value_off", OsmUtils.FALSE_VALUE);
        disableOff = Boolean.parseBoolean(attributes.get("disable_off"));
        default_ = attributes.get("default");
    }

    /**
     * Create this class from an XML element's attributes.
     * @param attributes the XML attributes
     * @return the new instance
     * @throws IllegalArgumentException on invalid attributes
     */
    static Check fromXML(Map<String, String> attributes) throws IllegalArgumentException {
        return new Check(attributes);
    }

    @Override
    boolean addToPanel(JPanel p, Composite.Instance parentInstance) {
        TaggingPreset.Instance presetInstance = parentInstance.getPresetInstance();
        TaggingPresetDialog dialog = presetInstance.getDialog();
        // find out if our key is already used in the selection.
        final Usage usage = Usage.determineBooleanUsage(presetInstance.getSelected(), key);
        final String oneValue = usage.map.isEmpty() ? null : usage.map.lastKey();
        QuadStateCheckBox.State initialState;
        Boolean def = "on".equals(default_) ? Boolean.TRUE : "off".equals(default_) ? Boolean.FALSE : null;

        if (usage.map.size() < 2 && (oneValue == null || valueOn.equals(oneValue) || valueOff.equals(oneValue))) {
            if (def != null && !PROP_FILL_DEFAULT.get()) {
                // default is set and filling default values feature is disabled - check if all primitives are untagged
                for (Tagged s : presetInstance.getSelected()) {
                    if (s.hasKeys()) {
                        def = null;
                    }
                }
            }

            // all selected objects share the same value which is either true or false or unset,
            // we can display a standard check box.
            initialState = valueOn.equals(oneValue) || Boolean.TRUE.equals(def)
                    ? QuadStateCheckBox.State.SELECTED
                    : valueOff.equals(oneValue) || Boolean.FALSE.equals(def)
                    ? QuadStateCheckBox.State.NOT_SELECTED
                    : QuadStateCheckBox.State.UNSET;

        } else {
            def = null;
            // the objects have different values, or one or more objects have something
            // else than true/false. we display a quad-state check box
            // in "partial" state.
            initialState = QuadStateCheckBox.State.PARTIAL;
        }

        final List<QuadStateCheckBox.State> allowedStates = new ArrayList<>(4);
        if (QuadStateCheckBox.State.PARTIAL == initialState)
            allowedStates.add(QuadStateCheckBox.State.PARTIAL);
        allowedStates.add(QuadStateCheckBox.State.SELECTED);
        if (!disableOff || valueOff.equals(oneValue))
            allowedStates.add(QuadStateCheckBox.State.NOT_SELECTED);
        allowedStates.add(QuadStateCheckBox.State.UNSET);

        QuadStateCheckBox check;
        check = new QuadStateCheckBox(icon == null ? localeText : null, initialState,
                allowedStates.toArray(new QuadStateCheckBox.State[0]));
        check.setPropertyText(key);
        check.setState(check.getState()); // to update the tooltip text
        check.setComponentPopupMenu(getPopupMenu());

        if (icon != null) {
            JPanel checkPanel = IconTextCheckBox.wrap(check, localeText, getIcon());
            checkPanel.applyComponentOrientation(dialog.getDefaultComponentOrientation());
            p.add(checkPanel, GBC.eol()); // Do not fill, see #15104
        } else {
            check.applyComponentOrientation(dialog.getDefaultComponentOrientation());
            // Note: if we are in a checkgroup we are in a GridLayout
            p.add(check, GBC.eol()); // Do not fill, see #15104
        }
        Instance instance = new Instance(this, parentInstance, check, initialState, def);
        parentInstance.putInstance(this, instance);
        check.addChangeListener(l -> presetInstance.fireChangeEvent(instance));
        return true;
    }

    @Override
    boolean isInteractive() {
        return true;
    }

    class Instance extends InteractiveItem.Instance {
        private QuadStateCheckBox checkbox;
        private QuadStateCheckBox.State originalState;
        private Boolean def;

        Instance(Item item, Composite.Instance parent, QuadStateCheckBox checkbox, QuadStateCheckBox.State originalState, Boolean def) {
            super(item, parent, checkbox);
            this.checkbox = checkbox;
            this.originalState = originalState;
            this.def = def;
        }

        @Override
        public void addChangedTag(Map<String, String> changedTags) {
            // if nothing has changed, don't create a command.
            if (def == null && (checkbox.getState() == originalState)) return;

            // otherwise change things according to the selected value.
            changedTags.put(key, getValue());
        }

        @Override
        public void addCurrentTag(Map<String, String> currentTags) {
            currentTags.put(key, getValue());
        }

        String getValue() {
            return
                checkbox.getState() == QuadStateCheckBox.State.SELECTED ? valueOn
                    : checkbox.getState() == QuadStateCheckBox.State.NOT_SELECTED ? valueOff
                        : checkbox.getState() == QuadStateCheckBox.State.PARTIAL ? DIFFERENT
                            : null;
        }

        @Override
        void setValue(String newValue) {
            checkbox.setState(
                valueOn.equals(newValue) ? QuadStateCheckBox.State.SELECTED
                    : valueOff.equals(newValue) ? QuadStateCheckBox.State.NOT_SELECTED
                        : DIFFERENT.equals(newValue) ? QuadStateCheckBox.State.PARTIAL
                            : QuadStateCheckBox.State.UNSET
            );
        }
    }

    @Override
    MatchType getDefaultMatch() {
        return MatchType.NONE;
    }

    @Override
    public List<String> getValues() {
        return disableOff ? Arrays.asList(valueOn) : Arrays.asList(valueOn, valueOff);
    }

    @Override
    public String toString() {
        return "Check [key=" + key + ", text=" + text + ", "
                + (localeText != null ? "locale_text=" + localeText + ", " : "")
                + (valueOn != null ? "value_on=" + valueOn + ", " : "")
                + (valueOff != null ? "value_off=" + valueOff + ", " : "")
                + "default_=" + default_ + ']';
    }
}
