// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JPanel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Unit tests of {@link Label} class.
 */
class LabelTest {

    /**
     * Setup test.
     */
    @RegisterExtension
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules();

    /**
     * Unit test for {@link Label#addToPanel}.
     */
    @Test
    void testAddToPanel() {
        TestUtils.assumeWorkingJMockit();
        JPanel p = new JPanel();
        assertTrue(TaggingPresetsTest.build("label").addToPanel(p, TaggingPresetsTest.createMockInstance()));
        assertTrue(p.getComponentCount() > 0);
    }
}
