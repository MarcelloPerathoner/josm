// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint;

import java.awt.Color;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.openstreetmap.josm.data.osm.visitor.paint.MapPaintSettings;
import org.openstreetmap.josm.gui.mappaint.mapcss.CSSColors;
import org.openstreetmap.josm.gui.mappaint.mapcss.Expression;
import org.openstreetmap.josm.tools.ColorHelper;
import org.openstreetmap.josm.tools.GenericParser;
import org.openstreetmap.josm.tools.Logging;

/**
 * Simple map of properties with dynamic typing.
 */
public final class Cascade {

    private final Map<String, Object> prop;

    private boolean defaultSelectedHandling = true;

    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})");

    private static final GenericParser<Object> GENERIC_PARSER = new GenericParser<>()
            .registerParser(float.class, Cascade::toFloat)
            .registerParser(Float.class, Cascade::toFloat)
            .registerParser(double.class, Cascade::toDouble)
            .registerParser(Double.class, Cascade::toDouble)
            .registerParser(boolean.class, Cascade::toBool)
            .registerParser(Boolean.class, Cascade::toBool)
            .registerParser(float[].class, Cascade::toFloatArray)
            .registerParser(Color.class, Cascade::toColor)
            .registerParser(String.class, Cascade::toString);

    /**
     * Constructs a new {@code Cascade}.
     */
    public Cascade() {
        this.prop = new HashMap<>();
    }

    /**
     * Constructs a new {@code Cascade} from existing one.
     * @param other other Cascade
     */
    public Cascade(Cascade other) {
        this.prop = new HashMap<>(other.prop);
    }

    /**
     * Gets the value for a given key with the given type
     * @param <T> the expected type
     * @param key the key
     * @param def default value, can be null
     * @param klass the same as T
     * @return if a value that can be converted to class klass has been mapped to key, returns this
     *      value, def otherwise
     */
    public <T> T get(String key, T def, Class<T> klass) {
        return get(key, def, klass, false);
    }

    /**
     * Gets a length-ish value adjusted to the current screen resolution.
     * @param key the key
     * @param def the default value or null
     * @return the adjusted length or the adjusted default value
     */
    public Float getAdjusted(String key, Float def) {
        Float f = convertTo(get(key), Float.class);
        if (f != null)
            return MapPaintSettings.INSTANCE.adj(f);
        return MapPaintSettings.INSTANCE.adj(def);
    }

    /**
     * Gets a length-ish value adjusted to the current screen resolution. This function
     * understands the keywords 'thinnest' and 'default'.
     *
     * @param key the key
     * @param def the default value or null
     * @return the adjusted length or the adjusted default value
     */
    public Float getWidth(String key, Float def){
        Object v = get(key);
        Float val = convertTo(v, Float.class);
        if (val != null) {
            return MapPaintSettings.INSTANCE.adj(val);
        }
        if (v instanceof Keyword) {
            if (Keyword.THINNEST.equals(v))
                return 0f;
            if (Keyword.DEFAULT.equals(v))
                return MapPaintSettings.INSTANCE.adj((float) MapPaintSettings.INSTANCE.getDefaultSegmentWidth());
        }
        return def;
    }

    public Float getWidth(String key){
        return getWidth(key, null);
    }

    /**
     * Get value for the given key
     * @param <T> the expected type
     * @param key the key
     * @param def default value, can be null
     * @param klass the same as T
     * @param suppressWarnings show or don't show a warning when some value is
     *      found, but cannot be converted to the requested type
     * @return if a value that can be converted to class klass has been mapped to key, returns this
     *      value, def otherwise
     */
    public <T> T get(String key, T def, Class<T> klass, boolean suppressWarnings) {
        Object o = prop.get(key);
        if (o == null)
            return def;
        if (klass.isInstance(o))
            return klass.cast(o);
        if (o instanceof Expression)
            o = ((Expression) o).evaluate();
        if (o == null)
            return def; // avoid the warning
        T res = convertTo(o, klass);
        if (res == null) {
            if (!suppressWarnings) {
                Logging.warn(String.format("Unable to convert property %s to type %s: found '%s' of type %s!", key, klass, o, o.getClass()));
            }
            return def;
        } else
            return res;
    }

    /**
     * Gets a property for the given key (like stroke, ...)
     * @param key The key of the property
     * @return The value or <code>null</code> if it is not set. May be of any type
     */
    public Object get(String key) {
        return prop.get(key);
    }

    /**
     * Gets an expression given key (like stroke, ...)
     * @param key The key of the property
     * @return The expression or <code>null</code>
     */
    public Expression getExpression(String key) {
        Object exp = prop.get(key);
        return exp instanceof Expression ? (Expression) exp : null;
    }

    /**
     * Sets the property for the given key
     * @param key The key
     * @param val The value
     */
    public void put(String key, Object val) {
        prop.put(key, val);
    }

    /**
     * Sets the property for the given key, removes it if the value is <code>null</code>
     * @param key The key
     * @param val The value, may be <code>null</code>
     */
    public void putOrClear(String key, Object val) {
        if (val != null) {
            prop.put(key, val);
        } else {
            prop.remove(key);
        }
    }

    /**
     * Removes the property with the given key
     * @param key The key
     */
    public void remove(String key) {
        prop.remove(key);
    }

    /**
     * Converts an object to a given other class.
     *
     * Only conversions that are useful for MapCSS are supported
     * @param <T> The class type
     * @param o The object to convert
     * @param klass The class
     * @return The converted object or <code>null</code> if the conversion failed
     */
    public static <T> T convertTo(Object o, Class<T> klass) {
        if (o == null)
            return null;
        if (klass.isInstance(o))
            return klass.cast(o);

        return GENERIC_PARSER.supports(klass)
                ? GENERIC_PARSER.parse(klass, o)
                : null;
    }

    private static String toString(Object o) {
        if (o instanceof Keyword)
            return ((Keyword) o).val;
        if (o instanceof Color) {
            return ColorHelper.color2html((Color) o);
        }
        return o.toString();
    }

    private static Float toFloat(Object o) {
        if (o instanceof Number)
            return ((Number) o).floatValue();
        if (o instanceof String && !((String) o).isEmpty()) {
            try {
                return Float.valueOf((String) o);
            } catch (NumberFormatException e) {
                Logging.debug("''{0}'' cannot be converted to float", o);
            }
        }
        if (o instanceof Boolean)
            return ((boolean) o) ? 1.0f : 0.0f;
        return null;
    }

    private static Double toDouble(Object o) {
        final Float number = toFloat(o);
        return number != null ? Double.valueOf(number) : null;
    }

    private static Boolean toBool(Object o) {
        if (o instanceof Boolean)
            return (Boolean) o;
        String s = null;
        if (o instanceof Keyword) {
            s = ((Keyword) o).val;
        } else if (o instanceof String) {
            s = (String) o;
        }
        if (s != null)
            return !(s.isEmpty() || "false".equals(s) || "no".equals(s) || "0".equals(s) || "0.0".equals(s));
        if (o instanceof Number)
            return ((Number) o).floatValue() != 0;
        if (o instanceof List)
            return !((List<?>) o).isEmpty();
        if (o instanceof float[])
            return ((float[]) o).length != 0;

        return null;
    }

    private static float[] toFloatArray(Object o) {
        if (o instanceof float[])
            return (float[]) o;
        if (o instanceof List) {
            List<?> l = (List<?>) o;
            float[] a = new float[l.size()];
            for (int i = 0; i < l.size(); ++i) {
                Float f = toFloat(l.get(i));
                if (f == null)
                    return null;
                else
                    a[i] = f;
            }
            return a;
        }
        Float f = toFloat(o);
        if (f != null)
            return new float[] {f};
        return null;
    }

    private static Color toColor(Object o) {
        if (o instanceof Color)
            return (Color) o;
        if (o instanceof Keyword)
            return CSSColors.get(((Keyword) o).val);
        if (o instanceof String) {
            Color c = CSSColors.get((String) o);
            if (c != null)
                return c;
            if (HEX_COLOR_PATTERN.matcher((String) o).matches()) {
                return ColorHelper.html2color((String) o);
            }
        }
        return null;
    }

    @Override
    public String toString() {
        // List properties in alphabetical order to be deterministic, without changing "prop" to a TreeMap
        // (no reason too, not sure about the potential memory/performance impact of such a change)
        TreeSet<String> props = new TreeSet<>();
        for (Entry<String, Object> entry : prop.entrySet()) {
            StringBuilder sb = new StringBuilder(entry.getKey()).append(':');
            Object val = entry.getValue();
            if (val instanceof float[]) {
                sb.append(Arrays.toString((float[]) val));
            } else if (val instanceof Color) {
                sb.append(ColorHelper.color2html((Color) val));
            } else if (val != null) {
                sb.append(val);
            }
            sb.append("; ");
            props.add(sb.toString());
        }
        return props.stream().collect(Collectors.joining("", "Cascade{ ", "}"));
    }

    /**
     * Checks if this cascade has a value for given key
     * @param key The key to check
     * @return <code>true</code> if there is a value
     */
    public boolean containsKey(String key) {
        return prop.containsKey(key);
    }

    /**
     * Get if the default selection drawing should be used for the object this cascade applies to
     * @return <code>true</code> to use the default selection drawing
     */
    public boolean isDefaultSelectedHandling() {
        return defaultSelectedHandling;
    }

    /**
     * Set that the default selection drawing should be used for the object this cascade applies to
     * @param defaultSelectedHandling <code>true</code> to use the default selection drawing
     */
    public void setDefaultSelectedHandling(boolean defaultSelectedHandling) {
        this.defaultSelectedHandling = defaultSelectedHandling;
    }
}
