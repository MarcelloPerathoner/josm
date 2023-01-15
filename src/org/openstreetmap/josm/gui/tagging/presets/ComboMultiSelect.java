// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.JLabel;

import org.openstreetmap.josm.tools.AlphanumComparator;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.Logging;

/**
 * Abstract superclass for combo box and multi-select list types.
 */
abstract class ComboMultiSelect extends InteractiveItem {
    /** The context used for translating values */
    final String valuesContext;
    /** Disabled internationalisation for value to avoid mistakes, see #11696 */
    final boolean valuesNoI18n;
    /** The default value for the item. If not specified, the current value of the key is chosen as default (if applicable).*/
    final String default_;
    /** Whether to sort the values, defaults to true. */
    private final boolean valuesSort;
    /** Whether to offer display values for search via {@link TaggingPresetSelector} */
    private final boolean valuesSearchable;
    /**
     * The character that separates values.
     * In case of {@link Combo} the default is comma.
     */
    final char delimiter;

    /**
     * The standard entries in the combobox dropdown or multiselect list. These entries are defined
     * in {@code defaultpresets.xml} (or in other custom preset files).
     */
    final List<PresetListEntry> presetListEntries = new ArrayList<>();

    /** Helps avoid duplicate list entries */
    final Map<String, PresetListEntry.Instance> seenValues = new HashMap<>();

    private List<String> valuesSet;
    private List<String> displayValuesSet = new ArrayList<>();

    /**
     * Constructor.
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on illegal attributes
     */
    ComboMultiSelect(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
        valuesContext = attributes.get("values_context");
        valuesNoI18n = TaggingPresetUtils.parseBoolean(attributes.get("values_no_i18n"));
        valuesSort = TaggingPresetUtils.parseBoolean(attributes.getOrDefault("values_sort", "on"));
        valuesSearchable = TaggingPresetUtils.parseBoolean(attributes.get("values_searchable"));
        default_ = attributes.get("default");
        delimiter = attributes.getOrDefault("delimiter", getDefaultDelimiter()).charAt(0);

        initPresetListEntries(attributes);
    }

    @Override
    void endElement() {
        super.endElement();
        if (valuesSort && TaggingPresets.SORT_VALUES.get()) {
            Collections.sort(presetListEntries,
                (a, b) -> AlphanumComparator.getInstance().compare(a.getDisplayValue(this), b.getDisplayValue(this)));
        }
        valuesSet = presetListEntries.stream().map(PresetListEntry::getValue).collect(Collectors.toList());
        if (valuesSearchable) {
            displayValuesSet = presetListEntries.stream().map(e -> e.getDisplayValue(this)).collect(Collectors.toList());
        }
    }

    @Override
    public List<String> getValues() {
        return valuesSet;
    }

    /**
     * Returns the values to display.
     * @return the values to display
     */
    public List<String> getDisplayValues() {
        return displayValuesSet;
    }

    /**
     * Returns the default delimiter used in multi-value attributes.
     * @return the default delimiter
     */
    abstract String getDefaultDelimiter();

    /**
     * Adds the label to the panel
     *
     * @param c the panel
     * @return the label
     */
    JLabel addLabel(JComponent c) {
        final JLabel label = new JLabel(tr("{0}:", localeText));
        addIcon(label);
        label.setToolTipText(getKeyTooltipText());
        label.setComponentPopupMenu(getPopupMenu());
        label.applyComponentOrientation(TaggingPresetDialog.getDefaultComponentOrientation());
        c.add(label, GBC.std().insets(0, 0, 10, 0));
        return label;
    }

    private List<String> getValuesFromCode(String valuesFrom) {
        // get the values from a Java function
        String[] classMethod = valuesFrom.split("#", -1);
        if (classMethod.length == 2) {
            try {
                Method method = Class.forName(classMethod[0]).getMethod(classMethod[1]);
                // ComboMultiSelect method is public static String[] methodName()
                int mod = method.getModifiers();
                if (Modifier.isPublic(mod) && Modifier.isStatic(mod)
                        && method.getReturnType().equals(String[].class) && method.getParameterTypes().length == 0) {
                    return Arrays.asList((String[]) method.invoke(null));
                } else {
                    Logging.error(tr("Broken tagging preset \"{0}-{1}\" - Java method given in ''values_from'' is not \"{2}\"", key, text,
                            "public static String[] methodName()"));
                }
            } catch (ReflectiveOperationException e) {
                Logging.error(tr("Broken tagging preset \"{0}-{1}\" - Java method given in ''values_from'' threw {2} ({3})", key, text,
                        e.getClass().getName(), e.getMessage()));
                Logging.debug(e);
            }
        }
        return null; // NOSONAR
    }

    /**
     * Checks if list {@code a} is either null or the same length as list {@code b}.
     *
     * @param a The list to check
     * @param b The other list
     * @param name The name of the list for error reporting
     * @return {@code a} if both lists have the same length or {@code null}
     */
    private List<String> checkListsSameLength(List<String> a, List<String> b, String name) {
        if (a != null && a.size() != b.size()) {
            Logging.error(tr("Broken tagging preset \"{0}-{1}\" - number of items in ''{2}'' must be the same as in ''values''",
                            key, text, name));
            Logging.error(tr("Detailed information: {0} <> {1}", a, b));
            return null; // NOSONAR
        }
        return a;
    }

    private void initPresetListEntries(Map<String, String> attributes) {
        /**
         * A list of entries.
         * The list has to be separated by commas (for the {@link Combo} box) or by the specified delimiter (for the {@link MultiSelect}).
         * If a value contains the delimiter, the delimiter may be escaped with a backslash.
         * If a value contains a backslash, it must also be escaped with a backslash. */
        final String values = attributes.get("values");
        /**
         * To use instead of {@link #valueEditor} if the list of values has to be obtained with a Java method of this form:
         * <p>{@code public static String[] getValues();}<p>
         * The value must be: {@code full.package.name.ClassName#methodName}.
         */
        final String valuesFrom = attributes.get("values_from");
        /**
         * A list of entries that is displayed to the user.
         * Must be the same number and order of entries as {@link #valueEditor} and editable must be false or not specified.
         * For the delimiter character and escaping, see the remarks at {@link #valueEditor}.
         */
        final String displayValues = attributes.get("display_values");
        /** The localized version of {@link #displayValues}. */
        final String localeDisplayValues = attributes.get("locale_display_values");
        /**
         * A delimiter-separated list of texts to be displayed below each {@code display_value}.
         * (Only if it is not possible to describe the entry in 2-3 words.)
         * Instead of comma separated list instead using {@link #valueEditor}, {@link #displayValues} and {@link #shortDescriptions},
         * the following form is also supported:<p>
         * {@code <list_entry value="" display_value="" short_description="" icon="" icon_size="" />}
         */
        final String shortDescriptions = attributes.get("short_descriptions");
        /** The localized version of {@link #shortDescriptions}. */
        final String localeShortDescriptions = attributes.get("locale_short_descriptions");

        List<String> valueList = null;
        List<String> displayList = null;
        List<String> localeDisplayList = null;

        if (valuesFrom != null) {
            valueList = getValuesFromCode(valuesFrom);
        }

        if (valueList == null) {
            // get from {@code values} attribute
            valueList = TaggingPresetUtils.splitEscaped(delimiter, values);
        }
        if (valueList == null) {
            return;
        }

        if (!valuesNoI18n) {
            localeDisplayList = TaggingPresetUtils.splitEscaped(delimiter, localeDisplayValues);
            displayList = TaggingPresetUtils.splitEscaped(delimiter, displayValues);
        }
        List<String> localeShortDescriptionsList = TaggingPresetUtils.splitEscaped(delimiter, localeShortDescriptions);
        List<String> shortDescriptionsList = TaggingPresetUtils.splitEscaped(delimiter, shortDescriptions);

        displayList = checkListsSameLength(displayList, valueList, "display_values");
        localeDisplayList = checkListsSameLength(localeDisplayList, valueList, "locale_display_values");
        shortDescriptionsList = checkListsSameLength(shortDescriptionsList, valueList, "short_descriptions");
        localeShortDescriptionsList = checkListsSameLength(localeShortDescriptionsList, valueList, "locale_short_descriptions");

        for (int i = 0; i < valueList.size(); i++) {
            Map<String, String> attribs = new HashMap<>();
            attribs.put("value", valueList.get(i));
            if (displayList != null)
                attribs.put("display_value", displayList.get(i));
            if (localeDisplayList != null)
                attribs.put("locale_display_value", localeDisplayList.get(i));
            if (shortDescriptionsList != null)
                attribs.put("short_description", shortDescriptionsList.get(i));
            if (localeShortDescriptionsList != null)
                attribs.put("locale_short_description", localeShortDescriptionsList.get(i));
            addItem(PresetListEntry.fromXML(attribs));
        }
    }

    abstract class Instance extends InteractiveItem.Instance {
        /**
         * Used to determine if the user has edited the value. This is not the same as the initial value
         * shown in the component. The original value is the state of the data before the edit. The initial
         * value may already be an edit suggested by the software.
         */
        String originalValue;
        Usage usage;

        Instance(Item item, Composite.Instance parent, JComponent component, Usage usage) {
            super(item, parent, component);
            this.usage = usage;
        }

        ComboMultiSelect getTemplate() {
            return ComboMultiSelect.this;
        }

        @Override
        public void addChangedTag(Map<String, String> changedTags) {
            String value = getValue();

            // no change if same as before
            if (value.equals(originalValue))
                return;
            changedTags.put(key, value);

            if (isUseLastAsDefault()) {
                LAST_VALUES.put(key, value);
            }
        }

        @Override
        public void addCurrentTag(Map<String, String> currentTags) {
            currentTags.put(key, getValue());
        }

        @Override
        String getValue() {
            return getSelectedItem().getValue();
        }

        /**
         * Returns the value selected in the combobox or a synthetic value if a multiselect.
         *
         * @return the value
         */
        abstract PresetListEntry.Instance getSelectedItem();

        /**
         * Returns the initial value to use for this preset.
         * <p>
         * The initial value is the value shown in the control when the preset dialog opens. For a
         * discussion of all the options see the enclosed tickets.
         *
         * @return The initial value to use.
         *
         * @see "https://josm.openstreetmap.de/ticket/5564"
         * @see "https://josm.openstreetmap.de/ticket/12733"
         * @see "https://josm.openstreetmap.de/ticket/17324"
         */
        String getInitialValue() {
            String initialValue = null;
            originalValue = "";

            if (usage.hasUniqueValue()) {
                // all selected primitives have the same not empty value for this key
                initialValue = usage.getFirst();
                originalValue = initialValue;
            } else if (!usage.unused()) {
                // at least one primitive has a value for this key (but not all have the same one)
                initialValue = DIFFERENT;
                originalValue = initialValue;
            } else if (!usage.hadKeys() || isForceUseLastAsDefault() || PROP_FILL_DEFAULT.get()) {
                // at this point no primitive had any value for this key
                if (!getPresetInstance().isPresetInitiallyMatches() && isUseLastAsDefault() && LAST_VALUES.containsKey(key)) {
                    initialValue = LAST_VALUES.get(key);
                } else {
                    initialValue = default_;
                }
            }
            return initialValue != null ? initialValue : "";
        }
    }

    /**
     * Adds a preset list entry.
     * @param e list entry to add
     */
    @Override
    void addItem(Item e) {
        if (e instanceof PresetListEntry)
            presetListEntries.add((PresetListEntry) e);
    }

    /**
     * Adds a collection of preset list entries.
     * @param e list entries to add
     */
    void addListEntries(Collection<PresetListEntry> e) {
        for (PresetListEntry i : e) {
            addItem(i);
        }
    }

    @Override
    MatchType getDefaultMatch() {
        return MatchType.NONE;
    }
}
