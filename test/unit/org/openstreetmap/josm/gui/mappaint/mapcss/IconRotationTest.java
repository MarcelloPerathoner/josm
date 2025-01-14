// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint.mapcss;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.geom.AffineTransform;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmUtils;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.mappaint.Environment;
import org.openstreetmap.josm.gui.mappaint.MultiCascade;
import org.openstreetmap.josm.gui.mappaint.styleelement.NodeElement;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.annotations.Projection;
import org.openstreetmap.josm.tools.Utils;

/**
 * Unit tests of icon-rotation mapcss style property.
 */
@BasicPreferences
@Projection
class IconRotationTest {

    private DataSet ds;

    private final String CSS_N_WAY_ROTATION = "node { symbol-shape: triangle; icon-rotation: way; }";

    /**
     * Setup test
     */
    @BeforeEach
    public void setUp() {
        ds = new DataSet();
    }

    private void assertRotated(double expected, AffineTransform actual) {
        AffineTransform exp = new AffineTransform();
        exp.rotate(expected);
        double[] ex = new double[6];
        double[] ac = new double[6];
        exp.getMatrix(ex);
        actual.getMatrix(ac);
        for (int i = 0; i < 6; ++i) {
            assertEquals(ex[i], ac[i], 0.001);
        }
    }


    @Test
    void testRotationNone() {
        String css = "node { symbol-shape: triangle; }";
        Node n = (Node) OsmUtils.createPrimitive("n");
        ds.addPrimitive(n);

        NodeElement ne = NodeElement.create(createStyleEnv(n, css));
        assertRotated(0f, ne.iconTransform);
    }

    @Test
    void testRotationRad() {
        String css = "node { symbol-shape: triangle; icon-rotation: 3.14; }";
        Node n = (Node) OsmUtils.createPrimitive("n");
        ds.addPrimitive(n);

        NodeElement ne = NodeElement.create(createStyleEnv(n, css));
        assertRotated(3.14f, ne.iconTransform);
    }

    @Test
    void testRotationDeg() {
        String css = "node { symbol-shape: triangle; icon-rotation: 22.5°; }";
        Node n = (Node) OsmUtils.createPrimitive("n");
        ds.addPrimitive(n);

        NodeElement ne = NodeElement.create(createStyleEnv(n, css));
        assertRotated(Math.PI/8, ne.iconTransform);
    }

    @Test
    void testRotationWayNoParent() {
        Node n = (Node) OsmUtils.createPrimitive("n");
        ds.addPrimitive(n);

        NodeElement ne = NodeElement.create(createStyleEnv(n, CSS_N_WAY_ROTATION));
        assertRotated(0f, ne.iconTransform);
    }

    @Test
    void testRotationWay() {
        //   n0
        // n1
        //   n2
        //   n3
        Node n0 = new Node(new LatLon(0.0, 0.2));
        Node n1 = new Node(new LatLon(-0.1, 0.1));
        Node n2 = new Node(LatLon.ZERO);
        Node n3 = new Node(new LatLon(0.0, -0.1));
        Way w = new Way();
        w.addNode(n0);
        w.addNode(n1);
        w.addNode(n2);
        w.addNode(n3);
        ds.addPrimitiveRecursive(w);

        assertRotated(Utils.toRadians(225),
                      NodeElement.create(createStyleEnv(n0, CSS_N_WAY_ROTATION)).iconTransform);
        assertRotated(Utils.toRadians(270),
                      NodeElement.create(createStyleEnv(n1, CSS_N_WAY_ROTATION)).iconTransform);
        assertRotated(Utils.toRadians(270 + 26.56),
                      NodeElement.create(createStyleEnv(n2, CSS_N_WAY_ROTATION)).iconTransform);
        assertRotated(Utils.toRadians(270),
                      NodeElement.create(createStyleEnv(n3, CSS_N_WAY_ROTATION)).iconTransform);
    }

    /**
     * icon-rotation: way; refuses to rotate if direction is ambiguous
     */
    @Test
    void testRotationWayMultiple() {
        Node n = new Node(LatLon.ZERO);
        Node n1 = new Node(new LatLon(0.1, 0.1));
        Node n2 = new Node(new LatLon(-0.1, 0.1));
        Way w1 = new Way();
        Way w2 = new Way();
        w1.addNode(n);
        w1.addNode(n1);
        w2.addNode(n);
        w2.addNode(n2);
        ds.addPrimitiveRecursive(w1);
        ds.addPrimitiveRecursive(w2);

        assertRotated(Utils.toRadians(0),
                      NodeElement.create(createStyleEnv(n, CSS_N_WAY_ROTATION)).iconTransform);
    }

    private Environment createStyleEnv(IPrimitive osm, String css) {
        MapCSSStyleSource source = new MapCSSStyleSource(css);
        source.loadStyleSource();
        Environment env = new Environment(osm, new MultiCascade(), MultiCascade.DEFAULT, source);

        for (MapCSSRule r : source.rules) {
            r.execute(env);
        }

        return env;
    }
}
