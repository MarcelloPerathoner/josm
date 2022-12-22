// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.function.Consumer;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.openstreetmap.josm.data.validation.OsmValidator;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.data.validation.Test.TagTest;
import org.openstreetmap.josm.data.validation.tests.MapCSSTagChecker;
import org.openstreetmap.josm.data.validation.tests.OpeningHourTest;
import org.openstreetmap.josm.gui.tagging.DataHandlers.DataSetHandler;
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
        presetInstance.addListener(new TaggingPreset.DebouncedChangeListener(this, 500));

        allTests = new ArrayList<>();
        allTests.add(OsmValidator.getTest(MapCSSTagChecker.class));
        allTests.add(OsmValidator.getTest(OpeningHourTest.class));
        OsmValidator.initializeTests(allTests);
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        // User edited in dialog
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
            cloneHandler.get().forEach(p -> t.check(p));
            errors.addAll(t.getErrors());
        });
        f.accept(errors);
    }
}
