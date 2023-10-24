// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.openstreetmap.josm.data.preferences.AbstractProperty.ValueChangeEvent;
import org.openstreetmap.josm.data.preferences.AbstractProperty.ValueChangeListener;
import org.openstreetmap.josm.data.validation.OsmValidator;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.data.validation.Test.TagTest;
import org.openstreetmap.josm.data.validation.tests.MapCSSTagChecker;
import org.openstreetmap.josm.data.validation.tests.OpeningHourTest;
import org.openstreetmap.josm.gui.tagging.DataHandlers.DataSetHandler;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPreset.DebouncedChangeListener;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;

/**
 * The interface to the validator
 * <p>
 * The validator only operates on OsmPrimitives in a DataSet.
 * <p>
 * We want to validate before the changes are saved to the main dataset, so we have to
 * clone the primitives into a new DataSet, apply the changes to the new DataSet and
 * then call the validator on the cloned primitives.
 * <p>
 * The result of the validation is communicated visually through the a JLabel with a
 * warning icon. If the icon is enabled the validation went wrong.
 */

public class TaggingPresetValidator implements ChangeListener {
    final TaggingPreset.Instance presetInstance;
    final DataSetHandler handler;
    final Consumer<Collection<TestError>> f;
    final Collection<TagTest> allTests;
    private final DebouncedChangeListener listener;
    private boolean enabled;

    /**
     * Constructor
     *
     * @param presetInstance the preset dialog
     * @param handler the data handler
     * @param f the consumer
     */
    public TaggingPresetValidator(TaggingPreset.Instance presetInstance,
            DataSetHandler handler, Consumer<Collection<TestError>> f) {
        this.presetInstance = presetInstance;
        this.handler = handler;
        this.f = f;

        listener = new TaggingPreset.DebouncedChangeListener(this, 500);
        presetInstance.addListener(listener);

        allTests = new ArrayList<>();
        allTests.add(OsmValidator.getTest(MapCSSTagChecker.class));
        allTests.add(OsmValidator.getTest(OpeningHourTest.class));
        OsmValidator.initializeTests(allTests);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        // User edited in dialog
        if (enabled)
            validate();
    }

    /**
     * Validates the user input for the selected primitives.
     */
    void validate() {
        Logging.debug("Validating ...");
        TaggingPreset.PresetLinkHandler cloneHandler =
            new TaggingPreset.PresetLinkHandler(handler, presetInstance);

        // run all tests and collect all errors
        Collection<TestError> errors = new HashSet<>(); // we want no duplicate messages
        allTests.forEach(t -> {
            t.clear();
            cloneHandler.get().forEach(t::check);
            errors.addAll(t.getErrors());
        });
        f.accept(errors);
    }

    static class EnableValidatorAction extends AbstractAction implements ValueChangeListener<Boolean> {
        private static final String NEW_STATE = "newState";
        /** Use text in menu, or none in togglebutton */
        private boolean useText;

        EnableValidatorAction(PropertyChangeListener listener, boolean useText) {
            super(null);
            this.addPropertyChangeListener(listener);
            TaggingPresets.USE_VALIDATOR.addListener(this);
            this.useText = useText;
            putValue(Action.SHORT_DESCRIPTION, tr("Enable or disable the validator for all presets."));
            if (Config.getPref().getBoolean("text.popupmenu.useicons", true)) {
                new ImageProvider("preferences/validator").getResource().attachImageIcon(this);
            }
            updateState();
        }
        // "data/error" "layer/validator_small" "misc/error" "misc/check_large" "misc/green_check" "misc/grey_x"
        // preferences/validator

        /**
         * Updates the text and the icon.
         */
        public void updateState() {
            if (TaggingPresets.USE_VALIDATOR.get()) {
                if (useText)
                    putValue(Action.NAME, tr("Disable validator"));
                putValue(SELECTED_KEY, true);
                putValue(NEW_STATE, false);
            } else {
                if (useText)
                    putValue(Action.NAME, tr("Enable validator"));
                putValue(SELECTED_KEY, false);
                putValue(NEW_STATE, true);
            }
        }

        @Override
        public void actionPerformed(ActionEvent ae) {
            TaggingPresets.USE_VALIDATOR.put((Boolean) getValue(NEW_STATE));
            firePropertyChange("enableValidator", null, getValue(NEW_STATE));
        }

        @Override
        public void valueChanged(ValueChangeEvent<? extends Boolean> e) {
            updateState();
        }
    }
}
