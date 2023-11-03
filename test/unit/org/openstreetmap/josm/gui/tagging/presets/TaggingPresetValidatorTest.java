// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmUtils;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.validation.OsmValidator;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.data.validation.tests.MapCSSTagChecker;
import org.openstreetmap.josm.data.validation.tests.OpeningHourTest;
import org.openstreetmap.josm.gui.tagging.DataHandlers.DataSetHandler;
import org.openstreetmap.josm.gui.tagging.DataHandlers.ReadOnlyHandler;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.annotations.Projection;
import org.openstreetmap.josm.testutils.annotations.TaggingPresets;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.TextTagParser;


/**
 * Unit tests for {@code TaggingPresetValidator}
 */
@TaggingPresets
@Projection
@BasicPreferences
class TaggingPresetValidatorTest {
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
        // stupid Test class just drops ways with less than 2 nodes on the floor
        DataSet ds = new DataSet();
        Node n1 = new Node(new EastNorth(0, 0));
        Node n2 = new Node(new EastNorth(1, 0));
        Way way = (Way) OsmUtils.createPrimitive("way incline=10m width=1mm opening_hours=\"Mo-Fr 8-10\"");
        ds.addPrimitive(n1);
        ds.addPrimitive(n2);
        ds.addPrimitive(way);
        way.addNode(n1);
        way.addNode(n2);
        ds.setSelected(way);

        DataSetHandler handler = new DataSetHandler().setDataSet(ds);

        TaggingPresetValidator validator = new TaggingPresetValidator(OsmValidator.getTests(), handler,
            errors -> {
                errorMsg = errors.stream().map(this::format).sorted().collect(Collectors.joining("\n"));
            }
        );

        validator.validate();

        // CHECKSTYLE.OFF: LineLength
        assertNotNull(errorMsg);
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
