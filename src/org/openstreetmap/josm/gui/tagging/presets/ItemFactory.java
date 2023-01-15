// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.HashMap;
import java.util.Map;

/**
 * A factory for preset items.
 */
public final class ItemFactory {
    /** The map from XML element name to template class constructor. */
    private static Map<String, FromXML> fromXML = buildMap();

    @FunctionalInterface
    private interface FromXML {
        Item apply(Map<String, String> attributes) throws IllegalArgumentException;
    }

    private ItemFactory() {
        // contains only static stuff
    }

    /**
     * The factory method.
     *
     * @param localname build a preset item of this kind
     * @param attributes the attributes of the item
     * @return the item
     */
    static Item build(String localname, Map<String, String> attributes) {
        FromXML f = fromXML.get(localname);
        Item item = null;
        if (f != null)
            item = f.apply(attributes);
        if (item == null)
            throw new IllegalArgumentException(tr("Unknown element {0}", localname));
        return item;
    }

    private static Map<String, FromXML> buildMap() {
        // CHECKSTYLE.OFF: SingleSpaceSeparator
        Map<String, FromXML> map = new HashMap<>();
        map.put("chunk",          (FromXML) Chunk::fromXML);
        map.put("reference",      (FromXML) Reference::fromXML);

        map.put("presets",        (FromXML) Root::fromXML);
        map.put("group",          (FromXML) TaggingPresetMenu::fromXML);
        map.put("item",           (FromXML) TaggingPreset::fromXML);
        map.put("separator",      (FromXML) TaggingPresetSeparator::fromXML);

        map.put("check",          (FromXML) Check::fromXML);
        map.put("checkgroup",     (FromXML) CheckGroup::fromXML);
        map.put("combo",          (FromXML) Combo::fromXML);
        map.put("container",      (FromXML) Container::fromXML);
        map.put("item_separator", (FromXML) ItemSeparator::fromXML);
        map.put("key",            (FromXML) Key::fromXML);
        map.put("label",          (FromXML) Label::fromXML);
        map.put("link",           (FromXML) Link::fromXML);
        map.put("list_entry",     (FromXML) PresetListEntry::fromXML);
        map.put("multiselect",    (FromXML) MultiSelect::fromXML);
        map.put("optional",       (FromXML) Optional::fromXML);
        map.put("preset_link",    (FromXML) PresetLink::fromXML);
        map.put("role",           (FromXML) Role::fromXML);
        map.put("roles",          (FromXML) Roles::fromXML);
        map.put("space",          (FromXML) Space::fromXML);
        map.put("text",           (FromXML) Text::fromXML);

        map.put("if",             (FromXML) If::fromXML);
        map.put("switch",         (FromXML) Switch::fromXML);
        map.put("case",           (FromXML) Case::fromXML);
        map.put("otherwise",      (FromXML) Otherwise::fromXML);
        map.put("tabs",           (FromXML) Tabs::fromXML);
        map.put("tab",            (FromXML) Tab::fromXML);

        // CHECKSTYLE.ON: SingleSpaceSeparator
        return map;
    }

    /**
     * Prepares an attribute map like the one used by the XML parser.
     * <p>
     * The number of parameters must be a multiple of 2. The first of the pair is the
     * key, the second the value.
     *
     * @param attributes the attributes to set eg. ("key", "highway", "value", "primary")
     * @return a map suitable to be passed to {@code fromXML}.
     * @throws IllegalArgumentException on error in the attributes
     */
    static Map<String, String> attributesToMap(String... attributes) throws IllegalArgumentException {
        if (attributes.length % 2 != 0)
            throw new IllegalArgumentException();
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < attributes.length; i += 2) {
            map.put(attributes[i], attributes[i + 1]);
        }
        return map;
    }
}
