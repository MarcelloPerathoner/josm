// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.tagging.DataHandlers.ReadOnlyHandler;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.tools.TextTagParser;
import org.openstreetmap.josm.tools.Utils;
import org.xml.sax.SAXException;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Unit tests of {@link TaggingPresets} class.
 */
public class TaggingPresetsTest {
    /**
     * Setup test.
     */
    @RegisterExtension
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules();

    /**
     * A convenience factory method to ease testing.
     *
     * @param text The text describing the item in {@link java.text.MessageFormat}, eg.
     * {@code text key=highway value=primary} or {@code checkgroup columns=4}
     *
     * @param objects parameters to message format
     * @return a new item
     */
    public static Item build(String text, Object... objects) {
        final String[] arr = MessageFormat.format(text, objects).split("\\s+", 2);
        String localname = arr[0];
        Map<String, String> attributes = new HashMap<>();
        if (arr.length > 1)
            attributes = TextTagParser.readTagsFromText(arr[1]);
        return ItemFactory.build(localname, attributes);
    }

    /**
     * Initialize presets from resource without loading presets from user prefs.
     *
     * @param presetsUrl the url of the presets file to load for testing
     * @return the TaggingPresets instance
     * @throws SAXException in case of parser errors
     * @throws IOException if the url was not found
     */
    public static TaggingPresets initFromResource(String presetsUrl) throws SAXException, IOException {
        TaggingPresets taggingPresets = new TaggingPresets();
        taggingPresets.addSource(presetsUrl, TaggingPresetReader.read(presetsUrl, false));
        taggingPresets.initCache();
        return taggingPresets;
    }

    /**
     * Initialize presets from defaultpresets.xml without loading presets from user prefs.
     *
     * @return the TaggingPresets instance
     * @throws SAXException in case of parser errors
     * @throws IOException if the url was not found
     */
    public static TaggingPresets initFromDefaultPresets() throws SAXException, IOException {
        return initFromResource("resource://data/defaultpresets.xml");
    }

    /**
     * Reads TaggingPresets from a literal XML fragment.
     * <p>
     * Example of XML fragment:
     * <pre>
     * {@code
     * <item name="Primary" type="way,closedway">
     *   <key key="highway" value="primary" />
     *   <text key="ref" text="Reference" />
     * </item>
     * }
     * </pre>
     * @param xml the XML literal
     * @return the TaggingPresets instance
     * @throws SAXException in case of invalid XML
     */
    public static TaggingPresets initFromLiteral(String xml) throws SAXException {
        TaggingPresets taggingPresets = new TaggingPresets();
        taggingPresets.addSource("test-literal", TaggingPresetReader.readLiteral(
            "<?xml version='1.0' encoding='UTF-8'?>" +
            "<presets xmlns='http://josm.openstreetmap.de/tagging-preset-1.0'>" +
            xml +
            "</presets>",
            false
        ));
        taggingPresets.initCache();
        return taggingPresets;
    }

    /**
     * Creates a mock {@code TaggingPreset.Instance}.
     * <p>
     * The returned instance is only good enough to be used as parameter for testing
     * {@link Item#addToPanel}.
     *
     * @param selected the selected primitives
     * @return the new {@code #Instance}
     */
    public static TaggingPreset.Instance createMockInstance(OsmPrimitive... selected) {
        TaggingPreset preset = (TaggingPreset) build("item");
        return preset.new Instance(preset, new ReadOnlyHandler(Arrays.asList(selected)), null);
    }

    /**
     * Add a child to a parent
     * <p>
     * This is a helper for SearchCompilerTest, that has no access to the Composite
     * class.
     * @param parent to parent
     * @param child the child to add
     */
    public static void addTo(Item parent, Item child) {
        parent.addItem(child);
    }

    /**
     * Returns all links found in all Link presets
     * <p>
     * This is a helper for TaggingPresetPreferenceTestIT, which has no access to the
     * Link class.
     * @param taggingPresets the tagging presets
     *
     * @return a list of urls
     */
    public static Collection<String> getAllUrlsInLinks(TaggingPresets taggingPresets) {
        Collection<TaggingPreset> presets = taggingPresets.getAllPresets();
        return presets.stream().flatMap(x -> x.getAllItems(Link.class).stream()).map(link -> link.getUrl())
            .filter(s -> !Utils.isEmpty(s)).collect(Collectors.toList());
    }
}
