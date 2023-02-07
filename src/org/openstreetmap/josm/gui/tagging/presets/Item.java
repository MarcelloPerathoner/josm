// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import java.awt.Color;
import java.awt.Dimension;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import javax.swing.JComponent;
import javax.swing.JMenu;

import org.openstreetmap.josm.data.preferences.BooleanProperty;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 * Template class for preset dialog construction.
 * <p>
 * This class and all its subclasses are immutable.  They basically are in-memory
 * representations of a {@code presets.xml} file.  Their main use is as templates to
 * create a {@link TaggingPresetDialog}.
 * <p>
 * To every Item class there is a companion class {@link Instance} that holds all
 * mutable data. An Instance is created along with every active component and registered
 * with the {@link TaggingPresetDialog}.  The preset dialog calls the registered item
 * instances {@link Item.Instance#addChangedTag to save the user edits}.
 * <p>
 * All data access goes through the
 * {@link org.openstreetmap.josm.gui.tagging.DataHandlers.TaggedHandler TaggedHandler}.
 * By plugging in a different handler, the preset dialog can edit the JOSM
 * {@code DataSet} or any other key/value data store.
 * <p>
 * We are emulating the <i>Composite</i> pattern, described in "Design Patterns", Gamma,
 * Helm, Johnson, and Vlissides, Addison-Wesley, 1995.  This class represents what the
 * authors call the <i>Component</i>.  (We cannot call it that because it would clash
 * with the swing Component class.)
 *
 * @since XXX
 */
abstract class Item {
    /**
     * Display OSM keys as {@linkplain org.openstreetmap.josm.gui.widgets.OsmIdTextField#setHint hint}
     */
    static final BooleanProperty DISPLAY_KEYS_AS_HINT = new BooleanProperty("taggingpreset.display-keys-as-hint", true);

    /**
     * Constructor.
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on illegal attributes
     */
    Item(Map<String, String> attributes) throws IllegalArgumentException {
        // empty
    }

    /**
     * Companion class to hold mutable data.
     * <p>
     * An instance of this class will be created by any template class that needs mutable instance
     * data.
     */
    abstract static class Instance {
        /** The template item that created us. */
        private final Item item;
        /** Our parent instance, most probably the TaggingPresetDialog */
        private final Composite.Instance parent;

        Instance(Item item, Composite.Instance parent) {
            this.item = item;
            this.parent = parent;
        }

        /**
         * Returns the template item that created this instance.
         * @return the item
         */
        Item getItem() {
            return item;
        }

        /**
         * Returns the preset instance
         * @return the preset instance
         */
        TaggingPreset.Instance getPresetInstance() {
            return parent.getPresetInstance();
        }

        /**
         * Adds the key and the value to the map.
         * <p>
         * The value is the value after any edits by the user.
         *
         * @param currentTags The map to add to.
         */
        abstract void addCurrentTag(Map<String, String> currentTags);

        /**
         * Adds the key and the value to the map only if the value was edited.
         *
         * Called by {@link TaggingPreset.Instance#getChangedTags} to collect changes.
         *
         * @param changedTags The map to add to.
         */
        abstract void addChangedTag(Map<String, String> changedTags);

        /**
         * Returns the OSM key associated with the instance.
         * @return the OSM key or null
         */
        String getKey() {
            return null;
        }

        /**
         * Returns the current value after any edits by the user.
         * @return the current value or null
         */
        String getValue() {
            return null;
        }

        /**
         * Programmatically sets the a value.
         */
        void setValue(String newValue) {
            // empty
        }

        /**
         * Recalculates a calculated field. Does nothing for non-calculated fields.
         */
        void recalculate() {
            // empty
        }

        /**
         * Highlights the item.
         * <p>
         * Highlights the item by drawing a colored border and optionally sets a
         * tooltip. Use case: signal input errors
         *
         * @param color the color to use for the border
         * @param message the tooltip to set
         */
        void highlight(Color color, String message) {
            // empty
        }
    }

    /**
     * for use of {@link TaggingPresetReader} only
     * @param s the content to set
     */
    void setContent(String s) {
        // empty
    }

    /**
     * for use of {@link TaggingPresetReader} only
     */
    void endElement() {
        // empty
    }

    /**
     * Adds an item to the container
     * @param item the item to add
     */
    void addItem(Item item) {
        // empty
    }

    /**
     * Called before item is removed.
     * <p>
     * Use to remove listeners, etc.
     */
    void destroy() {
        // empty
    }

    /**
     * Creates the Swing components for this preset item and adds them to the panel.
     *
     * @param c The parent component where our components must be added
     * @param parent The instance of the parent Composite
     * @return {@code true} if this item adds semantic tagging elements, {@code false} otherwise.
     */
    boolean addToPanel(JComponent c, Composite.Instance parent) {
        return false;
    }

    /**
     * Adds this item to the menu if it is a preset item.
     * <p>
     * This is overridden in {@link TaggingPreset} and descendants.
     * @param parentMenu the parent menu
     */
    void addToMenu(JMenu parentMenu) {
        // empty
    }

    /**
     * Return true if this item adds an interactive swing element to the panel.
     * @return true if interactive
     */
    boolean isInteractive() {
        return false;
    }

    /**
     * When this function is called, the item should add itself to the list if it satisfies the
     * predicate.  If the item is a sequence it should also ask its children to do the same.
     *
     * @param list the list to add to
     * @param p a predicate all added items must satisfy
     * @param followReferences whether to follow references or not
     */
    void addToItemList(List<Item> list, Predicate<Item> p, boolean followReferences) {
        if (p.test(this))
            list.add(this);
    }

    /**
     * When this function is called, the item should add itself to the list if it is an instance of
     * {@code type}. If the item is a sequence it should also ask its children to do the same.
     *
     * @param <E> the type
     * @param list the list to add to
     * @param type the type
     * @param followReferences whether to follow references or not
     */
    <E> void addToItemList(List<E> list, Class<E> type, boolean followReferences) {
        if (type.isInstance(this))
            list.add(type.cast(this));
    }

    /**
     * Parse an icon size from the attributes.
     * @param attributes the attributes to parse
     * @param defaultSize the default size
     * @return the parsed or default size
     */
    Dimension parseIconSize(Map<String, String> attributes, ImageProvider.ImageSizes defaultSize) {
        int s = Integer.parseInt(attributes.getOrDefault("icon_size", "-1"));
        if (s != -1) {
            return ImageProvider.adj(new Dimension(s, s));
        } else {
            return defaultSize.getImageDimension();
        }
    }

    /**
     * Various fixups after the whole xml file has been read.
     * <p>
     * If you are a chunk, add yourself to the map.  If you are a reference, save the map for later.
     *
     * @param chunks the chunks map
     * @param parent the parent item
     */
    void fixup(Map<String, Chunk> chunks, Item parent) { // NOSONAR
        // empty
    }

    @Override
    public String toString() {
        return "Item";
    }
}
