// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.Color;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.gui.mappaint.MapPaintStyles.TagKeyReference;
import org.openstreetmap.josm.gui.mappaint.mapcss.LiteralExpression;
import org.openstreetmap.josm.gui.mappaint.styleelement.TextLabel;

/**
 * Extended text directives tests.
 */
class MapCSSWithExtendedTextDirectivesTest {
    /**
     * Test null strategy.
     */
    @Test
    void testCreateNullStrategy() {
        MultiCascade mc = new MultiCascade();
        Node osm = new Node();
        Environment env = new Environment(osm, mc, "default", null);

        TextLabel te = TextLabel.create(env, Color.WHITE, false /* no default annotate */);
        assertNull(te);
    }
}
