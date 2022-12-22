// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmUtils;
import org.openstreetmap.josm.data.validation.OsmValidator;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.gui.tagging.DataHandlers.ReadOnlyHandler;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Unit tests for {@code TaggingPresetValidator}
 */
class TaggingPresetValidatorTest {
    /**
     * Setup test.
     */
    @RegisterExtension
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().projection();

    @BeforeEach
    void setUp() {
        Locale.setDefault(Locale.ENGLISH);
        OsmValidator.initialize();
    }

    String errorMsg = null;

    String format (TestError err) {
        String desc = err.getDescription();
        if (desc != null)
            return err.getMessage() + " (" + err.getDescription() + ")";
        return err.getMessage();
    }

    /**
     * Tests {@link TaggingPresetValidator#validate}
     */
    @Test
    void testValidate() {
        TestUtils.assumeWorkingJMockit();
        OsmPrimitive primitive = OsmUtils.createPrimitive("way incline=10m width=1mm opening_hours=\"Mo-Fr 8-10\"");
        Collection<OsmPrimitive> selection = Collections.singletonList(primitive);

        TaggingPresetValidator validator = new TaggingPresetValidator(
            TaggingPresetsTest.createMockInstance(),
            new ReadOnlyHandler(selection),
            errors -> { errorMsg = errors.stream().map(e -> format(e)).sorted().collect(Collectors.joining("\n")); }
        );
        validator.validate();

        // CHECKSTYLE.OFF: LineLength
        assertTrue(errorMsg != null);
        assertEquals(
            "Opening hours syntax (Hours without minutes)\n" +
            "suspicious tag combination (incline on suspicious object)\n" +
            "suspicious tag combination (width on suspicious object)\n" +
            "unusual value of incline, use x% or x° or up or down instead\n" +
            "unusual value of width: meters is default; only positive values; point is decimal separator; if units, put space then unit",
            errorMsg);
        // CHECKSTYLE.ON: LineLength
    }
}
