// License: GPL. For details, see LICENSE file.

package org.openstreetmap.josm.gui.mappaint.mapcss;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.gui.mappaint.Environment;
import org.openstreetmap.josm.gui.mappaint.mapcss.ExpressionFactory.CacheableExpression;
import org.openstreetmap.josm.tools.Utils;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.spi.preferences.PreferenceChangeEvent;
import org.openstreetmap.josm.spi.preferences.PreferenceChangedListener;
import org.openstreetmap.josm.tools.LanguageInfo;


/**
 * Function that searches a list of configurable keys for the text to display.
 * <p>
 * The list of keys can be configured in the JOSM preferences.  See the preference
 * options <code>mappaint.nameOrder</code> and <code>mappaint.nameComplementOrder</code>
 *
 * @since  3987 (creation as {@code DeriveLabelFromNameTagsCompositionStrategy})
 * @since 10599 (functional interface)
 * @since   xxx (implemented as mapcss function {@code auto_text()})
 */
public class FunctionAutoText extends CacheableExpression {
    private static Listener listener = null;

    public FunctionAutoText() {
        super(Cacheability.STABLE, 0, 0);
    }

    @Override
    public Object evalImpl(Environment env) {
        return getText(env.osm);
    }

    public static String getText(IPrimitive osm) {
        if (!osm.hasKeys()) return null;

        if (listener == null) {
            listener = new Listener(); // NOSONAR we need prefs already initialized
        }

        StringBuilder name = new StringBuilder();

        for (String key : listener.nameTags) {
            String value = osm.get(key);
            if (value != null) {
                name.append(value);
                break;
            }
        }
        for (String key : listener.nameComplementTags) {
            String value = osm.get(key);
            if (value != null) {
                if (name.length() == 0) {
                    name.append(value);
                } else {
                    name.append(" (").append(value).append(')');
                }
                break;
            }
        }
        return name.toString();
    }

    static class Listener implements PreferenceChangedListener {

        /**
         * Hold the keysts name tags from the preferences.
         */
        public Listener() {
            Config.getPref().addPreferenceChangeListener(this);
            initNameTagsFromPreferences();
        }

        @Override
        public void preferenceChanged(PreferenceChangeEvent e) {
            if (e.getKey() != null && e.getKey().startsWith("mappaint.name")) {
                initNameTagsFromPreferences();
            }
        }

        /**
         * The list of default name tags from which a label candidate is derived.
         */
        private static final String[] DEFAULT_NAME_TAGS = {
            "name:" + LanguageInfo.getJOSMLocaleCode(),
            "name",
            "int_name",
            "distance",
            "railway:position",
            "ref",
            "operator",
            "brand",
            "addr:unit",
            "addr:flats",
            "addr:housenumber"
        };

        /**
         * The list of default name complement tags from which a label candidate is derived.
         */
        private static final String[] DEFAULT_NAME_COMPLEMENT_TAGS = {
            "capacity"
        };

        private List<String> nameTags = new ArrayList<>();
        private List<String> nameComplementTags = new ArrayList<>();

        private static List<String> buildNameTags(List<String> nameTags) {
            if (nameTags == null) {
                return new ArrayList<>();
            }
            return nameTags.stream()
                    .filter(tag -> !Utils.isStripEmpty(tag))
                    .collect(Collectors.toList());
        }

        /**
         * Sets the name tags to be looked up in order to build up the label.
         *
         * @param nameTags the name tags. null values are ignored.
         */
        public void setNameTags(List<String> nameTags) {
            this.nameTags = buildNameTags(nameTags);
        }

        /**
         * Sets the name complement tags to be looked up in order to build up the label.
         *
         * @param nameComplementTags the name complement tags. null values are ignored.
         * @since 6541
         */
        public void setNameComplementTags(List<String> nameComplementTags) {
            this.nameComplementTags = buildNameTags(nameComplementTags);
        }

        /**
         * Replies an unmodifiable list of the name tags used to compose the label.
         *
         * @return the list of name tags
         */
        public List<String> getNameTags() {
            return Collections.unmodifiableList(nameTags);
        }

        /**
         * Replies an unmodifiable list of the name complement tags used to compose the label.
         *
         * @return the list of name complement tags
         * @since 6541
         */
        public List<String> getNameComplementTags() {
            return Collections.unmodifiableList(nameComplementTags);
        }

        /**
         * Initializes the name tags to use from a list of default name tags (see
         * {@link #DEFAULT_NAME_TAGS} and {@link #DEFAULT_NAME_COMPLEMENT_TAGS})
         * and from name tags configured in the preferences using the keys
         * <code>mappaint.nameOrder</code> and <code>mappaint.nameComplementOrder</code>.
         */
        public final void initNameTagsFromPreferences() {
            if (Config.getPref() == null) {
                this.nameTags = new ArrayList<>(Arrays.asList(DEFAULT_NAME_TAGS));
                this.nameComplementTags = new ArrayList<>(Arrays.asList(DEFAULT_NAME_COMPLEMENT_TAGS));
            } else {
                this.nameTags = new ArrayList<>(
                        Config.getPref().getList("mappaint.nameOrder", Arrays.asList(DEFAULT_NAME_TAGS))
                );
                this.nameComplementTags = new ArrayList<>(
                        Config.getPref().getList("mappaint.nameComplementOrder", Arrays.asList(DEFAULT_NAME_COMPLEMENT_TAGS))
                );
            }
        }
    }

    @Override
    public String toString() {
        return '{' + getClass().getSimpleName() + '}';
    }
}
