// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openstreetmap.josm.gui.tagging.presets.TaggingPresetsTest.build;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.OsmUtils;
import org.openstreetmap.josm.data.osm.search.SearchParseError;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Unit tests of {@code TaggingPreset}
 */
class TaggingPresetTest {
    /**
     * Setup test.
     */
    @RegisterExtension
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules();

    /**
     * Tests {@link TaggingPreset#test(IPrimitive)}
     * @throws SearchParseError never
     */
    @Test
    void test() throws SearchParseError {
        Key key = (Key) build("key key=railway value=tram_stop");
        Map<String, Chunk> chunks = new HashMap<>();

        TaggingPreset preset = (TaggingPreset) build("item");
        preset.addItem(key);
        preset.fixup(chunks, null);

        assertFalse(preset.test(OsmUtils.createPrimitive("node foo=bar")));
        assertTrue(preset.test(OsmUtils.createPrimitive("node railway=tram_stop")));

        preset = (TaggingPreset) build("item type=node");
        preset.addItem(key);
        preset.fixup(chunks, null);

        assertTrue(preset.test(OsmUtils.createPrimitive("node railway=tram_stop")));
        assertFalse(preset.test(OsmUtils.createPrimitive("way railway=tram_stop")));

        preset = (TaggingPreset) build("item type=node match_expression=-public_transport");
        preset.addItem(key);
        preset.fixup(chunks, null);

        assertTrue(preset.test(OsmUtils.createPrimitive("node railway=tram_stop")));
        assertFalse(preset.test(OsmUtils.createPrimitive("node railway=tram_stop public_transport=stop_position")));
    }
}
