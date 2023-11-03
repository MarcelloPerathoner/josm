// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openstreetmap.josm.gui.tagging.presets.TaggingPresetsTest.build;

import javax.swing.JPanel;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.testutils.annotations.TaggingPresets;

/**
 * Unit tests of {@link Link} class.
 */
@TaggingPresets
class LinkTest {
    /**
     * Unit test for {@link Link#addToPanel}.
     */
    @Test
    void testAddToPanel() {
        TestUtils.assumeWorkingJMockit();
        JPanel p = new JPanel();
        Link l = (Link) build("link");
        assertFalse(l.addToPanel(p, TaggingPresetsTest.createMockInstance()));
        assertEquals(0, p.getComponentCount());

        p = new JPanel();
        l = (Link) build("link href=" + Config.getUrls().getJOSMWebsite());
        assertFalse(l.addToPanel(p, TaggingPresetsTest.createMockInstance()));
        assertTrue(p.getComponentCount() > 0);

        p = new JPanel();
        l = (Link) build("link locale_href=" + Config.getUrls().getJOSMWebsite());
        assertFalse(l.addToPanel(p, TaggingPresetsTest.createMockInstance()));
        assertTrue(p.getComponentCount() > 0);
    }
}
