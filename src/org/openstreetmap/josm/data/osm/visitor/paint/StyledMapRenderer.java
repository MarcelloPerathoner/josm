// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.osm.visitor.paint;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.TexturePaint;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.AbstractButton;
import javax.swing.FocusManager;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.INode;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.IRelation;
import org.openstreetmap.josm.data.osm.IRelationMember;
import org.openstreetmap.josm.data.osm.IWay;
import org.openstreetmap.josm.data.osm.OsmData;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmUtils;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.data.osm.visitor.paint.relations.Multipolygon;
import org.openstreetmap.josm.data.osm.visitor.paint.relations.Multipolygon.PolyData;
import org.openstreetmap.josm.data.osm.visitor.paint.relations.MultipolygonCache;
import org.openstreetmap.josm.data.preferences.AbstractProperty;
import org.openstreetmap.josm.data.preferences.BooleanProperty;
import org.openstreetmap.josm.data.preferences.IntegerProperty;
import org.openstreetmap.josm.data.preferences.StringProperty;
import org.openstreetmap.josm.gui.MapViewState.MapViewPoint;
import org.openstreetmap.josm.gui.NavigatableComponent;
import org.openstreetmap.josm.gui.draw.MapViewPath;
import org.openstreetmap.josm.gui.draw.MapViewPositionAndRotation;
import org.openstreetmap.josm.gui.layer.MapViewGraphics;
import org.openstreetmap.josm.gui.mappaint.ElemStyles;
import org.openstreetmap.josm.gui.mappaint.MapPaintStyles;
import org.openstreetmap.josm.gui.mappaint.StyleElementList;
import org.openstreetmap.josm.gui.mappaint.styleelement.AreaElement;
import org.openstreetmap.josm.gui.mappaint.styleelement.AreaIconElement;
import org.openstreetmap.josm.gui.mappaint.styleelement.BoxTextElement;
import org.openstreetmap.josm.gui.mappaint.styleelement.MapImage;
import org.openstreetmap.josm.gui.mappaint.styleelement.NodeElement;
import org.openstreetmap.josm.gui.mappaint.styleelement.StyleElement;
import org.openstreetmap.josm.gui.mappaint.styleelement.Symbol;
import org.openstreetmap.josm.gui.mappaint.styleelement.TextElement;
import org.openstreetmap.josm.gui.mappaint.styleelement.TextLabel;
import org.openstreetmap.josm.gui.mappaint.styleelement.placement.PositionForAreaStrategy;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Geometry;
import org.openstreetmap.josm.tools.Geometry.AreaAndPerimeter;
import org.openstreetmap.josm.tools.HiDPISupport;
import org.openstreetmap.josm.tools.JosmRuntimeException;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.ShapeClipper;
import org.openstreetmap.josm.tools.Utils;
import org.openstreetmap.josm.tools.bugreport.BugReport;

/**
 * A map renderer which renders a map according to style rules in a set of style sheets.
 * @since 486
 */
public class StyledMapRenderer extends AbstractMapRenderer {
    private static final ExecutorService executorService = Executors.newFixedThreadPool(8, new RenderThreadFactory());
    private static ThreadPoolExecutor renderExecutorService = null;

    private double circum;
    private double scale;

    private MapPaintSettings paintSettings;
    private ElemStyles styles;

    private Color highlightColorTransparent;

    /**
     * Flags used to store the primitive state along with the style. This is the normal style.
     * <p>
     * Not used in any public interfaces.
     */
    static final int FLAG_NORMAL = 0;
    /**
     * A primitive with {@link OsmPrimitive#isDisabled()}
     */
    static final int FLAG_DISABLED = 1;
    /**
     * A primitive with {@link OsmPrimitive#isMemberOfSelected()}
     */
    static final int FLAG_MEMBER_OF_SELECTED = 2;
    /**
     * A primitive with {@link OsmPrimitive#isSelected()}
     */
    static final int FLAG_SELECTED = 4;
    /**
     * A primitive with {@link OsmPrimitive#isOuterMemberOfSelected()}
     */
    static final int FLAG_OUTERMEMBER_OF_SELECTED = 8;

    private static final double PHI = Utils.toRadians(20);
    private static final double COS_PHI = Math.cos(PHI);
    private static final double SIN_PHI = Math.sin(PHI);
    /**
     * If we should use left hand traffic.
     */
    private static final AbstractProperty<Boolean> PREFERENCE_LEFT_HAND_TRAFFIC
            = new BooleanProperty("mappaint.lefthandtraffic", false).cached();
    /**
     * Indicates that the renderer should enable anti-aliasing
     * @since 11758
     */
    public static final AbstractProperty<Boolean> PREFERENCE_ANTIALIASING_USE
            = new BooleanProperty("mappaint.use-antialiasing", true).cached();
    /**
     * The mode that is used for anti-aliasing
     * @since 11758
     */
    public static final AbstractProperty<String> PREFERENCE_TEXT_ANTIALIASING
            = new StringProperty("mappaint.text-antialiasing", "default").cached();

    /**
     * How many threads to use for rendering. 0 = let the system decide. 1 = use the
     * EDT. 2+ use this many threads.
     * @since xxx
     */
    public static final AbstractProperty<Integer> PREFERENCE_RENDER_CONCURRENCY
            = new IntegerProperty("mappaint.render.concurrency", 1).cached();

    /**
     * The line with to use for highlighting
     */
    private static final AbstractProperty<Integer> HIGHLIGHT_LINE_WIDTH = new IntegerProperty("mappaint.highlight.width", 4).cached();
    private static final AbstractProperty<Integer> HIGHLIGHT_POINT_RADIUS = new IntegerProperty("mappaint.highlight.radius", 7).cached();
    private static final AbstractProperty<Integer> WIDER_HIGHLIGHT = new IntegerProperty("mappaint.highlight.bigger-increment", 5).cached();
    private static final AbstractProperty<Integer> HIGHLIGHT_STEP = new IntegerProperty("mappaint.highlight.step", 4).cached();

    private Collection<WaySegment> highlightWaySegments;

    //flag that activate wider highlight mode
    private final boolean useWiderHighlight;

    private boolean useStrokes;
    private boolean showNames;
    private boolean showIcons;
    private boolean isOutlineOnly;

    private boolean leftHandTraffic;
    private Object antialiasing;

    private Supplier<RenderBenchmarkCollector> benchmarkFactory = RenderBenchmarkCollector.defaultBenchmarkSupplier();

    /**
     * Constructs a new {@code StyledMapRenderer}.
     *
     * @param g the graphics context. Must not be null.
     * @param nc the map viewport. Must not be null.
     * @param isInactiveMode if true, the paint visitor shall render OSM objects such that they
     * look inactive. Example: rendering of data in an inactive layer using light gray as color only.
     * @throws IllegalArgumentException if {@code g} is null
     * @throws IllegalArgumentException if {@code nc} is null
     */
    public StyledMapRenderer(NavigatableComponent nc, boolean isInactiveMode) {
        super(nc, isInactiveMode);
        Component focusOwner = FocusManager.getCurrentManager().getFocusOwner();
        useWiderHighlight = !(focusOwner instanceof AbstractButton || focusOwner == nc);
        this.styles = MapPaintStyles.getStyles();
    }

    /**
     * Set the {@link ElemStyles} instance to use for this renderer.
     * @param styles the {@code ElemStyles} instance to use
     */
    public void setStyles(ElemStyles styles) {
        this.styles = styles;
    }

    /**
     * Return the number of threads to use for rendering
     */
    private static int getRenderThreads() {
        int nThreads = PREFERENCE_RENDER_CONCURRENCY.get();
        if (nThreads == 0) {
            nThreads = Runtime.getRuntime().availableProcessors();
        }
        return nThreads;
    }

    /**
     * Returns the executor service to use for rendering
     * <p>
     * Returns null if we should use the EDT.
     *
     * @return the executor service or null
     */
    private static ThreadPoolExecutor getRenderExecutorService() {
        int nThreads = getRenderThreads();
        if (nThreads == 1)
            return null;
        if (renderExecutorService == null) {
            renderExecutorService = new ThreadPoolExecutor(nThreads, nThreads,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new RenderThreadFactory());
        } else {
            renderExecutorService.setCorePoolSize(nThreads);
        }
        return renderExecutorService;
    }

    private void displaySegments(Graphics2D g, MapViewPath path,
            Path2D orientationArrows, Path2D onewayArrows, Path2D onewayArrowsCasing,
            Color color, BasicStroke line, BasicStroke dashes, Color dashedColor) {
        g.setColor(isInactiveMode ? inactiveColor : color);
        if (useStrokes) {
            g.setStroke(line);
        }
        g.draw(path.computeClippedLine(g.getStroke()));

        if (!isInactiveMode && useStrokes && dashes != null) {
            g.setColor(dashedColor);
            g.setStroke(dashes);
            g.draw(path.computeClippedLine(dashes));
        }

        if (orientationArrows != null) {
            g.setColor(isInactiveMode ? inactiveColor : color);
            g.setStroke(new BasicStroke(line.getLineWidth(), line.getEndCap(), BasicStroke.JOIN_MITER, line.getMiterLimit()));
            g.draw(orientationArrows);
        }

        if (onewayArrows != null) {
            g.setStroke(new BasicStroke(1, line.getEndCap(), BasicStroke.JOIN_MITER, line.getMiterLimit()));
            g.fill(onewayArrowsCasing);
            g.setColor(isInactiveMode ? inactiveColor : backgroundColor);
            g.fill(onewayArrows);
        }

        if (useStrokes) {
            g.setStroke(new BasicStroke());
        }
    }

    /**
     * Worker function for drawing areas.
     *
     * @param area the path object for the area that should be drawn; in case
     * of multipolygons, this can path can be a complex shape with one outer
     * polygon and one or more inner polygons
     * @param color The color to fill the area with.
     * @param fillImage The image to fill the area with. Overrides color.
     * @param extent if not null, area will be filled partially; specifies, how
     * far to fill from the boundary towards the center of the area;
     * if null, area will be filled completely
     * @param pfClip clipping area for partial fill (only needed for unclosed
     * polygons)
     * @param disabled If this should be drawn with a special disabled style.
     */
    protected void drawArea(Graphics2D g, MapViewPath area, Color color,
            MapImage fillImage, Float extent, MapViewPath pfClip, boolean disabled) {
        if (!isOutlineOnly && color.getAlpha() != 0) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            if (fillImage == null) {
                if (isInactiveMode) {
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.33f));
                }
                g.setColor(color);
                computeFill(g, area, extent, pfClip, 4);
            } else {
                // TexturePaint requires BufferedImage -> get base image from possible multi-resolution image
                Image img = HiDPISupport.getBaseImage(fillImage.getImage(disabled));
                if (img != null) {
                    g.setPaint(new TexturePaint((BufferedImage) img,
                            new Rectangle(0, 0, fillImage.getWidth(), fillImage.getHeight())));
                } else {
                    Logging.warn("Unable to get image from " + fillImage);
                }
                float alpha = fillImage.getAlphaFloat();
                if (!Utils.equalsEpsilon(alpha, 1f)) {
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                }
                computeFill(g, area, extent, pfClip, 10);
                g.setPaintMode();
            }
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
        }
    }

    /**
     * Fill the given shape. If partial fill is used, computes the clipping.
     * @param shape the given shape
     * @param extent if not null, area will be filled partially; specifies, how
     * far to fill from the boundary towards the center of the area;
     * if null, area will be filled completely
     * @param pfClip clipping area for partial fill (only needed for unclosed
     * polygons)
     * @param mitterLimit parameter for BasicStroke
     *
     */
    private void computeFill(Graphics2D g, Shape shape, Float extent, MapViewPath pfClip, float mitterLimit) {
        if (extent == null) {
            g.fill(shape);
        } else {
            Shape oldClip = g.getClip();
            Shape clip = shape;
            if (pfClip != null) {
                clip = pfClip;
            }
            g.clip(clip);
            g.setStroke(new BasicStroke(2 * extent, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, mitterLimit));
            g.draw(shape);
            g.setClip(oldClip);
            g.setStroke(new BasicStroke());
        }
    }

    /**
     * Draws a multipolygon area.
     * @param r The multipolygon relation
     * @param color The color to fill the area with.
     * @param fillImage The image to fill the area with. Overrides color.
     * @param extent if not null, area will be filled partially; specifies, how
     * far to fill from the boundary towards the center of the area;
     * if null, area will be filled completely
     * @param extentThreshold if not null, determines if the partial filled should
     * be replaced by plain fill, when it covers a certain fraction of the total area
     * @param disabled If this should be drawn with a special disabled style.
     * @since 12285
     */
    public void drawArea(Graphics2D g, Relation r, Color color, MapImage fillImage, Float extent, Float extentThreshold, boolean disabled) {
        Multipolygon multipolygon = MultipolygonCache.getInstance().get(r);
        if (!r.isDisabled() && !multipolygon.getOuterWays().isEmpty()) {
            for (PolyData pd : multipolygon.getCombinedPolygons()) {
                if (!isAreaVisible(pd.get())) {
                    continue;
                }
                MapViewPath p = shapeEastNorthToMapView(pd.get());
                MapViewPath pfClip = null;
                if (extent != null) {
                    if (!usePartialFill(pd.getAreaAndPerimeter(null), extent, extentThreshold)) {
                        extent = null;
                    } else if (!pd.isClosed()) {
                        pfClip = shapeEastNorthToMapView(getPFClip(pd, extent * scale));
                    }
                }
                drawArea(g, p,
                        pd.isSelected() ? paintSettings.getRelationSelectedColor(color.getAlpha()) : color,
                        fillImage, extent, pfClip, disabled);
            }
        }
    }

    /**
     * Convert shape in EastNorth coordinates to MapViewPath and remove invisible parts.
     * For complex shapes this improves performance drastically because the methods in Graphics2D.clip() and Graphics2D.draw() are rather slow.
     * @param shape the shape to convert
     * @return the converted shape
     */
    private MapViewPath shapeEastNorthToMapView(Path2D.Double shape) {
        MapViewPath convertedShape = null;
        if (shape != null) {
            convertedShape = new MapViewPath(mapState);
            convertedShape.appendFromEastNorth(shape);
            convertedShape.setWindingRule(Path2D.WIND_EVEN_ODD);

            Rectangle2D extViewBBox = mapState.getViewClipRectangle().getInView();
            if (!extViewBBox.contains(convertedShape.getBounds2D())) {
                // remove invisible parts of shape
                Path2D.Double clipped = ShapeClipper.clipShape(convertedShape, extViewBBox);
                if (clipped != null) {
                    convertedShape.reset();
                    convertedShape.append(clipped, false);
                }
            }
        }
        return convertedShape;
    }

    /**
     * Draws an area defined by a way. They way does not need to be closed, but it should.
     * @param w The way.
     * @param color The color to fill the area with.
     * @param fillImage The image to fill the area with. Overrides color.
     * @param extent if not null, area will be filled partially; specifies, how
     * far to fill from the boundary towards the center of the area;
     * if null, area will be filled completely
     * @param extentThreshold if not null, determines if the partial filled should
     * be replaced by plain fill, when it covers a certain fraction of the total area
     * @param disabled If this should be drawn with a special disabled style.
     * @since 12285
     */
    public void drawArea(Graphics2D g, IWay<?> w, Color color, MapImage fillImage, Float extent, Float extentThreshold, boolean disabled) {
        MapViewPath pfClip = null;
        if (extent != null) {
            if (!usePartialFill(Geometry.getAreaAndPerimeter(w.getNodes()), extent, extentThreshold)) {
                extent = null;
            } else if (!w.isClosed()) {
                pfClip = shapeEastNorthToMapView(getPFClip(w, extent * scale));
            }
        }
        drawArea(g, getPath(w), color, fillImage, extent, pfClip, disabled);
    }

    /**
     * Determine, if partial fill should be turned off for this object, because
     * only a small unfilled gap in the center of the area would be left.
     * <p>
     * This is used to get a cleaner look for urban regions with many small
     * areas like buildings, etc.
     * @param ap the area and the perimeter of the object
     * @param extent the "width" of partial fill
     * @param threshold when the partial fill covers that much of the total
     * area, the partial fill is turned off; can be greater than 100% as the
     * covered area is estimated as <code>perimeter * extent</code>
     * @return true, if the partial fill should be used, false otherwise
     */
    private boolean usePartialFill(AreaAndPerimeter ap, float extent, Float threshold) {
        if (threshold == null) return true;
        return ap.getPerimeter() * extent * scale < threshold * ap.getArea();
    }

    public boolean getUseStrokes() {
        return useStrokes;
    }

    /**
     * Draw a text onto a node
     * @param n The node to draw the text on
     * @param bs The text and it's alignment.
     */
    public Rectangle getBoxTextBounds(INode n, BoxTextElement bs, Graphics2D g) {
        TextLabel text = bs.text;
        String s = text.getString(n);
        if (Utils.isEmpty(s))
            return null;

        Font defaultFont = g.getFont();
        g.setFont(text.font);

        FontRenderContext frc = g.getFontRenderContext();
        Rectangle2D bounds = text.font.getStringBounds(s, frc);
        LineMetrics metrics = text.font.getLineMetrics(s, frc);
        Point pt = bs.anchor(bounds, metrics);

        MapViewPoint p = mapState.getPointFor(n);
        final MapViewPoint viewPoint = mapState.getForView(p.getInViewX(), p.getInViewY());
        final AffineTransform affineTransform = new AffineTransform();
        affineTransform.setToTranslation(
                Math.round(viewPoint.getInViewX()),
                Math.round(viewPoint.getInViewY()));

        if (text.textTransform != null) {
            affineTransform.concatenate(text.textTransform);
        }
        affineTransform.translate(pt.x, pt.y);
        g.setFont(defaultFont);
        return StyleElement.transformBounds(bounds.getBounds(), affineTransform);
    }

    /**
     * Draw a text onto a node
     * @param n The node to draw the text on
     * @param bs The text and it's alignment.
     */
    public void drawBoxText(Graphics2D g, INode n, BoxTextElement bs) {
        if (!isShowNames() || bs == null)
            return;

        TextLabel text = bs.text;
        String s = text.getString(n);
        if (Utils.isEmpty(s)) return;

        Font defaultFont = g.getFont();
        g.setFont(text.font);

        FontRenderContext frc = g.getFontRenderContext();
        Rectangle2D bounds = text.font.getStringBounds(s, frc);
        LineMetrics metrics = text.font.getLineMetrics(s, frc);
        Point pt = bs.anchor(bounds, metrics);

        MapViewPoint p = mapState.getPointFor(n);
        final MapViewPoint viewPoint = mapState.getForView(p.getInViewX(), p.getInViewY());
        final AffineTransform affineTransform = new AffineTransform();
        affineTransform.setToTranslation(
                Math.round(viewPoint.getInViewX()),
                Math.round(viewPoint.getInViewY()));

        if (text.textTransform != null) {
            affineTransform.concatenate(text.textTransform);
        }
        affineTransform.translate(pt.x, pt.y);
        displayText(g, n, text, s, affineTransform);
        g.setFont(defaultFont);
    }

    @Override
    public void drawNode(Graphics2D g, INode n, Color color, int size, boolean fill) {
        if (size <= 0 && !n.isHighlighted())
            return;

        MapViewPoint p = mapState.getPointFor(n);

        if (n.isHighlighted()) {
            drawPointHighlight(g, p.getInView(), size);
        }

        if (size > 1 && p.isInView()) {
            int radius = size / 2;

            if (isInactiveMode || n.isDisabled()) {
                g.setColor(inactiveColor);
            } else {
                g.setColor(color);
            }
            Rectangle2D rect = new Rectangle2D.Double(p.getInViewX()-radius-1d, p.getInViewY()-radius-1d, size + 1d, size + 1d);
            if (fill) {
                g.fill(rect);
            } else {
                g.draw(rect);
            }
        }
    }

    /**
     * Draw the icon for a given node.
     * @param n The node
     * @param img The icon to draw at the node position
     * @param disabled {@code} true to render disabled version, {@code false} for the standard version
     * @param selected {@code} true to render it as selected, {@code false} otherwise
     * @param member {@code} true to render it as a relation member, {@code false} otherwise
     * @param affineTransform the affine transformation or {@code null}
     */
    public void drawNodeIcon(Graphics2D g, INode n, MapImage img, boolean disabled, boolean selected, boolean member,
            AffineTransform affineTransform) {
        MapViewPoint p = mapState.getPointFor(n);

        int w = img.getWidth();
        int h = img.getHeight();
        if (n.isHighlighted()) {
            drawPointHighlight(g, p.getInView(), Math.max(w, h));
        }

        AffineTransform at = new AffineTransform();
        at.translate(p.getInViewX(), p.getInViewY());
        at.concatenate(affineTransform);

        drawIcon(g, img, disabled, selected, member, at, (gr, r) -> {
            Color color = getSelectionHintColor(disabled, selected);
            gr.setColor(color);
            gr.draw(r);
        });
    }

    /**
     * Draw the icon for a given area. Normally, the icon is drawn around the center of the area.
     * @param osm The primitive to draw the icon for
     * @param img The icon to draw
     * @param disabled {@code} true to render disabled version, {@code false} for the standard version
     * @param selected {@code} true to render it as selected, {@code false} otherwise
     * @param member {@code} true to render it as a relation member, {@code false} otherwise
     * @param theta the angle of rotation in radians
     * @param iconPosition Where to place the icon.
     * @since 11670
     */
    public void drawAreaIcon(Graphics2D g, IPrimitive osm, MapImage img, boolean disabled, boolean selected, boolean member, double theta,
            PositionForAreaStrategy iconPosition) {
        Rectangle2D.Double iconRect = new Rectangle2D.Double(-img.getWidth() / 2.0, -img.getHeight() / 2.0, img.getWidth(), img.getHeight());

        forEachPolygon(osm, path -> {
            MapViewPositionAndRotation placement = iconPosition.findLabelPlacement(path, iconRect);
            if (placement == null) {
                return;
            }
            MapViewPoint p = placement.getPoint();
            AffineTransform affineTransform = new AffineTransform();
            affineTransform.translate(p.getInViewX(), p.getInViewY());
            affineTransform.rotate(theta + placement.getRotation());
            drawIcon(g, img, disabled, selected, member, affineTransform, (gr, r) -> {
                if (useStrokes) {
                    gr.setStroke(new BasicStroke(2));
                }
                // only draw a minor highlighting, so that users do not confuse this for a point.
                Color color = getSelectionHintColor(disabled, selected);
                color = new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (color.getAlpha() * .2));
                gr.setColor(color);
                gr.draw(r);
            });
        });
    }

    public void drawIcon(Graphics2D g, MapImage img, boolean disabled, boolean selected, boolean member,
            AffineTransform affineTransform, BiConsumer<Graphics2D, Rectangle2D> selectionDrawer) {
        float alpha = img.getAlphaFloat();

        Graphics2D temporaryGraphics = (Graphics2D) g.create();
        if (!Utils.equalsEpsilon(alpha, 1f)) {
            temporaryGraphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        }

        temporaryGraphics.transform(affineTransform);
        int drawX = -img.getWidth() / 2 + img.offsetX;
        int drawY = -img.getHeight() / 2 + img.offsetY;
        temporaryGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        temporaryGraphics.drawImage(img.getImage(disabled), drawX, drawY, nc);
        if (selected || member) {
            selectionDrawer.accept(temporaryGraphics, new Rectangle2D.Double(drawX - 2d, drawY - 2d, img.getWidth() + 4d, img.getHeight() + 4d));
        }
    }

    public Color getSelectionHintColor(boolean disabled, boolean selected) {
        Color color;
        if (disabled) {
            color = inactiveColor;
        } else if (selected) {
            color = selectedColor;
        } else {
            color = relationSelectedColor;
        }
        return color;
    }

    /**
     * Draw the symbol and possibly a highlight marking on a given node.
     * @param n The position to draw the symbol on
     * @param s The symbol to draw
     * @param fillColor The color to fill the symbol with
     * @param strokeColor The color to use for the outer corner of the symbol
     */
    public void drawNodeSymbol(Graphics2D g, INode n, Symbol s, Color fillColor, Color strokeColor) {
        MapViewPoint p = mapState.getPointFor(n);

        if (n.isHighlighted()) {
            drawPointHighlight(g, p.getInView(), s.size);
        }

        if (fillColor != null || strokeColor != null) {
            Shape shape = s.buildShapeAround(p.getInViewX(), p.getInViewY());

            if (fillColor != null) {
                g.setColor(fillColor);
                g.fill(shape);
            }
            if (s.stroke != null) {
                g.setStroke(s.stroke);
                g.setColor(strokeColor);
                g.draw(shape);
                g.setStroke(new BasicStroke());
            }
        }
    }

    /**
     * Draw a number of the order of the two consecutive nodes within the
     * parents way
     *
     * @param n1 First node of the way segment.
     * @param n2 Second node of the way segment.
     * @param orderNumber The number of the segment in the way.
     * @param clr The color to use for drawing the text.
     */
    public void drawOrderNumber(Graphics2D g, INode n1, INode n2, int orderNumber, Color clr) {
        MapViewPoint p1 = mapState.getPointFor(n1);
        MapViewPoint p2 = mapState.getPointFor(n2);
        drawOrderNumber(g, p1, p2, orderNumber, clr);
    }

    /**
     * highlights a given GeneralPath using the settings from BasicStroke to match the line's
     * style. Width of the highlight can be changed by user preferences
     * @param path path to draw
     * @param line line style
     */
    private void drawPathHighlight(Graphics2D g, MapViewPath path, BasicStroke line) {
        if (path == null)
            return;
        g.setColor(highlightColorTransparent);
        float w = line.getLineWidth() + paintSettings.adj(HIGHLIGHT_LINE_WIDTH.get());
        if (useWiderHighlight) {
            w += paintSettings.adj(WIDER_HIGHLIGHT.get());
        }
        int step = Math.max(HIGHLIGHT_STEP.get(), 1);
        while (w >= line.getLineWidth()) {
            g.setStroke(new BasicStroke(w, line.getEndCap(), line.getLineJoin(), line.getMiterLimit()));
            g.draw(path);
            w -= step;
        }
    }

    /**
     * highlights a given point by drawing a rounded rectangle around it. Give the
     * size of the object you want to be highlighted, width is added automatically.
     * @param p point
     * @param size highlight size
     */
    private void drawPointHighlight(Graphics2D g, Point2D p, int size) {
        g.setColor(highlightColorTransparent);
        int radius = paintSettings.adj(HIGHLIGHT_POINT_RADIUS.get());
        int s = size + radius;
        if (useWiderHighlight) {
            s += paintSettings.adj(WIDER_HIGHLIGHT.get());
        }
        int step = Math.max(HIGHLIGHT_STEP.get(), 1);
        while (s >= size) {
            int r = (int) Math.floor(s/2d);
            g.fill(new RoundRectangle2D.Double(p.getX()-r, p.getY()-r, s, s, r, r));
            s -= step;
        }
    }

    /**
     * Calculates the position of the icon of a turn restriction
     * @param r The turn restriction relation
     */
    public AffineTransform calcRestrictionTransform(IRelation<?> r) {
        IWay<?> fromWay = null;
        IWay<?> toWay = null;
        IPrimitive via = null;

        /* find the "from", "via" and "to" elements */
        for (IRelationMember<?> m : r.getMembers()) {
            if (m.getMember().isIncomplete())
                return null;
            else {
                if (m.isWay()) {
                    IWay<?> w = (IWay<?>) m.getMember();
                    if (w.getNodesCount() < 2) {
                        continue;
                    }

                    switch(m.getRole()) {
                    case "from":
                        if (fromWay == null) {
                            fromWay = w;
                        }
                        break;
                    case "to":
                        if (toWay == null) {
                            toWay = w;
                        }
                        break;
                    case "via":
                        if (via == null) {
                            via = w;
                        }
                        break;
                    default: // Do nothing
                    }
                } else if (m.isNode()) {
                    INode n = (INode) m.getMember();
                    if (via == null && "via".equals(m.getRole())) {
                        via = n;
                    }
                }
            }
        }

        if (fromWay == null || toWay == null || via == null)
            return null;

        INode viaNode;
        if (via instanceof INode) {
            viaNode = (INode) via;
            if (!fromWay.isFirstLastNode(viaNode))
                return null;
        } else {
            IWay<?> viaWay = (IWay<?>) via;
            INode firstNode = viaWay.firstNode();
            INode lastNode = viaWay.lastNode();
            boolean onewayvia = Boolean.FALSE;

            String onewayviastr = viaWay.get("oneway");
            if (onewayviastr != null) {
                if ("-1".equals(onewayviastr)) {
                    onewayvia = Boolean.TRUE;
                    INode tmp = firstNode;
                    firstNode = lastNode;
                    lastNode = tmp;
                } else {
                    onewayvia = Optional.ofNullable(OsmUtils.getOsmBoolean(onewayviastr)).orElse(Boolean.FALSE);
                }
            }

            if (fromWay.isFirstLastNode(firstNode)) {
                viaNode = firstNode;
            } else if (!onewayvia && fromWay.isFirstLastNode(lastNode)) {
                viaNode = lastNode;
            } else
                return null;
        }

        /* find the "direct" nodes before the via node */
        INode fromNode;
        if (fromWay.firstNode() == via) {
            fromNode = fromWay.getNode(1);
        } else {
            fromNode = fromWay.getNode(fromWay.getNodesCount()-2);
        }

        Point pFrom = nc.getPoint(fromNode);
        Point pVia = nc.getPoint(viaNode);

        /* starting from via, go back the "from" way a few pixels
           (calculate the vector vx/vy with the specified length and the direction
           away from the "via" node along the first segment of the "from" way)
         */
        double distanceFromVia = 14;
        double dx = pFrom.x >= pVia.x ? pFrom.x - pVia.x : pVia.x - pFrom.x;
        double dy = pFrom.y >= pVia.y ? pFrom.y - pVia.y : pVia.y - pFrom.y;

        double fromAngle;
        if (dx == 0) {
            fromAngle = Math.PI/2;
        } else {
            fromAngle = Math.atan(dy / dx);
        }
        double fromAngleDeg = Utils.toDegrees(fromAngle);

        double vx = distanceFromVia * Math.cos(fromAngle);
        double vy = distanceFromVia * Math.sin(fromAngle);

        if (pFrom.x < pVia.x) {
            vx = -vx;
        }
        if (pFrom.y < pVia.y) {
            vy = -vy;
        }

        /* go a few pixels away from the way (in a right angle)
           (calculate the vx2/vy2 vector with the specified length and the direction
           90degrees away from the first segment of the "from" way)
         */
        double distanceFromWay = paintSettings.adj(10);
        double vx2 = 0;
        double vy2 = 0;
        double iconAngle = 0;

        if (pFrom.x >= pVia.x && pFrom.y >= pVia.y) {
            if (!leftHandTraffic) {
                vx2 = distanceFromWay * Math.cos(Utils.toRadians(fromAngleDeg - 90));
                vy2 = distanceFromWay * Math.sin(Utils.toRadians(fromAngleDeg - 90));
            } else {
                vx2 = distanceFromWay * Math.cos(Utils.toRadians(fromAngleDeg + 90));
                vy2 = distanceFromWay * Math.sin(Utils.toRadians(fromAngleDeg + 90));
            }
            iconAngle = 270+fromAngleDeg;
        }
        if (pFrom.x < pVia.x && pFrom.y >= pVia.y) {
            if (!leftHandTraffic) {
                vx2 = distanceFromWay * Math.sin(Utils.toRadians(fromAngleDeg));
                vy2 = distanceFromWay * Math.cos(Utils.toRadians(fromAngleDeg));
            } else {
                vx2 = distanceFromWay * Math.sin(Utils.toRadians(fromAngleDeg + 180));
                vy2 = distanceFromWay * Math.cos(Utils.toRadians(fromAngleDeg + 180));
            }
            iconAngle = 90-fromAngleDeg;
        }
        if (pFrom.x < pVia.x && pFrom.y < pVia.y) {
            if (!leftHandTraffic) {
                vx2 = distanceFromWay * Math.cos(Utils.toRadians(fromAngleDeg + 90));
                vy2 = distanceFromWay * Math.sin(Utils.toRadians(fromAngleDeg + 90));
            } else {
                vx2 = distanceFromWay * Math.cos(Utils.toRadians(fromAngleDeg - 90));
                vy2 = distanceFromWay * Math.sin(Utils.toRadians(fromAngleDeg - 90));
            }
            iconAngle = 90+fromAngleDeg;
        }
        if (pFrom.x >= pVia.x && pFrom.y < pVia.y) {
            if (!leftHandTraffic) {
                vx2 = distanceFromWay * Math.sin(Utils.toRadians(fromAngleDeg + 180));
                vy2 = distanceFromWay * Math.cos(Utils.toRadians(fromAngleDeg + 180));
            } else {
                vx2 = distanceFromWay * Math.sin(Utils.toRadians(fromAngleDeg));
                vy2 = distanceFromWay * Math.cos(Utils.toRadians(fromAngleDeg));
            }
            iconAngle = 270-fromAngleDeg;
        }

        AffineTransform affineTransform = new AffineTransform();
        affineTransform.translate(pVia.x + vx + vx2, pVia.y + vy + vy2);
        affineTransform.rotate(Math.toRadians(iconAngle));
        return affineTransform;
    }

    /**
     * Draws a text for the given primitive
     * @param osm The primitive to draw the text for
     * @param text The text definition (font/position/.../text content) to draw
     * @param labelPositionStrategy The position of the text
     * @since 11722
     */
    public void drawText(Graphics2D g, IPrimitive osm, TextLabel text, PositionForAreaStrategy labelPositionStrategy) {
        if (!isShowNames()) {
            return;
        }
        String name = text.getString(osm);
        if (Utils.isEmpty(name)) {
            return;
        }

        FontMetrics fontMetrics = g.getFontMetrics(text.font); // if slow, use cache
        Rectangle2D stringBounds = fontMetrics.getStringBounds(name, g); // if slow, approximate by strlen()*maxcharbounds(font)

        Font defaultFont = g.getFont();
        forEachPolygon(osm, path -> {
            //TODO: Ignore areas that are out of bounds.
            PositionForAreaStrategy position = labelPositionStrategy;
            MapViewPositionAndRotation center = position.findLabelPlacement(path, stringBounds);
            if (center != null) {
                displayText(g, osm, text, name, getDisplayTextTransform(stringBounds, center));
            } else if (position.supportsGlyphVector()) {
                List<GlyphVector> gvs = Utils.getGlyphVectorsBidi(name, text.font, g.getFontRenderContext());
                List<GlyphVector> translatedGvs = position.generateGlyphVectors(path, stringBounds, gvs);
                displayText(g,
                    () -> translatedGvs.forEach(gv -> g.drawGlyphVector(gv, 0, 0)),
                    () -> translatedGvs.stream().collect(
                                Path2D.Double::new,
                                (p, gv) -> p.append(gv.getOutline(0, 0), false),
                                (p1, p2) -> p1.append(p2, false)),
                    osm.isDisabled(),
                    text
                );
            } else {
                Logging.trace("Couldn't find a correct label placement for {0} / {1}", osm, name);
            }
        });
        g.setFont(defaultFont);
    }

    public AffineTransform getDisplayTextTransform(Rectangle2D stringBounds, MapViewPositionAndRotation center) {
        AffineTransform at = new AffineTransform();
        if (Math.abs(center.getRotation()) < .01) {
            // Explicitly no rotation: move to full pixels.
            at.setToTranslation(
                    Math.round(center.getPoint().getInViewX() - stringBounds.getCenterX()),
                    Math.round(center.getPoint().getInViewY() - stringBounds.getCenterY()));
        } else {
            at.setToTranslation(
                    center.getPoint().getInViewX(),
                    center.getPoint().getInViewY());
            at.rotate(center.getRotation());
            at.translate(-stringBounds.getCenterX(), -stringBounds.getCenterY());
        }
        return at;
    }

    private void displayText(Graphics2D g, IPrimitive osm, TextLabel text, String name, AffineTransform at) {
        displayText(g,
            () -> {
                AffineTransform defaultTransform = g.getTransform();
                g.transform(at);
                g.setFont(text.font);
                g.drawString(name, 0, 0);
                g.setTransform(defaultTransform);
            },
            () -> {
                FontRenderContext frc = g.getFontRenderContext();
                TextLayout tl = new TextLayout(name, text.font, frc);
                return tl.getOutline(at);
            },
            osm.isDisabled(),
            text
        );
    }

    /**
     * Displays text at specified position including its halo, if applicable.
     *
     * @param fill The function that fills the text
     * @param outline The function to draw the outline
     * @param disabled {@code true} if element is disabled (filtered out)
     * @param text text style to use
     */
    private void displayText(Graphics2D g, Runnable fill, Supplier<Shape> outline, boolean disabled, TextLabel text) {
        if (isInactiveMode || disabled) {
            g.setColor(inactiveColor);
            fill.run();
        } else if (text.haloRadius != null) {
            g.setStroke(new BasicStroke(2*text.haloRadius, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
            g.setColor(text.haloColor);
            Shape textOutline = outline.get();
            g.draw(textOutline);
            g.setStroke(new BasicStroke());
            g.setColor(text.color);
            g.fill(textOutline);
        } else {
            g.setColor(text.color);
            fill.run();
        }
    }

    /**
     * Calls a consumer for each path of the area shape-
     * @param osm A way or a multipolygon
     * @param consumer The consumer to call.
     */
    public void forEachPolygon(IPrimitive osm, Consumer<MapViewPath> consumer) {
        if (osm instanceof IWay) {
            consumer.accept(getPath((IWay<?>) osm));
        } else if (osm instanceof Relation) {
            Multipolygon multipolygon = MultipolygonCache.getInstance().get((Relation) osm);
            if (!multipolygon.getOuterWays().isEmpty()) {
                for (PolyData pd : multipolygon.getCombinedPolygons()) {
                    MapViewPath path = new MapViewPath(mapState);
                    path.appendFromEastNorth(pd.get());
                    path.setWindingRule(MapViewPath.WIND_EVEN_ODD);
                    consumer.accept(path);
                }
            }
        }
    }

    /**
     * draw way. This method allows for two draw styles (line using color, dashes using dashedColor) to be passed.
     * @param way The way to draw
     * @param color The base color to draw the way in
     * @param line The line style to use. This is drawn using color.
     * @param dashes The dash style to use. This is drawn using dashedColor. <code>null</code> if unused.
     * @param dashedColor The color of the dashes.
     * @param offset The offset
     * @param showOrientation show arrows that indicate the technical orientation of
     *              the way (defined by order of nodes)
     * @param showHeadArrowOnly True if only the arrow at the end of the line but not those on the segments should be displayed.
     * @param showOneway show symbols that indicate the direction of the feature,
     *              e.g. oneway street or waterway
     * @param onewayReversed for oneway=-1 and similar
     */
    public void drawWay(Graphics2D g, IWay<?> way, Color color, BasicStroke line, BasicStroke dashes, Color dashedColor, float offset,
            boolean showOrientation, boolean showHeadArrowOnly,
            boolean showOneway, boolean onewayReversed) {

        MapViewPath path = new MapViewPath(mapState);
        MapViewPath orientationArrows = showOrientation ? new MapViewPath(mapState) : null;
        MapViewPath onewayArrows;
        MapViewPath onewayArrowsCasing;

        List<? extends INode> wayNodes = way.getNodes();
        if (wayNodes.size() < 2) return;

        // only highlight the segment if the way itself is not highlighted
        if (!way.isHighlighted() && highlightWaySegments != null) {
            MapViewPath highlightSegs = null;
            for (WaySegment ws : highlightWaySegments) {
                if (ws.getWay() != way || ws.getLowerIndex() < offset || !ws.isUsable()) {
                    continue;
                }
                if (highlightSegs == null) {
                    highlightSegs = new MapViewPath(mapState);
                }

                highlightSegs.moveTo(ws.getFirstNode());
                highlightSegs.lineTo(ws.getSecondNode());
            }

            drawPathHighlight(g, highlightSegs, line);
        }

        Iterator<MapViewPoint> it = new OffsetIterator(mapState, wayNodes, offset);
        ArrowPaintHelper drawArrowHelper = null;
        double minSegmentLenSq = 0;
        if (showOrientation) {
            drawArrowHelper = new ArrowPaintHelper(PHI, 10 + line.getLineWidth());
            minSegmentLenSq = Math.pow(drawArrowHelper.getOnLineLength() * 1.3, 2);
        }
        MapViewPoint p1 = it.next();
        path.moveTo(p1);
        while (it.hasNext()) {
            MapViewPoint p2 = it.next();
            path.lineTo(p2);

            /* draw arrow */
            if (drawArrowHelper != null) {
                final boolean drawArrow;
                if (way.isSelected()) {
                    // always draw last arrow - no matter how short the segment is
                    drawArrow = !it.hasNext() || p1.distanceToInViewSq(p2) > minSegmentLenSq;
                } else {
                    // not selected: only draw arrow when it fits
                    drawArrow = (!showHeadArrowOnly || !it.hasNext()) && p1.distanceToInViewSq(p2) > minSegmentLenSq;
                }
                if (drawArrow) {
                    drawArrowHelper.paintArrowAt(orientationArrows, p2, p1);
                }
            }
            p1 = p2;
        }
        if (showOneway) {
            onewayArrows = new MapViewPath(mapState);
            onewayArrowsCasing = new MapViewPath(mapState);
            double interval = 60;

            path.visitClippedLine(60, (inLineOffset, start, end, startIsOldEnd) -> {
                double segmentLength = start.distanceToInView(end);
                if (segmentLength > 0.001) {
                    final double nx = (end.getInViewX() - start.getInViewX()) / segmentLength;
                    final double ny = (end.getInViewY() - start.getInViewY()) / segmentLength;

                    // distance from p1
                    double dist = interval - (inLineOffset % interval);

                    while (dist < segmentLength) {
                        appendOnewayPath(onewayReversed, start, nx, ny, dist, 3d, onewayArrowsCasing);
                        appendOnewayPath(onewayReversed, start, nx, ny, dist, 2d, onewayArrows);
                        dist += interval;
                    }
                }
            });
        } else {
            onewayArrows = null;
            onewayArrowsCasing = null;
        }

        if (way.isHighlighted()) {
            drawPathHighlight(g, path, line);
        }
        displaySegments(g, path, orientationArrows, onewayArrows, onewayArrowsCasing, color, line, dashes, dashedColor);
    }

    private static void appendOnewayPath(boolean onewayReversed, MapViewPoint p1, double nx, double ny, double dist,
            double onewaySize, Path2D onewayPath) {
        // scale such that border is 1 px
        final double fac = -(onewayReversed ? -1 : 1) * onewaySize * (1 + SIN_PHI) / (SIN_PHI * COS_PHI);
        final double sx = nx * fac;
        final double sy = ny * fac;

        // Attach the triangle at the incenter and not at the tip.
        // Makes the border even at all sides.
        final double x = p1.getInViewX() + nx * (dist + (onewayReversed ? -1 : 1) * (onewaySize / SIN_PHI));
        final double y = p1.getInViewY() + ny * (dist + (onewayReversed ? -1 : 1) * (onewaySize / SIN_PHI));

        onewayPath.moveTo(x, y);
        onewayPath.lineTo(x + COS_PHI * sx - SIN_PHI * sy, y + SIN_PHI * sx + COS_PHI * sy);
        onewayPath.lineTo(x + COS_PHI * sx + SIN_PHI * sy, y - SIN_PHI * sx + COS_PHI * sy);
        onewayPath.lineTo(x, y);
    }

    /**
     * Gets the "circum". This is the distance on the map in meters that 100 screen pixels represent.
     * @return The "circum"
     */
    public double getCircum() {
        return circum;
    }

    @Override
    public void getColors() {
        super.getColors();
        this.highlightColorTransparent = new Color(highlightColor.getRed(), highlightColor.getGreen(), highlightColor.getBlue(), 100);
        this.backgroundColor = styles.getBackgroundColor();
    }

    @Override
    public void getSettings(Graphics2D g, boolean virtual) {
        super.getSettings(g, virtual);
        paintSettings = MapPaintSettings.INSTANCE;

        circum = nc.getDist100Pixel();
        scale = nc.getScale();

        leftHandTraffic = PREFERENCE_LEFT_HAND_TRAFFIC.get();

        useStrokes = paintSettings.getUseStrokesDistance() > circum;
        showNames = paintSettings.getShowNamesDistance() > circum;
        showIcons = paintSettings.getShowIconsDistance() > circum;
        isOutlineOnly = paintSettings.isOutlineOnly();

        antialiasing = Boolean.TRUE.equals(PREFERENCE_ANTIALIASING_USE.get()) ?
                        RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);

        Object textAntialiasing;
        switch (PREFERENCE_TEXT_ANTIALIASING.get()) {
            case "on":
                textAntialiasing = RenderingHints.VALUE_TEXT_ANTIALIAS_ON;
                break;
            case "off":
                textAntialiasing = RenderingHints.VALUE_TEXT_ANTIALIAS_OFF;
                break;
            case "gasp":
                textAntialiasing = RenderingHints.VALUE_TEXT_ANTIALIAS_GASP;
                break;
            case "lcd-hrgb":
                textAntialiasing = RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB;
                break;
            case "lcd-hbgr":
                textAntialiasing = RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HBGR;
                break;
            case "lcd-vrgb":
                textAntialiasing = RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_VRGB;
                break;
            case "lcd-vbgr":
                textAntialiasing = RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_VBGR;
                break;
            default:
                textAntialiasing = RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT;
        }
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, textAntialiasing);
    }

    private MapViewPath getPath(IWay<?> w) {
        MapViewPath path = new MapViewPath(mapState);
        if (w.isClosed()) {
            path.appendClosed(w.getNodes(), false);
        } else {
            path.append(w.getNodes(), false);
        }
        return path;
    }

    private static Path2D.Double getPFClip(IWay<?> w, double extent) {
        Path2D.Double clip = new Path2D.Double();
        buildPFClip(clip, w.getNodes(), extent);
        return clip;
    }

    private static Path2D.Double getPFClip(PolyData pd, double extent) {
        Path2D.Double clip = new Path2D.Double();
        clip.setWindingRule(Path2D.WIND_EVEN_ODD);
        buildPFClip(clip, pd.getNodes(), extent);
        for (PolyData pdInner : pd.getInners()) {
            buildPFClip(clip, pdInner.getNodes(), extent);
        }
        return clip;
    }

    /**
     * Fix the clipping area of unclosed polygons for partial fill.
     * <p>
     * The current algorithm for partial fill simply strokes the polygon with a
     * large stroke width after masking the outside with a clipping area.
     * This works, but for unclosed polygons, the mask can crop the corners at
     * both ends (see #12104).
     * <p>
     * This method fixes the clipping area by sort of adding the corners to the
     * clip outline.
     *
     * @param clip the clipping area to modify (initially empty)
     * @param nodes nodes of the polygon
     * @param extent the extent
     */
    private static void buildPFClip(Path2D.Double clip, List<? extends INode> nodes, double extent) {
        boolean initial = true;
        for (INode n : nodes) {
            EastNorth p = n.getEastNorth();
            if (p != null) {
                if (initial) {
                    clip.moveTo(p.getX(), p.getY());
                    initial = false;
                } else {
                    clip.lineTo(p.getX(), p.getY());
                }
            }
        }
        if (nodes.size() >= 3) {
            EastNorth fst = nodes.get(0).getEastNorth();
            EastNorth snd = nodes.get(1).getEastNorth();
            EastNorth lst = nodes.get(nodes.size() - 1).getEastNorth();
            EastNorth lbo = nodes.get(nodes.size() - 2).getEastNorth();

            EastNorth cLst = getPFDisplacedEndPoint(lbo, lst, fst, extent);
            EastNorth cFst = getPFDisplacedEndPoint(snd, fst, cLst != null ? cLst : lst, extent);
            if (cLst == null && cFst != null) {
                cLst = getPFDisplacedEndPoint(lbo, lst, cFst, extent);
            }
            if (cLst != null) {
                clip.lineTo(cLst.getX(), cLst.getY());
            }
            if (cFst != null) {
                clip.lineTo(cFst.getX(), cFst.getY());
            }
        }
    }

    /**
     * Get the point to add to the clipping area for partial fill of unclosed polygons.
     * <p>
     * <code>(p1,p2)</code> is the first or last way segment and <code>p3</code> the
     * opposite endpoint.
     *
     * @param p1 1st point
     * @param p2 2nd point
     * @param p3 3rd point
     * @param extent the extent
     * @return a point q, such that p1,p2,q form a right angle
     * and the distance of q to p2 is <code>extent</code>. The point q lies on
     * the same side of the line p1,p2 as the point p3.
     * Returns null if p1,p2,p3 forms an angle greater 90 degrees. (In this case
     * the corner of the partial fill would not be cut off by the mask, so an
     * additional point is not necessary.)
     */
    private static EastNorth getPFDisplacedEndPoint(EastNorth p1, EastNorth p2, EastNorth p3, double extent) {
        double dx1 = p2.getX() - p1.getX();
        double dy1 = p2.getY() - p1.getY();
        double dx2 = p3.getX() - p2.getX();
        double dy2 = p3.getY() - p2.getY();
        if (dx1 * dx2 + dy1 * dy2 < 0) {
            double len = Math.sqrt(dx1 * dx1 + dy1 * dy1);
            if (len == 0) return null;
            double dxm = -dy1 * extent / len;
            double dym = dx1 * extent / len;
            if (dx1 * dy2 - dx2 * dy1 < 0) {
                dxm = -dxm;
                dym = -dym;
            }
            return new EastNorth(p2.getX() + dxm, p2.getY() + dym);
        }
        return null;
    }

    /**
     * Test if the area is visible
     * @param area The area, interpreted in east/north space.
     * @return true if it is visible.
     */
    private boolean isAreaVisible(Path2D.Double area) {
        Rectangle2D bounds = area.getBounds2D();
        if (bounds.isEmpty()) return false;
        MapViewPoint p = mapState.getPointFor(new EastNorth(bounds.getX(), bounds.getY()));
        if (p.getInViewY() < 0 || p.getInViewX() > mapState.getViewWidth()) return false;
        p = mapState.getPointFor(new EastNorth(bounds.getX() + bounds.getWidth(), bounds.getY() + bounds.getHeight()));
        return p.getInViewX() >= 0 && p.getInViewY() <= mapState.getViewHeight();
    }

    /**
     * Determines if the paint visitor shall render OSM objects such that they look inactive.
     * @return {@code true} if the paint visitor shall render OSM objects such that they look inactive
     */
    public boolean isInactiveMode() {
        return isInactiveMode;
    }

    /**
     * Check if icons should be rendered
     * @return <code>true</code> to display icons
     */
    public boolean isShowIcons() {
        return showIcons;
    }

    /**
     * Test if names should be rendered
     * @return <code>true</code> to display names
     */
    public boolean isShowNames() {
        return showNames && doSlowOperations;
    }

    /**
     * Computes the flags for a given OSM primitive.
     * @param primitive The primititve to compute the flags for.
     * @param checkOuterMember <code>true</code> if we should also add {@link #FLAG_OUTERMEMBER_OF_SELECTED}
     * @return The flag.
     * @since 13676 (signature)
     */
    public static int computeFlags(IPrimitive primitive, boolean checkOuterMember) {
        if (primitive.isDisabled()) {
            return FLAG_DISABLED;
        } else if (primitive.isSelected()) {
            return FLAG_SELECTED;
        } else if (checkOuterMember && primitive.isOuterMemberOfSelected()) {
            return FLAG_OUTERMEMBER_OF_SELECTED;
        } else if (primitive.isMemberOfSelected()) {
            return FLAG_MEMBER_OF_SELECTED;
        } else {
            return FLAG_NORMAL;
        }
    }

    /**
     * Sets the factory that creates the benchmark data receivers.
     * @param benchmarkFactory The factory.
     * @since 10697
     */
    public void setBenchmarkFactory(Supplier<RenderBenchmarkCollector> benchmarkFactory) {
        this.benchmarkFactory = benchmarkFactory;
    }

    /**
     * Splits a {@link MapViewGraphics} screen into subscreens
     * <p>
     * Each MapViewGraphics will get ist own Graphics2D and clip region into the buffer
     * image.
     *
     * @param mvGraphics the MapViewGraphics to split
     * @param count the number of subscreens
     * @return a list of MapViewGraphics
     */
    private Collection<MapViewGraphics> splitMapViewGraphics(MapViewGraphics mvGraphics, int count) {
        double exp = Math.log(count) / Math.log(2d);
        int vCount = (int) Math.round(Math.pow(2d, Math.floor(exp / 2)));
        int hCount = count / vCount;
        Rectangle r = mvGraphics.getBounds();
        Collection<MapViewGraphics> l = new ArrayList<>();
        int dx = r.width / hCount;
        int dy = r.height / vCount;
        // Logging.info("StyledMapRenderer.splitMapViewGraphics: splitting the screen into {0}x{1} regions", hCount, vCount);
        // Logging.info("StyledMapRenderer.splitMapViewGraphics: with size {0}x{1}", dx, dy);

        int x = r.x;
        for (int i = 0; i < hCount; ++i) {
            int y = r.y;
            for (int j = 0; j < vCount; ++j) {
                BufferedImage buffer = mvGraphics.getBuffer();
                // A new Graphics2D for each thread!!!
                Graphics2D g = buffer.createGraphics();
                g.setClip(x, y, dx, dy);
                l.add(new MapViewGraphics(
                    buffer,
                    g,
                    new Rectangle(x, y, dx, dy)
                ));
                y += dy;
            }
            x += dx;
        }
        return l;
    }

    Envelope toEnvelope(Rectangle r) {
        return new Envelope(r.x, r.x + r.width, r.y, r.y + r.height);
    }

    @Override
    public void render(final OsmData<?, ?, ?, ?> data, boolean renderVirtualNodes, MapViewGraphics mvGraphics) {
        // Logging.info("StyledMapRenderer.render: {0}", mvGraphics.toString());
        RenderBenchmarkCollector benchmark = benchmarkFactory.get();
        getSettings(mvGraphics.getDefaultGraphics(), renderVirtualNodes);
        try {
            Lock readLock = data.getReadLock();
            if (readLock.tryLock(1, TimeUnit.SECONDS)) {
                try {
                    paintWithLock(data, renderVirtualNodes, benchmark, mvGraphics);
                } finally {
                    readLock.unlock();
                }
            } else {
                Logging.warn("Cannot paint layer {0}: It is locked.");
            }
        } catch (InterruptedException e) {
            Logging.warn("Cannot paint layer {0}: Interrupted");
        }
    }

    private void paintWithLock(final OsmData<?, ?, ?, ?> data, boolean renderVirtualNodes, RenderBenchmarkCollector benchmark,
            MapViewGraphics mvGraphics) {
        try {
            // Logging.info("StyledMapRenderer.paintWithLock: {0}", mvGraphics.toString());
            BufferedImage buffer = mvGraphics.getBuffer();
            boolean drawArea = circum <= Config.getPref().getInt("mappaint.fillareas", 10_000_000);
            boolean drawMultipolygon = drawArea && Config.getPref().getBoolean("mappaint.multipolygon", true);
            boolean drawRestriction = Config.getPref().getBoolean("mappaint.restriction", true);
            styles.setDrawMultipolygon(drawMultipolygon);

            highlightWaySegments = data.getHighlightedWaySegments();

            benchmark.renderStart(circum);

            ExecutorService es = getRenderExecutorService();
            final Collection<StyleRecord> allStyleElems = (es == null) ? new ConcurrentSkipListSet<>() : null;
            final Quadtree quadtree = (es != null) ? new Quadtree() : null;
            final BBox bbox = mapState.getForView(mvGraphics.getBounds()).getLatLonBoundsBox().toBBox();

            List<Future<?>> futureList = new ArrayList<>();
            ThreadLocal<Graphics2D> threadLocalG = ThreadLocal.withInitial(buffer::createGraphics);

            for (IRelation<?> relation : data.searchRelations(bbox)) {
                if (relation.isDrawable()) {
                    futureList.add(executorService.submit(() -> {
                        int flags = StyledMapRenderer.computeFlags(relation, false);
                        StyleElementList sl = styles.get(relation, circum, nc);
                        for (StyleElement s : sl) {
                            if ((drawMultipolygon
                                        && drawArea
                                        && (s instanceof AreaElement || s instanceof AreaIconElement)
                                        && (flags & StyledMapRenderer.FLAG_DISABLED) == 0)
                                    || (drawMultipolygon && drawArea && s instanceof TextElement)
                                    || (drawRestriction && s instanceof NodeElement)) {
                                StyleRecord sr = new StyleRecord(s, relation, flags);
                                Rectangle bounds = sr.getBounds(paintSettings, this, threadLocalG.get());
                                if (bounds != null) {
                                    if (quadtree != null) {
                                        synchronized(quadtree) {
                                            quadtree.insert(toEnvelope(bounds), sr);
                                        }
                                    }
                                    if (allStyleElems != null) {
                                        allStyleElems.add(sr);
                                    }
                                }
                            }
                        }
                        return 0;
                    }));
                }
            }
            for (IWay<?> way : data.searchWays(bbox)) {
                if (way.isDrawable()) {
                    futureList.add(executorService.submit(() -> {
                        int flags = StyledMapRenderer.computeFlags(way, false);
                        StyleElementList sl = styles.get(way, circum, nc);
                        for (StyleElement s : sl) {
                            if ((drawArea && (flags & StyledMapRenderer.FLAG_DISABLED) == 0) || !(s instanceof AreaElement)) {
                                StyleRecord sr = new StyleRecord(s, way, flags);
                                Rectangle bounds = sr.getBounds(paintSettings, this, threadLocalG.get());
                                if (bounds != null) {
                                    if (quadtree != null) {
                                        synchronized(quadtree) {
                                            quadtree.insert(toEnvelope(bounds), sr);
                                        }
                                    }
                                    if (allStyleElems != null) {
                                        allStyleElems.add(sr);
                                    }
                                }
                            }
                        }
                        return 0;
                    }));
                }
            }
            for (INode node : data.searchNodes(bbox)) {
                if (node.isDrawable()) {
                    futureList.add(executorService.submit(() -> {
                        int flags = StyledMapRenderer.computeFlags(node, false);
                        StyleElementList sl = styles.get(node, circum, nc);
                        for (StyleElement s : sl) {
                            StyleRecord sr = new StyleRecord(s, node, flags);
                            Rectangle bounds = sr.getBounds(paintSettings, this, threadLocalG.get());
                            if (bounds != null) {
                                if (quadtree != null) {
                                    synchronized(quadtree) {
                                        quadtree.insert(toEnvelope(bounds), sr);
                                    }
                                }
                                if (allStyleElems != null) {
                                    allStyleElems.add(sr);
                                }
                            }
                        }
                        return 0;
                    }));
                }
            }

            // We have to wait until everybody is done because we need to paint starting
            // with the artifact with the lowest z-order.
            for (Future<?> f : futureList) {
                if (!f.isDone()) {
                    try { f.get(); }
                    catch (CancellationException | ExecutionException ex) {
                        Logging.error(ex);
                    }
                }
            }
            futureList.clear();

            if (!benchmark.renderSort()) {
                return;
            }
            if (!benchmark.renderDraw(allStyleElems)) {
                return;
            }

            if (allStyleElems != null) {
                // Render in the EDT for wimps
                // Logging.info("StyledMapRenderer.paintWithLock: rendering in the EDT");
                Graphics2D g = buffer.createGraphics();
                for (StyleRecord styleRecord : allStyleElems) {
                    styleRecord.paintPrimitive(paintSettings, this, g);
                }
            }
            if (quadtree != null) {
                // Here we use multiple non-EDT threads to render into the {@link
                // BufferedImage}. What? Don't you know swing is not thread-safe?
                // <p>
                // Each thread uses its own {@link Graphics2D}, so we can guarantee that
                // no two threads will ever use a single Graphics2D at the same time.
                // This keeps us from crashing.
                // <p>
                // Because we cannot enforce data visibility without synchronization,
                // and synchronizing the whole buffer image would lose much of the speed
                // gained by concurrency, we must give each thread a different
                // rectangular region of the buffer to paint.
                // <p>
                // But that brings other problems, like primitives whose extent on
                // screen is different from what their lat/lon bounds would suggest, ie.
                // a node's text is bigger than a point and area icons are smaller than
                // the area itself. We must calculate the screen bounds of each style in
                // advance and use them to decide in which screen regions the style
                // element needs to be drawn.

                AtomicInteger elementsDrawn = new AtomicInteger();

                for (MapViewGraphics mvg : splitMapViewGraphics(mvGraphics, getRenderThreads())) {
                    futureList.add(executorService.submit(() -> {
                        Rectangle clipBounds = mvg.getBounds();
                        Graphics2D g = mvg.getDefaultGraphics();
                        List<StyleRecord> l = quadtree.query(toEnvelope(clipBounds));
                        l.stream().filter(sr -> sr.intersects(clipBounds)).sorted().forEach(styleRecord -> {
                            styleRecord.paintPrimitive(paintSettings, this, g);
                            elementsDrawn.incrementAndGet();
                        });
                    }));
                }
                for (Future<?> f : futureList) {
                    if (!f.isDone()) {
                        try {
                            f.get();
                        }
                        catch (CancellationException | ExecutionException ex) {
                            Logging.error(ex);
                        }
                    }
                }
                futureList.clear();
                Logging.info("Elements in quadtree: {0}", quadtree.size());
                Logging.info("Elements drawn: {0}", elementsDrawn.get());
            }
            benchmark.renderDone();

            if (renderVirtualNodes)
                drawVirtualNodes(mvGraphics.getDefaultGraphics(), data, bbox);

        } catch (InterruptedException | JosmRuntimeException | IllegalArgumentException | IllegalStateException e) {
            throw BugReport.intercept(e)
                    .put("data", data)
                    .put("circum", circum)
                    .put("scale", scale)
                    .put("paintSettings", paintSettings)
                    .put("renderVirtualNodes", renderVirtualNodes);
        }
    }

    /**
     * The render thread factory.
     * <p>
     * A render thread keeps its own copy of the Graphics context and accesses it
     * sequentially.
     */
    private static class RenderThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        private class RenderThread extends Thread {
            RenderThread(Runnable r, String name) {
                super(r, name);
                setDaemon(true);
                setPriority(Thread.NORM_PRIORITY);
            }
        }

        public Thread newThread(Runnable r) {
            return new RenderThread(r, "StyledMapRenderer-" + threadNumber.getAndIncrement());
        }
    }
}
