// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.swing.JCheckBox;
import javax.swing.JComponent;

import org.openstreetmap.josm.data.osm.OsmUtils;
import org.openstreetmap.josm.data.osm.Tagged;
import org.openstreetmap.josm.gui.widgets.JosmCheckBox;
import org.openstreetmap.josm.gui.widgets.QuadStateButtonModel;
import org.openstreetmap.josm.gui.widgets.QuadStateButtonModel.State;
import org.openstreetmap.josm.tools.GBC;

/**
 * Check item.
 */
final class Check extends InteractiveItem {

    /** the value to set when checked (default is "yes") */
    private final String valueOn;
    /** the value to set when unchecked (default is "no") */
    private final String valueOff;
    /** whether the off value is disabled in the dialog, i.e., only unset or yes are provided */
    private final boolean disableOff;
    /** "on" or "off" or unset (default is unset) */
    private final String defaultValue; // only used for tagless objects

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
        defaultValue = attributes.get("default");
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
    boolean addToPanel(JComponent p, Composite.Instance parentInstance) {
        TaggingPreset.Instance presetInstance = parentInstance.getPresetInstance();
        // find out if our key is already used in the selection.
        final Usage usage = Usage.determineBooleanUsage(presetInstance.getSelected(), key);
        final String oneValue = usage.map.isEmpty() ? null : usage.map.lastKey();
        State initialState;
        Boolean def = "on".equals(defaultValue) ? Boolean.TRUE : "off".equals(defaultValue) ? Boolean.FALSE : null;

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
                    ? State.SELECTED
                    : valueOff.equals(oneValue) || Boolean.FALSE.equals(def)
                    ? State.NOT_SELECTED
                    : State.UNSET;

        } else {
            def = null;
            // the objects have different values, or one or more objects have something
            // else than true/false. we display a quad-state check box
            // in "partial" state.
            initialState = State.PARTIAL;
        }

        final List<State> allowedStates = new ArrayList<>();
        allowedStates.add(State.SELECTED);
        if (!disableOff || valueOff.equals(oneValue))
            allowedStates.add(State.NOT_SELECTED);
        allowedStates.add(State.UNSET);
        if (State.PARTIAL == initialState)
            allowedStates.add(State.PARTIAL);

        JCheckBox check3 = new JosmCheckBox(localeText, getIcon());
        check3.setModel(new QuadStateButtonModel(initialState, new ArrayList<>(allowedStates)));
        p.add(check3, GBC.eol()); // Do not fill, see #15104

        Instance instance = new Instance(this, parentInstance, check3, initialState, def);
        parentInstance.putInstance(this, instance);
        check3.addChangeListener(l -> presetInstance.fireChangeEvent(instance));
        return true;
    }

    @Override
    boolean isInteractive() {
        return true;
    }

    class Instance extends InteractiveItem.Instance {
        private final QuadStateButtonModel model;
        private final State originalState;
        private final Boolean def;

        Instance(Item item, Composite.Instance parent, JCheckBox checkbox, State originalState, Boolean def) {
            super(item, parent, checkbox);
            this.model = (QuadStateButtonModel) checkbox.getModel();
            this.originalState = originalState;
            this.def = def;
        }

        @Override
        public void addChangedTag(Map<String, String> changedTags) {
            // if nothing has changed, don't create a command.
            if (def == null && (model.getState() == originalState)) return;

            // otherwise change things according to the selected value.
            changedTags.put(key, getValue());
        }

        @Override
        public void addCurrentTag(Map<String, String> currentTags) {
            currentTags.put(key, getValue());
        }

        @Override
        String getValue() {
            return
                model.getState() == State.SELECTED ? valueOn
                    : model.getState() == State.NOT_SELECTED ? valueOff
                        : model.getState() == State.PARTIAL ? DIFFERENT
                            : null;
        }

        @Override
        void setValue(String newValue) {
            model.setState(
                valueOn.equals(newValue) ? State.SELECTED
                    : valueOff.equals(newValue) ? State.NOT_SELECTED
                        : DIFFERENT.equals(newValue) ? State.PARTIAL
                            : State.UNSET
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
                + "default_=" + defaultValue + ']';
    }
}
