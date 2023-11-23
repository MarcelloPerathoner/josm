// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.ProjectionBounds;
import org.openstreetmap.josm.data.SystemOfMeasurement;
import org.openstreetmap.josm.data.ViewportData;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.data.osm.visitor.paint.MapPaintSettings;
import org.openstreetmap.josm.data.preferences.BooleanProperty;
import org.openstreetmap.josm.data.preferences.DoubleProperty;
import org.openstreetmap.josm.data.preferences.IntegerProperty;
import org.openstreetmap.josm.data.projection.Projection;
import org.openstreetmap.josm.data.projection.ProjectionChangeListener;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;
import org.openstreetmap.josm.gui.PrimitiveHoverListener.PrimitiveHoverEvent;
import org.openstreetmap.josm.gui.help.Helpful;
import org.openstreetmap.josm.gui.layer.NativeScaleLayer;
import org.openstreetmap.josm.gui.layer.NativeScaleLayer.Scale;
import org.openstreetmap.josm.gui.layer.NativeScaleLayer.ScaleList;
import org.openstreetmap.josm.gui.mappaint.MapPaintStyles;
import org.openstreetmap.josm.gui.mappaint.styleelement.StyleElement;
import org.openstreetmap.josm.gui.mappaint.styleelement.LineElement;
import org.openstreetmap.josm.gui.mappaint.styleelement.NodeElement;
import org.openstreetmap.josm.gui.mappaint.StyleElementList;
import org.openstreetmap.josm.gui.mappaint.mapcss.MapCSSStyleSource;
import org.openstreetmap.josm.gui.util.CursorManager;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Pair;
import org.openstreetmap.josm.tools.Utils;
import org.openstreetmap.josm.tools.bugreport.BugReportExceptionHandler;

/**
 * A component that can be navigated by a {@link MapMover}. Used as map view and for the
 * zoomer in the download dialog.
 *
 * @author imi
 * @since 41
 */
public class NavigatableComponent extends JComponent implements Helpful {

    private static final double ALIGNMENT_EPSILON = 1e-3;

    /**
     * Interface to notify listeners of the change of the zoom area.
     * @since 10600 (functional interface)
     */
    @FunctionalInterface
    public interface ZoomChangeListener {
        /**
         * Method called when the zoom area has changed.
         */
        void zoomChanged();
    }

    /**
     * To determine if a primitive is currently selectable.
     */
    public transient Predicate<OsmPrimitive> isSelectablePredicate = prim -> {
        if (!prim.isSelectable()) return false;
        // if it isn't displayed on screen, you cannot click on it
        return !getStyleElementList(prim).isEmpty();
    };

    /** The distance to search for primitives around the mouse pointer. Should be set to
     * the radius of the biggest symbols. */
    public static final IntegerProperty PROP_SEARCH_DISTANCE = new IntegerProperty("mappaint.search-distance", 32);
    /** Node snap distance.  How far can the mouse pointer be from the center of a node
     * and still select that node.  Bigger values make it easier to select small nodes
     * but make it harder to discrimate between nearby nodes.  The mouse will always
     * select when it is inside the bounding box of a node, its symbol, or icon. */
    public static final IntegerProperty PROP_SNAP_DISTANCE = new IntegerProperty("mappaint.node.snap-distance", 10);
    /** Segment snap distance. How far can the mouse pointer be from the centerline of a segment and still select that segment. */
    public static final IntegerProperty PROP_SEGMENT_SNAP_DISTANCE = new IntegerProperty("mappaint.segment.snap-distance", 10);
    /** Zoom steps to get double scale */
    public static final DoubleProperty PROP_ZOOM_RATIO = new DoubleProperty("zoom.ratio", 2.0);
    /** Divide intervals between native resolution levels to smaller steps if they are much larger than zoom ratio */
    public static final BooleanProperty PROP_ZOOM_INTERMEDIATE_STEPS = new BooleanProperty("zoom.intermediate-steps", true);
    /** scale follows native resolution of layer status when layer is created */
    public static final BooleanProperty PROP_ZOOM_SCALE_FOLLOW_NATIVE_RES_AT_LOAD = new BooleanProperty(
            "zoom.scale-follow-native-resolution-at-load", true);

    private int searchDistance = MapPaintSettings.INSTANCE.adj(PROP_SEARCH_DISTANCE.get());
    private double nodeSnapDistance = MapPaintSettings.INSTANCE.adj(PROP_SNAP_DISTANCE.get());
    private double waySnapDistance = MapPaintSettings.INSTANCE.adj(PROP_SEGMENT_SNAP_DISTANCE.get());

    // premultiplied values. we use squared distances so we don't have to take roots
    private double nodeSnapDistanceSq = nodeSnapDistance * nodeSnapDistance;
    private double waySnapDistanceSq = waySnapDistance * waySnapDistance;

    /**
     * The layer which scale is set to.
     */
    private transient NativeScaleLayer nativeScaleLayer;

    /**
     * the zoom listeners
     */
    private static final CopyOnWriteArrayList<ZoomChangeListener> zoomChangeListeners = new CopyOnWriteArrayList<>();

    /**
     * Removes a zoom change listener
     *
     * @param listener the listener. Ignored if null or already absent
     */
    public static void removeZoomChangeListener(ZoomChangeListener listener) {
        zoomChangeListeners.remove(listener);
    }

    /**
     * Adds a zoom change listener
     *
     * @param listener the listener. Ignored if null or already registered.
     */
    public static void addZoomChangeListener(ZoomChangeListener listener) {
        if (listener != null) {
            zoomChangeListeners.addIfAbsent(listener);
        }
    }

    protected static void fireZoomChanged() {
        GuiHelper.runInEDTAndWait(() -> {
            for (ZoomChangeListener l : zoomChangeListeners) {
                l.zoomChanged();
            }
        });
    }

    private final CopyOnWriteArrayList<PrimitiveHoverListener> primitiveHoverListeners = new CopyOnWriteArrayList<>();
    private IPrimitive previousHoveredPrimitive;
    private final PrimitiveHoverMouseListener primitiveHoverMouseListenerHelper = new PrimitiveHoverMouseListener();

    /**
     * Removes a primitive hover listener
     *
     * @param listener the listener. Ignored if null or already absent.
     * @since 18574
     */
    public void removePrimitiveHoverListener(PrimitiveHoverListener listener) {
        primitiveHoverListeners.remove(listener);
    }

    /**
     * Adds a primitive hover listener
     *
     * @param listener the listener. Ignored if null or already registered.
     * @since 18574
     */
    public void addPrimitiveHoverListener(PrimitiveHoverListener listener) {
        if (listener != null) {
            primitiveHoverListeners.addIfAbsent(listener);
        }
    }

    /**
     * Send a {@link PrimitiveHoverEvent} to registered {@link PrimitiveHoverListener}s
     * @param e primitive hover event information
     * @since 18574
     */
    protected void firePrimitiveHovered(PrimitiveHoverEvent e) {
        GuiHelper.runInEDT(() -> {
            for (PrimitiveHoverListener l : primitiveHoverListeners) {
                try {
                    l.primitiveHovered(e);
                } catch (RuntimeException ex) {
                    Logging.logWithStackTrace(Logging.LEVEL_ERROR, "Error in primitive hover listener", ex);
                    BugReportExceptionHandler.handleException(ex);
                }
            }
        });
    }

    private void updateHoveredPrimitive(IPrimitive hovered, MouseEvent e) {
        if (!Objects.equals(hovered, previousHoveredPrimitive)) {
            firePrimitiveHovered(new PrimitiveHoverEvent(hovered, previousHoveredPrimitive, e));
            previousHoveredPrimitive = hovered;
        }
    }

    // The only events that may move/resize this map view are window movements or changes to the map view size.
    // We can clean this up more by only recalculating the state on repaint.
    private final transient HierarchyListener hierarchyListenerNavigatableComponent = e -> {
        long interestingFlags = HierarchyEvent.ANCESTOR_MOVED | HierarchyEvent.SHOWING_CHANGED;
        if ((e.getChangeFlags() & interestingFlags) != 0) {
            updateLocationState();
        }
    };

    private final transient ComponentAdapter componentListenerNavigatableComponent = new ComponentAdapter() {
        @Override
        public void componentShown(ComponentEvent e) {
            updateLocationState();
        }

        @Override
        public void componentResized(ComponentEvent e) {
            updateLocationState();
        }
    };

    protected transient ViewportData initialViewport;

    protected final transient CursorManager cursorManager = new CursorManager(this);

    /**
     * The current state (scale, center, ...) of this map view.
     */
    private transient MapViewState state;

    /**
     * Main uses weak link to store this, so we need to keep a reference.
     */
    private final ProjectionChangeListener projectionChangeListener = (oldValue, newValue) -> fixProjection();

    /**
     * Constructs a new {@code NavigatableComponent}.
     */
    public NavigatableComponent() {
        setLayout(null);
        state = MapViewState.createDefaultState(getWidth(), getHeight());
        ProjectionRegistry.addProjectionChangeListener(projectionChangeListener);
    }

    @Override
    public void addNotify() {
        updateLocationState();
        addHierarchyListener(hierarchyListenerNavigatableComponent);
        addComponentListener(componentListenerNavigatableComponent);
        addPrimitiveHoverMouseListeners();
        super.addNotify();
    }

    @Override
    public void removeNotify() {
        removeHierarchyListener(hierarchyListenerNavigatableComponent);
        removeComponentListener(componentListenerNavigatableComponent);
        removePrimitiveHoverMouseListeners();
        super.removeNotify();
    }

    private void addPrimitiveHoverMouseListeners() {
        addMouseMotionListener(primitiveHoverMouseListenerHelper);
        addMouseListener(primitiveHoverMouseListenerHelper);
    }

    private void removePrimitiveHoverMouseListeners() {
        removeMouseMotionListener(primitiveHoverMouseListenerHelper);
        removeMouseListener(primitiveHoverMouseListenerHelper);
    }

    /**
     * Choose a layer that scale will be snap to its native scales.
     * @param nativeScaleLayer layer to which scale will be snapped
     */
    public void setNativeScaleLayer(NativeScaleLayer nativeScaleLayer) {
        this.nativeScaleLayer = nativeScaleLayer;
        zoomTo(getCenter(), scaleRound(getScale()));
        repaint();
    }

    /**
     * Replies the layer which scale is set to.
     * @return the current scale layer (may be {@code null})
     */
    public NativeScaleLayer getNativeScaleLayer() {
        return nativeScaleLayer;
    }

    /**
     * Get a new scale that is zoomed in from previous scale
     * and snapped to selected native scale layer.
     * @return new scale
     */
    public double scaleZoomIn() {
        return scaleZoomManyTimes(-1);
    }

    /**
     * Get a new scale that is zoomed out from previous scale
     * and snapped to selected native scale layer.
     * @return new scale
     */
    public double scaleZoomOut() {
        return scaleZoomManyTimes(1);
    }

    /**
     * Get a new scale that is zoomed in/out a number of times
     * from previous scale and snapped to selected native scale layer.
     * @param times count of zoom operations, negative means zoom in
     * @return new scale
     */
    public double scaleZoomManyTimes(int times) {
        if (nativeScaleLayer != null) {
            ScaleList scaleList = nativeScaleLayer.getNativeScales();
            if (scaleList != null) {
                if (Boolean.TRUE.equals(PROP_ZOOM_INTERMEDIATE_STEPS.get())) {
                    scaleList = scaleList.withIntermediateSteps(PROP_ZOOM_RATIO.get());
                }
                Scale s = scaleList.scaleZoomTimes(getScale(), PROP_ZOOM_RATIO.get(), times);
                return s != null ? s.getScale() : 0;
            }
        }
        return getScale() * Math.pow(PROP_ZOOM_RATIO.get(), times);
    }

    /**
     * Get a scale snapped to native resolutions, use round method.
     * It gives nearest step from scale list.
     * Use round method.
     * @param scale to snap
     * @return snapped scale
     */
    public double scaleRound(double scale) {
        return scaleSnap(scale, false);
    }

    /**
     * Get a scale snapped to native resolutions.
     * It gives nearest lower step from scale list, usable to fit objects.
     * @param scale to snap
     * @return snapped scale
     */
    public double scaleFloor(double scale) {
        return scaleSnap(scale, true);
    }

    /**
     * Get a scale snapped to native resolutions.
     * It gives nearest lower step from scale list, usable to fit objects.
     * @param scale to snap
     * @param floor use floor instead of round, set true when fitting view to objects
     * @return new scale
     */
    public double scaleSnap(double scale, boolean floor) {
        if (nativeScaleLayer != null) {
            ScaleList scaleList = nativeScaleLayer.getNativeScales();
            if (scaleList != null) {
                if (Boolean.TRUE.equals(PROP_ZOOM_INTERMEDIATE_STEPS.get())) {
                    scaleList = scaleList.withIntermediateSteps(PROP_ZOOM_RATIO.get());
                }
                Scale snapscale = scaleList.getSnapScale(scale, PROP_ZOOM_RATIO.get(), floor);
                return snapscale != null ? snapscale.getScale() : scale;
            }
        }
        return scale;
    }

    /**
     * Zoom in current view. Use configured zoom step and scaling settings.
     */
    public void zoomIn() {
        zoomTo(state.getCenter().getEastNorth(), scaleZoomIn());
    }

    /**
     * Zoom out current view. Use configured zoom step and scaling settings.
     */
    public void zoomOut() {
        zoomTo(state.getCenter().getEastNorth(), scaleZoomOut());
    }

    protected void updateLocationState() {
        if (isVisibleOnScreen()) {
            state = state.usingLocation(this);
        }
    }

    protected boolean isVisibleOnScreen() {
        return SwingUtilities.getWindowAncestor(this) != null && isShowing();
    }

    /**
     * Changes the projection settings used for this map view.
     * <p>
     * Made public temporarily, will be made private later.
     */
    public void fixProjection() {
        state = state.usingProjection(ProjectionRegistry.getProjection());
        repaint();
    }

    /**
     * Gets the current view state. This includes the scale, the current view area and the position.
     * @return The current state.
     */
    public MapViewState getState() {
        return state;
    }

    /**
     * Returns the text describing the given distance in the current system of measurement.
     * @param dist The distance in metres.
     * @return the text describing the given distance in the current system of measurement.
     * @since 3406
     */
    public static String getDistText(double dist) {
        return SystemOfMeasurement.getSystemOfMeasurement().getDistText(dist);
    }

    /**
     * Returns the text describing the given distance in the current system of measurement.
     * @param dist The distance in metres
     * @param format A {@link NumberFormat} to format the area value
     * @param threshold Values lower than this {@code threshold} are displayed as {@code "< [threshold]"}
     * @return the text describing the given distance in the current system of measurement.
     * @since 7135
     */
    public static String getDistText(final double dist, final NumberFormat format, final double threshold) {
        return SystemOfMeasurement.getSystemOfMeasurement().getDistText(dist, format, threshold);
    }

    /**
     * Returns the text describing the distance in meter that correspond to 100 px on screen.
     * @return the text describing the distance in meter that correspond to 100 px on screen
     */
    public String getDist100PixelText() {
        return getDistText(getDist100Pixel());
    }

    /**
     * Get the distance in meter that correspond to 100 px on screen.
     *
     * @return the distance in meter that correspond to 100 px on screen
     */
    public double getDist100Pixel() {
        return getDist100Pixel(true);
    }

    /**
     * Get the distance in meter that correspond to 100 px on screen.
     *
     * @param alwaysPositive if true, makes sure the return value is always
     * &gt; 0. (Two points 100 px apart can appear to be identical if the user
     * has zoomed out a lot and the projection code does something funny.)
     * @return the distance in meter that correspond to 100 px on screen
     */
    public double getDist100Pixel(boolean alwaysPositive) {
        int w = getWidth()/2;
        int h = getHeight()/2;
        ILatLon ll1 = getLatLon(w-50, h);
        ILatLon ll2 = getLatLon(w+50, h);
        double gcd = ll1.greatCircleDistance(ll2);
        if (alwaysPositive && gcd <= 0)
            return 0.1;
        return gcd;
    }

    /**
     * Returns the current center of the viewport.
     * <p>
     * (Use {@link #zoomTo(EastNorth)} to the change the center.)
     *
     * @return the current center of the viewport
     */
    public EastNorth getCenter() {
        return state.getCenter().getEastNorth();
    }

    /**
     * Returns the current scale.
     * <p>
     * In east/north units per pixel.
     *
     * @return the current scale
     */
    public double getScale() {
        return state.getScale();
    }

    /**
     * Returns geographic coordinates from a specific pixel coordination on the screen.
     * @param x X-Pixelposition to get coordinate from
     * @param y Y-Pixelposition to get coordinate from
     *
     * @return Geographic coordinates from a specific pixel coordination on the screen.
     */
    public EastNorth getEastNorth(int x, int y) {
        return state.getForView(x, y).getEastNorth();
    }

    /**
     * Determines the projection bounds of view area.
     * @return the projection bounds of view area
     */
    public ProjectionBounds getProjectionBounds() {
        return getState().getViewArea().getProjectionBounds();
    }

    /* FIXME: replace with better method - used by MapSlider */
    public ProjectionBounds getMaxProjectionBounds() {
        Bounds b = getProjection().getWorldBoundsLatLon();
        return new ProjectionBounds(getProjection().latlon2eastNorth(b.getMin()),
                getProjection().latlon2eastNorth(b.getMax()));
    }

    /* FIXME: replace with better method - used by Main to reset Bounds when projection changes, don't use otherwise */
    public Bounds getRealBounds() {
        return getState().getViewArea().getCornerBounds();
    }

    /**
     * Returns unprojected geographic coordinates for a specific pixel position on the screen.
     * @param x X-Pixelposition to get coordinate from
     * @param y Y-Pixelposition to get coordinate from
     *
     * @return Geographic unprojected coordinates from a specific pixel position on the screen.
     */
    public LatLon getLatLon(int x, int y) {
        return getProjection().eastNorth2latlon(getEastNorth(x, y));
    }

    /**
     * Returns unprojected geographic coordinates for a specific pixel position on the screen.
     * @param x X-Pixelposition to get coordinate from
     * @param y Y-Pixelposition to get coordinate from
     *
     * @return Geographic unprojected coordinates from a specific pixel position on the screen.
     */
    public LatLon getLatLon(double x, double y) {
        return getLatLon((int) x, (int) y);
    }

    /**
     * Determines the projection bounds of given rectangle.
     * @param r rectangle
     * @return the projection bounds of {@code r}
     */
    public ProjectionBounds getProjectionBounds(Rectangle r) {
        return getState().getViewArea(r).getProjectionBounds();
    }

    /**
     * Returns minimum bounds that will cover a given rectangle.
     * @param r rectangle
     * @return Minimum bounds that will cover rectangle
     */
    public Bounds getLatLonBounds(Rectangle r) {
        return ProjectionRegistry.getProjection().getLatLonBoundsBox(getProjectionBounds(r));
    }

    /**
     * Creates an affine transform that is used to convert the east/north coordinates to view coordinates.
     * @return The affine transform.
     */
    public AffineTransform getAffineTransform() {
        return getState().getAffineTransform();
    }

    /**
     * Return the point on the screen where this Coordinate would be.
     * @param p The point, where this geopoint would be drawn.
     * @return The point on screen where "point" would be drawn, relative to the own top/left.
     */
    public Point2D getPoint2D(EastNorth p) {
        if (null == p)
            return new Point();
        return getState().getPointFor(p).getInView();
    }

    /**
     * Return the point on the screen where this Coordinate would be.
     * <p>
     * Alternative: {@link #getState()}, then {@link MapViewState#getPointFor(ILatLon)}
     * @param latlon The point, where this geopoint would be drawn.
     * @return The point on screen where "point" would be drawn, relative to the own top/left.
     */
    public Point2D getPoint2D(ILatLon latlon) {
        if (latlon == null) {
            return new Point();
        } else {
            return getPoint2D(latlon.getEastNorth(ProjectionRegistry.getProjection()));
        }
    }

    /**
     * Return the point on the screen where this Coordinate would be.
     * <p>
     * Alternative: {@link #getState()}, then {@link MapViewState#getPointFor(ILatLon)}
     * @param latlon The point, where this geopoint would be drawn.
     * @return The point on screen where "point" would be drawn, relative to the own top/left.
     */
    public Point2D getPoint2D(LatLon latlon) {
        return getPoint2D((ILatLon) latlon);
    }

    /**
     * Return the point on the screen where this Node would be.
     * <p>
     * Alternative: {@link #getState()}, then {@link MapViewState#getPointFor(ILatLon)}
     * @param n The node, where this geopoint would be drawn.
     * @return The point on screen where "node" would be drawn, relative to the own top/left.
     */
    public Point2D getPoint2D(Node n) {
        return getPoint2D(n.getEastNorth());
    }

    /**
     * loses precision, may overflow (depends on p and current scale)
     * @param p east/north
     * @return point
     * @see #getPoint2D(EastNorth)
     */
    public Point getPoint(EastNorth p) {
        Point2D d = getPoint2D(p);
        return new Point((int) d.getX(), (int) d.getY());
    }

    /**
     * loses precision, may overflow (depends on p and current scale)
     * @param latlon lat/lon
     * @return point
     * @see #getPoint2D(LatLon)
     * @since 12725
     */
    public Point getPoint(ILatLon latlon) {
        Point2D d = getPoint2D(latlon);
        return new Point((int) d.getX(), (int) d.getY());
    }

    /**
     * loses precision, may overflow (depends on p and current scale)
     * @param latlon lat/lon
     * @return point
     * @see #getPoint2D(LatLon)
     */
    public Point getPoint(LatLon latlon) {
        return getPoint((ILatLon) latlon);
    }

    /**
     * loses precision, may overflow (depends on p and current scale)
     * @param n node
     * @return point
     * @see #getPoint2D(Node)
     */
    public Point getPoint(Node n) {
        Point2D d = getPoint2D(n);
        return new Point((int) d.getX(), (int) d.getY());
    }

    /**
     * Zoom to the given coordinate and scale.
     *
     * @param newCenter The center x-value (easting) to zoom to.
     * @param newScale The scale to use.
     */
    public void zoomTo(EastNorth newCenter, double newScale) {
        zoomTo(newCenter, newScale, false);
    }

    /**
     * Zoom to the given coordinate and scale.
     *
     * @param center The center x-value (easting) to zoom to.
     * @param scale The scale to use.
     * @param initial true if this call initializes the viewport.
     */
    public void zoomTo(EastNorth center, double scale, boolean initial) {
        Bounds b = getProjection().getWorldBoundsLatLon();
        ProjectionBounds pb = getProjection().getWorldBoundsBoxEastNorth();
        double newScale = scale;
        int width = getWidth();
        int height = getHeight();

        // make sure, the center of the screen is within projection bounds
        double east = center.east();
        double north = center.north();
        east = Math.max(east, pb.minEast);
        east = Math.min(east, pb.maxEast);
        north = Math.max(north, pb.minNorth);
        north = Math.min(north, pb.maxNorth);
        EastNorth newCenter = new EastNorth(east, north);

        // don't zoom out too much, the world bounds should be at least
        // half the size of the screen
        double pbHeight = pb.maxNorth - pb.minNorth;
        if (height > 0 && 2 * pbHeight < height * newScale) {
            double newScaleH = 2 * pbHeight / height;
            double pbWidth = pb.maxEast - pb.minEast;
            if (width > 0 && 2 * pbWidth < width * newScale) {
                double newScaleW = 2 * pbWidth / width;
                newScale = Math.max(newScaleH, newScaleW);
            }
        }

        // don't zoom in too much, minimum: 100 px = 1 cm
        LatLon ll1 = getLatLon(width / 2 - 50, height / 2);
        LatLon ll2 = getLatLon(width / 2 + 50, height / 2);
        if (ll1.isValid() && ll2.isValid() && b.contains(ll1) && b.contains(ll2)) {
            double dm = ll1.greatCircleDistance((ILatLon) ll2);
            double den = 100 * getScale();
            double scaleMin = 0.01 * den / dm / 100;
            if (newScale < scaleMin && !Double.isInfinite(scaleMin)) {
                newScale = scaleMin;
            }
        }

        // snap scale to imagery if needed
        newScale = scaleRound(newScale);

        // Align to the pixel grid:
        // This is a sub-pixel correction to ensure consistent drawing at a certain scale.
        // For example take 2 nodes, that have a distance of exactly 2.6 pixels.
        // Depending on the offset, the distance in rounded or truncated integer
        // pixels will be 2 or 3. It is preferable to have a consistent distance
        // and not switch back and forth as the viewport moves. This can be achieved by
        // locking an arbitrary point to integer pixel coordinates. (Here the EastNorth
        // origin is used as reference point.)
        // Note that the normal right mouse button drag moves the map by integer pixel
        // values, so it is not an issue in this case. It only shows when zooming
        // in & back out, etc.
        MapViewState mvs = getState().usingScale(newScale);
        mvs = mvs.movedTo(mvs.getCenter(), newCenter);
        Point2D enOrigin = mvs.getPointFor(new EastNorth(0, 0)).getInView();
        // as a result of the alignment, it is common to round "half integer" values
        // like 1.49999, which is numerically unstable; add small epsilon to resolve this
        Point2D enOriginAligned = new Point2D.Double(
                Math.round(enOrigin.getX()) + ALIGNMENT_EPSILON,
                Math.round(enOrigin.getY()) + ALIGNMENT_EPSILON);
        EastNorth enShift = mvs.getForView(enOriginAligned.getX(), enOriginAligned.getY()).getEastNorth();
        newCenter = newCenter.subtract(enShift);

        EastNorth oldCenter = getCenter();
        if (!newCenter.equals(oldCenter) || !Utils.equalsEpsilon(getScale(), newScale)) {
            if (!initial) {
                pushZoomUndo(oldCenter, getScale());
            }
            zoomNoUndoTo(newCenter, newScale, initial);
        }
    }

    /**
     * Zoom to the given coordinate without adding to the zoom undo buffer.
     *
     * @param newCenter The center x-value (easting) to zoom to.
     * @param newScale The scale to use.
     * @param initial true if this call initializes the viewport.
     */
    private void zoomNoUndoTo(EastNorth newCenter, double newScale, boolean initial) {
        if (!Utils.equalsEpsilon(getScale(), newScale)) {
            state = state.usingScale(newScale);
        }
        if (!newCenter.equals(getCenter())) {
            state = state.movedTo(state.getCenter(), newCenter);
        }
        if (!initial) {
            repaint();
            fireZoomChanged();
        }
    }

    /**
     * Zoom to given east/north.
     * @param newCenter new center coordinates
     */
    public void zoomTo(EastNorth newCenter) {
        zoomTo(newCenter, getScale());
    }

    /**
     * Zoom to given lat/lon.
     * @param newCenter new center coordinates
     * @since 12725
     */
    public void zoomTo(ILatLon newCenter) {
        zoomTo(getProjection().latlon2eastNorth(newCenter));
    }

    /**
     * Zoom to given lat/lon.
     * @param newCenter new center coordinates
     */
    public void zoomTo(LatLon newCenter) {
        zoomTo((ILatLon) newCenter);
    }

    /**
     * Thread class for smooth scrolling. Made a separate class, so we can safely terminate it.
     */
    private class SmoothScrollThread extends Thread {
        private boolean doStop;
        private final EastNorth oldCenter = getCenter();
        private final EastNorth finalNewCenter;
        private final long frames;
        private final long sleepTime;

        SmoothScrollThread(EastNorth newCenter, long frameNum, int fps) {
            super("smooth-scroller");
            finalNewCenter = newCenter;
            frames = frameNum;
            sleepTime = 1000L / fps;
        }

        @Override
        public void run() {
            try {
                for (int i = 0; i < frames && !doStop; i++) {
                    final EastNorth z = oldCenter.interpolate(finalNewCenter, (1.0+i) / frames);
                    GuiHelper.runInEDTAndWait(() -> zoomTo(z));
                    Thread.sleep(sleepTime);
                }
            } catch (InterruptedException ex) {
                Logging.warn("Interruption during smooth scrolling");
            }
        }

        public void stopIt() {
            doStop = true;
        }
    }

    /**
     * Create a thread that moves the viewport to the given center in an animated fashion.
     * @param newCenter new east/north center
     */
    public void smoothScrollTo(EastNorth newCenter) {
        final EastNorth oldCenter = getCenter();
        if (!newCenter.equals(oldCenter)) {
            final int fps = Config.getPref().getInt("smooth.scroll.fps", 20);     // animation frames per second
            final int speed = Config.getPref().getInt("smooth.scroll.speed", 1500); // milliseconds for full-screen-width pan
            final int maxtime = Config.getPref().getInt("smooth.scroll.maxtime", 5000); // milliseconds maximum scroll time
            final double distance = newCenter.distance(oldCenter) / getScale();
            double milliseconds = distance / getWidth() * speed;
            if (milliseconds > maxtime) { // prevent overlong scroll time, speed up if necessary
                milliseconds = maxtime;
            }

            ThreadGroup group = Thread.currentThread().getThreadGroup();
            Thread[] threads = new Thread[group.activeCount()];
            group.enumerate(threads, true);
            boolean stopped = false;
            for (Thread t : threads) {
                if (t instanceof SmoothScrollThread) {
                    ((SmoothScrollThread) t).stopIt();
                    /* handle this case outside in case there is more than one smooth thread */
                    stopped = true;
                }
            }
            if (stopped && milliseconds > maxtime/2.0) { /* we aren't fast enough, skip smooth */
                Logging.warn("Skip smooth scrolling");
                zoomTo(newCenter);
            } else {
                long frames = Math.round(milliseconds * fps / 1000);
                if (frames <= 1)
                    zoomTo(newCenter);
                else
                    new SmoothScrollThread(newCenter, frames, fps).start();
            }
        }
    }

    public void zoomManyTimes(double x, double y, int times) {
        double oldScale = getScale();
        double newScale = scaleZoomManyTimes(times);
        zoomToFactor(x, y, newScale / oldScale);
    }

    public void zoomToFactor(double x, double y, double factor) {
        double newScale = getScale()*factor;
        EastNorth oldUnderMouse = getState().getForView(x, y).getEastNorth();
        MapViewState newState = getState().usingScale(newScale);
        newState = newState.movedTo(newState.getForView(x, y), oldUnderMouse);
        zoomTo(newState.getCenter().getEastNorth(), newScale);
    }

    public void zoomToFactor(EastNorth newCenter, double factor) {
        zoomTo(newCenter, getScale()*factor);
    }

    public void zoomToFactor(double factor) {
        zoomTo(getCenter(), getScale()*factor);
    }

    /**
     * Zoom to given projection bounds.
     * @param box new projection bounds
     */
    public void zoomTo(ProjectionBounds box) {
        double newScale = box.getScale(getWidth(), getHeight());
        newScale = scaleFloor(newScale);
        zoomTo(box.getCenter(), newScale);
    }

    /**
     * Zoom to given bounds.
     * @param box new bounds
     */
    public void zoomTo(Bounds box) {
        zoomTo(new ProjectionBounds(getProjection().latlon2eastNorth(box.getMin()),
                getProjection().latlon2eastNorth(box.getMax())));
    }

    /**
     * Zoom to given viewport data.
     * @param viewport new viewport data
     */
    public void zoomTo(ViewportData viewport) {
        if (viewport == null) return;
        if (viewport.getBounds() != null) {
            if (!viewport.getBounds().hasExtend()) {
                // see #18623
                BoundingXYVisitor v = new BoundingXYVisitor();
                v.visit(viewport.getBounds());
                zoomTo(v);
            } else {
                zoomTo(viewport.getBounds());
            }

        } else {
            zoomTo(viewport.getCenter(), viewport.getScale(), true);
        }
    }

    /**
     * Set the new dimension to the view.
     * @param v box to zoom to
     */
    public void zoomTo(BoundingXYVisitor v) {
        if (v == null) {
            v = new BoundingXYVisitor();
        }
        if (v.getBounds() == null) {
            v.visit(getProjection().getWorldBoundsLatLon());
        }

        // increase bbox. This is required
        // especially if the bbox contains one single node, but helpful
        // in most other cases as well.
        // Do not zoom if the current scale covers the selection, #16706
        final MapView mapView = MainApplication.getMap().mapView;
        final double mapScale = mapView.getScale();
        final double minScale = v.getBounds().getScale(mapView.getWidth(), mapView.getHeight());
        v.enlargeBoundingBoxLogarithmically();
        final double maxScale = v.getBounds().getScale(mapView.getWidth(), mapView.getHeight());
        if (minScale <= mapScale && mapScale < maxScale) {
            mapView.zoomTo(v.getBounds().getCenter());
        } else {
            zoomTo(v.getBounds());
        }
    }

    private static class ZoomData {
        private final EastNorth center;
        private final double scale;

        ZoomData(EastNorth center, double scale) {
            this.center = center;
            this.scale = scale;
        }

        public EastNorth getCenterEastNorth() {
            return center;
        }

        public double getScale() {
            return scale;
        }
    }

    private final transient Stack<ZoomData> zoomUndoBuffer = new Stack<>();
    private final transient Stack<ZoomData> zoomRedoBuffer = new Stack<>();
    private long zoomTimestamp = System.currentTimeMillis();

    private void pushZoomUndo(EastNorth center, double scale) {
        long now = System.currentTimeMillis();
        if ((now - zoomTimestamp) > (Config.getPref().getDouble("zoom.undo.delay", 1.0) * 1000)) {
            zoomUndoBuffer.push(new ZoomData(center, scale));
            if (zoomUndoBuffer.size() > Config.getPref().getInt("zoom.undo.max", 50)) {
                zoomUndoBuffer.remove(0);
            }
            zoomRedoBuffer.clear();
        }
        zoomTimestamp = now;
    }

    /**
     * Zoom to previous location.
     */
    public void zoomPrevious() {
        if (!zoomUndoBuffer.isEmpty()) {
            ZoomData zoom = zoomUndoBuffer.pop();
            zoomRedoBuffer.push(new ZoomData(getCenter(), getScale()));
            zoomNoUndoTo(zoom.getCenterEastNorth(), zoom.getScale(), false);
        }
    }

    /**
     * Zoom to next location.
     */
    public void zoomNext() {
        if (!zoomRedoBuffer.isEmpty()) {
            ZoomData zoom = zoomRedoBuffer.pop();
            zoomUndoBuffer.push(new ZoomData(getCenter(), getScale()));
            zoomNoUndoTo(zoom.getCenterEastNorth(), zoom.getScale(), false);
        }
    }

    /**
     * Determines if zoom history contains "undo" entries.
     * @return {@code true} if zoom history contains "undo" entries
     */
    public boolean hasZoomUndoEntries() {
        return !zoomUndoBuffer.isEmpty();
    }

    /**
     * Determines if zoom history contains "redo" entries.
     * @return {@code true} if zoom history contains "redo" entries
     */
    public boolean hasZoomRedoEntries() {
        return !zoomRedoBuffer.isEmpty();
    }

    private BBox getBBox(Point p, int snapDistance) {
        return new BBox(getLatLon(p.x - snapDistance, p.y - snapDistance),
                getLatLon(p.x + snapDistance, p.y + snapDistance));
    }

    private StyleElementList getStyleElementList(OsmPrimitive osm) {
        MapCSSStyleSource.STYLE_SOURCE_LOCK.readLock().lock();
        try {
            return MapPaintStyles.getStyles().get(osm, getDist100Pixel(), this);
        } finally {
            MapCSSStyleSource.STYLE_SOURCE_LOCK.readLock().unlock();
        }
    }

    /**
     * Returns all nodes whose bounding box on screen contains {@code p} or whose centers
     * are closer to {@code p} than {@code mappaint.node.snap-distance}. The nodes must
     * not be in {@code ignore}, and must satisfy {@code predicate}. The nodes are
     * returned sorted by their distance to {@code p} in ascending order.
     *
     * @param p the screen point
     * @param ignore nodes to ignore
     * @param predicate the returned nodes must satisfy
     *
     * @return the nearest nodes
     */
    public final List<Node> getNearestNodes(Point p,
            Collection<? extends OsmPrimitive> ignore,
            Predicate<OsmPrimitive> predicate) {

        DataSet ds = MainApplication.getLayerManager().getActiveDataSet();

        List<Pair<Node, Double>> l = new ArrayList<>();
        if (ds != null) {
            for (Node n : ds.searchNodes(getBBox(p, searchDistance))) {
                if (predicate != null && !predicate.test(n))
                    continue;
                if (ignore != null && ignore.contains(n))
                    continue;
                double distSq = getPoint2D(n).distanceSq(p);
                if (distSq <= nodeSnapDistanceSq || hitTest(p, n)) {
                    l.add(new Pair<>(n, distSq));
                }
            }
            // sort according to distance ascending
            // N.B. we must not sort selected nodes in front of unselected
            // or the order in the middle-click-menu will change
            Collections.sort(l, Comparator.comparing(i -> i.b));
        }
        return l.stream().map(i -> i.a).collect(Collectors.toList());
    }

    /**
     * Convenience method for {@link #getNearestNodes(Point, Collection, Predicate)}.
     *
     * @param p the screen point
     * @param predicate the returned nodes must satisfy
     *
     * @return the nearest nodes
     */
    public final List<Node> getNearestNodes(Point p, Predicate<OsmPrimitive> predicate) {
        return getNearestNodes(p, null, predicate);
    }

    /**
     * Return the nearest node to {@code p} that is closer than
     * {@code mappaint.node.snap-distance} and satisfies {@code predicate}.
     * <p>
     * The following algorithm is used:
     * <ul>
     * <li>If {@code useSelected} is true, prefer the closest selected node. Use
     * case: remove a primitive from a selection.
     *
     * <li>Else if {@code preferredRefs} is not null, prefer the closest node that
     * is referred by any of the primitives in {@code preferredRefs}.
     *
     * <li>Else prefer the closest new node (node with id==0) within about the same
     * distance as the node closest to {@code p}.
     *
     * <li>Else return the closest node.
     *
     * <li>Else return null.
     * </ul>
     * @param p the screen point
     * @param predicate the node must satisfy
     * @param useSelected prefer a selected node
     * @param preferredRefs prefer a node referred by one of these primitives
     *
     * @return the nearest node
     * @since 6065
     */
    public final Node getNearestNode(Point p, Predicate<OsmPrimitive> predicate,
            boolean useSelected, Collection<OsmPrimitive> preferredRefs) {

        List<Node> nlist = getNearestNodes(p, predicate);
        if (nlist.isEmpty()) return null;

        Node result;

        if (useSelected) {
            result = nlist.stream().filter(n -> n.isSelected()).findFirst().orElse(null);
            if (result != null) return result;
        }
        if (preferredRefs != null && !preferredRefs.isEmpty()) {
            Set<OsmPrimitive> prefs = new HashSet<>(preferredRefs);
            result = nlist.stream().filter(
                n -> n.getReferrers().stream().anyMatch(prefs::contains)
                ).findFirst().orElse(null);
            if (result != null) return result;
        }

        Node firstNew = nlist.stream().filter(n -> n.isNew()).findFirst().orElse(null);
        Node first = nlist.stream().findFirst().orElse(null);

        if (firstNew == null) return first;
        if (first == null) return null;
        if (first == firstNew) return first;

        Point2D pt = new Point2D.Double(p.x, p.y);
        double distNew = pt.distance(getPoint2D(firstNew));
        double dist = pt.distance(getPoint2D(first));

        return (distNew - dist < 1) ? firstNew : first;
    }

    /**
     * Convenience method for {@link #getNearestNode(Point, Predicate, boolean, Collection)}.
     *
     * @param p the screen point
     * @param predicate the node must satisfy
     * @param useSelected prefer a selected node
     *
     * @return the nearest node
     */
    public final Node getNearestNode(Point p,
            Predicate<OsmPrimitive> predicate, boolean useSelected) {

        return getNearestNode(p, predicate, useSelected, null);
    }

    /**
     * Convenience method for {@link #getNearestNode(Point, Predicate, boolean, Collection)}.
     * <p>
     * Prefers selected nodes.
     *
     * @param p the screen point
     * @param predicate the node must satisfy
     *
     * @return the nearest node
     */
    public final Node getNearestNode(Point p, Predicate<OsmPrimitive> predicate) {
        return getNearestNode(p, predicate, true, null);
    }

    /**
     * To determine the perpendicular distance from a point {@code pt} to a way
     * segment {@code pt1, pt2} we calculate the height of the triangle
     * {@code pt, pt1, pt2} using the formula:
     * <p>
     *   h^2=b^2-(\frac{-a^2+b^2+c^2}{2c})^2
     * <p>
     * See: <a
     * href="https://en.wikipedia.org/wiki/Heron%27s_formula#Algebraic_proof_using_the_Pythagorean_theorem">Proof
     * of Heron's formula using the Pythagorean theorem</a>.
     * <p>
     * To avoid taking a square root we rewrite the formula as:
     * <p>
     *   h^2 = b^2 - \frac{(-a^2 + b^2 + c^2)^2}{4 c^2}
     * <p>
     * N.B. Floating point math is not associative so we have to be careful
     * about the order of evaluation. The method is public so we can test it.
     *
     * @param aSq length of side a squared
     * @param bSq length of side b squared
     * @param cSq length of way segment squared
     * @return perpendicular distance squared
     */
    public final double Heron(double aSq, double bSq, double cSq) {
        double f = (bSq - aSq) + cSq;    // make order of evaluation explicit
        return bSq - (f * f) / (4 * cSq);
    }

    /**
     * Returns all way segments whose screen representation contains the point {@code p}
     * or whose centerlines are closer to {@code p} than
     * {@code mappaint.segment.snap-distance}. Their ways must not be in {@code ignore},
     * and must satisfy {@code predicate}.  The way segments are returned sorted by the
     * distance of their centerlines to {@code p} in ascending order.
     *
     * @param p the screen point
     * @param ignore ways to ignore
     * @param predicate the ways must satisfy
     * @return the way segments
     */

    public List<WaySegment> getNearestWaySegments(
        Point p, Collection<OsmPrimitive> ignore, Predicate<OsmPrimitive> predicate) {
        DataSet ds = MainApplication.getLayerManager().getActiveDataSet();

        List<Pair<WaySegment, Double>> l = new ArrayList<>();
        if (ds != null) {
            Point2D pt = new Point2D.Double(p.getX(), p.getY());

            for (Way w : ds.searchWays(getBBox(p, searchDistance))) {
                if (predicate != null && !predicate.test(w))
                    continue;
                if (ignore != null && ignore.contains(w))
                    continue;

                double lineWidth = 0d;
                for (StyleElement e : getStyleElementList(w)) {
                    if (e instanceof LineElement) {
                        LineElement le = (LineElement) e;
                        lineWidth = Math.max(lineWidth, le.width);
                    }
                }
                // actually square distance from centerline
                double lineWidthSq = Math.max(lineWidth * lineWidth / 4d, waySnapDistanceSq);

                for (Pair<Node, Node> s : w.getNodePairs(false)) {
                    Point2D pt1 = getPoint2D(s.a);
                    Point2D pt2 = getPoint2D(s.b);

                    double a2 = pt.distanceSq(pt1);
                    double b2 = pt.distanceSq(pt2);
                    double c2 = pt1.distanceSq(pt2);

                    // early out if point is too far away from segment
                    if (a2 <= c2 + lineWidthSq && b2 <= c2 + lineWidthSq) {
                        double distSq = Heron(a2, b2, c2);
                        if (distSq <= lineWidthSq) {
                            WaySegment ws = WaySegment.forNodePair(w, s.a, s.b);
                            l.add(new Pair<>(ws, distSq));
                        }
                    }
                }
            }
            // sort according to distance ascending
            // N.B. we must not sort selected ways in front of unselected
            // or the order in the middle-click-menu will change
            Collections.sort(l, Comparator.comparing(i -> i.b));
        }
        return l.stream().map(i -> i.a).collect(Collectors.toList());
    }

    /**
     * Convenience method for {@link #getNearestWaySegments(Point, Collection, Predicate)}.
     *
     * @param p the screen point
     * @param predicate the way segments must satisfy
     *
     * @return the nearest way segments
     */
    public final List<WaySegment> getNearestWaySegments(Point p, Predicate<OsmPrimitive> predicate) {
        return getNearestWaySegments(p, null, predicate);
    }

    /**
     * Return the nearest way segment to {@code p}.
     * <p>
     * The function first looks for all way segments whose screen representation
     * contains the point {@code p} or whose centerlines are closer to {@code p} than
     * the preference {@code mappaint.segment.snap-distance}.
     * Then the following algorithm is used:
     * <ul>
     *  <li>If {@code preferSelected} is true, prefer the closest selected way segment.
     *      Use case: remove a primitive from a selection.</li>
     *  <li>Else if {@code preferRefs} is not null, prefer the closest way segment
     *      that is referred by any of the primitives in {@code preferRefs}.</li>
     *  <li>Else return the closest way segment.</li>
     *  <li>Else return null.</li>
     * </ul>
     *
     * @param p the screen point
     * @param predicate the way segment must satisfy
     * @param preferSelected if true prefer a selected way segment
     * @param preferRefs if not null prefer way segments related to these primitives
     *
     * @return The nearest way segment
     * @see #getNearestWaySegments(Point, Collection, Predicate)
     * @since 6065
     */
    public final WaySegment getNearestWaySegment(Point p, Predicate<OsmPrimitive> predicate,
            boolean preferSelected, Collection<OsmPrimitive> preferRefs) {

        List<WaySegment> wslist = getNearestWaySegments(p, predicate);
        if (wslist.isEmpty()) return null;

        if (preferSelected) {
            WaySegment result = wslist.stream().filter(ws -> ws.getWay().isSelected()).findFirst().orElse(null);
            if (result != null) return result;
        }
        if (preferRefs != null && !preferRefs.isEmpty()) {
            Set<OsmPrimitive> prefs = new HashSet<>(preferRefs);
            WaySegment result = wslist.stream().filter(
                ws -> prefs.contains(ws.getFirstNode()) ||
                    prefs.contains(ws.getSecondNode()) ||
                    ws.getWay().getReferrers().stream().anyMatch(prefs::contains)
                ).findFirst().orElse(null);
            if (result != null) return result;
        }
        return wslist.stream().findFirst().orElse(null);
    }

    /**
     * Convenience method for {@link #getNearestWaySegment(Point, Predicate, boolean, Collection)}.
     *
     * @param p the screen point
     * @param predicate the way segment must satisfy
     * @param useSelected prefer a selected way segment
     *
     * @return The nearest way segment
     */
    public final WaySegment getNearestWaySegment(Point p,
            Predicate<OsmPrimitive> predicate, boolean useSelected) {

        return getNearestWaySegment(p, predicate, useSelected, null);
    }

    /**
     * Convenience method for {@link #getNearestWaySegment(Point, Predicate, boolean, Collection)}.
     * <p>
     * Prefers selected way segments.
     *
     * @param p the screen point
     * @param predicate the way segment must satisfy
     *
     * @return The nearest way segment
     */
    public final WaySegment getNearestWaySegment(Point p, Predicate<OsmPrimitive> predicate) {
        return getNearestWaySegment(p, predicate, true, null);
    }

    /**
     * Return all ways that are closer to {@code p} than
     * {@code mappaint.segment.snap-distance}, and are not in {@code ignore},
     * and satisfy {@code predicate}, sorted by their distance to {@code p}
     * ascending.
     * <p>
     * For the algorithm see: {@link #getNearestWaySegments(Point, Collection, Predicate)}
     *
     * @param p the screen point
     * @param ignore nodes to ignore
     * @param predicate the ways must satisfy
     *
     * @return the nearest ways
     */
    public final List<Way> getNearestWays(Point p,
            Collection<OsmPrimitive> ignore, Predicate<OsmPrimitive> predicate) {
        return getNearestWaySegments(p, ignore, predicate).stream()
                .map(ws -> ws.getWay())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Convenience method for {@link #getNearestWays(Point, Collection, Predicate)}.
     *
     * @param p the screen point
     * @param predicate the ways must satisfy
     *
     * @return the nearest ways
     */
    public final List<Way> getNearestWays(Point p, Predicate<OsmPrimitive> predicate) {
        return getNearestWays(p, null, predicate);
    }

    /**
     * Return the way nearest to point {@code p} that is closer than
     * {@code mappaint.segment.snap-distance} and satisfies {@code predicate}.
     * Prefer selected ways.
     *
     * @param p the screen point
     * @param predicate the way must satisfy
     *
     * @return the nearest way or null
     * @see #getNearestWaySegment(Point, Predicate, boolean, Collection)
     */
    public final Way getNearestWay(Point p, Predicate<OsmPrimitive> predicate) {
        WaySegment nearestWaySeg = getNearestWaySegment(p, predicate, true, null);
        return (nearestWaySeg == null) ? null : nearestWaySeg.getWay();
    }

    /**
     * The *result* does not depend on the current map selection state,
     * neither does the result *order*.
     * It solely depends on the distance to point p.
     * <p>
     * First, nodes will be searched. If there are nodes within BBox found,
     * return a collection of those nodes only.
     * <p>
     * If no nodes are found, search for nearest ways. If there are ways
     * within BBox found, return a collection of those ways only.
     * <p>
     * If nothing is found, return an empty collection.
     *
     * @param p The point on screen.
     * @param ignore a collection of ways which are not to be returned.
     * @param predicate the returned object has to fulfill certain properties.
     *
     * @return Primitives nearest to the given screen point that are not in ignore.
     * @see #getNearestNodes(Point, Collection, Predicate)
     * @see #getNearestWays(Point, Collection, Predicate)
     */
    public final List<OsmPrimitive> getNearestNodesOrWays(Point p,
            Collection<OsmPrimitive> ignore, Predicate<OsmPrimitive> predicate) {
        List<OsmPrimitive> nearestList = Collections.emptyList();
        OsmPrimitive osm = getNearestNodeOrWay(p, predicate, false);

        if (osm instanceof Node) {
            nearestList = new ArrayList<>(getNearestNodes(p, ignore, predicate));
        } else if (osm instanceof Way) {
            nearestList = new ArrayList<>(getNearestWays(p, ignore, predicate));
        }

        return nearestList;
    }

    /**
     * The *result* does not depend on the current map selection state,
     * neither does the result *order*.
     * It solely depends on the distance to point p.
     *
     * @param p The point on screen.
     * @param predicate the returned object has to fulfill certain properties.
     * @return Primitives nearest to the given screen point.
     * @see #getNearestNodesOrWays(Point, Collection, Predicate)
     */
    public final List<OsmPrimitive> getNearestNodesOrWays(Point p, Predicate<OsmPrimitive> predicate) {
        return getNearestNodesOrWays(p, null, predicate);
    }

    /**
     * Returns true if the given screen point is inside the screen representation of the node.
     *
     * @param p a screen point
     * @param n the node
     * @return true if inside
     */
    private boolean hitTest(Point p, Node n) {
        Point2D screenPosNode = getPoint2D(n);
        for (StyleElement e : getStyleElementList(n)) {
            if (e instanceof NodeElement) {
                NodeElement ne = (NodeElement) e;
                Rectangle r = ne.getBoxProvider().get().getBox();
                r.x += (int) screenPosNode.getX();
                r.y += (int) screenPosNode.getY();
                if (r.contains(p))
                    return true;
            }
        }
        return false;
    }

    /**
     * Return the nearest node or way to a point on the screen.
     *
     * Start by looking for the nearest node. If found and if {@code p} is
     * inside the bbox of this node, return it. Else look for the nearest way
     * and, if found, return it. Finally, if no nearest primitive is found at
     * all, return null.
     *
     * If {@code use_selected} is true, prefer selected primitives and
     * primitives referred by selected primitives over other primitives.
     *
     * @param p The point on screen
     * @param predicate the primitive must satisfy
     * @param useSelected prefer selected primitives and primitives referred by
     *                    selected primitives
     *
     * @return The nearest node or way or null
     * @see #getNearestNode(Point, Predicate, boolean, Collection)
     * @see #getNearestWaySegment(Point, Predicate, boolean, Collection)
     */
    public final OsmPrimitive getNearestNodeOrWay(Point p,
            Predicate<OsmPrimitive> predicate, boolean useSelected) {

        DataSet ds = MainApplication.getLayerManager().getActiveDataSet();
        Collection<OsmPrimitive> sel = (useSelected && ds != null) ? ds.getSelected() : null;

        Node n = getNearestNode(p, predicate, useSelected, sel);
        if (n != null)
            return n;

        WaySegment ws = getNearestWaySegment(p, predicate, useSelected, sel);
        if (ws != null)
            return ws.getWay();

        return null;
    }

    /**
     * Return all primitives that are closest to {@code p}, and are not in
     * {@code ignore}, and satisfy {@code predicate}.
     * <p>
     * Return a list containing:
     * <ul>
     * <li>the closest ways in ascending order of distance from {@code p},
     * <li>then the closest nodes in the same order,
     * <li>then all direct parent relations of those ways and nodes.
     * </ul>
     * <p>
     * Use case: the "middle-click" dialog.
     *
     * @param p the screen point
     * @param ignore primitives to ignore
     * @param predicate the primitives must satisfy
     *
     * @return the primitives
     */
    public final List<OsmPrimitive> getAllNearest(Point p,
            Collection<OsmPrimitive> ignore,
            Predicate<OsmPrimitive> predicate) {

        List<OsmPrimitive> nearestList = new ArrayList<>();
        nearestList.addAll(getNearestWays(p, ignore, predicate));
        nearestList.addAll(getNearestNodes(p, ignore, predicate));

        // add the parent relations
        nearestList.addAll(nearestList.stream()
                .flatMap(o -> o.referrers(Relation.class))
                .filter(predicate)
                .filter(r -> ignore == null || !ignore.contains(r))
                .distinct()
                .collect(Collectors.toList()));

        return nearestList;
    }

    /**
     * Convenience method for {@link #getAllNearest(Point, Collection, Predicate)}.
     *
     * @param p the screen point
     * @param predicate the primitives must satisfy
     *
     * @return the primitives
     */
    public final List<OsmPrimitive> getAllNearest(Point p, Predicate<OsmPrimitive> predicate) {
        return getAllNearest(p, null, predicate);
    }

    /**
     * Returns the projection to be used in calculating stuff.
     * @return The projection to be used in calculating stuff.
     */
    public Projection getProjection() {
        return state.getProjection();
    }

    @Override
    public String helpTopic() {
        String n = getClass().getName();
        return n.substring(n.lastIndexOf('.')+1);
    }

    /**
     * Return an ID which is unique as long as viewport dimensions are the same
     * @return A unique ID, as long as viewport dimensions are the same
     */
    public int getViewID() {
        EastNorth center = getCenter();
        String x = String.valueOf(center.east()) +
                '_' + center.north() +
                '_' + getScale() +
                '_' + getWidth() +
                '_' + getHeight() +
                '_' + getProjection();
        CRC32 id = new CRC32();
        id.update(x.getBytes(StandardCharsets.UTF_8));
        return (int) id.getValue();
    }

    /**
     * Set new cursor.
     * @param cursor The new cursor to use.
     * @param reference A reference object that can be passed to the next set/reset calls to identify the caller.
     */
    public void setNewCursor(Cursor cursor, Object reference) {
        cursorManager.setNewCursor(cursor, reference);
    }

    /**
     * Set new cursor.
     * @param cursor the type of predefined cursor
     * @param reference A reference object that can be passed to the next set/reset calls to identify the caller.
     */
    public void setNewCursor(int cursor, Object reference) {
        setNewCursor(Cursor.getPredefinedCursor(cursor), reference);
    }

    /**
     * Remove the new cursor and reset to previous
     * @param reference Cursor reference
     */
    public void resetCursor(Object reference) {
        cursorManager.resetCursor(reference);
    }

    /**
     * Gets the cursor manager that is used for this NavigatableComponent.
     * @return The cursor manager.
     */
    public CursorManager getCursorManager() {
        return cursorManager;
    }

    /**
     * Get a max scale for projection that describes world in 1/512 of the projection unit
     * @return max scale
     */
    public double getMaxScale() {
        ProjectionBounds world = getMaxProjectionBounds();
        return Math.max(
            world.maxNorth-world.minNorth,
            world.maxEast-world.minEast
        )/512;
    }

    /**
     * Listener for mouse movement events. Used to detect when primitives are being hovered over with the mouse pointer
     * so that registered {@link PrimitiveHoverListener}s can be notified.
     */
    private class PrimitiveHoverMouseListener extends MouseAdapter {
        @Override
        public void mouseMoved(MouseEvent e) {
            OsmPrimitive hovered = getNearestNodeOrWay(e.getPoint(), isSelectablePredicate, true);
            updateHoveredPrimitive(hovered, e);
        }

        @Override
        public void mouseExited(MouseEvent e) {
            updateHoveredPrimitive(null, e);
        }
    }
}
