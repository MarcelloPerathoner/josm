// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import java.util.Map;

/**
 * A collection of items to be inserted in place of a {@link Reference}.
 */
final class Chunk extends Composite {
    private final String id;

    /**
     * Constructor.
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on illegal attributes
     */
    Chunk(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
        id = attributes.get("id");
    }

    /**
     * Create a {@code Chunk} from an XML element's attributes.
     * @param attributes the XML attributes
     * @return the {@code Chunk}
     * @throws IllegalArgumentException on invalid attributes
     */
    static Chunk fromXML(Map<String, String> attributes) throws IllegalArgumentException {
        return new Chunk(attributes);
    }

    @Override
    void fixup(Map<String, Chunk> chunks, Item parent) {
        super.fixup(chunks, parent);
        chunks.put(getId(), this);
    }

    /**
     * Returns the chunk id.
     * @return the chunk id
     */
    public String getId() {
        return id;
    }

    @Override
    public String toString() {
        return "Chunk [id=" + id + "]";
    }
}
