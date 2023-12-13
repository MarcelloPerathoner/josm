// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint.styleelement;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.util.Objects;

import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.visitor.paint.MapPaintSettings;
import org.openstreetmap.josm.data.osm.visitor.paint.StyledMapRenderer;
import org.openstreetmap.josm.gui.MapViewState.MapViewPoint;
import org.openstreetmap.josm.gui.draw.MapViewPositionAndRotation;
import org.openstreetmap.josm.gui.mappaint.Cascade;
import org.openstreetmap.josm.gui.mappaint.Environment;
import org.openstreetmap.josm.gui.mappaint.Keyword;
import org.openstreetmap.josm.gui.mappaint.StyleKeys;
import org.openstreetmap.josm.gui.mappaint.styleelement.placement.PartiallyInsideAreaStrategy;
import org.openstreetmap.josm.gui.mappaint.styleelement.placement.PositionForAreaStrategy;

/**
 * This class defines how an icon is rendered onto the area.
 * @author Michael Zangl
 * @since 11730
 */
public class AreaIconElement extends StyleElement {
    /**
     * The icon that is displayed on the center of the area.
     */
    private final MapImage iconImage;

    /**
     * The position of the icon inside the area.
     */
    private final PositionForAreaStrategy iconPosition;

    /** The image bounds, centered */
    private final Rectangle iconRect;

    /** The icon rotation */
    private final double theta;

    protected AreaIconElement(Cascade c, MapImage iconImage, PositionForAreaStrategy iconPosition) {
        super(c, 4.8f);
        this.iconImage = Objects.requireNonNull(iconImage, "iconImage");
        this.iconPosition = Objects.requireNonNull(iconPosition, "iconPosition");
        this.theta = c.get(ICON_ROTATION, 0d, Double.class);
        int w = iconImage.getWidth();
        int h = iconImage.getHeight();
        this.iconRect = new Rectangle(-w / 2, -h / 2, w, h);
    }

    @Override
    public Rectangle getBounds(IPrimitive osm, MapPaintSettings paintSettings, StyledMapRenderer renderer, Graphics2D g,
            boolean selected, boolean outermember, boolean member) {
        if (renderer.isShowIcons()) {
            Rectangle bounds = new Rectangle(0, 0, -1, -1);
            AffineTransform affineTransform = new AffineTransform();

            renderer.forEachPolygon(osm, path -> {
                MapViewPositionAndRotation placement = iconPosition.findLabelPlacement(path, iconRect);
                if (placement != null) {
                    MapViewPoint p = placement.getPoint();
                    affineTransform.setToTranslation(p.getInViewX(), p.getInViewY());
                    affineTransform.rotate(theta + placement.getRotation());
                    bounds.add(transformBounds(iconRect, affineTransform));
                }
            });
            return bounds;
        }
        return null;
    }

    @Override
    public void paintPrimitive(IPrimitive osm, MapPaintSettings paintSettings, StyledMapRenderer renderer, Graphics2D g,
            boolean selected, boolean outermember, boolean member) {
        if (renderer.isShowIcons()) {
            renderer.forEachPolygon(osm, path -> {
                MapViewPositionAndRotation placement = iconPosition.findLabelPlacement(path, iconRect);
                if (placement == null) {
                    return;
                }
                MapViewPoint p = placement.getPoint();
                AffineTransform affineTransform = new AffineTransform();
                affineTransform.translate(p.getInViewX(), p.getInViewY());
                affineTransform.rotate(theta + placement.getRotation());
                boolean disabled = renderer.isInactiveMode() || osm.isDisabled();
                renderer.drawIcon(g, iconImage, disabled, selected, member, affineTransform, (gr, r) -> {
                    if (renderer.getUseStrokes()) {
                        gr.setStroke(new BasicStroke(2));
                    }
                    // only draw a minor highlighting, so that users do not confuse this for a point.
                    Color color = renderer.getSelectionHintColor(disabled, selected);
                    color = new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (color.getAlpha() * .2));
                    gr.setColor(color);
                    gr.draw(r);
                });
            });
        }
    }

    /**
     * Create a new {@link AreaIconElement}
     * @param env The current style definitions
     * @return The area element or <code>null</code> if there is no icon.
     */
    public static AreaIconElement create(final Environment env) {
        final Cascade c = env.getCascade();
        MapImage iconImage = NodeElement.createIcon(env);
        if (iconImage != null) {
            Keyword positionKeyword = c.get(StyleKeys.ICON_POSITION, null, Keyword.class);
            PositionForAreaStrategy position = PositionForAreaStrategy.forKeyword(positionKeyword, PartiallyInsideAreaStrategy.INSTANCE);
            return new AreaIconElement(c, iconImage, position);
        } else {
            return null;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), theta, iconImage, iconPosition);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        AreaIconElement other = (AreaIconElement) obj;
        return Objects.equals(iconImage, other.iconImage) &&
                Objects.equals(theta, other.theta) &&
                Objects.equals(iconPosition, other.iconPosition);
    }

    @Override
    public String toString() {
        return "AreaIconElement{" + super.toString() + "iconImage=[" + iconImage + "]}";
    }
}
