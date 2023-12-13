// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint.styleelement;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.Objects;

import org.openstreetmap.josm.data.osm.INode;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.IRelation;
import org.openstreetmap.josm.data.osm.visitor.paint.MapPaintSettings;
import org.openstreetmap.josm.data.osm.visitor.paint.StyledMapRenderer;
import org.openstreetmap.josm.gui.draw.SymbolShape;
import org.openstreetmap.josm.gui.mappaint.Cascade;
import org.openstreetmap.josm.gui.mappaint.Environment;
import org.openstreetmap.josm.gui.mappaint.Keyword;
import org.openstreetmap.josm.gui.mappaint.MapPaintStyles.IconReference;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.CheckParameterUtil;
import org.openstreetmap.josm.tools.ColorHelper;
import org.openstreetmap.josm.tools.Utils;

/**
 * applies for Nodes and turn restriction relations
 */
public class NodeElement extends StyleElement {
    /**
     * The image that should be drawn or {@code null}.
     */
    public final MapImage mapImage;
    /**
     * The symbol that should be drawn or {@code null}.
     */
    public final Symbol symbol;

    /** {@code icon-rotation} * {@code icon-transform} */
    public final AffineTransform iconTransform;

    private static final String[] ICON_KEYS = {ICON_IMAGE, ICON_WIDTH, ICON_HEIGHT, ICON_OPACITY, ICON_OFFSET_X, ICON_OFFSET_Y};

    protected NodeElement(Cascade c, MapImage mapImage, Symbol symbol, float defaultMajorZindex,
            AffineTransform iconTransform) {
        super(c, defaultMajorZindex);
        this.mapImage = mapImage;
        this.symbol = symbol;
        this.iconTransform = iconTransform;
    }

    /**
     * Creates a new node element for the given Environment
     * @param env The environment
     * @return The node element style or <code>null</code> if the node should not be painted.
     */
    public static NodeElement create(Environment env) {
        return create(env, 4f, false);
    }

    static NodeElement create(Environment env, float defaultMajorZindex, boolean allowDefault) {
        MapImage mapImage = createIcon(env);
        Symbol symbol = null;
        if (mapImage == null) {
            symbol = createSymbol(env);
        }

        // optimization: if we neither have a symbol, nor a mapImage
        // we don't have to check for the remaining style properties and we don't
        // have to allocate a node element style.
        if (!allowDefault && symbol == null && mapImage == null) return null;

        Cascade c = env.getCascade();
        Float iconRotation = c.get(ICON_ROTATION, null, Float.class);
        AffineTransform iconTransform = c.get(ICON_TRANSFORM, new AffineTransform(), AffineTransform.class);
        if (iconRotation != null) {
            iconTransform.rotate(iconRotation);
        }

        return new NodeElement(env.getCascade(), mapImage, symbol, defaultMajorZindex, iconTransform);
    }

    /**
     * Create a map icon for the environment using the default keys.
     * @param env The environment to read the icon form
     * @return The icon or <code>null</code> if no icon is defined
     * @since 11670
     */
    public static MapImage createIcon(final Environment env) {
        return createIcon(env, ICON_KEYS);
    }

    /**
     * Create a map icon for the environment.
     * @param env The environment to read the icon form
     * @param keys The keys, indexed by the ICON_..._IDX constants.
     * @return The icon or <code>null</code> if no icon is defined
     */
    public static MapImage createIcon(final Environment env, final String... keys) {
        CheckParameterUtil.ensureParameterNotNull(env, "env");
        CheckParameterUtil.ensureParameterNotNull(keys, "keys");

        Cascade c = env.getCascade();

        final IconReference iconRef = c.get(keys[ICON_IMAGE_IDX], null, IconReference.class, true);
        if (iconRef == null) {
            return null;
        }

        Float widthF = c.getWidth(keys[ICON_WIDTH_IDX]);
        Float heightF = c.getWidth(keys[ICON_HEIGHT_IDX]);

        Float offsetXF = 0f;
        Float offsetYF = 0f;
        if (keys.length >= 6) {
            offsetXF = c.getWidth(keys[ICON_OFFSET_X_IDX]);
            offsetYF = c.getWidth(keys[ICON_OFFSET_Y_IDX]);
        }

        final MapImage mapImage = new MapImage(iconRef.iconName, iconRef.source);

        mapImage.width = widthF == null ? -1 : Math.round(widthF);
        mapImage.height = heightF == null ? -1 : Math.round(heightF);
        mapImage.offsetX = offsetXF == null ? 0 : Math.round(offsetXF);
        mapImage.offsetY = offsetYF == null ? 0 : Math.round(offsetYF);

        mapImage.alpha = Utils.clamp(Config.getPref().getInt("mappaint.icon-image-alpha", 255), 0, 255);
        Integer pAlpha = ColorHelper.float2int(c.get(keys[ICON_OPACITY_IDX], null, Float.class));
        if (pAlpha != null) {
            mapImage.alpha = pAlpha;
        }
        return mapImage;
    }

    /**
     * Create a symbol for the environment
     * @param env The environment to read the icon from
     * @return The symbol.
     */
    private static Symbol createSymbol(Environment env) {
        Cascade c = env.getCascade();

        Keyword shapeKW = c.get("symbol-shape", null, Keyword.class);
        if (shapeKW == null)
            return null;

        SymbolShape shape = SymbolShape.fromKeyword(shapeKW);
        if (shape == null) {
            return null;
        }

        Float size = c.getWidth("symbol-size", 10f);

        Float strokeWidth = c.getWidth("symbol-stroke-width");

        Color strokeColor = c.get("symbol-stroke-color", null, Color.class);

        if (strokeWidth == null && strokeColor != null) {
            strokeWidth = 1f;
        } else if (strokeWidth != null && strokeColor == null) {
            strokeColor = Color.ORANGE;
        }

        Stroke stroke = null;
        if (strokeColor != null && strokeWidth != null) {
            Integer strokeAlpha = ColorHelper.float2int(c.get("symbol-stroke-opacity", null, Float.class));
            if (strokeAlpha != null) {
                strokeColor = new Color(strokeColor.getRed(), strokeColor.getGreen(),
                        strokeColor.getBlue(), strokeAlpha);
            }
            stroke = new BasicStroke(strokeWidth);
        }

        Color fillColor = c.get("symbol-fill-color", null, Color.class);
        if (stroke == null && fillColor == null) {
            fillColor = Color.BLUE;
        }

        if (fillColor != null) {
            Integer fillAlpha = ColorHelper.float2int(c.get("symbol-fill-opacity", null, Float.class));
            if (fillAlpha != null) {
                fillColor = new Color(fillColor.getRed(), fillColor.getGreen(),
                        fillColor.getBlue(), fillAlpha);
            }
        }

        return new Symbol(shape, Math.round(size), stroke, strokeColor, fillColor);
    }

    @Override
    public void paintPrimitive(IPrimitive primitive, MapPaintSettings settings, StyledMapRenderer renderer, Graphics2D g,
            boolean selected, boolean outermember, boolean member) {
        if (primitive instanceof INode node) {
            if (mapImage != null && renderer.isShowIcons()) {
                renderer.drawNodeIcon(g, node, mapImage, renderer.isInactiveMode() || node.isDisabled(), selected, member, iconTransform);
            } else if (symbol != null) {
                drawNodeSymbol(g, settings, renderer, selected, member, node);
            } else {
                Color color;
                boolean isConnection = node.isConnectionNode();

                if (renderer.isInactiveMode() || node.isDisabled()) {
                    color = settings.getInactiveColor();
                } else if (selected) {
                    color = settings.getSelectedColor();
                } else if (member) {
                    color = settings.getRelationSelectedColor();
                } else if (isConnection) {
                    if (node.isTagged()) {
                        color = settings.getTaggedConnectionColor();
                    } else {
                        color = settings.getConnectionColor();
                    }
                } else {
                    if (node.isTagged()) {
                        color = settings.getTaggedColor();
                    } else {
                        color = settings.getNodeColor();
                    }
                }

                final int size = max(
                        selected ? settings.getSelectedNodeSize() : 0,
                        node.isTagged() ? settings.getTaggedNodeSize() : 0,
                        isConnection ? settings.getConnectionNodeSize() : 0,
                        settings.getUnselectedNodeSize());

                final boolean fill = (selected && settings.isFillSelectedNode()) ||
                (node.isTagged() && settings.isFillTaggedNode()) ||
                (isConnection && settings.isFillConnectionNode()) ||
                settings.isFillUnselectedNode();

                renderer.drawNode(g, node, color, settings.adj(size), fill);
            }
        } else if (primitive instanceof IRelation<?> relation && mapImage != null) {
            // a turn restriction
            AffineTransform at = renderer.calcRestrictionTransform(relation);
            if (at != null)
                renderer.drawIcon(g, mapImage, renderer.isInactiveMode() || primitive.isDisabled(),
                    false, false, at, (graphics2D, rectangle2D) -> {});
        }
    }

    @Override
    public Rectangle getBounds(IPrimitive primitive, MapPaintSettings settings, StyledMapRenderer renderer, Graphics2D g,
            boolean selected, boolean outermember, boolean member) {
        if (primitive instanceof INode node) {
            Rectangle bounds = transformBounds(getSymbolBounds(), iconTransform);
            Point2D pt = renderer.getNavigatableComponent().getPoint2D(node);
            bounds.translate((int) pt.getX(), (int) pt.getY());
            return bounds;
        } else if (primitive instanceof IRelation<?> relation && mapImage != null) {
            // a turn restriction
            AffineTransform at = renderer.calcRestrictionTransform(relation);
            if (at != null) {
                return transformBounds(getSymbolBounds(), at);
            }
        }
        return null;
    }

    private void drawNodeSymbol(Graphics2D g, MapPaintSettings settings, StyledMapRenderer painter, boolean selected, boolean member,
            INode n) {
        Color fillColor = symbol.fillColor;
        if (fillColor != null) {
            if (painter.isInactiveMode() || n.isDisabled()) {
                fillColor = settings.getInactiveColor();
            } else if (defaultSelectedHandling && selected) {
                fillColor = settings.getSelectedColor(fillColor.getAlpha());
            } else if (member) {
                fillColor = settings.getRelationSelectedColor(fillColor.getAlpha());
            }
        }
        Color strokeColor = symbol.strokeColor;
        if (strokeColor != null) {
            if (painter.isInactiveMode() || n.isDisabled()) {
                strokeColor = settings.getInactiveColor();
            } else if (defaultSelectedHandling && selected) {
                strokeColor = settings.getSelectedColor(strokeColor.getAlpha());
            } else if (member) {
                strokeColor = settings.getRelationSelectedColor(strokeColor.getAlpha());
            }
        }
        painter.drawNodeSymbol(g, n, symbol, fillColor, strokeColor);
    }

    /**
     * Gets the bounding box for the symbol or image in this node.
     * <p>
     * Without any transform applied. Use to anchor text to the symbol.
     *
     * @return the bbox rectangle
     */
    public Rectangle getSymbolBounds() {
        if (mapImage != null) {
            int x = mapImage.offsetX;
            int y = mapImage.offsetY;
            int w = mapImage.getWidth(); // actually load the image
            int h = mapImage.getHeight();
            return new Rectangle(x - w / 2, y - h / 2, w, h);
        } else if (symbol != null) {
            return new Rectangle(-symbol.size/2, -symbol.size/2, symbol.size, symbol.size);
        } else {
            return DefaultStyles.PreferenceChangeListener.getRect();
        }
    }

    static int max(int a, int b, int c, int d) {
        // Profile before switching to a stream/int[] array
        // This was 66% give or take for painting nodes in terms of memory allocations
        // and was ~17% of the CPU allocations. By not using a vararg method call, we avoid
        // the creation of an array. By avoiding both streams and arrays, the cost for this method is negligible.
        // This means that this saves about 7% of the CPU cycles during map paint, and about 20%
        // of the memory allocations during map paint.
        return Math.max(a, Math.max(b, Math.max(c, d)));
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mapImage, symbol, iconTransform);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        if (!super.equals(obj)) return false;
        NodeElement that = (NodeElement) obj;
        return Objects.equals(mapImage, that.mapImage) &&
        Objects.equals(iconTransform, that.iconTransform) &&
        Objects.equals(symbol, that.symbol);
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder(64).append("NodeElement{").append(super.toString());
        if (mapImage != null) {
            s.append(" icon=[").append(mapImage).append(']');
        }
        if (symbol != null) {
            s.append(" symbol=[").append(symbol).append(']');
        }
        s.append('}');
        return s.toString();
    }
}
