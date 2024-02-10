// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.swing.JComponent;

import org.openstreetmap.josm.data.osm.Tag;

/**
 * A hidden item with a hardcoded value.
 */
final class Key extends KeyedItem {

    /** The hardcoded value for key. May change if this is a calculated field. */
    private String value;
    /**
     * If non-null the {@code value} is appended to an already existing value instead of replacing it.
     * This string is used as separator.
     * <p>
     * Note: this is not the same as {@code delimiter}. Delimiter is the value used to
     * separate values in the tagging preset XML, this value is the delimiter used in
     * the OSM database.
     */
    final String appendWith;
    final String appendRegexSearch;
    final String appendRegexReplace;


    /**
     * Private constructor. Use {@link #fromXML} instead.
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on illegal attributes
     */
    private Key(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
        value = attributes.getOrDefault("value", "");
        appendWith = attributes.get("append_with");
        appendRegexSearch = checkRegex(attributes.get("append_regex_search"));
        appendRegexReplace = attributes.getOrDefault("append_regex_replace", "");
    }

    /**
     * Create a {@code Key} from an XML element's attributes.
     * @param attributes the XML attributes
     * @return the {@code Key}
     * @throws IllegalArgumentException on invalid attributes
     */
    static Key fromXML(Map<String, String> attributes) throws IllegalArgumentException {
        return new Key(attributes);
    }

    /**
     * Returns the value
     * @return the value
     */
    public String getValue() {
        return value;
    }

    @Override
    boolean addToPanel(JComponent p, Composite.Instance parentInstance) {
        parentInstance.putInstance(this, new Instance(this, parentInstance));
        return false;
    }

    class Instance extends Item.Instance {
        private String originalValue;

        Instance(Item item, Composite.Instance parent) {
            super(item, parent);

            // Use originalValue to avoid the "Set 1 tags for 0 objects" entry in the
            // command stack window.
            TaggingPreset.Instance presetInstance = parent.getPresetInstance();
            // find out if our key is already used in the selection.
            Usage usage = Usage.determineTextUsage(presetInstance.getSelected(), key);
            if (usage.hasUniqueValue()) {
                // all objects use the same value
                originalValue = usage.getFirst();
            }
        }

        @Override
        void recalculate() {
            if (valueTemplate != null)
                value = valueTemplate.getText(getPresetInstance());
        }

        @Override
        public void addChangedTag(Map<String, String> changedTags) {
            if (!value.equals(originalValue)) {
                if (appendWith != null && originalValue != null) {
                    String v = value;
                    if (appendRegexSearch != null)
                        v = value.replaceAll(appendRegexSearch, appendRegexReplace);
                    changedTags.put(key, String.join(appendWith, originalValue, v));
                } else {
                    changedTags.put(key, value);
                }
            }
        }

        @Override
        public void addCurrentTag(Map<String, String> currentTags) {
            currentTags.put(key, value);
        }

        @Override
        String getValue() {
            return value;
        }

    }

    /**
     * Returns the {@link Tag} set by this item
     * @return the tag
     */
    Tag asTag() {
        return new Tag(key, value);
    }

    @Override
    MatchType getDefaultMatch() {
        return MatchType.KEY_VALUE_REQUIRED;
    }

    @Override
    public List<String> getValues() {
        return Collections.singletonList(value);
    }

    String checkRegex(String regex) {
        if (regex == null)
            return null;
        try {
            Pattern.compile(regex);
            return regex;
        } catch(PatternSyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public String toString() {
        return "Key [key=" + key + ", value=" + value + ", text=" + text
                + ", text_context=" + textContext + ", match=" + getMatchType()
                + ']';
    }
}
