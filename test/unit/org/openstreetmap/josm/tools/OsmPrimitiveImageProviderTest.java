// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.Dimension;
import java.io.IOException;
import java.util.EnumSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import javax.swing.ImageIcon;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.JOSMFixture;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmUtils;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresetUtils;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresets;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresetsTest;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.tools.OsmPrimitiveImageProvider.Options;
import org.xml.sax.SAXException;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Unit tests of {@link OsmPrimitiveImageProvider}
 */
class OsmPrimitiveImageProviderTest {

    /**
     * Setup test.
     */
    @RegisterExtension
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().mapStyles().presets();

    /**
     * Setup test.
     */
    @BeforeAll
    public static void setUp() {
        JOSMFixture.createUnitTestFixture().init();
    }

    /**
     * Unit test of {@link OsmPrimitiveImageProvider#getResource}.
     * @throws SAXException if defaultpresets.xml is invalid XML
     * @throws IOException if defaultpresets.xml defaults
     * @throws InterruptedException if any thread is interrupted
     * @throws ExecutionException if any thread throws
     * @throws TimeoutException on timeout
     */
    @Test
    void testGetResource() throws SAXException, IOException, InterruptedException, ExecutionException, TimeoutException {
        TaggingPresets taggingPresets = TaggingPresetsTest.initFromDefaultPresets();

        TaggingPresetUtils.waitForIconsLoaded(taggingPresets.getAllPresets(), 30);

        final EnumSet<Options> noDefault = EnumSet.of(Options.NO_DEFAULT);
        final Dimension iconSize = new Dimension(16, 16);

        assertNull(ImageProvider.getPadded(new Node(), new Dimension(0, 0)));
        assertNotNull(ImageProvider.getPadded(new Node(), iconSize));
        assertNull(OsmPrimitiveImageProvider.getResource(new Node(), noDefault));
        assertNotNull(OsmPrimitiveImageProvider.getResource(OsmUtils.createPrimitive("node amenity=restaurant"), noDefault));
        assertNull(OsmPrimitiveImageProvider.getResource(OsmUtils.createPrimitive("node barrier=hedge"),
                EnumSet.of(Options.NO_DEFAULT, Options.NO_DEPRECATED)));
        assertNotNull(OsmPrimitiveImageProvider.getResource(OsmUtils.createPrimitive("way waterway=stream"), noDefault));
        assertNotNull(OsmPrimitiveImageProvider.getResource(OsmUtils.createPrimitive("relation type=route route=railway"), noDefault));
    }

    /**
     * Unit test of {@link OsmPrimitiveImageProvider#getResource} for non-square images.
     */
    @Test
    void testGetResourceNonSquare() {
        final ImageIcon bankIcon = OsmPrimitiveImageProvider
                .getResource(OsmUtils.createPrimitive("node amenity=bank"), Options.DEFAULT)
                .getPaddedIcon(ImageProvider.ImageSizes.LARGEICON.getImageDimension());
        assertEquals(ImageProvider.ImageSizes.LARGEICON.getWidth(), bankIcon.getIconWidth());
        assertEquals(ImageProvider.ImageSizes.LARGEICON.getHeight(), bankIcon.getIconHeight());
        final ImageIcon addressIcon = OsmPrimitiveImageProvider
                .getResource(OsmUtils.createPrimitive("node \"addr:housenumber\"=123"), Options.DEFAULT)
                .getPaddedIcon(ImageProvider.ImageSizes.LARGEICON.getImageDimension());
        assertEquals(ImageProvider.ImageSizes.LARGEICON.getWidth(), addressIcon.getIconWidth());
        assertEquals(ImageProvider.ImageSizes.LARGEICON.getHeight(), addressIcon.getIconHeight());
    }
}
