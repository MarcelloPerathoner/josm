// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint.styleelement;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.Objects;

import org.openstreetmap.josm.data.osm.INode;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.visitor.paint.MapPaintSettings;
import org.openstreetmap.josm.data.osm.visitor.paint.PaintColors;
import org.openstreetmap.josm.data.osm.visitor.paint.StyledMapRenderer;
import org.openstreetmap.josm.gui.mappaint.Cascade;
import org.openstreetmap.josm.gui.mappaint.Environment;
import org.openstreetmap.josm.gui.mappaint.Keyword;
import org.openstreetmap.josm.tools.CheckParameterUtil;

/**
 * Text style attached to a style with a bounding box, like an icon or a symbol.
 */
public class BoxTextElement extends StyleElement {

    /**
     * Something that provides us with a {@link BoxProviderResult}
     * @since 10600 (functional interface)
     */
    @FunctionalInterface
    public interface BoxProvider {
        /**
         * Compute and get the {@link BoxProviderResult}. The temporary flag is set if the result of the computation may change in the future.
         * @return The result of the computation.
         */
        BoxProviderResult get();
    }

    /**
     * A box rectangle with a flag if it is temporary.
     */
    public static class BoxProviderResult {
        private final Rectangle box;
        private final boolean temporary;

        /**
         * Create a new box provider result
         * @param box The box
         * @param temporary The temporary flag, will be returned by {@link #isTemporary()}
         */
        public BoxProviderResult(Rectangle box, boolean temporary) {
            this.box = box;
            this.temporary = temporary;
        }

        /**
         * Returns the box.
         * @return the box
         */
        public Rectangle getBox() {
            return box;
        }

        /**
         * Determines if the box can change in future calls of the {@link BoxProvider#get()} method
         * @return {@code true} if the box can change in future calls of the {@code BoxProvider#get()} method
         */
        public boolean isTemporary() {
            return temporary;
        }
    }

    /**
     * A {@link BoxProvider} that always returns the same non-temporary rectangle
     */
    public static class SimpleBoxProvider implements BoxProvider {
        private final Rectangle box;

        /**
         * Constructs a new {@code SimpleBoxProvider}.
         * @param box the box
         */
        public SimpleBoxProvider(Rectangle box) {
            this.box = box;
        }

        @Override
        public BoxProviderResult get() {
            return new BoxProviderResult(box, false);
        }

        @Override
        public int hashCode() {
            return Objects.hash(box);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            SimpleBoxProvider that = (SimpleBoxProvider) obj;
            return Objects.equals(box, that.box);
        }
    }

    /**
     * Caches the default text color from the preferences.
     *
     * FIXME: the cache isn't updated if the user changes the preference during a JOSM
     * session. There should be preference listener updating this cache.
     */
    private static volatile Color defaultTextColorCache;

    /**
     * The text this element should display.
     */
    public TextLabel text;
    /**
     * The x offset of the text.
     */
    public int xOffset;
    /**
     * The y offset of the text. In screen space (inverted to user space)
     */
    public int yOffset;
    /**
     * The horizontal text alignment for this text.
     */
    public Keyword hAlign;
    /**
     * The vertical text alignment for this text.
     */
    public Keyword vAlign;

    protected BoxProvider boxProvider;

    /**
     * Create a new {@link BoxTextElement}
     * @param c The current cascade
     * @param text The text to display
     * @param boxProvider The box provider to use
     * @param offsetX x offset, in screen space
     * @param offsetY y offset, in screen space
     * @param hAlign The horizontal alignment {@link Keyword}
     * @param vAlign The vertical alignment {@link Keyword}
     */
    public BoxTextElement(Cascade c, TextLabel text, BoxProvider boxProvider,
            int offsetX, int offsetY, Keyword hAlign, Keyword vAlign) {
        super(c, 5f);
        xOffset = offsetX;
        yOffset = offsetY;
        CheckParameterUtil.ensureParameterNotNull(text);
        CheckParameterUtil.ensureParameterNotNull(hAlign);
        CheckParameterUtil.ensureParameterNotNull(vAlign);
        this.text = text;
        this.boxProvider = boxProvider;
        this.hAlign = hAlign;
        this.vAlign = vAlign;
    }

    /**
     * Create a new {@link BoxTextElement} with a boxprovider and a box.
     * @param env The MapCSS environment
     * @param boxProvider The box provider.
     * @return A new {@link BoxTextElement} or <code>null</code> if the creation failed.
     */
    public static BoxTextElement create(Environment env, BoxProvider boxProvider) {
        if (defaultTextColorCache == null)
            initDefaultParameters();

        TextLabel text = TextLabel.create(env, defaultTextColorCache, false);
        if (text == null) return null;
        // Skip any primitives that don't have text to draw. (Styles are recreated for any tag change.)
        // The concrete text to render is not cached in this object, but computed for each
        // repaint. This way, one BoxTextElement object can be used by multiple primitives (to save memory).
        if (text.text == null) return null;

        Cascade c = env.getCascade();
        Keyword hAlign = c.get(TEXT_ANCHOR_HORIZONTAL, Keyword.RIGHT, Keyword.class);
        Keyword vAlign = c.get(TEXT_ANCHOR_VERTICAL, Keyword.BOTTOM, Keyword.class);

        Point2D offset = TextLabel.getTextOffset(c);

        return new BoxTextElement(c, text, boxProvider, (int) offset.getX(), (int) -offset.getY(), hAlign, vAlign);
    }

    /**
     * Get the box in which the content should be drawn.
     * @return The box.
     */
    public Rectangle getBox() {
        return boxProvider.get().getBox();
    }

    private static void initDefaultParameters() {
        if (defaultTextColorCache == null)
            defaultTextColorCache = PaintColors.TEXT.get();
    }

    @Override
    public void paintPrimitive(IPrimitive osm, MapPaintSettings settings, StyledMapRenderer painter,
            boolean selected, boolean outermember, boolean member) {
        if (osm instanceof INode) {
            painter.drawBoxText((INode) osm, this);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        if (!super.equals(obj)) return false;
        BoxTextElement that = (BoxTextElement) obj;
        return hAlign == that.hAlign &&
               vAlign == that.vAlign &&
               xOffset == that.xOffset &&
               yOffset == that.yOffset &&
               Objects.equals(text, that.text) &&
               Objects.equals(boxProvider, that.boxProvider);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), text, boxProvider, hAlign, vAlign, xOffset, yOffset);
    }

    @Override
    public String toString() {
        return "BoxTextElement{" + super.toString() + ' ' + text.toStringImpl()
                + " box=" + getBox() + " hAlign=" + hAlign + " vAlign=" + vAlign + " xOffset=" + xOffset + " yOffset=" + yOffset + '}';
    }
}
