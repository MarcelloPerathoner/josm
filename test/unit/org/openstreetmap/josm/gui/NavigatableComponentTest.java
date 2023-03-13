// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JPanel;

import org.CustomMatchers;
import org.hamcrest.CustomTypeSafeMatcher;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.ProjectionBounds;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Some tests for the {@link NavigatableComponent} class.
 * @author Michael Zangl
 *
 */

// We need prefs for the hit tests.
@BasicPreferences
class NavigatableComponentTest {

    private static final class NavigatableComponentMock extends NavigatableComponent {
        @Override
        public Point getLocationOnScreen() {
            return new Point(30, 40);
        }

        @Override
        protected boolean isVisibleOnScreen() {
            return true;
        }

        @Override
        public void processMouseMotionEvent(MouseEvent mouseEvent) {
            super.processMouseMotionEvent(mouseEvent);
        }
    }

    private static final int HEIGHT = 200;
    private static final int WIDTH = 300;
    private static final int SEARCH_DISTANCE = 32;
    private static final int NODE_SNAP_DISTANCE = 16;
    private static final int SEGMENT_SNAP_DISTANCE = 8;
    private NavigatableComponentMock component;

    /**
     * We need the projection for coordinate conversions.
     */
    @RegisterExtension
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().preferences().projection();

    /**
     * Create a new, fresh {@link NavigatableComponent}
     */
    @BeforeEach
    public void setUp() {
        Config.getPref().putInt("mappaint.search-distance", SEARCH_DISTANCE);
        Config.getPref().putInt("mappaint.node.snap-distance", NODE_SNAP_DISTANCE);
        Config.getPref().putInt("mappaint.segment.snap-distance", SEGMENT_SNAP_DISTANCE);
        component = new NavigatableComponentMock();
        component.setBounds(new Rectangle(WIDTH, HEIGHT));
        // wait for the event to be propagated.
        GuiHelper.runInEDTAndWait(() -> { /* Do nothing */ });
        component.setVisible(true);
        JPanel parent = new JPanel();
        parent.add(component);
        component.updateLocationState();
    }

    /**
     * Test if the default scale was set correctly.
     */
    @Test
    void testDefaultScale() {
        assertEquals(ProjectionRegistry.getProjection().getDefaultZoomInPPD(), component.getScale(), 0.00001);
    }

    /**
     * Tests {@link NavigatableComponent#getPoint2D(EastNorth)}
     */
    @Test
    void testPoint2DEastNorth() {
        assertThat(component.getPoint2D((EastNorth) null), CustomMatchers.is(new Point2D.Double()));
        Point2D shouldBeCenter = component.getPoint2D(component.getCenter());
        assertThat(shouldBeCenter, CustomMatchers.is(new Point2D.Double(WIDTH / 2.0, HEIGHT / 2.0)));

        EastNorth testPoint = component.getCenter().add(300 * component.getScale(), 200 * component.getScale());
        Point2D testPointConverted = component.getPoint2D(testPoint);
        assertThat(testPointConverted, CustomMatchers.is(new Point2D.Double(WIDTH / 2.0 + 300, HEIGHT / 2.0 - 200)));
    }

    /**
     * TODO: Implement this test.
     */
    @Test
    void testPoint2DLatLon() {
        assertThat(component.getPoint2D((LatLon) null), CustomMatchers.is(new Point2D.Double()));
        // TODO: Really test this.
    }

    /**
     * Tests {@link NavigatableComponent#zoomTo(LatLon)}
     */
    @Test
    void testZoomToLatLon() {
        component.zoomTo(new LatLon(10, 10));
        Point2D shouldBeCenter = component.getPoint2D(new LatLon(10, 10));
        // 0.5 pixel tolerance, see isAfterZoom
        assertEquals(shouldBeCenter.getX(), WIDTH / 2., 0.5);
        assertEquals(shouldBeCenter.getY(), HEIGHT / 2., 0.5);
    }

    /**
     * Tests {@link NavigatableComponent#zoomToFactor(double)} and {@link NavigatableComponent#zoomToFactor(EastNorth, double)}
     */
    @Test
    void testZoomToFactor() {
        EastNorth center = component.getCenter();
        double initialScale = component.getScale();

        // zoomToFactor(double)
        component.zoomToFactor(0.5);
        assertEquals(initialScale / 2, component.getScale(), 0.00000001);
        assertThat(component.getCenter(), isAfterZoom(center, component.getScale()));
        component.zoomToFactor(2);
        assertEquals(initialScale, component.getScale(), 0.00000001);
        assertThat(component.getCenter(), isAfterZoom(center, component.getScale()));

        // zoomToFactor(EastNorth, double)
        EastNorth newCenter = new EastNorth(10, 20);
        component.zoomToFactor(newCenter, 0.5);
        assertEquals(initialScale / 2, component.getScale(), 0.00000001);
        assertThat(component.getCenter(), isAfterZoom(newCenter, component.getScale()));
        component.zoomToFactor(newCenter, 2);
        assertEquals(initialScale, component.getScale(), 0.00000001);
        assertThat(component.getCenter(), isAfterZoom(newCenter, component.getScale()));
    }

    /**
     * Tests {@link NavigatableComponent#getEastNorth(int, int)}
     */
    @Test
    void testGetEastNorth() {
        EastNorth center = component.getCenter();
        assertThat(component.getEastNorth(WIDTH / 2, HEIGHT / 2), CustomMatchers.is(center));

        EastNorth testPoint = component.getCenter().add(WIDTH * component.getScale(), HEIGHT * component.getScale());
        assertThat(component.getEastNorth(3 * WIDTH / 2, -HEIGHT / 2), CustomMatchers.is(testPoint));
    }

    /**
     * Tests {@link NavigatableComponent#zoomToFactor(double, double, double)}
     */
    @Test
    void testZoomToFactorCenter() {
        // zoomToFactor(double, double, double)
        // assumes getEastNorth works as expected
        EastNorth testPoint1 = component.getEastNorth(0, 0);
        EastNorth testPoint2 = component.getEastNorth(200, 150);
        double initialScale = component.getScale();

        component.zoomToFactor(0, 0, 0.5);
        assertEquals(initialScale / 2, component.getScale(), 0.00000001);
        assertThat(component.getEastNorth(0, 0), isAfterZoom(testPoint1, component.getScale()));
        component.zoomToFactor(0, 0, 2);
        assertEquals(initialScale, component.getScale(), 0.00000001);
        assertThat(component.getEastNorth(0, 0), isAfterZoom(testPoint1, component.getScale()));

        component.zoomToFactor(200, 150, 0.5);
        assertEquals(initialScale / 2, component.getScale(), 0.00000001);
        assertThat(component.getEastNorth(200, 150), isAfterZoom(testPoint2, component.getScale()));
        component.zoomToFactor(200, 150, 2);
        assertEquals(initialScale, component.getScale(), 0.00000001);
        assertThat(component.getEastNorth(200, 150), isAfterZoom(testPoint2, component.getScale()));

    }

    /**
     * Tests {@link NavigatableComponent#getProjectionBounds()}
     */
    @Test
    void testGetProjectionBounds() {
        ProjectionBounds bounds = component.getProjectionBounds();
        assertThat(bounds.getCenter(), CustomMatchers.is(component.getCenter()));

        assertThat(bounds.getMin(), CustomMatchers.is(component.getEastNorth(0, HEIGHT)));
        assertThat(bounds.getMax(), CustomMatchers.is(component.getEastNorth(WIDTH, 0)));
    }

    /**
     * Tests {@link NavigatableComponent#getRealBounds()}
     */
    @Test
    void testGetRealBounds() {
        Bounds bounds = component.getRealBounds();
        assertThat(bounds.getCenter(), CustomMatchers.is(component.getLatLon(WIDTH / 2, HEIGHT / 2)));

        assertThat(bounds.getMin(), CustomMatchers.is(component.getLatLon(0, HEIGHT)));
        assertThat(bounds.getMax(), CustomMatchers.is(component.getLatLon(WIDTH, 0)));
    }

    @Test
    void testHoverListeners() {
        AtomicReference<PrimitiveHoverListener.PrimitiveHoverEvent> hoverEvent = new AtomicReference<>();
        PrimitiveHoverListener testListener = hoverEvent::set;
        assertNull(hoverEvent.get());
        component.addNotify();
        component.addPrimitiveHoverListener(testListener);
        DataSet ds = new DataSet();
        MainApplication.getLayerManager().addLayer(new OsmDataLayer(ds, "testHoverListeners", null));
        LatLon center = component.getRealBounds().getCenter();
        Node node1 = new Node(center);
        ds.addPrimitive(node1);
        double x = component.getBounds().getCenterX();
        double y = component.getBounds().getCenterY();
        // Check hover over primitive
        MouseEvent node1Event = new MouseEvent(component, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(),
                0, (int) x, (int) y, 0, false, MouseEvent.NOBUTTON);
        component.processMouseMotionEvent(node1Event);
        GuiHelper.runInEDTAndWait(() -> { /* Sync */ });
        PrimitiveHoverListener.PrimitiveHoverEvent event = hoverEvent.getAndSet(null);
        assertNotNull(event);
        assertSame(node1, event.getHoveredPrimitive());
        assertNull(event.getPreviousPrimitive());
        assertSame(node1Event, event.getMouseEvent());
        // Check moving to the (same) primitive. No new mouse motion event should be called.
        component.processMouseMotionEvent(node1Event);
        GuiHelper.runInEDTAndWait(() -> { /* Sync */ });
        event = hoverEvent.getAndSet(null);
        assertNull(event);
        // Check moving off primitive. A new mouse motion event should be called with the previous primitive and null.
        MouseEvent noNodeEvent =
                new MouseEvent(component, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, 0, 0, 0, false, MouseEvent.NOBUTTON);
        component.processMouseMotionEvent(noNodeEvent);
        GuiHelper.runInEDTAndWait(() -> { /* Sync */ });
        event = hoverEvent.getAndSet(null);
        assertNotNull(event);
        assertSame(node1, event.getPreviousPrimitive());
        assertNull(event.getHoveredPrimitive());
        assertSame(noNodeEvent, event.getMouseEvent());
        // Check moving to area with no primitive with no previous hover primitive
        component.processMouseMotionEvent(
                new MouseEvent(component, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, 1, 1, 0, false, MouseEvent.NOBUTTON));
        assertNull(hoverEvent.get());
    }

    /**
     * Check that EastNorth is the same as expected after zooming the NavigatableComponent.
     *
     * Adds tolerance of 0.5 pixel for pixel grid alignment, see
     * {@link NavigatableComponent#zoomTo(EastNorth, double, boolean)}
     * @param expected expected
     * @param scale current scale
     * @return Matcher object
     */
    private Matcher<EastNorth> isAfterZoom(EastNorth expected, double scale) {
        return new CustomTypeSafeMatcher<EastNorth>(Objects.toString(expected)) {
            @Override
            protected boolean matchesSafely(EastNorth actual) {
                // compare pixels (east/north divided by scale)
                return Math.abs((expected.getX() - actual.getX()) / scale) <= 0.5
                        && Math.abs((expected.getY() - actual.getY()) / scale) <= 0.5;
            }
        };
    }

    @Nested
    class Hit_Tests {

        DataSet ds;
        Node n1, n2, n3, n4, n5, n6;
        Way w1, w2, w3, w4;
        Relation r1, r2, r3, r4;

        @BeforeEach
        public void setUp() {
            ds = new DataSet();
            n1 = new Node(new EastNorth(0, 0));                     // bottom left
            n2 = new Node(new EastNorth(WIDTH, 0));                 // bottom right
            n3 = new Node(new EastNorth(0, HEIGHT));                // topl left
            n4 = new Node(new EastNorth(WIDTH, HEIGHT));            // top right
            n5 = new Node(new EastNorth(NODE_SNAP_DISTANCE / 2, 0));    // within snap of n1
            n6 = new Node(new EastNorth(WIDTH / 2, HEIGHT / 2));    // center (not in ds)
            ds.addPrimitive(n1);
            ds.addPrimitive(n2);
            ds.addPrimitive(n3);
            ds.addPrimitive(n4);
            ds.addPrimitive(n5);
            w1 = new Way();
            w2 = new Way();
            w3 = new Way();
            w4 = new Way();
            w1.addNode(n1);  // bottom
            w1.addNode(n2);
            w2.addNode(n3);  // top
            w2.addNode(n4);
            w3.addNode(n1);  // ascending diagonal
            w3.addNode(n4);
            w4.addNode(n3);  // descending diagonal
            w4.addNode(n2);
            ds.addPrimitive(w1);
            ds.addPrimitive(w2);
            ds.addPrimitive(w3);
            ds.addPrimitive(w4);
            r1 = new Relation();
            r2 = new Relation();
            r3 = new Relation();
            r4 = new Relation();
            r1.addMember(new RelationMember("rel1", w1));
            r2.addMember(new RelationMember("rel2", w2));
            r3.addMember(new RelationMember("rel3", w3));
            r4.addMember(new RelationMember("rel4", w4));
            ds.addPrimitive(r1);
            ds.addPrimitive(r2);
            ds.addPrimitive(r3);
            ds.addPrimitive(r4);
            component.zoomTo(new EastNorth(WIDTH / 2, HEIGHT / 2), 1.0, true);

            OsmDataLayer layer = new OsmDataLayer(ds, "test layer", null);
            MainApplication.getLayerManager().addLayer(layer);
            MainApplication.getLayerManager().setActiveLayer(layer);
        }

        final <T> void assertEqualsList(List<T> expected, List<T> found) {
            if (!expected.equals(found))
                fail("Expected list \n" + expected + "\nfound list \n" + found);
            Iterator<T> iter = expected.iterator();
            for (T p : found) {
                T n = iter.next();
                if (p != n) {
                    fail("Expected item " + p + " found item " + n);
                    return;
                }
            }
            if (iter.hasNext()) {
                fail("Found too many items");
            }
        }

        @Test
        @DisplayName("Test Heron's Formula")
        void testHeron() {
            assertEquals(0.5, component.Heron(1.0, 1.0, 2.0));
            assertEquals((12.0 / 5.0) * (12.0 / 5.0), component.Heron(9.0, 16.0, 25.0));
            // reversing the way segment should give the *exact* same answer
            assertEquals(component.Heron(9.0, 16.0, 25.0), component.Heron(16.0, 9.0, 25.0), 0);
            assertEquals(component.Heron(Math.PI, Math.E, 1.0), component.Heron(Math.E, Math.PI, 1.0), 0);
        }

        @Test
        void testNearestNodes() {
            List<Node> l;
            Point p;

            p = new Point(component.getPoint(n1));
            l = component.getNearestNodes(p, null, x -> true);
            assertEqualsList(Arrays.asList(n1, n5), l);

            p = new Point(component.getPoint(n5));
            l = component.getNearestNodes(p, null, x -> true);
            assertEqualsList(Arrays.asList(n5, n1), l);

            // nothing here
            p = new Point(component.getPoint(n6));
            l = component.getNearestNodes(p, null, x -> true);
            assertEqualsList(new ArrayList<Node>(), l);
        }

        @Test
        void testNearestNode() {
            Point p;
            Node n;

            p = new Point(component.getPoint(n5));

            // prefer nothing
            n = component.getNearestNode(p, x -> true, false, null);
            assertEquals(n5, n);

            // prefer selected but there ain't any
            n = component.getNearestNode(p, x -> true, true, null);
            assertEquals(n5, n);

            // prefer referred by way but there ain't any
            n = component.getNearestNode(p, x -> true, true, Collections.singletonList(w2));
            assertEquals(n5, n);

            ds.setSelected(n1);

            // don't prefer anything
            n = component.getNearestNode(p, x -> true, false, null);
            assertEquals(n5, n);

            // prefer selected
            n = component.getNearestNode(p, x -> true, true, null);
            assertEquals(n1, n);

            // prefer node n1 as part of w1
            n = component.getNearestNode(p, x -> true, false, Collections.singletonList(w1));
            assertEquals(n1, n);

            // nothing to see here
            p = new Point(component.getPoint(n6));
            n = component.getNearestNode(p, x -> true, false, null);
            assertEquals(null, n);
        }

        @Test
        void testNearestWaySegments() {
            List<WaySegment> l;
            Point p;

            p = new Point(component.getPoint(n1)); // 0, 200
            l = component.getNearestWaySegments(p, null, x -> true);
            assertEquals(2, l.size());

            l = component.getNearestWaySegments(p, Arrays.asList(w3), x -> true);
            assertEquals(1, l.size());
        }

        @Test
        void testNearestWays() {
            List<Way> l;
            Point p;

            p = new Point(component.getPoint(n1));
            p.translate(0, -1); // make it nearer to w3
            l = component.getNearestWays(p, null, x -> true);
            assertEqualsList(Arrays.asList(w3, w1), l);

            l = component.getNearestWays(p, Arrays.asList(w3), x -> true);
            assertEqualsList(Arrays.asList(w1), l);
        }

        @Test
        void testNearestWaySegment() {
            WaySegment ws;
            Point p;

            p = new Point(component.getPoint(n1)); // 0, 200
            p.translate(0, -1);

            // nearest way is w3
            ws = component.getNearestWaySegment(p, x -> true, false, null);
            assertEquals(w3, ws.getWay());

            ds.setSelected(w1);

            // prefer selected w1
            ws = component.getNearestWaySegment(p, x -> true, true, null);
            assertEquals(w1, ws.getWay());

            // prefer w1 referenced by r1
            ws = component.getNearestWaySegment(p, x -> true, false, Collections.singletonList(r1));
            assertEquals(w1, ws.getWay());
        }

        @Test
        void testNearestAll() {
            List<OsmPrimitive> l;
            Point p;

            p = new Point(component.getPoint(n1));
            p.translate(1, -1); // w3 -> w1 -> n1 -> n5
            l = component.getAllNearest(p, null, x -> true);
            assertEqualsList(Arrays.asList(w3, w1, n1, n5, r3, r1), l);

            p.translate(0, HEIGHT); // nothing here
            l = component.getAllNearest(p, null, x -> true);
            assertEqualsList(new ArrayList<OsmPrimitive>(), l);
        }

    }
}
