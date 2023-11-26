// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint.mapcss;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.mappaint.Cascade;
import org.openstreetmap.josm.gui.mappaint.Environment;
import org.openstreetmap.josm.gui.mappaint.MapPaintStyles;
import org.openstreetmap.josm.gui.mappaint.MultiCascade;
import org.openstreetmap.josm.gui.mappaint.StyleSource;
import org.openstreetmap.josm.gui.mappaint.mapcss.Selector.GeneralSelector;
import org.openstreetmap.josm.gui.mappaint.mapcss.parsergen.MapCSSParser;
import org.openstreetmap.josm.gui.mappaint.mapcss.parsergen.Token;
import org.openstreetmap.josm.gui.mappaint.MapPaintStyles.IconReference;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Pair;

/**
 * Factory to generate {@link Expression}s.
 * <p>
 * See {@link #createFunctionExpression}.
 */
public final class ExpressionFactory {

    /**
     * Marks functions which should be executed also when one or more arguments are null.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface NullableArguments {}

    @FunctionalInterface
    public interface TriFunction<T, U, V, R> {
        R apply(T t, U u, V v);
    }

    @FunctionalInterface
    public interface QuadFunction<T, U, V, W, R> {
        R apply(T t, U u, V v, W w);
    }

    static final Map<String, Supplier<CacheableExpression>> expressionMap = new HashMap<>();

    static {
        initFactories();
    }

    /** The MapCSS constant meaning "none" */
    static final String NONE = "";

    /**
     * Returns true if the input equals MapCSS "none"
     * @param s the input
     * @return true if the input equals MapCSS "none"
     */
    static boolean isNone(Object s) {
        return s == null || NONE.equals(s);
    }

    static boolean eq(String a, String b) {
        if (isNone(a) && isNone(b))
            return true;
        return !isNone(a) && a.equals(b);
    }

    /**
     * Equal comparison with canonization for numbers
     */
    static boolean equal(String a, String b) {
        if (isNone(a) != isNone(b))
            return false;
        if (isNone(a) && isNone(b))
            return true;
        // neither a nor b can be null here
        try {
            Double da = Double.valueOf(a);
            Double db = Double.valueOf(b);
            return da.equals(db);
        } catch (NumberFormatException e) {
            return a.equals(b);
        }
    }

    static boolean ne(String s1, String s2) {
        return !eq(s1, s2);
    }

    static boolean notEqual(String a, String b) {
        return !equal(a, b);
    }

    static String fBoolean(String s) {
        if (isNone(s)) return null;
        return ("0".equals(s) || "no".equals(s) || "false".equals(s) || "".equals(s)) ? "false" : "true";
    }

    static Integer fInt(Double d) {
        if (isNone(d)) return null;
        return (int) (double) d;
    }

    /**
     * Returns the parsed value as number or {@code null}
     */
    static Double num(String s) {
        if (isNone(s)) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // CHECKSTYLE.OFF: SingleSpaceSeparator
    @SuppressWarnings("unchecked")
    private static void initFactories() {
        add("CRC32_checksum",    () -> new FunctionExpression<>(Functions::CRC32_checksum, String.class));
        add("JOSM_pref",         () -> new EnvBiFunctionExpression<>(Functions::JOSM_pref, String.class, String.class));
        add("JOSM_search",       () -> new EnvFunctionExpression<>(Functions::JOSM_search, String.class));
        add("URL_decode",        () -> new FunctionExpression<>(Functions::URL_decode,  String.class));
        add("URL_encode",        () -> new FunctionExpression<>(Functions::URL_encode,  String.class));
        add("XML_encode",        () -> new FunctionExpression<>(Functions::XML_encode,  String.class));
        add("any",               AnyFunctionExpression::new);
        add("areasize",          () -> new EnvSupplierExpression(Functions::areasize));
        add("boolean",           () -> new FunctionExpression<>(ExpressionFactory::fBoolean, String.class));
        add("child_tag",         () -> new EnvFunctionExpression<>(Functions::child_tag, String.class));
        add("concat",            ConcatFunctionExpression::new);
        add("count",             () -> new FunctionExpression<>(Functions::count, List.class));
        add("count_roles",       CountRolesFunctionExpression::new);
        add("eq",                () -> new BiFunctionExpression<>(ExpressionFactory::eq, String.class, String.class));
        add("equal",             () -> new BiFunctionExpression<>(ExpressionFactory::equal, String.class, String.class));
        add("get$2",             () -> new BiFunctionExpression<>(Functions::get, List.class, Float.class));
        add("get$3",             () -> new TriFunctionExpression<>(Functions::get, List.class, Float.class, Float.class));
        add("gpx_distance",      () -> new EnvSupplierExpression(Functions::gpx_distance));
        add("has_tag_key",       () -> new EnvFunctionExpression<>(Functions::has_tag_key, String.class));
        add("hsb_color",         () -> new TriFunctionExpression<>(Functions::hsb_color, Float.class, Float.class, Float.class));
        add("index",             () -> new EnvSupplierExpression(Functions::index));
        add("int",               () -> new FunctionExpression<>(ExpressionFactory::fInt, Double.class));
        add("is_anticlockwise",  () -> new EnvSupplierExpression(Functions::is_anticlockwise));
        add("is_clockwise",      () -> new EnvSupplierExpression(Functions::is_clockwise));
        add("is_prop_set$1",     () -> new EnvFunctionExpression<>(Functions::is_prop_set, String.class));
        add("is_prop_set$2",     () -> new EnvBiFunctionExpression<>(Functions::is_prop_set, String.class, String.class));
        add("is_similar",        () -> new BiFunctionExpression<>(Functions::is_similar, String.class, String.class));
        add("join",              JoinFunctionExpression::new);
        add("join_list",         () -> new BiFunctionExpression<>(Functions::join_list, String.class, List.class));
        add("length",            () -> new FunctionExpression<>(String::length, String.class));
        add("list",              AsListFunctionExpression::new);
        add("ne",                () -> new BiFunctionExpression<>(ExpressionFactory::ne, String.class, String.class));
        add("num",               () -> new FunctionExpression<>(ExpressionFactory::num, String.class));
        add("not_equal",         () -> new BiFunctionExpression<>(ExpressionFactory::notEqual, String.class, String.class));
        add("number_of_tags",    () -> new EnvSupplierExpression(Functions::number_of_tags));
        add("osm_changeset_id",  () -> new EnvSupplierExpression(Functions::osm_changeset_id));
        add("osm_id",            () -> new EnvSupplierExpression(Functions::osm_id));
        add("osm_timestamp",     () -> new EnvSupplierExpression(Functions::osm_timestamp));
        add("osm_user_id",       () -> new EnvSupplierExpression(Functions::osm_user_id));
        add("osm_user_name",     () -> new EnvSupplierExpression(Functions::osm_user_name));
        add("osm_version",       () -> new EnvSupplierExpression(Functions::osm_version));
        add("parent_osm_id",     () -> new EnvSupplierExpression(Functions::parent_osm_id));
        add("parent_tag",        () -> new EnvFunctionExpression<>(Functions::parent_tag, String.class));
        add("parent_tags",       () -> new EnvFunctionExpression<>(Functions::parent_tags, String.class));
        add("parent_way_angle",  () -> new EnvSupplierExpression(Functions::parent_way_angle));
        add("print",             () -> new FunctionExpression<>(Functions::print, Object.class));
        add("println",           () -> new FunctionExpression<>(Functions::println, Object.class));
        add("prop$1",            () -> new EnvFunctionExpression<>(Functions::prop, String.class));
        add("prop$2",            () -> new EnvBiFunctionExpression<>(Functions::prop, String.class, Object.class));
        add("regexp_match$2",    () -> new BiFunctionExpression<>(Functions::regexp_match, String.class, String.class));
        add("regexp_match$3",    () -> new TriFunctionExpression<>(Functions::regexp_match, String.class, String.class, String.class));
        add("regexp_test$2",     () -> new BiFunctionExpression<>(Functions::regexp_test, String.class, String.class));
        add("regexp_test$3",     () -> new TriFunctionExpression<>(Functions::regexp_test, String.class, String.class, String.class));
        add("replace",           () -> new TriFunctionExpression<>(Functions::replace, String.class, String.class, String.class));
        add("rgb",               () -> new TriFunctionExpression<>(Functions::rgb, Float.class, Float.class, Float.class));
        add("rgba",              RGBAFunctionExpression::new);
        add("role",              () -> new EnvSupplierExpression(Functions::role));
        add("setting",           () -> new EnvFunctionExpression<>(Functions::setting, String.class));
        add("sort",              SortFunctionExpression::new);
        add("sort_list",         () -> new FunctionExpression<>(Functions::sort_list, List.class));
        add("split",             () -> new BiFunctionExpression<>(Functions::split, String.class, String.class));
        add("str",               () -> new FunctionExpression<>(s -> s, String.class));
        add("substring$2",       () -> new BiFunctionExpression<>(Functions::substring, String.class, Float.class));
        add("substring$3",       () -> new TriFunctionExpression<>(Functions::substring, String.class, Float.class, Float.class));
        add("sum",               () -> new FunctionExpression<>(Functions::sum, List.class));
        add("tag",               () -> new EnvFunctionExpression<>(Functions::tag, String.class));
        add("tag_regex$1",       () -> new EnvFunctionExpression<>(Functions::tag_regex, String.class));
        add("tag_regex$2",       () -> new EnvBiFunctionExpression<>(Functions::tag_regex, String.class, String.class));
        add("title",             () -> new FunctionExpression<>(Functions::title, String.class));
        add("to_boolean",        () -> new FunctionExpression<>(Functions::to_boolean, String.class));
        add("to_byte",           () -> new FunctionExpression<>(Functions::to_byte, String.class));
        add("to_double",         () -> new FunctionExpression<>(Functions::to_double, String.class));
        add("to_float",          () -> new FunctionExpression<>(Functions::to_float, String.class));
        add("to_int",            () -> new FunctionExpression<>(Functions::to_int, String.class));
        add("to_long",           () -> new FunctionExpression<>(Functions::to_long, String.class));
        add("to_short",          () -> new FunctionExpression<>(Functions::to_short, String.class));
        add("tr",                TrFunctionExpression::new);
        add("uniq",              UniqFunctionExpression::new);
        add("trim_list",         () -> new FunctionExpression<>(Functions::trim_list, List.class));
        add("filter_list_regex", () -> new BiFunctionExpression<>(Functions::filter_list_regex, String.class, List.class));
        add("uniq_list",         () -> new FunctionExpression<>(Functions::uniq_list, List.class));
        add("waylength",         () -> new EnvSupplierExpression(Functions::waylength));

        add("inside",            () -> new EnvFunctionExpression<>(Functions::inside, String.class));
        add("outside",           () -> new EnvFunctionExpression<>(Functions::outside, String.class));
        add("center",            () -> new EnvSupplierExpression(Functions::center));
        add("at",                () -> new EnvBiFunctionExpression<>(Functions::at, Double.class, Double.class));
        add("is_right_hand_traffic", () -> new EnvSupplierExpression(Functions::is_right_hand_traffic));

        add("plus",       () -> new MathOperator((a, b) -> a + b));
        add("minus$1",    MinusOperator::new);
        add("minus",      () -> new MathOperator((a, b) -> a - b));
        add("times",      () -> new MathOperator((a, b) -> a * b));
        add("divided_by", () -> new MathOperator((a, b) -> a / b));
        add("mod",        () -> new MathOperator((a, b) -> a % b));
        add("max",        () -> new MathOperator(Math::max, true));
        add("min",        () -> new MathOperator(Math::min, true));
        add("atan2",      () -> new MathOperator(Math::atan2));

        add("greater",       () -> new BiFunctionExpression<>((a, b) -> a > b,  Double.class, Double.class));
        add("less",          () -> new BiFunctionExpression<>((a, b) -> a < b,  Double.class, Double.class));
        add("greater_equal", () -> new BiFunctionExpression<>((a, b) -> a >= b, Double.class, Double.class));
        add("less_equal",    () -> new BiFunctionExpression<>((a, b) -> a <= b, Double.class, Double.class));

        add("not",    () -> new FunctionExpression<>(a -> !a, Boolean.class));
        add("trim",   () -> new FunctionExpression<>(Functions::trim, String.class));
        add("lower",  () -> new FunctionExpression<>(Functions::lower, String.class));
        add("upper",  () -> new FunctionExpression<>(Functions::upper, String.class));
        add("cardinal_to_radians", () -> new FunctionExpression<>(Functions::cardinal_to_radians, String.class));
        add("degree_to_radians",   () -> new FunctionExpression<>(Functions::degree_to_radians, Double.class));

        add("abs",    () -> new FunctionExpression<>(Math::abs,    Double.class));
        add("signum", () -> new FunctionExpression<>(Math::signum, Double.class));
        add("sqrt",   () -> new FunctionExpression<>(Math::sqrt,   Double.class));
        add("exp",    () -> new FunctionExpression<>(Math::exp,    Double.class));
        add("log",    () -> new FunctionExpression<>(Math::log,    Double.class));
        add("ceil",   () -> new FunctionExpression<>(Math::ceil,   Double.class));
        add("round",  () -> new FunctionExpression<>(Math::round,  Double.class));
        add("floor",  () -> new FunctionExpression<>(Math::floor,  Double.class));

        add("sin",    () -> new FunctionExpression<>(Math::sin,  Double.class));
        add("cos",    () -> new FunctionExpression<>(Math::cos,  Double.class));
        add("tan",    () -> new FunctionExpression<>(Math::tan,  Double.class));
        add("sinh",   () -> new FunctionExpression<>(Math::sinh, Double.class));
        add("cosh",   () -> new FunctionExpression<>(Math::cosh, Double.class));
        add("tanh",   () -> new FunctionExpression<>(Math::tanh, Double.class));
        add("asin",   () -> new FunctionExpression<>(Math::asin, Double.class));
        add("acos",   () -> new FunctionExpression<>(Math::acos, Double.class));
        add("atan",   () -> new FunctionExpression<>(Math::atan, Double.class));

        add("alpha",      () -> new FunctionExpression<>(Functions::alpha, Color.class));
        add("blue",       () -> new FunctionExpression<>(Functions::blue, Color.class));
        add("green",      () -> new FunctionExpression<>(Functions::green, Color.class));
        add("red",        () -> new FunctionExpression<>(Functions::red, Color.class));
        add("color2html", () -> new FunctionExpression<>(Functions::color2html, Color.class));
        add("html2color", () -> new FunctionExpression<>(Functions::html2color, String.class));

        add("cond",       CondOperator::new);
        add("and",        AndOperator::new);
        add("or",         OrOperator::new);

        add("random",     RandomFunctionExpression::new);
        add("eval",       EvalFunctionExpression::new);

        // new functions

        add("transform",          TransformFunctionExpression::new);
        add("translate",          TranslateFunctionExpression::new);
        add("rotate",             RotateFunctionExpression::new);
        add("scale",              ScaleFunctionExpression::new);
        add("skew",               SkewFunctionExpression::new);
        add("matrix",             MatrixFunctionExpression::new);

        add("heading",            HeadingFunctionExpression::new);
        add("metric",             MetricFunctionExpression::new);
        add("is_null",            IsNullFunctionExpression::new);

        add("auto_text",          FunctionAutoText::new);
        add("map_build",          MapBuilderExpression::new);
        add("map_get",            MapGetExpression::new);
        add("get_cond",           () -> new EnvFunctionExpression<>(Functions::get_cond, Integer.class));
        add("icon_reference",     IconReferenceExpression::new);
        add("image_provider",     ImageProviderExpression::new);
        add("split_traffic_sign", FunctionSplitTrafficSign::new);
        add("URL_query_encode$1", () -> new FunctionExpression<>(Functions::URL_query_encode, Map.class));
        add("URL_query_encode$2", () -> new BiFunctionExpression<>(Functions::URL_query_encode, String.class, List.class));
        add("zoom_level",         ZoomLevelFunctionExpression::new);
    }
    // CHECKSTYLE.ON: SingleSpaceSeparator

    private ExpressionFactory() {
        // Hide default constructor for utils classes
    }

    private static void add(String name, Supplier<CacheableExpression> newExpression) {
        expressionMap.put(name, newExpression);
    }

    /**
     * Main method to create an function-like expression.
     *
     * @param name the name of the function or operator
     * @param args the list of arguments (as expressions)
     * @param sheet the mapcss stylesheet
     * @param token the current parser token
     * @return the generated Expression. If no suitable function can be found,
     *         returns a {@code LiteralExpression} containing null
     * @throws MapCSSException if the function cannot be found
     */
    public static Expression createFunctionExpression(String name, List<Expression> args,
                MapCSSStyleSource sheet, Token token) {
        int argCount = args.size();
        String specializedName = name + "$" + argCount;
        // look for the specialized version first, eg. unary minus before generic minus
        Supplier<CacheableExpression> expressionSupplier = expressionMap.get(specializedName);
        if (expressionSupplier == null) {
            expressionSupplier = expressionMap.get(name);
            if (expressionSupplier == null) {
                MapCSSException ex = new MapCSSException(tr(
                    "MapCSS function ''{0}'' taking {1} arguments not found.", name, argCount));
                ex.setSource(sheet, token);
                throw ex;
            }
        }
        CacheableExpression expression = expressionSupplier.get();
        expression.setName(name); // for debugging
        expression.setArgs(args);
        expression.setBeginPos(sheet, token);
        return expression;
    }

    /**
     * Creates a {@link RelativeFloatExpression} for the {@link MapCSSParser}.
     *
     * @param f the float
     * @param property the property name
     * @param sheet the stylesheet
     * @param token the tokenbeing parsed
     * @return a RelativeFloatExpression
     */
    public static Expression createRelativeFloatExpression(Float f, String property, Subpart subPart,
                MapCSSStyleSource sheet, Token token) {
        String layer = MultiCascade.DEFAULT;

        // special-case "casing-width" as it refers to the property "width" on the same
        // layer
        if ("casing-width".equals(property)) {
            property = "width";
            layer = null;
        } else if (subPart == null) {
            // Prevent endless loops
            MapCSSException ex = new MapCSSException(tr("Relative float ''{0}: +{1}'' used on ''default'' subpart", property, f));
            ex.setSource(sheet, token);
            throw ex;
        }

        CacheableExpression expression = new RelativeFloatExpression(f, property, layer);
        expression.setName("+" + f); // for debugging
        expression.setBeginPos(sheet, token);
        return expression;

        // plus(prop(property, layer), f)

        /*
        Expression prop = createFunctionExpression("prop",
            Arrays.asList(
                LiteralExpression.create(property, sheet, token),
                LiteralExpression.create(layer, sheet, token)
            ), sheet, token);

        return createFunctionExpression("plus",
            Arrays.asList(
                prop,
                LiteralExpression.create(f, sheet, token)
            ), sheet, token);
        */
    }

    public abstract static class MapCSSExpression implements Expression {
        /** The mapcss source file */
        private String sourceUrl;
        /** The line in the mapcss source file */
        private int beginLine;
        /** The column in the mapcss source file */
        private int beginColumn;

        /**
         * This function must be overridden in subclasses to produce the actual result of
         * the Expression.  The Environment should be passed on from the root expression.
         * @param environment the environment
         *
         * @return the result of evaluating the Expression
         */
        abstract Object evalImpl(Environment environment);

        /**
         * Evaluates the expression.
         * @return the result of the evaluation, it's type depends on the expression
         */
        @Override
        public Object evaluate() {
            return null; // only literal expressions can evaluate w/o environment
        }

        /**
         * Evaluates the expression in the given environment.
         * @return the result of the evaluation, it's type depends on the expression
         */
        @Override
        public Object evaluate(Environment env) {
            return evalImpl(env);
        }

        /**
         * Evaluates the expression in the captured environment and converts it to the
         * given type.
         * @return the result of the evaluation or {@code def}
         */
        public <T> T evaluate(Class<T> klass, T def) {
            return null;
        }

        /**
         * Evaluates the expression in the given environment and converts it to the
         * given type.
         * @return the result of the evaluation or {@code def}
         */
        public <T> T evaluate(Environment env, Class<T> klass, T def) {
            T result = Cascade.convertTo(evaluate(env), klass);
            return result != null ? result : def;
        }

        @Override
        public void setBeginPos(String sourceUrl, int beginLine, int beginColumn) {
            this.sourceUrl = sourceUrl != null ? sourceUrl.intern() : null;
            this.beginLine = beginLine;
            this.beginColumn = beginColumn;
        }

        public void setBeginPos(MapCSSStyleSource sheet, Token token) {
            setBeginPos(
                sheet != null ? sheet.url : null,
                token != null ? token.beginLine : -1,
                token != null ? token.beginColumn : -1
            );
        }

        @Override
        public String getSourceLine() {
            return sourceUrl + ":" + beginLine + ":" + beginColumn;
        }

        @Override
        public String getSourceUrl() {
            return sourceUrl;
        }

        @Override
        public int getBeginLine() {
            return beginLine;
        }

        @Override
        public int getBeginColumn() {
            return beginColumn;
        }

        public static String formatSourceLine(MapCSSStyleSource sheet, Token token) {
            return sheet.url + ":" + token.beginLine + ":" + token.beginColumn;
        }

        public MapCSSException mapCSSException(String message) {
            MapCSSException ex = new MapCSSException(message);
            ex.setSource(getSourceUrl());
            ex.setLine(getBeginLine());
            ex.setColumn(getBeginColumn());
            return ex;
        }

        public MapCSSException mapCSSException(String message, Throwable cause) {
            MapCSSException ex = new MapCSSException(message, cause);
            ex.setSource(getSourceUrl());
            ex.setLine(getBeginLine());
            ex.setColumn(getBeginColumn());
            return ex;
        }

    }

    /**
     * A generic function wrapped as Expression.
     * <p>
     * Cacheability: If the function is IMMUTABLE then
     * {@link Instruction.AssignmentInstruction#execute execute} will evaluate the
     * function against the given Environment and store the <i>result</i> in a property of
     * the cascade.
     * <p>
     * If the function is STABLE or VOLATILE, the Expression with the captured
     * Environment will be stored in a property of the cascade, and must be evaluated at
     * painting time.
     * <p>
     * Notes: Cacheability is a new concept and should be expanded to include all
     * expressions.  To make use of the distinction between STABLE and VOLATILE an edit
     * counter could be implemented in the DataSet, so that STABLE functions can be
     * cached against the edit counter.
     */
    public abstract static class CacheableExpression extends MapCSSExpression {
        /** The name of the function; used only for debugging and error reporting. */
        String name;
        /** The arguments to the function */
        List<Expression> args = Collections.emptyList();
        /** The cacheablility of this expression */
        Cacheability cacheability = Cacheability.IMMUTABLE;
        /** The minimum legal number of arguments */
        private Integer minArgs = 0;
        /** The maximum legal number of arguments */
        private @Nullable Integer maxArgs = null;
        /** The cached result or {@code null} */
        Object cached = null;

        /**
         * Constructor
         * @param name the name of the function in mapcss (used for debugging only)
         * @param args the argument list
         */
        CacheableExpression(Cacheability cacheability, Integer minArgs, Integer maxArgs) {
            this.cacheability = cacheability;
            this.minArgs = minArgs == null ? 0 : minArgs;
            this.maxArgs = maxArgs;
        }

        /**
         * Evaluates the expression in the given environment and eventually caches the
         * result.
         * <p>
         * Note: an expression that depends on the environment should never be IMMUTABLE
         * and will not be cached.
         *
         * @return the result of the evaluation or {@code null}
         */
        @Override
        public Object evaluate(Environment environment) {
            if (cached != null)
                 return cached;
            if (environment == null)
                return null;
            Object o;
            try {
                o = evalImpl(environment);
            } catch (MapCSSException e) {
                throw e;
            } catch (Exception e) {
                throw mapCSSException("MapCSS Runtime error", e);
            }
            if (cacheability == Cacheability.IMMUTABLE) {
                cached = o;
                if (cached != null)
                    // if the result is cached we don't need the expression arguments anymore
                    // frees some memory
                    args = Collections.emptyList();
            }
            return o;
        }

        /**
         * Return the argument list
         * @return the argument list
         */
        public List<Expression> getArgs() {
            return Collections.unmodifiableList(args);
        }

        /**
         * Sets the arguments to the function.
         * @param args the arguments
         * @return {@code this}
         */
        CacheableExpression setArgs(@Nullable List<Expression> args) {
            if (args != null)
                this.args = args;
            checkArgsSize();
            // reduce cacheability to the least cacheable among the arguments
            for (Expression arg : this.args) {
                this.cacheability = leastCacheable(this.cacheability, arg.getCacheability());
            }
            return this;
        }

        public String getName() {
            return name;
        }

        CacheableExpression setName(String name) {
            this.name = name;
            return this;
        }

        public String toString() {
            return getName() + "(" + args.stream().map(Object::toString).collect(Collectors.joining(", ")) + ")";
        }

        /**
         * Returns the least cacheable of two cacheabilities
         * @param a a cacheability
         * @param b a cacheability
         * @return the least cacheable of both
         */
        Cacheability leastCacheable(Cacheability a, Cacheability b) {
            return a.compareTo(b) > 0 ? a : b;
        }

        @Override
        public Cacheability getCacheability() {
            return cacheability;
        }

        /**
         * Checks the number of actual arguments
         * @param minExpected the minumum expected number of arguments
         * @param maxExpected the maximum expected number of arguments
         * @throws IllegalArgumentException if there are more or less arguments than that
         */
        void checkArgsSize() throws IllegalArgumentException {
            if ((args.size() < minArgs) || (maxArgs != null && args.size() > maxArgs))
                throw new IllegalArgumentException(tr(
                    "Wrong number of arguments in function: ''{0}''. Expected between {1} and {2}, but got {3}.\n" +
                    "... at {4}",
                    name, minArgs, maxArgs, args.size(), getSourceLine()));
        }

        /*
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof CacheableExpression))
                return false;
            CacheableExpression other = (CacheableExpression) obj;
            if (cached != null && other.cached != null)
                return cached.equals(other.cached);
            return super.equals(obj);
        }

        @Override
        public int hashCode() {
            if (cached != null)
                return cached.hashCode();
            return super.hashCode();
        }
        */

        /**
         * Returns the argument at position {@code index} or {@code null}.
         * @param env the environment
         * @param index the index of the argument
         * @return the argument as {@code String} or {@code null} if the argument was not found
         *         or the conversion failed
         */
        String argAsString(Environment env, int index) {
            if (args.size() <= index) return null;
            return Cascade.convertTo(args.get(index).evaluate(env), String.class);
        }

        /**
         * Returns the argument at position {@code index} or {@code null}.
         * @param env the environment
         * @param index the index of the argument
         * @param def the default value
         * @return the argument as {@code Double} or {@code null} if the argument was not found
         *         or the conversion failed
         */
        Double argAsDouble(Environment env, int index, Double def) {
            Double result = null;
            if (args.size() > index) {
                result = Cascade.convertTo(args.get(index).evaluate(env), Double.class);
            }
            return result == null ? def : result;
        }
    }

    /**
     * An expression that captures the execution environment.
     */
    public static class RootExpression implements Expression {
        Expression expression;
        Environment environment;

        RootExpression(Expression expression, Environment environment) {
            this.expression = expression;
            this.environment = environment;
        }

        /**
         * Evaluates the expression.
         * @return the result of the evaluation, it's type depends on the expression
         */
        @Override
        public Object evaluate() {
            return expression.evaluate(environment);
        }

        /**
         * Evaluates the expression in the given environment.
         * @return the result of the evaluation, it's type depends on the expression
         */
        @Override
        public Object evaluate(Environment env) {
            return expression.evaluate(env);
        }

        /**
         * Evaluates the expression in the captured environment and converts it to the
         * given type.
         * @return the result of the evaluation or {@code def}
         */
        public <T> T evaluate(Class<T> klass, T def) {
            T result = Cascade.convertTo(expression.evaluate(environment), klass);
            return result != null ? result : def;
        }

        public RootExpression setEnvironment(Environment environment) {
            this.environment = environment;
            return this;
        }

        public Environment getEnvironment() {
            return environment;
        }
    }

    /**
     * Expression representing a relative float, eg. {@code width: +1;}
     *
     */
    public static class RelativeFloatExpression extends CacheableExpression {
        final float f;
        final String property;
        final String layer;

        /**
         * @param f the float to add
         * @param property the name of the property
         */
        RelativeFloatExpression(float f, String property, String layer) {
            super(Cacheability.STABLE, 0, 0);
            this.f = f;
            this.property = property;
            this.layer = layer;
        }

        @Override
        public Object evalImpl(Environment env) {
            Float base = Cascade.convertTo(env.getCascade(layer).get(property), Float.class);
            if (base == null) {
                Logging.warn(tr(
                    "{3}: ''{1}: +{0}'': there is no ''{1}'' on the ''{2}'' layer.",
                    f, property, layer, getSourceLine()));
                // throw mapCSSException(tr(
                //     "Relative float ''+{0}'': there is no ''{1}'' on the ''{2}'' layer.", f, property, layer));
                return f;
            }
            return base + f;
        }
    }

    /**
     * Expression representing a supplier of arbitrary type.
     *
     * @params f the function to call
     * @params klass the type of the argument to function f
     */
    static class SupplierExpression extends CacheableExpression {
        Supplier<Object> f;

        SupplierExpression(Supplier<Object> f) {
            super(Cacheability.IMMUTABLE, 0, 0);
            this.f = f;
        }

        @Override
        public Object evalImpl(Environment env) {
            return f.get();
        }
    }

    /**
     * Expression representing a supplier of arbitrary type taking env as argument.
     *
     * @params f the function to call
     * @params klass the type of the argument to function f
     */
    static class EnvSupplierExpression extends CacheableExpression {
        Function<Environment, Object> f;

        EnvSupplierExpression(Function<Environment, Object> fe) {
            super(Cacheability.STABLE, 0, 0);
            this.f = fe;
        }

        @Override
        public Object evalImpl(Environment env) {
            return f.apply(env);
        }
    }

    /**
     * Expression representing a function taking 1 argument
     *
     * @params f the function to call
     * @params klass the type of the argument to function f
     */
    static class FunctionExpression<T> extends CacheableExpression {
        Function<T, Object> f;
        final Class<T> klass;

        FunctionExpression(Function<T, Object> f, Class<T> klass) {
            super(Cacheability.IMMUTABLE, 1, 1);
            this.f = f;
            this.klass = klass;
        }

        @Override
        public Object evalImpl(Environment env) {
            T arg = Cascade.convertTo(args.get(0).evaluate(env), klass);
            return f.apply(arg);
        }
    }

    /**
     * Expression representing a function taking env + 1 argument
     *
     * @params f the function to call
     * @params klass the type of the second argument to function f
     */
    static class EnvFunctionExpression<T> extends CacheableExpression {
        BiFunction<Environment, T, Object> f;
        final Class<T> klass;

        EnvFunctionExpression(BiFunction<Environment, T, Object> f, Class<T> klass) {
            super(Cacheability.STABLE, 1, 1);
            this.f = f;
            this.klass = klass;
        }

        @Override
        public Object evalImpl(Environment env) {
            T arg = Cascade.convertTo(args.get(0).evaluate(env), klass);
            return f.apply(env, arg);
        }
    }

    /**
     * Expression representing a function taking 2 arguments
     */
    static class BiFunctionExpression<T, U> extends CacheableExpression {
        BiFunction<T, U, Object> f;
        final Class<T> klass1;
        final Class<U> klass2;

        BiFunctionExpression(BiFunction<T, U, Object> f, Class<T> klass1, Class<U> klass2) {
            super(Cacheability.IMMUTABLE, 2, 2);
            this.f = f;
            this.klass1 = klass1;
            this.klass2 = klass2;
        }

        @Override
        public Object evalImpl(Environment env) {
            T arg1 = Cascade.convertTo(args.get(0).evaluate(env), klass1);
            U arg2 = Cascade.convertTo(args.get(1).evaluate(env), klass2);
            return f.apply(arg1, arg2);
        }
    }

    /**
     * Expression representing a function taking env + 2 arguments
     */
    static class EnvBiFunctionExpression<T, U> extends CacheableExpression {
        TriFunction<Environment, T, U, Object> f;
        final Class<T> klass1;
        final Class<U> klass2;

        EnvBiFunctionExpression(TriFunction<Environment, T, U, Object> f, Class<T> klass1, Class<U> klass2) {
            super(Cacheability.STABLE, 2, 2);
            this.f = f;
            this.klass1 = klass1;
            this.klass2 = klass2;
        }

        @Override
        public Object evalImpl(Environment env) {
            T arg1 = Cascade.convertTo(args.get(0).evaluate(env), klass1);
            U arg2 = Cascade.convertTo(args.get(1).evaluate(env), klass2);
            return f.apply(env, arg1, arg2);
        }
    }

    /**
     * Expression representing a function taking 3 arguments
     */
    static class TriFunctionExpression<T, U, V> extends CacheableExpression {
        TriFunction<T, U, V, Object> f;
        final Class<T> klass1;
        final Class<U> klass2;
        final Class<V> klass3;

        TriFunctionExpression(TriFunction<T, U, V, Object> f, Class<T> klass1, Class<U> klass2, Class<V> klass3) {
            super(Cacheability.IMMUTABLE, 3, 3);
            this.f = f;
            this.klass1 = klass1;
            this.klass2 = klass2;
            this.klass3 = klass3;
        }

        @Override
        public Object evalImpl(Environment env) {
            T arg1 = Cascade.convertTo(args.get(0).evaluate(env), klass1);
            U arg2 = Cascade.convertTo(args.get(1).evaluate(env), klass2);
            V arg3 = Cascade.convertTo(args.get(2).evaluate(env), klass3);
            return f.apply(arg1, arg2, arg3);
        }
    }

    /**
     * Returns {@code true} if the argument is {@code null}.
     * <p>
     * Example usage:
     * <pre>
     *   node[direction=forward][is_null(heading())]
     * </pre>
     */
    static class IsNullFunctionExpression extends CacheableExpression {
        IsNullFunctionExpression() {
            super(Cacheability.IMMUTABLE, 1, 1);
        }

        @Override
        public Object evalImpl(Environment env) {
            return args.get(0).evaluate(env) == null;
        }
    }

    static class EvalFunctionExpression extends CacheableExpression {
        EvalFunctionExpression() {
            super(Cacheability.IMMUTABLE, 1, 1);
        }

        @Override
        public Object evalImpl(Environment env) {
            return args.get(0).evaluate(env);
        }
    }

    static class RandomFunctionExpression extends CacheableExpression {
        RandomFunctionExpression() {
            // Note: IMMUTABLE is the legacy behaviour, should arguably be VOLATILE instead
            super(Cacheability.IMMUTABLE, 0, 0);
        }

        @Override
        public Object evalImpl(Environment env) {
            return Math.random();
        }
    }

    /**
     * Converts the given length in meters into map units
     * <p>
     * Example: {@code metric(3)} yields 1.5 at a zoom scale of 2m = 1px
     * <p>
     * {@code icon-offset-x: metric(3)} offsets the icon 3 meters
     */
    static class MetricFunctionExpression extends CacheableExpression {
        MetricFunctionExpression() {
            super(Cacheability.VOLATILE, 1, 1);
        }

        @Override
        public Object evalImpl(Environment env) {
            Double metric = argAsDouble(env, 0, null); // represents a length in m
            if (metric == null) return null;
            return (env.nc == null) ? null : metric * 100 / env.nc.getDist100Pixel(false);
        }
    }

    /**
     * Returns the current zoom level
     * <p>
     * Example: {@code zoom_level()} yields 15 at zoom level 15
     * @return the current zoom level as int
     */
    static class ZoomLevelFunctionExpression extends CacheableExpression {
        ZoomLevelFunctionExpression() {
            super(Cacheability.VOLATILE, 0, 0);
        }

        @Override
        public Object evalImpl(Environment env) {
            return (env.nc == null) ? null : GeneralSelector.scale2level(env.nc.getScale());
        }
    }

    /**
     * Returns the heading of the node or {@code null}.
     * <p>
     * A heading exists only if the node is part of at most one incoming way segment and
     * one outgoing way segment.  A node that is part of three or more way segments
     * cannot have a heading.
     * <p>
     * If the rule was matched by a parent > child selector this function will consider
     * only parent ways that match the parent selector. Consider the rules:
     * <pre>
     *   way[highway]  > node[ford=yes] { icon-rotation: heading(); }
     *   way[waterway] > node[ford=yes] { icon-rotation: heading(); }
     * </pre>
     * The first rule will rotate the icon in relation to the highway, the second one in
     * relation to the waterway.
     * <p>
     * The function can take zero or one parameters. The value of the parameter is added
     * to the heading.  To get the backward heading use:
     * <pre>
     *   icon-rotation: heading(0.5turn);
     * </pre>
     * <p>
     * This function returns the projected heading, not the great-circle bearing.
     *
     * @see EastNorth#heading(EastNorth)
     */
    static class HeadingFunctionExpression extends CacheableExpression {
        HeadingFunctionExpression() {
            super(Cacheability.STABLE, 0, 1);
        }

        @Override
        public Object evalImpl(Environment env) {
            if (env.osm instanceof Node) {
                Node n = (Node) env.osm;
                Node before = n;
                Node after = n;
                int incoming = 0;
                int outgoing = 0;
                for (Way way : n.getParentWays()) {
                    if (env.left != null && !env.left.matches(new Environment(way))) {
                        continue;
                    }
                    for (Pair<Node, Node> p : way.getNodePairs(false)) {
                        if (p.b == n) {
                            before = p.a;
                            incoming++;
                        }
                        if (p.a == n) {
                            after = p.b;
                            outgoing++;
                        }
                    }
                }
                if (incoming <= 1 && outgoing <= 1) {
                    EastNorth a = before.getEastNorth();
                    EastNorth b = after.getEastNorth();
                    Double refHeading = argAsDouble(env, 0, 0d);
                    if (!a.equalsEpsilon(b, 1e-7))
                        return a.heading(b, refHeading);
                }
            }
            return null;
        }
    }

    /**
     * Returns a supplier of an affine transformation matrix.
     * <p>
     * The arguments to this function are one or more of the CSS transform functions to
     * be applied. The transform functions are multiplied in order from left to right,
     * meaning that composite transforms are effectively applied in order from right to
     * left.
     * <pre>
     *   text-transform: transform(translate(x, y));
     *   text-transform: transform(translate(-x, -y), rotate(0.5turn), translate(x, y));
     * </pre>
     */
    static class TransformFunctionExpression extends CacheableExpression {
        TransformFunctionExpression() {
            super(Cacheability.IMMUTABLE, 1, null);
        }

        @Override
        public AffineTransform evalImpl(Environment env) {
            if (env.osm instanceof Node) {
                AffineTransform at = new AffineTransform();
                for (Expression arg : args) {
                    Object tmp = arg.evaluate(env);
                    if (tmp instanceof AffineTransform)
                        at.concatenate((AffineTransform) tmp);
                }
                return at;
            }
            return null;
        }
    }

    static class TranslateFunctionExpression extends CacheableExpression {
        TranslateFunctionExpression() {
            super(Cacheability.IMMUTABLE, 2, 2);
        }

        @Override
        public Object evalImpl(Environment env) {
            if (env.osm instanceof Node) {
                AffineTransform at = new AffineTransform();
                at.translate(argAsDouble(env, 0, 0d), argAsDouble(env, 1, 0d));
                return at;
            }
            return null;
        }
    }

    static class RotateFunctionExpression extends CacheableExpression {
        RotateFunctionExpression() {
            super(Cacheability.IMMUTABLE, 1, 1);
        }

        @Override
        public Object evalImpl(Environment env) {
            if (env.osm instanceof Node) {
                AffineTransform at = new AffineTransform();
                at.rotate(argAsDouble(env, 0, 0d));
                return at;
            }
            return null;
        }
    }

    static class ScaleFunctionExpression extends CacheableExpression {
        ScaleFunctionExpression() {
            super(Cacheability.IMMUTABLE, 1, 2);
        }

        @Override
        public Object evalImpl(Environment env) {
            if (env.osm instanceof Node) {
                AffineTransform at = new AffineTransform();
                if (args.size() == 1)
                    at.scale(argAsDouble(env, 0, 0d), argAsDouble(env, 0, 0d));
                if (args.size() == 2)
                    at.scale(argAsDouble(env, 0, 0d), argAsDouble(env, 1, 0d));
                return at;
            }
            return null;
        }
    }

    static class SkewFunctionExpression extends CacheableExpression {
        SkewFunctionExpression() {
            super(Cacheability.IMMUTABLE, 2, 2);
        }

        @Override
        public Object evalImpl(Environment env) {
            if (env.osm instanceof Node) {
                AffineTransform at = new AffineTransform();
                at.shear(argAsDouble(env, 0, 0d), argAsDouble(env, 1, 0d));
                return at;
            }
            return null;
        }
    }

    static class MatrixFunctionExpression extends CacheableExpression {
        MatrixFunctionExpression() {
            super(Cacheability.IMMUTABLE, 6, 6);
        }

        @Override
        public Object evalImpl(Environment env) {
            if (env.osm instanceof Node) {
                return new AffineTransform(
                    argAsDouble(env, 0, 0d),
                    argAsDouble(env, 1, 0d),
                    argAsDouble(env, 2, 0d),
                    argAsDouble(env, 3, 0d),
                    argAsDouble(env, 4, 0d),
                    argAsDouble(env, 5, 0d)
                );
            }
            return null;
        }
    }

    /**
     * Conditional operator.
     */
    public static class CondOperator extends CacheableExpression {
        /**
         * Constructs a new {@code CondOperator}.
         */
        public CondOperator() {
            super(Cacheability.IMMUTABLE, 3, 3);
        }

        @Override
        public Object evalImpl(Environment env) {
            Boolean b = Cascade.convertTo(args.get(0).evaluate(env), Boolean.class);
            if (b != null && b)
                return args.get(1).evaluate(env);
            else
                return args.get(2).evaluate(env);
        }
    }

    /**
     * "And" logical operator.
     */
    public static class AndOperator extends CacheableExpression {
        /**
         * Constructs a new {@code AndOperator}.
         */
        public AndOperator() {
            super(Cacheability.IMMUTABLE, 1, null);
        }

        @Override
        public Object evalImpl(Environment env) {
            return args.stream()
                    .map(arg -> Cascade.convertTo(arg.evaluate(env), Boolean.class))
                    .allMatch(Boolean.TRUE::equals);
        }
    }

    /**
     * "Or" logical operator.
     */
    public static class OrOperator extends CacheableExpression {
        /**
         * Constructs a new {@code OrOperator}.
         */
        public OrOperator() {
            super(Cacheability.IMMUTABLE, 1, null);
        }

        @Override
        public Object evalImpl(Environment env) {
            return args.stream()
                    .map(arg -> Cascade.convertTo(arg.evaluate(env), Boolean.class))
                    .anyMatch(Boolean.TRUE::equals);
        }
    }

    /**
     * Binary Math operator.
     */
    public static class MathOperator extends CacheableExpression {
        private BinaryOperator<Double> f;
        boolean useMinMaxNullSemantics = false;

        /**
         * Constructor
         * @param f the binary math operator
         */
        public MathOperator(BinaryOperator<Double> f) {
            super(Cacheability.IMMUTABLE, 1, null);
            this.f = f;
        }

        public MathOperator(BinaryOperator<Double> f, boolean useMinMaxNullSemantics) {
            super(Cacheability.IMMUTABLE, 1, null);
            this.f = f;
            this.useMinMaxNullSemantics = useMinMaxNullSemantics;
        }

        @Override
        public Object evalImpl(Environment env) {
            Double result = null;
            Iterator<Object> i = null;

            try {
                if (args.size() == 1) {
                    // special case: the first and only argument is a list of Object
                    i = Cascade.convertTo(args.get(0).evaluate(env), List.class).iterator();
                }
                if (i == null) {
                    // normal case: the arguments are expressions yielding Object
                    i = args.stream().map(e -> e.evaluate(env)).iterator();
                }
                while (i.hasNext()) {
                    Double next = Cascade.convertTo(i.next(), Double.class);
                    if (result == null)
                        result = next; // first argument
                    // the MapCSS spec is ambiguous here: it says that none's in number
                    // operations behave like 0's, but it also says that: min(3, 5, "") => 3
                    else if (!useMinMaxNullSemantics && next == null)
                        result = f.apply(result, 0d);
                    else if (next != null)
                        result = f.apply(result, next);
                }
            } catch (ArithmeticException | NullPointerException e) {
                Logging.log(Logging.LEVEL_ERROR, "Exception while applying MathOperator: " + toString() + "\n", e);
                return null;
            }
            return result;
        }
    }

    /**
     * Unary minus operator
     */
    public static class MinusOperator extends CacheableExpression {
        MinusOperator() {
            super(Cacheability.IMMUTABLE, 1, 1);
        }

        @Override
        public Object evalImpl(Environment env) {
            Double result = Cascade.convertTo(args.get(0).evaluate(env), Double.class);
            if (result == null)
                return null;
            return -result;
        }
    }

    /**
     * Returns the concatenation of the given strings.
     * <p>
     * The function takes one or more arguments. Arguments that do not convert into
     * String are ignored.
     * @see Collectors#joining
     */
    public static class ConcatFunctionExpression extends CacheableExpression {
        public ConcatFunctionExpression() {
            super(Cacheability.IMMUTABLE, 1, null);
        }

        @Override
        public Object evalImpl(Environment env) {
            return args.stream()
                .map(arg -> Cascade.convertTo(arg.evaluate(env), String.class))
                .filter(Objects::nonNull)
                .collect(Collectors.joining());
        }
    }

    /**
     * Returns the concatenation of the given strings using a separator.
     * <p>
     * The function takes two or more arguments. The first argument is the separator and
     * must not be {@code null}.  The following arguments are the strings to be joined.
     * If they do not convert into String they are ignored.
     * @see Collectors#joining
     */
    public static class JoinFunctionExpression extends CacheableExpression {
        public JoinFunctionExpression() {
            super(Cacheability.IMMUTABLE, 2, null);
        }

        @Override
        public Object evalImpl(Environment env) {
            String separator = argAsString(env, 0);
            return args.stream()
                .skip(1)
                .map(arg -> Cascade.convertTo(arg.evaluate(env), String.class))
                .filter(Objects::nonNull)
                .collect(Collectors.joining(separator));
        }
    }

    /**
     * Builds a map from key, value pairs.
     * <p>
     * The arguments are interleaved keys and values.
     * <p>
     * A map is immutable.
     */
    public static class MapBuilderExpression extends CacheableExpression {
        public MapBuilderExpression() {
            super(Cacheability.IMMUTABLE, 0, null);
        }

        @Override
        CacheableExpression setArgs(@Nullable List<Expression> args) {
            if (args != null && args.size() % 2 != 0)
                throw new IllegalArgumentException(tr(
                    "Expected an even number of arguments in function: ''{0}'' but got {1}.\n" +
                    "... at {2}",
                    name, args.size(), getSourceLine()));
            return super.setArgs(args);
        }

        @Override
        public Object evalImpl(Environment env) {
            Map<String, Object> map = new HashMap<>();
            for (int i = 0; i < args.size(); i += 2) {
                String key = Cascade.convertTo(args.get(i).evaluate(env), String.class);
                map.put(key, args.get(i + 1).evaluate(env));
            }
            return map;
        }
    }

    /**
     * Gets a value from a map.
     * <p>
     * Arguments: map, key[, default].
     */
    public static class MapGetExpression extends CacheableExpression {
        public MapGetExpression() {
            super(Cacheability.IMMUTABLE, 2, 3);
        }

        @Override
        public Object evalImpl(Environment env) {
            Map<String, Object> map = Cascade.convertTo(args.get(0).evaluate(env), Map.class);
            if (map == null)
                return null;
            String key = Cascade.convertTo(args.get(1).evaluate(env), String.class);
            if (key == null)
                return null;
            if (args.size() == 3) {
                String def = Cascade.convertTo(args.get(2).evaluate(env), String.class);
                return map.getOrDefault(key, def);
            }
            return map.get(key);
        }
    }

    /**
     * Returns the bounding box of the icon.
     *
     * @return the bounding box as list of left, top, right, bottom
    public static class BoundingBoxExpression extends CacheableExpression {
        public BoundingBoxExpression() {
            super(Cacheability.VOLATILE, 0, 0);
        }

        @Override
        public Object evalImpl(Environment env) {
            MapImage img = NodeElement.createIcon(env);
            Logging.info("Getting the bounding box: {0}", img.getHeight());
            return Arrays.asList(0, 0, img.getWidth(), img.getHeight());
        }
    }
    */

    /**
     * Returns the count of the members of a relation that have one of the given roles
     * @see Functions#count_roles
     */
    public static class CountRolesFunctionExpression extends CacheableExpression {
        public CountRolesFunctionExpression() {
            super(Cacheability.IMMUTABLE, 1, null);
        }

        @Override
        public Object evalImpl(Environment env) {
            return Functions.count_roles(env,
                args.stream()
                    .map(arg -> Cascade.convertTo(arg.evaluate(env), String.class))
                    .toArray(String[]::new));
        }
    }

    /**
     * Returns a color from the given R, G, B, and A values
     * @see Functions#rgba
     */
    public static class RGBAFunctionExpression extends CacheableExpression {
        public RGBAFunctionExpression() {
            super(Cacheability.IMMUTABLE, 4, 4);
        }

        @Override
        public Object evalImpl(Environment env) {
            Float r = Cascade.convertTo(args.get(0).evaluate(env), Float.class);
            Float g = Cascade.convertTo(args.get(1).evaluate(env), Float.class);
            Float b = Cascade.convertTo(args.get(2).evaluate(env), Float.class);
            Float a = Cascade.convertTo(args.get(3).evaluate(env), Float.class);
            return Functions.rgba(r, g, b, a);
        }
    }

    /**
     * Translates the string
     * <p>
     * Translates the string for the current locale. The first argument is the text to
     * translate, and the subsequent arguments are parameters for the string indicated
     * by <code>{0}</code>, <code>{1}</code>, …
     * @see Functions#tr
     */
    public static class TrFunctionExpression extends CacheableExpression {
        public TrFunctionExpression() {
            super(Cacheability.IMMUTABLE, 1, null);
        }

        @Override
        public Object evalImpl(Environment env) {
            String text = argAsString(env, 0);
            return org.openstreetmap.josm.tools.I18n.tr(text,
                args.stream().skip(1)
                    .map(arg -> Cascade.convertTo(arg.evaluate(env), String.class))
                    .toArray(Object[]::new));
        }
    }

    /**
     * Returns a sorted list of the given arguments as strings.
     * <p>
     * Arbitrary many strings may be passed as arguments. Arguments that do not convert
     * to String are ignored.
     */
    public static class SortFunctionExpression extends CacheableExpression {
        public SortFunctionExpression() {
            super(Cacheability.IMMUTABLE, 1, null);
        }

        @Override
        public Object evalImpl(Environment env) {
            return args.stream()
                .map(arg -> Cascade.convertTo(arg.evaluate(env), String.class))
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());
        }
    }

    /**
     * Returns a list of the given strings with duplicates removed.
     * <p>
     * Arbitrary many strings may be passed as arguments. Arguments that do not convert
     * to String are ignored. For unsorted argument lists no stability guarantees are
     * given.
     */
    public static class UniqFunctionExpression extends CacheableExpression {
        public UniqFunctionExpression() {
            super(Cacheability.IMMUTABLE, 1, null);
        }

        @Override
        public Object evalImpl(Environment env) {
            return args.stream()
                .map(arg -> Cascade.convertTo(arg.evaluate(env), String.class))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        }
    }

    /**
     * Returns the first non-null object or {@code null}.
     * <p>
     * The name originates from <a href="http://wiki.openstreetmap.org/wiki/MapCSS/0.2/eval">MapCSS standard</a>.
     */
    public static class AnyFunctionExpression extends CacheableExpression {
        public AnyFunctionExpression() {
            super(Cacheability.IMMUTABLE, 1, null);
        }

        @Override
        public Object evalImpl(Environment env) {
            return args.stream()
                .map(arg -> arg.evaluate(env))
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
        }
    }

    /**
     * Returns the arguments as a list of Objects
     * <p>
     * An arbitrary number of arguments will be evaluated and the results will be put
     * into a list.
     */
    public static class AsListFunctionExpression extends CacheableExpression {
        public AsListFunctionExpression() {
            super(Cacheability.IMMUTABLE, 1, null);
        }

        @Override
        public Object evalImpl(Environment env) {
            return args.stream()
                .map(arg -> arg.evaluate(env))
                .collect(Collectors.toList());
        }
    }

    public static class ImageProviderExpression extends CacheableExpression {

        public ImageProviderExpression() {
            super(Cacheability.IMMUTABLE, 2, 2);
        }

        @Override
        public Object evalImpl(Environment env) {
            final String iconName = argAsString(env, 0);
            final StyleSource source = Cascade.convertTo(args.get(1).evaluate(env), StyleSource.class);
            final String namespace = source.getPrefName();

            return new ImageProvider(iconName)
                    .setDirs(MapPaintStyles.getIconSourceDirs(source))
                    .setId("mappaint." + namespace)
                    .setArchive(source.zipIcons)
                    .setInArchiveDir(source.getZipEntryDirName())
                    .setOptional(true);
        }
    }

    public static class IconReferenceExpression extends CacheableExpression {

        public IconReferenceExpression() {
            super(Cacheability.IMMUTABLE, 2, 2);
        }

        @Override
        public Object evalImpl(Environment env) {
            final String iconName = argAsString(env, 0);
            final StyleSource source = Cascade.convertTo(args.get(1).evaluate(env), StyleSource.class);

            return new IconReference(iconName, source);
        }

        @Override
        public String toString() {
            return getName() + "(" + args.get(0).toString() + ", sheet)";
        }
    }
}
