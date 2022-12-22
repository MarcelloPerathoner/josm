// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JPanel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Unit tests of {@link Optional} class.
 */
class OptionalTest {

    /**
     * Setup test.
     */
    @RegisterExtension
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules();

    /**
     * Unit test for {@link Optional#addToPanel}.
     */
    @Test
    void testAddToPanel() {
        TestUtils.assumeWorkingJMockit();
        JPanel p = new JPanel();
        assertFalse(TaggingPresetsTest.build("optional").addToPanel(p, TaggingPresetsTest.createMockInstance()));
        // does not add anything if there are no optional items
        assertTrue(p.getComponentCount() == 0);
    }
}
