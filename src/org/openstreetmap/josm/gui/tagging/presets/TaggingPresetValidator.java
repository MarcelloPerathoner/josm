// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.preferences.AbstractProperty.ValueChangeEvent;
import org.openstreetmap.josm.data.preferences.AbstractProperty.ValueChangeListener;
import org.openstreetmap.josm.data.validation.OsmValidator;
import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.gui.tagging.DataHandlers.DataSetHandler;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.ImageResource;
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
    final Collection<Test> tests;
    final DataSetHandler handler;
    final Consumer<List<TestError>> f;
    private final TaggingPreset.DebouncedChangeListener listener;
    private boolean enabled;

    /**
     * Constructor
     *
     * @param tests the tests to apply
     * @param handler the data handler
     * @param f the consumer of test errors
     */
    public TaggingPresetValidator(Collection<Test> tests, DataSetHandler handler, Consumer<List<TestError>> f) {
        this.tests = tests;
        this.handler = handler;
        this.f = f;

        listener = new TaggingPreset.DebouncedChangeListener(this, 500);

        OsmValidator.initializeTests(tests);
    }

    public TaggingPreset.DebouncedChangeListener getListener() {
        return listener;
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
    public void validate() {
        Collection<OsmPrimitive> selection = handler.get();
        Logging.info("Validating {0} primitives by {1} tests.", selection.size(), tests.size());

        // run all tests and collect all errors
        List<TestError> errors = new ArrayList<>();
        tests.forEach(test -> {
            test.clear();
            test.setBeforeUpload(false);
            test.setPartialSelection(true);
            test.startTest(null);
            test.visit(selection);
            test.endTest();
            errors.addAll(test.getErrors());
            test.clear();
        });
        errors.forEach(error -> Logging.info(error.toString()));
        Logging.info("Done validating with {0} errors.", errors.size());
        f.accept(errors);
    }

    public static class EnableValidatorAction extends AbstractAction implements ValueChangeListener<Boolean> {
        private static final String NEW_STATE = "newState";
        /** Use text in menu, or none in togglebutton */
        private boolean useText;
        private ImageResource okIcon;
        private ImageResource errorIcon;
        private boolean ok;

        public EnableValidatorAction(PropertyChangeListener listener, boolean useText) {
            super(null);
            this.addPropertyChangeListener(listener);
            TaggingPresets.USE_VALIDATOR.addListener(this);
            this.useText = useText;
            if (Config.getPref().getBoolean("text.popupmenu.useicons", true)) {
                okIcon = new ImageProvider("preferences/validator").getResource();
                errorIcon = new ImageProvider("data/error").getResource();
            }
            setOk(true);
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
                putValue(Action.SHORT_DESCRIPTION, tr("Click to globally disable the validator."));
                putValue(SELECTED_KEY, true);
                putValue(NEW_STATE, false);
            } else {
                if (useText)
                    putValue(Action.NAME, tr("Enable validator"));
                putValue(Action.SHORT_DESCRIPTION, tr("Click to globally enable the validator."));
                putValue(SELECTED_KEY, false);
                putValue(NEW_STATE, true);
            }
            if (ok) {
                okIcon.attachImageIcon(this);
            } else {
                errorIcon.attachImageIcon(this);
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

        public void setOk(boolean ok) {
            this.ok = ok;
            updateState();
        }
    }
}
