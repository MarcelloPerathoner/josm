// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint.mapcss;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;

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

    @Test
    void testRotationNone() {
        String css = "node { symbol-shape: triangle; }";
        Node n = (Node) OsmUtils.createPrimitive("n");
        ds.addPrimitive(n);

        NodeElement ne = NodeElement.create(createStyleEnv(n, css));
        assertEquals(0f, ne.mapImageAngle.getRotationAngle(n));
    }

    @Test
    void testRotationRad() {
        String css = "node { symbol-shape: triangle; icon-rotation: 3.14; }";
        Node n = (Node) OsmUtils.createPrimitive("n");
        ds.addPrimitive(n);

        NodeElement ne = NodeElement.create(createStyleEnv(n, css));
        assertEquals(3.14f, ne.mapImageAngle.getRotationAngle(n));
    }

    @Test
    void testRotationDeg() {
        String css = "node { symbol-shape: triangle; icon-rotation: 22.5°; }";
        Node n = (Node) OsmUtils.createPrimitive("n");
        ds.addPrimitive(n);

        NodeElement ne = NodeElement.create(createStyleEnv(n, css));
        assertEquals(Math.PI/8, ne.mapImageAngle.getRotationAngle(n), 0.01);
    }

    @Test
    void testRotationWayNoParent() {
        Node n = (Node) OsmUtils.createPrimitive("n");
        ds.addPrimitive(n);

        NodeElement ne = NodeElement.create(createStyleEnv(n, CSS_N_WAY_ROTATION));
        assertEquals(0f, ne.mapImageAngle.getRotationAngle(n));
    }

    @Test
    void testRotationWay() {
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

        assertEquals(Utils.toRadians(225),
                     NodeElement.create(createStyleEnv(n0, CSS_N_WAY_ROTATION)).mapImageAngle.getRotationAngle(n0),
                     0.01);
        assertEquals(Utils.toRadians(225),
                     NodeElement.create(createStyleEnv(n1, CSS_N_WAY_ROTATION)).mapImageAngle.getRotationAngle(n1),
                     0.01);
        assertEquals(Utils.toRadians(-45),
                     NodeElement.create(createStyleEnv(n2, CSS_N_WAY_ROTATION)).mapImageAngle.getRotationAngle(n2),
                     0.01);
        assertEquals(Utils.toRadians(-90),
                     NodeElement.create(createStyleEnv(n3, CSS_N_WAY_ROTATION)).mapImageAngle.getRotationAngle(n3),
                     0.01);
    }

    /**
     * icon-rotation: way; picks first way when a node has multiple parent ways
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

        assertEquals(Utils.toRadians(45),
                     NodeElement.create(createStyleEnv(n, CSS_N_WAY_ROTATION)).mapImageAngle.getRotationAngle(n),
                     0.01);
    }

    @Test
    void testHeadingFunction() {
        Node n0 = new Node(new LatLon(0.0, 0.0)); // bl
        Node n1 = new Node(new LatLon(0.0, 0.1)); // br
        Node n2 = new Node(new LatLon(0.1, 0.1)); // tr
        Node n3 = new Node(new LatLon(0.1, 0.0)); // tl
        ds.addPrimitive(n0);
        ds.addPrimitive(n1);
        ds.addPrimitive(n2);
        ds.addPrimitive(n3);

        Way w0 = new Way();
        Way w1 = new Way();
        w0.addNode(n0);
        w0.addNode(n1);
        w0.addNode(n2);
        w1.addNode(n2);
        w1.addNode(n3);
        w0.setKeys(Collections.singletonMap("highway", "primary"));
        w1.setKeys(Collections.singletonMap("highway", "primary"));
        ds.addPrimitive(w0);
        ds.addPrimitive(w1);

        MapCSSStyleSource cssForward = new MapCSSStyleSource("node { symbol-shape: triangle; icon-rotation: heading(); }");
        MapCSSStyleSource cssBackward = new MapCSSStyleSource("node { symbol-shape: triangle; icon-rotation: heading(3.14159); }");
        MapCSSStyleSource cssParentChild = new MapCSSStyleSource("way[highway] > node { symbol-shape: triangle; icon-rotation: heading(); }");

        cssForward.loadStyleSource();
        cssBackward.loadStyleSource();
        cssParentChild.loadStyleSource();

        // forward rotation
        rotationTest(cssForward, n0,  90);
        rotationTest(cssForward, n1,  45);
        rotationTest(cssForward, n2, 315);
        rotationTest(cssForward, n3, 270);

        // backward rotation
        rotationTest(cssBackward, n0, 270);
        rotationTest(cssBackward, n1, 225);
        rotationTest(cssBackward, n2, 135);
        rotationTest(cssBackward, n3,  90);

        // create direction conflicts at nodes n0 and n3
        Way w2 = new Way();
        w2.addNode(n0);
        w2.addNode(n3);
        w2.setKeys(Collections.singletonMap("waterway", "stream"));
        ds.addPrimitive(w2);

        rotationTest(cssForward, n0,   0); // conflict
        rotationTest(cssForward, n1,  45);
        rotationTest(cssForward, n2, 315);
        rotationTest(cssForward, n3,   0); // conflict

        // resolve conflicts at nodes n0 and n3 by selecting only highways
        rotationTest(cssParentChild, n0,  90);
        rotationTest(cssParentChild, n1,  45);
        rotationTest(cssParentChild, n2, 315);
        rotationTest(cssParentChild, n3, 270);

        // change stream to highway: now it conflicts again
        w2.setKeys(Collections.singletonMap("highway", "secondary"));

        rotationTest(cssForward, n0,   0); // conflict
        rotationTest(cssForward, n1,  45);
        rotationTest(cssForward, n2, 315);
        rotationTest(cssForward, n3,   0); // conflict
    }

    private void rotationTest(MapCSSStyleSource source, Node n, int expected) {
        MultiCascade mc1 = new MultiCascade();
        source.apply(mc1, n, 10, false);
        assertEquals(Utils.toRadians(expected),
            mc1.getCascade(null).get("icon-rotation", 0d, Double.class),
            0.00001);
    }

    private Environment createStyleEnv(IPrimitive osm, String css) {
        MapCSSStyleSource source = new MapCSSStyleSource(css);
        source.loadStyleSource();
        Environment env = new Environment(osm, new MultiCascade(), Environment.DEFAULT_LAYER, source);

        for (MapCSSRule r : source.rules) {
            r.execute(env);
        }

        return env;
    }
}
