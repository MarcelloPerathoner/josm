// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint.mapcss;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.geom.AffineTransform;
import java.util.Collections;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.mappaint.Environment;
import org.openstreetmap.josm.gui.mappaint.MultiCascade;
import org.openstreetmap.josm.gui.mappaint.mapcss.Expression.Cacheability;
import org.openstreetmap.josm.gui.mappaint.mapcss.ExpressionFactory.EnvironmentExpression;
import org.openstreetmap.josm.gui.mappaint.mapcss.Instruction.AssignmentInstruction;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.annotations.Projection;
import org.openstreetmap.josm.tools.Utils;

/**
 * Unit tests of icon-rotation mapcss style property.
 */
@BasicPreferences
@Projection
class AffineTransformationsTest {
    private void cacheability(String css, String property, Cacheability expected) {
        MapCSSStyleSource source = new MapCSSStyleSource(css);
        source.loadStyleSource();
        Expression exp = null;

        for (MapCSSRule r : source.rules) {
            for (Instruction i : r.declaration.instructions) {
                if (i instanceof AssignmentInstruction) {
                    AssignmentInstruction ai = (AssignmentInstruction) i;
                    if (property.equals(ai.key)) {
                        exp = (Expression) ai.val;
                    }
                }
            }
        }
        assertNotNull(exp);
        assertEquals(expected, exp.getCacheability());
    }

    @Test
    @DisplayName("expression cacheability test")
    void testCacheability() {
        cacheability("node { icon-transform: transform(rotate(90deg)); }", "icon-transform", Cacheability.IMMUTABLE);
        cacheability("node { text-transform: transform(rotate(90deg)); }", "text-transform", Cacheability.IMMUTABLE);

        cacheability("node { icon-transform: transform(rotate(heading())); }", "icon-transform", Cacheability.STABLE);
        cacheability("node { text-transform: transform(rotate(heading())); }", "text-transform", Cacheability.STABLE);

        cacheability("node { icon-rotation: heading(); }", "icon-rotation", Cacheability.STABLE);
        cacheability("node { text-rotation: heading(); }", "text-rotation", Cacheability.STABLE);

        // this actually tests a legacy yet-unconverted function, those should always
        // return IMMUTABLE to mimick the legacy behaviour that just cached everything
        cacheability("node { icon-rotation: atan(15, 100); }", "icon-rotation", Cacheability.IMMUTABLE);
        cacheability("node { text-rotation: atan(15, 100); }", "text-rotation", Cacheability.IMMUTABLE);
    }

    private AffineTransform affine(String css) {
        DataSet ds = new DataSet();
        Node n0 = new Node(new LatLon(0.0, 0.0)); // bl
        ds.addPrimitive(n0);
        MapCSSStyleSource source = new MapCSSStyleSource("node { icon-transform: " + css + " }");
        source.loadStyleSource();
        MultiCascade mc1 = new MultiCascade();

        for (MapCSSRule r : source.rules) {
            Environment env = new Environment(n0, mc1, Environment.DEFAULT_LAYER, source);
            if (r.matches(env)) {
                r.execute(env);
            }
        }

        Object o = mc1.getCascade(null).get("icon-transform");
        assertNotNull(o);
        if (o instanceof AffineTransform) {
            return (AffineTransform) o;
        }
        return null;
    }

    private void AssertEquals(AffineTransform expected, AffineTransform actual) {
        double[] aExpected = new double[6];
        double[] aActual = new double[6];
        expected.getMatrix(aExpected);
        actual.getMatrix(aActual);
        assertArrayEquals(aExpected, aActual, 0.00001);
    }

    @Test
    @DisplayName("affine transformations test")
    void testAffineTransform() {
        AffineTransform at = new AffineTransform();
        at.translate(10, 10);
        AssertEquals(at, affine("transform(translate(10, 10));"));
        at.rotate(Math.PI / 2);
        AssertEquals(at, affine("transform(translate(10, 10), rotate(90deg));"));
        at.translate(-10, -10);
        AssertEquals(at, affine("transform(translate(10, 10), rotate(90deg), translate(-10, -10));"));

        at.setToIdentity();
        at.scale(2, 2);
        AssertEquals(at, affine("transform(scale(2, 2));"));
        at.shear(0.5, 0.2);
        AssertEquals(at, affine("transform(scale(2, 2), skew(0.5, 0.2));"));

        at.setTransform(1, 2, 3, 4, 5, 6);
        AssertEquals(at, affine("transform(matrix(1, 2, 3, 4, 5, 6));"));
    }

    @Nested
    @DisplayName("heading function test")
    @TestMethodOrder(OrderAnnotation.class)
    class HeadingFunctionTest {
        DataSet ds;
        Node n0;
        Node n1;
        Node n2;
        Node n3;
        Way w0;
        Way w1;
        Way w2;
        MapCSSStyleSource cssForward;
        MapCSSStyleSource cssBackward;
        MapCSSStyleSource cssParentChild;

        private HeadingFunctionTest() {
            cssForward     = new MapCSSStyleSource("               node { icon-rotation: heading();        }");
            cssBackward    = new MapCSSStyleSource("               node { icon-rotation: heading(0.5turn); }");
            cssParentChild = new MapCSSStyleSource("way[highway] > node { icon-rotation: heading();        }");

            n0 = new Node(new LatLon(0.0, 0.0)); // bl
            n1 = new Node(new LatLon(0.0, 0.1)); // br
            n2 = new Node(new LatLon(0.1, 0.1)); // tr
            n3 = new Node(new LatLon(0.1, 0.0)); // tl
            w0 = new Way();
            w1 = new Way();
            w2 = new Way();
            ds = new DataSet();
            ds.addPrimitive(n0);
            ds.addPrimitive(n1);
            ds.addPrimitive(n2);
            ds.addPrimitive(n3);
            w0.addNode(n0);
            w0.addNode(n1);
            w0.addNode(n2);
            w1.addNode(n2);
            w1.addNode(n3);
            w0.setKeys(Collections.singletonMap("highway", "primary"));
            w1.setKeys(Collections.singletonMap("highway", "primary"));
            ds.addPrimitive(w0);
            ds.addPrimitive(w1);

            cssForward.loadStyleSource();
            cssBackward.loadStyleSource();
            cssParentChild.loadStyleSource();
        }

        private void rotationTest(MapCSSStyleSource source, Node n, Integer expected) {
            MultiCascade mc1 = new MultiCascade();

            for (MapCSSRule r : source.rules) {
                Environment env = new Environment(n, mc1, Environment.DEFAULT_LAYER, source);
                if (r.matches(env)) {
                    r.execute(env);
                }
            }

            Object o = mc1.getCascade(null).get("icon-rotation");
            if (o instanceof EnvironmentExpression) {
                o = ((EnvironmentExpression) o).evaluate();
            }
            if (expected == null) {
                assertNull(o);
            } else {
                assertEquals(Utils.toRadians(expected), (Double) o, 0.00001d);
            }
        }

        @Test
        @DisplayName("forward rotation")
        @Order(1)
        void ForwardTest() {
            // forward rotation
            rotationTest(cssForward, n0,  90);
            rotationTest(cssForward, n1,  45);
            rotationTest(cssForward, n2, 315);
            rotationTest(cssForward, n3, 270);
        }

        @Test
        @DisplayName("backward rotation")
        @Order(2)
        void BackwardTest() {
            rotationTest(cssBackward, n0, 270);
            rotationTest(cssBackward, n1, 225);
            rotationTest(cssBackward, n2, 135);
            rotationTest(cssBackward, n3,  90);
        }

        @Test
        @DisplayName("nodes with conflicting directions")
        @Order(3)
        void ConflictTest() {
            // create direction conflicts at nodes n0 and n3
            w2.addNode(n0);
            w2.addNode(n3);
            w2.setKeys(Collections.singletonMap("waterway", "stream"));
            ds.addPrimitive(w2);

            assertEquals(2, n0.getParentWays().size());
            rotationTest(cssForward, n0, null); // conflict
            rotationTest(cssForward, n1,   45);
            rotationTest(cssForward, n2,  315);
            rotationTest(cssForward, n3, null); // conflict
        }

        @Test
        @DisplayName("conflicts resolved by parent-child selector")
        @Order(4)
        void ConflictsResolvedTest() {
            // create direction conflicts at nodes n0 and n3
            w2.addNode(n0);
            w2.addNode(n3);
            w2.setKeys(Collections.singletonMap("waterway", "stream"));
            ds.addPrimitive(w2);

            // resolve conflicts at nodes n0 and n3 by selecting only highway parents
            assertEquals(2, n0.getParentWays().size());
            rotationTest(cssParentChild, n0,  90);
            rotationTest(cssParentChild, n1,  45);
            rotationTest(cssParentChild, n2, 315);
            rotationTest(cssParentChild, n3, 270);
        }

        @Test
        @DisplayName("conflicts unresolved by parent-child selector")
        @Order(5)
        void ConflictAgainTest() {
            // create direction conflicts at nodes n0 and n3
            w2.addNode(n0);
            w2.addNode(n3);
            w2.setKeys(Collections.singletonMap("highway", "secondary"));
            ds.addPrimitive(w2);

            // selecting only highway parents buys nothing here
            assertEquals(2, n0.getParentWays().size());
            rotationTest(cssParentChild, n0, null); // conflict
            rotationTest(cssParentChild, n1,   45);
            rotationTest(cssParentChild, n2,  315);
            rotationTest(cssParentChild, n3, null); // conflict
        }
    }
}
