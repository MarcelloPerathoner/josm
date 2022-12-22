// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmUtils;
import org.openstreetmap.josm.gui.tagging.DataHandlers.ReadOnlyHandler;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.tools.template_engine.TemplateEntry;
import org.openstreetmap.josm.tools.template_engine.TemplateParser;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collection;
import java.util.Collections;

import javax.swing.JPanel;

/**
 * Unit tests of {@link TaggingPresetDialog}
 */
class TaggingPresetDialogTest {

    /**
     * Setup test.
     */
    @RegisterExtension
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().main();

    /**
     * Tests {@link TemplateEntry} evaluation
     * @throws Exception in case something goes wrong
     */
    @Test
    void testTemplate() throws Exception {
        TestUtils.assumeWorkingJMockit();
        OsmPrimitive primitive = OsmUtils.createPrimitive(
            "relation type=route route=bus public_transport:version=2 ref=42 name=xxx from=Foo to=Bar");
        TaggingPresets taggingPresets = TaggingPresetsTest.initFromDefaultPresets();
        Collection<TaggingPreset> presets = taggingPresets.getMatchingPresets(primitive);

        assertEquals(presets.size(), 1);

        TaggingPreset preset = presets.iterator().next();
        TaggingPreset.Instance presetInstance = preset.new Instance(
            preset, new ReadOnlyHandler(Collections.singleton(primitive)), null);
        preset.addToPanel(new JPanel(), presetInstance);

        TemplateEntry templateEntry = new TemplateParser("Bus {ref}: {from} -> {to}").parse();
        assertEquals("Bus 42: Foo -> Bar", templateEntry.getText(presetInstance));
        templateEntry = new TemplateParser("?{route=train 'Train'|route=bus 'Bus'|'X'} {ref}: {from} -> {to}").parse();
        assertEquals("Bus 42: Foo -> Bar", templateEntry.getText(presetInstance));
    }
}
