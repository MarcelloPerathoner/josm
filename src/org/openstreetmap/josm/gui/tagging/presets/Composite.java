// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import javax.swing.JComponent;
import javax.swing.JMenu;

/**
 * A collection of {@link Item}s.
 * <p>
 * This is the <i>Composite</i> pattern, as described in "Design Patterns", Gamma, Helm,
 * Johnson, and Vlissides, Addison-Wesley, 1995.  This class and its descendents
 * represents what the authors call the <i>Composite</i>.
 */
abstract class Composite extends Item {
    /** The list of items in the sequence. */
    final List<Item> items = new ArrayList<>();

    /**
     * Constructor.
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on illegal attributes
     */
    Composite(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
    }

    abstract static class Instance extends Item.Instance {

        Instance(Item item, Composite.Instance parent) {
            super(item, parent);
        }

        /**
         * Registers the instance with the parent instance.
         * <p>
         * All items have to create and register an instance if the item is editable.
         * <p>
         * Implementation note: for simplicity the instances are registered in a list on
         * the preset instance.  But in future they could be registered on the
         * respective parent to build a hierarchy.
         *
         * @param item the preset item
         * @param instance the instance
         */
        public void putInstance(Item item, Item.Instance instance) {
            getPresetInstance().putInstance(item, instance);
        }
    }

    /**
     * Adds an item to the container
     * @param item the item to add
     */
    @Override
    void addItem(Item item) {
        items.add(item);
    }

    @Override
    void destroy() {
        super.destroy();
        items.forEach(item -> item.destroy());
    }

    @Override
    boolean addToPanel(JComponent p, Composite.Instance parentInstance) {
        boolean hasElements = false;
        for (Item item : items) {
            hasElements |= item.addToPanel(p, parentInstance);
        }
        return hasElements;
    }

    @Override
    void addToMenu(JMenu parentMenu) {
        items.forEach(item -> item.addToMenu(parentMenu));
    }

    /**
     * Returns all items in this sequence. Convenience function.
     *
     * @return the list of all items
     */
    public List<Item> getAllItems() {
        return getAllItems(true);
    }

    /**
     * Returns all items in this sequence.
     * <p>
     * Use {@code followReference=true} when getting all items inside a preset.
     * <p>
     * Use {@code followReference=false} when getting all items inside an XML root
     * element, otherwise you would get the items in chunks many times over.
     *
     * @param followReferences whether to follow references or not
     * @return the list of all items
     */
    public List<Item> getAllItems(boolean followReferences) {
        List<Item> result = new ArrayList<>();
        items.forEach(item -> item.addToItemList(result, i -> true, followReferences));
        return result;
    }

    /**
     * Returns all items in this sequence that satisfy a predicate.
     * @param p the predicate all items must satisfy
     * @param followReferences whether to follow references or not
     * @return the list of all items
     */
    public List<Item> getAllItems(Predicate<Item> p, boolean followReferences) {
        List<Item> list = new ArrayList<>();
        items.forEach(item -> item.addToItemList(list, p, followReferences));
        return list;
    }

    /**
     * Returns all items of a type in this sequence.
     * @param <E> the type
     * @param type the type
     * @return the list of all items
     */
    public <E> List<E> getAllItems(Class<E> type) {
        return getAllItems(type, true);
    }

    /**
     * Returns all items of a type in this sequence.
     * @param <E> the type
     * @param type the type
     * @param followReferences whether to follow references or not
     * @return the list of all items
     */
    public <E> List<E> getAllItems(Class<E> type, boolean followReferences) {
        List<E> list = new ArrayList<>();
        items.forEach(item -> item.addToItemList(list, type, followReferences));
        return list;
    }

    /**
     * Returns all keys in this preset. Convenience function.
     *
     * @return list of all keys
     */
    public List<String> getAllKeys() {
        List<String> list = new ArrayList<>();
        for (KeyedItem item : getAllItems(KeyedItem.class)) {
            list.add(item.getKey());
        }
        return list;
    }

    /**
     * Returns all values for the given key in this preset. Convenience function.
     * @param key the key
     *
     * @return list of all values
     */
    public List<String> getAllValues(String key) {
        List<String> list = new ArrayList<>();
        for (KeyedItem item : getAllItems(KeyedItem.class)) {
            if (item.getKey().equals(key))
                list.addAll(item.getValues());
        }
        return list;
    }

    /**
     * Returns a list of the alternative autocomplete keys for the given key.
     * @param key the key
     * @return a list of keys
     */
    public List<String> getAlternativeAutocompleteKeys(String key) {
        for (Text item : getAllItems(Text.class)) {
            if (item.getKey().equals(key))
                return item.getAlternativeAutocompleteKeys();
        }
        return Collections.emptyList();
    }

    /**
     * Returns if the key is required for matching.
     * <p>
     * For the convenience of TagInfoExtract.
     * @param key the key
     * @return true if the key is required
     */
    public boolean isKeyRequired(String key) {
        for (KeyedItem item : getAllItems(KeyedItem.class)) {
            if (item.getKey().equals(key))
                return item.isKeyRequired();
        }
        return false;
    }

    /**
     * Returns all roles in this sequence. Convenience function.
     *
     * @return the list of all roles
     */
    public List<Role> getAllRoles() {
        return getAllRoles(true);
    }

    /**
     * Returns all roles in this sequence.
     * @param followReferences whether to follow references or not
     * @return the list of all roles
     */
    List<Role> getAllRoles(boolean followReferences) {
        return getAllItems(Role.class, followReferences);
    }

    @Override
    void addToItemList(List<Item> list, Predicate<Item> p, boolean followReferences) {
        super.addToItemList(list, p, followReferences);
        items.forEach(item -> item.addToItemList(list, p, followReferences));
    }

    @Override
    <E> void addToItemList(List<E> list, Class<E> type, boolean followReferences) {
        super.addToItemList(list, type, followReferences);
        items.forEach(item -> item.addToItemList(list, type, followReferences));
    }

    @Override
    void fixup(Map<String, Chunk> chunks, Item parent) {
        items.forEach(item -> item.fixup(chunks, this));
    }

    @Override
    public String toString() {
        return "Composite";
    }
}
