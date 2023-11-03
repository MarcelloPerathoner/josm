// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JPanel;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.testutils.annotations.TaggingPresets;


/**
 * Unit tests of {@link PresetListEntry} class.
 */
@TaggingPresets
class PresetListEntryTest {
    /**
     * Non-regression test for ticket <a href="https://josm.openstreetmap.de/ticket/12416">#12416</a>.
     */
    @Test
    void testTicket12416() {
        TestUtils.assumeWorkingJMockit();
        Combo combo = (Combo) TaggingPresetsTest.build("combo key=foo");
        PresetListEntry entry = (PresetListEntry) TaggingPresetsTest.build("list_entry value=");
        combo.addItem(entry);

        JPanel panel = new JPanel();
        TaggingPreset.Instance presetInstance = TaggingPresetsTest.createMockInstance();
        combo.addToPanel(panel, presetInstance);
        assertTrue(entry.newInstance((Combo.Instance) presetInstance.getInstance(combo)).getListDisplay(200).contains(" "));
    }

    /**
     * Non-regression test for ticket <a href="https://josm.openstreetmap.de/ticket/21550">#21550</a>
     */
    @Test
    void testTicket21550() {
        TestUtils.assumeWorkingJMockit();
        Combo combo = (Combo) TaggingPresetsTest.build("combo key=foo");
        PresetListEntry entry = (PresetListEntry) TaggingPresetsTest.build("list_entry value=");
        combo.addItem(entry);

        JPanel panel = new JPanel();
        TaggingPreset.Instance presetInstance = TaggingPresetsTest.createMockInstance();
        combo.addToPanel(panel, presetInstance);
        assertDoesNotThrow(entry.newInstance((Combo.Instance) presetInstance.getInstance(combo))::getCount);
    }
}
