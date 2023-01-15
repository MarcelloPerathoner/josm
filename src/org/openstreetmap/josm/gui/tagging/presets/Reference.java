// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import javax.swing.JComponent;

/**
 * A reference to be satisfied by a {@link Chunk}
 */
final class Reference extends Item {
    private final String ref;
    private Map<String, Chunk> chunks;

    /**
     * Private constructor.
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on illegal attributes
     */
    private Reference(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
        ref = attributes.get("ref");
    }

    @Override
    void fixup(Map<String, Chunk> chunks, Item parent) {
        super.fixup(chunks, parent);
        this.chunks = chunks;
    }

    /**
     * Create a {@code Reference} from an XML element's attributes.
     * @param attributes the XML attributes
     * @return the {@code Reference}
     * @throws IllegalArgumentException on invalid attributes
     */
    static Reference fromXML(Map<String, String> attributes) throws IllegalArgumentException {
        return new Reference(attributes);
    }

    @Override
    void destroy() {
        chunks = null;
        super.destroy();
    }

    private Chunk getChunk() {
        Chunk chunk = chunks.get(ref);
        if (chunk == null)
            throw new IllegalArgumentException(tr("Reference to undefined chunk: {0}", ref));
        return chunk;
    }

    @Override
    void addToItemList(List<Item> list, Predicate<Item> p, boolean followReferences) {
        super.addToItemList(list, p, followReferences);
        if (followReferences)
            getChunk().addToItemList(list, p, followReferences);
    }

    @Override
    <E> void addToItemList(List<E> list, Class<E> type, boolean followReferences) {
        super.addToItemList(list, type, followReferences);
        if (followReferences)
            getChunk().addToItemList(list, type, followReferences);
    }

    @Override
    boolean addToPanel(JComponent p, Composite.Instance parentInstance) {
        return getChunk().addToPanel(p, parentInstance);
    }

    @Override
    public String toString() {
        return "Reference [ref=" + ref + "]";
    }
}
