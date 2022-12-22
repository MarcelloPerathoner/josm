// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import javax.swing.JPopupMenu;

import org.openstreetmap.josm.data.osm.Tag;
import org.openstreetmap.josm.gui.dialogs.properties.HelpTagAction;
import org.openstreetmap.josm.gui.dialogs.properties.TaginfoAction;

/**
 * A preset item associated to an OSM key.
 */
abstract class KeyedItem extends TextItem {

    /** The OSM key associated with the item. */
    final String key;
    /**
     * Allows to change the matching process, i.e., determining whether the tags of an OSM object fit into this preset.
     * If a preset fits then it is linked in the Tags/Membership dialog.<ul>
     * <li>none: neutral, i.e., do not consider this item for matching</li>
     * <li>key: positive if key matches, neutral otherwise</li>
     * <li>key!: positive if key matches, negative otherwise</li>
     * <li>keyvalue: positive if key and value matches, neutral otherwise</li>
     * <li>keyvalue!: positive if key and value matches, negative otherwise</li></ul>
     * Note that for a match, at least one positive and no negative is required.
     * Default is "keyvalue!" for {@link Key} and "none" for {@link Text}, {@link Combo}, {@link MultiSelect} and {@link Check}.
     */
    final MatchType matchType;

    /**
     * Constructor.
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on illegal attributes
     */
    KeyedItem(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
        key = attributes.get("key");
        matchType = setMatchType(attributes.get("match"));
    }

    abstract class Instance extends Item.Instance {
        Instance(Item item, Composite.Instance parent) {
            super(item, parent);
        }

        @Override
        String getKey() {
            return ((KeyedItem) getItem()).getKey();
        }
    }

    MatchType setMatchType(String v) {
        if (v != null)
            return MatchType.ofString(v);
        return MatchType.ofString(getDefaultMatch().getValue());
    }

    /**
     * Enum denoting how a match (see {@link #matches}) is performed.
     */
    enum MatchType {

        /** Neutral, i.e., do not consider this item for matching. */
        NONE("none"),
        /** Positive if key matches, neutral otherwise. */
        KEY("key"),
        /** Positive if key matches, negative otherwise. */
        KEY_REQUIRED("key!"),
        /** Positive if key and value matches, neutral otherwise. */
        KEY_VALUE("keyvalue"),
        /** Positive if key and value matches, negative otherwise. */
        KEY_VALUE_REQUIRED("keyvalue!");

        private final String value;

        MatchType(String value) {
            this.value = value;
        }

        /**
         * Replies the associated textual value.
         * @return the associated textual value
         */
        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return value;
        }

        /**
         * Determines the {@code MatchType} for the given textual value.
         * @param type the textual value
         * @return the {@code MatchType} for the given textual value
         */
        public static MatchType ofString(String type) {
            for (MatchType i : EnumSet.allOf(MatchType.class)) {
                if (i.getValue().equals(type))
                    return i;
            }
            throw new IllegalArgumentException(type + " is not allowed");
        }
    }

    /**
     * Returns the key
     * @return the key
     */
    public String getKey() {
        return key;
    }

    /**
     * Returns the match type.
     * @return the match type
     */
    public String getMatchType() {
        return matchType.toString();
    }

    /**
     * Determines whether key or key+value are required.
     * @return whether key or key+value are required
     */
    public boolean isKeyRequired() {
        return MatchType.KEY_REQUIRED == matchType || MatchType.KEY_VALUE_REQUIRED == matchType;
    }

    /**
     * Returns the default match.
     * @return the default match
     */
    abstract MatchType getDefaultMatch();

    /**
     * Returns the list of values.
     * @return the list of values
     */
    public abstract List<String> getValues();

    String getKeyTooltipText() {
        return tr("This corresponds to the key ''{0}''", key);
    }

    /**
     * Tests whether the given tags match this KeyedItem.
     * <p>
     * Note that for a match, at least one positive and no negative is required.
     * <p>
     * The return value is either the singleton object {@code Boolean.TRUE}, the
     * singleton object {@code Boolean.FALSE}, or null.  Thus you can save some time by
     * comparing with == instead of equals().
     *
     * @param tags the tags to match
     * @return {@code Boolean.TRUE} on match, {@code null} if neutral, {@code Boolean.FALSE} on mismatch
     */
    public Boolean matches(Map<String, String> tags) {
        switch (matchType) {
        case NONE:
            return null; // NOSONAR
        case KEY:
            return tags.containsKey(key) ? Boolean.TRUE : null;
        case KEY_REQUIRED:
            return tags.containsKey(key) ? Boolean.TRUE : Boolean.FALSE;
        case KEY_VALUE:
            return tags.containsKey(key) && getValues().contains(tags.get(key)) ? Boolean.TRUE : null;
        case KEY_VALUE_REQUIRED:
            return tags.containsKey(key) && getValues().contains(tags.get(key)) ? Boolean.TRUE : Boolean.FALSE;
        default:
            throw new IllegalStateException();
        }
    }

    JPopupMenu getPopupMenu() {
        Tag tag = new Tag(key, null);
        JPopupMenu popupMenu = new JPopupMenu();
        popupMenu.add(tr("Key: {0}", key)).setEnabled(false);
        popupMenu.add(new HelpTagAction(() -> tag));
        TaginfoAction taginfoAction = new TaginfoAction(() -> tag, () -> null);
        popupMenu.add(taginfoAction.toTagHistoryAction());
        popupMenu.add(taginfoAction);
        return popupMenu;
    }

    @Override
    public String toString() {
        return "KeyedItem [key=" + key + ", text=" + text
                + ", text_context=" + textContext + ", match=" + matchType
                + ']';
    }
}
