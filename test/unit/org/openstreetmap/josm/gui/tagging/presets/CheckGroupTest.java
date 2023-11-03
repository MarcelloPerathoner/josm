// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import javax.swing.JPanel;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.testutils.annotations.TaggingPresets;

/**
 * Unit tests of {@link CheckGroup} class.
 */
@TaggingPresets
class CheckGroupTest {
    /**
     * Unit test for {@link CheckGroup#addToPanel}.
     */
    @Test
    void testAddToPanel() {
        TestUtils.assumeWorkingJMockit();

        CheckGroup cg = (CheckGroup) TaggingPresetsTest.build("checkgroup");
        JPanel p = new JPanel();
        assertFalse(cg.addToPanel(p, TaggingPresetsTest.createMockInstance()));
        assertEquals(0, p.getComponentCount()); // should not add an empty panel
    }
}
