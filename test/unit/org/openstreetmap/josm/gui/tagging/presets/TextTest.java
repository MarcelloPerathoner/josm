// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JPanel;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.TestUtils;

import org.openstreetmap.josm.testutils.annotations.Main;

/**
 * Unit tests of {@link Text} class.
 */
@Main
class TextTest {
    /**
     * Unit test for {@link Text#addToPanel}.
     */
    @Test
    void testAddToPanel() {
        TestUtils.assumeWorkingJMockit();
        JPanel p = new JPanel();
        assertTrue(TaggingPresetsTest.build("text").addToPanel(p, TaggingPresetsTest.createMockInstance()));
        assertTrue(p.getComponentCount() > 0);
    }
}
