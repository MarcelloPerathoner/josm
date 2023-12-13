// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint.styleelement;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.util.Objects;

import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.IWay;
import org.openstreetmap.josm.data.osm.visitor.paint.MapPaintSettings;
import org.openstreetmap.josm.data.osm.visitor.paint.OffsetIterator;
import org.openstreetmap.josm.data.osm.visitor.paint.StyledMapRenderer;
import org.openstreetmap.josm.gui.MapViewState.MapViewPoint;
import org.openstreetmap.josm.gui.draw.MapViewPath;
import org.openstreetmap.josm.gui.mappaint.Cascade;
import org.openstreetmap.josm.gui.mappaint.Environment;
import org.openstreetmap.josm.gui.mappaint.Keyword;
import org.openstreetmap.josm.tools.CheckParameterUtil;

/**
 * Style element that displays a repeated image pattern along a way.
 */
public class RepeatImageElement extends StyleElement {

    /**
     * The side on which the image should be aligned to the line.
     */
    public enum LineImageAlignment {
        /**
         * Align it to the top side of the line
         */
        TOP(.5),
        /**
         * Align it to the center of the line
         */
        CENTER(0),
        /**
         * Align it to the bottom of the line
         */
        BOTTOM(-.5);

        private final double alignmentOffset;

        LineImageAlignment(double alignmentOffset) {
            this.alignmentOffset = alignmentOffset;
        }

        /**
         * Gets the alignment offset.
         * @return The offset relative to the image height compared to placing the image in the middle of the line.
         */
        public double getAlignmentOffset() {
            return alignmentOffset;
        }
    }

    /**
     * The image to draw on the line repeatedly
     */
    public MapImage pattern;
    /**
     * The offset to the side of the way
     */
    public float offset;
    /**
     * The space between the images
     */
    public float spacing;
    /**
     * The offset of the first image along the way
     */
    public float phase;
    /**
     * The opacity
     */
    public float opacity;
    /**
     * The alignment of the image
     */
    public LineImageAlignment align;

    private static final String[] REPEAT_IMAGE_KEYS = {REPEAT_IMAGE, REPEAT_IMAGE_WIDTH, REPEAT_IMAGE_HEIGHT, REPEAT_IMAGE_OPACITY,
            null, null};

    /**
     * Create a new image element
     * @param c The cascade
     * @param pattern The image to draw on the line repeatedly
     * @param offset The offset to the side of the way
     * @param spacing The space between the images
     * @param phase The offset of the first image along the way
     * @param opacity The opacity
     * @param align The alignment of the image
     */
    public RepeatImageElement(Cascade c, MapImage pattern, float offset, float spacing, float phase, float opacity, LineImageAlignment align) {
        super(c, 2.9f);
        CheckParameterUtil.ensureParameterNotNull(pattern);
        CheckParameterUtil.ensureParameterNotNull(align);
        this.pattern = pattern;
        this.offset = offset;
        this.spacing = spacing;
        this.phase = phase;
        this.opacity = opacity;
        this.align = align;
    }

    /**
     * Create a RepeatImageElement from the given environment
     * @param env The environment
     * @return The image style element or <code>null</code> if none should be painted
     */
    public static RepeatImageElement create(Environment env) {
        MapImage pattern = NodeElement.createIcon(env, REPEAT_IMAGE_KEYS);
        if (pattern == null)
            return null;
        Cascade c = env.getCascade();
        float offset = c.getAdjusted(REPEAT_IMAGE_OFFSET, 0f);
        float spacing = c.getAdjusted(REPEAT_IMAGE_SPACING, 0f);
        float phase = -c.getAdjusted(REPEAT_IMAGE_PHASE, 0f);
        float opacity = c.get(REPEAT_IMAGE_OPACITY, 1f, Float.class);

        LineImageAlignment align = LineImageAlignment.CENTER;
        Keyword alignKW = c.get(REPEAT_IMAGE_ALIGN, Keyword.CENTER, Keyword.class);
        if (Keyword.TOP == alignKW) {
            align = LineImageAlignment.TOP;
        } else if (Keyword.BOTTOM == alignKW) {
            align = LineImageAlignment.BOTTOM;
        }

        return new RepeatImageElement(c, pattern, offset, spacing, phase, opacity, align);
    }

    @Override
    public Rectangle getBounds(IPrimitive primitive, MapPaintSettings paintSettings, StyledMapRenderer renderer, Graphics2D g,
            boolean selected, boolean outermember, boolean member) {
        if (primitive instanceof IWay<?> way) {
            final int imgHeight = pattern.getHeight();

            int dyImage = (int) ((align.getAlignmentOffset() - .5) * imgHeight);

            Rectangle bounds = new Rectangle(0, 0, -1, -1);
            OffsetIterator it = new OffsetIterator(renderer.getMapViewState(), way.getNodes(), offset);
            MapViewPoint start = it.next();
            AffineTransform at = new AffineTransform();
            while (it.hasNext()) {
                MapViewPoint end = it.next();
                double dx = end.getInViewX() - start.getInViewX();
                double dy = end.getInViewY() - start.getInViewY();
                Rectangle r = new Rectangle(0, -dyImage, (int) Math.sqrt(dx * dx + dy * dy), imgHeight);
                at.setToTranslation(start.getInViewX(), start.getInViewY());
                at.rotate(Math.atan2(dy, dx));
                bounds.add(transformBounds(r, at));
                start = end;
            }
            return bounds;
        }
        return null;
    }

    @Override
    public void paintPrimitive(IPrimitive primitive, MapPaintSettings paintSettings, StyledMapRenderer renderer, Graphics2D g,
            boolean selected, boolean outermember, boolean member) {
        if (primitive instanceof IWay) {
            IWay<?> way = (IWay<?>) primitive;
            boolean disabled = renderer.isInactiveMode() || way.isDisabled();
            final int imgWidth = pattern.getWidth();
            final double repeat = imgWidth + spacing;
            final int imgHeight = pattern.getHeight();

            int dy1 = (int) ((align.getAlignmentOffset() - .5) * imgHeight);
            int dy2 = dy1 + imgHeight;

            OffsetIterator it = new OffsetIterator(renderer.getMapViewState(), way.getNodes(), offset);
            MapViewPath path = new MapViewPath(renderer.getMapViewState());
            if (it.hasNext()) {
                path.moveTo(it.next());
            }
            while (it.hasNext()) {
                path.lineTo(it.next());
            }

            double startOffset = computeStartOffset(phase, repeat);

            Image image = pattern.getImage(disabled);
            Composite saveComposite = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
            AffineTransform saveTransform = g.getTransform();

            path.visitClippedLine(repeat, (inLineOffset, start, end, startIsOldEnd) -> {
                final double segmentLength = start.distanceToInView(end);
                if (segmentLength < 0.1) {
                    // avoid odd patterns when zoomed out.
                    return;
                }
                if (segmentLength > repeat * 500) {
                    // simply skip drawing so many images - something must be wrong.
                    return;
                }
                g.setTransform(saveTransform);
                g.translate(start.getInViewX(), start.getInViewY());
                double dx = end.getInViewX() - start.getInViewX();
                double dy = end.getInViewY() - start.getInViewY();
                g.rotate(Math.atan2(dy, dx));

                // The start of the next image
                // It is shifted by startOffset.
                double imageStart = -((inLineOffset - startOffset + repeat) % repeat);

                while (imageStart < segmentLength) {
                    int x = (int) imageStart;
                    int sx1 = Math.max(0, -x);
                    int sx2 = imgWidth - Math.max(0, x + imgWidth - (int) Math.ceil(segmentLength));
                    g.drawImage(image, x + sx1, dy1, x + sx2, dy2, sx1, 0, sx2, imgHeight, null);
                    imageStart += repeat;
                }
            });
            g.setComposite(saveComposite);
            g.setTransform(saveTransform);
        }
    }

    private static double computeStartOffset(double phase, final double repeat) {
        double startOffset = phase % repeat;
        if (startOffset < 0) {
            startOffset += repeat;
        }
        return startOffset;
    }

    @Override
    public boolean isProperLineStyle() {
        return true;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        if (!super.equals(obj)) return false;
        RepeatImageElement that = (RepeatImageElement) obj;
        return align == that.align &&
               Float.compare(that.offset, offset) == 0 &&
               Float.compare(that.spacing, spacing) == 0 &&
               Float.compare(that.phase, phase) == 0 &&
               Objects.equals(pattern, that.pattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), pattern, offset, spacing, phase, opacity, align);
    }

    @Override
    public String toString() {
        return "RepeatImageStyle{" + super.toString() + "pattern=[" + pattern +
                "], offset=" + offset + ", spacing=" + spacing +
                ", phase=" + -phase + ", opacity=" + opacity + ", align=" + align + '}';
    }
}
