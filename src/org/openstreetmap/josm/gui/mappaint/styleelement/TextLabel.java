// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint.styleelement;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.Objects;

import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.mappaint.Cascade;
import org.openstreetmap.josm.gui.mappaint.Environment;
import org.openstreetmap.josm.gui.mappaint.mapcss.FunctionAutoText;
import org.openstreetmap.josm.gui.mappaint.StyleKeys;
import org.openstreetmap.josm.tools.CheckParameterUtil;
import org.openstreetmap.josm.tools.ColorHelper;

/**
 * Represents the rendering style for a textual label placed somewhere on the map.
 * @since 3880
 */
public class TextLabel implements StyleKeys {
    /**
     * The text.
     */
    public final String text; // NOSONAR
    /**
     * the font to be used when rendering
     */
    public final Font font;
    /**
     * The color to draw the text in, includes alpha.
     */
    public final Color color; // NOSONAR
    /**
     * The radius of the halo effect.
     */
    public final Float haloRadius;
    /**
     * The color of the halo effect.
     */
    public final Color haloColor;

    /** {@code icon-rotation} * {@code icon-transform} or {@code null}. */
    public final AffineTransform textTransform;

    /**
     * Creates a new text element
     *
     * @param text the text to be rendered
     * @param font the font to be used. Must not be null.
     * @param color the color to be used. Must not be null
     * @param haloRadius halo radius
     * @param haloColor halo color
     * @param textTransform the affine transformation to apply to the text
     */
    protected TextLabel(String text, Font font, Color color,
                        Float haloRadius, Color haloColor,
                        AffineTransform textTransform) {
        this.text = text;
        this.font = Objects.requireNonNull(font, "font");
        this.color = Objects.requireNonNull(color, "color");
        this.haloRadius = haloRadius;
        this.haloColor = haloColor;
        this.textTransform = textTransform;
    }

    /**
     * Copy constructor
     *
     * @param other the other element.
     */
    public TextLabel(TextLabel other) {
        this.text = other.text;
        this.font = other.font;
        this.color = other.color;
        this.haloColor = other.haloColor;
        this.haloRadius = other.haloRadius;
        this.textTransform = other.textTransform;
    }

    /**
     * Builds a text element from style properties in {@code c} and the
     * default text color {@code defaultTextColor}
     *
     * @param env the environment
     * @param defaultTextColor the default text color. Must not be null.
     * @param defaultAnnotate true, if a text label shall be rendered by default, even if the style sheet
     *   doesn't include respective style declarations
     * @return the text element or null, if the style properties don't include
     * properties for text rendering
     * @throws IllegalArgumentException if {@code defaultTextColor} is null
     */
    public static TextLabel create(Environment env, Color defaultTextColor, boolean defaultAnnotate) {
        CheckParameterUtil.ensureParameterNotNull(defaultTextColor);
        Cascade c = env.getCascade();

        String text = c.get(TEXT, null, String.class);
        if (text == null && defaultAnnotate) {
            text = FunctionAutoText.getText(env.osm);
        }
        if (text == null) {
            return null;
        }

        Font font = StyleElement.getFont(c, text);

        Color color = c.get(TEXT_COLOR, defaultTextColor, Color.class);
        float alpha = c.get(TEXT_OPACITY, 1f, Float.class);
        color = ColorHelper.alphaMultiply(color, alpha);

        Float haloRadius = c.get(TEXT_HALO_RADIUS, null, Float.class);
        if (haloRadius != null && haloRadius <= 0) {
            haloRadius = null;
        }
        Color haloColor = null;
        if (haloRadius != null) {
            haloColor = c.get(TEXT_HALO_COLOR, ColorHelper.complement(color), Color.class);
            float haloAlphaFactor = c.get(TEXT_HALO_OPACITY, 1f, Float.class);
            haloColor = ColorHelper.alphaMultiply(haloColor, haloAlphaFactor);
        }

        AffineTransform textTransform = c.get(TEXT_TRANSFORM, new AffineTransform(), AffineTransform.class);
        Float textRotation = c.get(TEXT_ROTATION, null, Float.class);
        if (textRotation != null) {
            textTransform.rotate(textRotation);
        }
        return new TextLabel(text, font, color, haloRadius, haloColor, textTransform);
    }

    /**
     * Gets the text-offset property from a cascade
     * @param c The cascade
     * @return The text offset property
     */
    public static Point2D getTextOffset(Cascade c) {
        float xOffset = 0;
        float yOffset = 0;
        float[] offset = c.get(TEXT_OFFSET, null, float[].class);
        if (offset != null) {
            if (offset.length == 1) {
                yOffset = offset[0];
            } else if (offset.length >= 2) {
                xOffset = offset[0];
                yOffset = offset[1];
            }
        }
        xOffset = c.getAdjusted(TEXT_OFFSET_X, xOffset);
        yOffset = c.getAdjusted(TEXT_OFFSET_Y, yOffset);
        return new Point2D.Double(xOffset, yOffset);
    }

    /**
     * Replies the label to be rendered for the primitive {@code osm}.
     *
     * @param osm the OSM object
     * @return the label, or null, if {@code osm} is null or if no label can be
     * derived for {@code osm}
     */
    public String getString(IPrimitive osm) {
        return text;
    }

    @Override
    public String toString() {
        return "TextLabel{" + toStringImpl() + '}';
    }

    protected String toStringImpl() {
        StringBuilder sb = new StringBuilder(96);
        sb.append("text=").append(text)
          .append(" font=").append(font)
          .append(" color=").append(ColorHelper.color2html(color));
        if (haloRadius != null) {
            sb.append(" haloRadius=").append(haloRadius)
              .append(" haloColor=").append(haloColor);
        }
        return sb.toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, font, color, haloRadius, haloColor, textTransform);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        TextLabel textLabel = (TextLabel) obj;
        return Objects.equals(text, textLabel.text) &&
                Objects.equals(font, textLabel.font) &&
                Objects.equals(color, textLabel.color) &&
                Objects.equals(haloRadius, textLabel.haloRadius) &&
                Objects.equals(textTransform, textLabel.textTransform) &&
                Objects.equals(haloColor, textLabel.haloColor);
    }
}
