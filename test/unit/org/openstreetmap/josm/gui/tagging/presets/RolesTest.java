// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import javax.swing.JPanel;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.testutils.annotations.TaggingPresets;

/**
 * Unit tests of {@link Roles} class.
 */
@TaggingPresets
class RolesTest {
    /**
     * Unit test for {@link Roles#addToPanel}.
     */
    @Test
    void testAddToPanel() {
        TestUtils.assumeWorkingJMockit();
        JPanel p = new JPanel();
        assertFalse(TaggingPresetsTest.build("roles").addToPanel(p, TaggingPresetsTest.createMockInstance()));
        // does not add anything if there are no roles
        assertEquals(0, p.getComponentCount());
    }
}
