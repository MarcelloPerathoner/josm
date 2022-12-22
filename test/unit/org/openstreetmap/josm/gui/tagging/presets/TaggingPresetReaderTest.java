// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.CustomMatchers.hasSize;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.xml.sax.SAXException;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Unit tests of {@link TaggingPresetReader} class.
 */
@Execution(ExecutionMode.CONCURRENT)
class TaggingPresetReaderTest {

    /**
     * Setup test.
     */
    @RegisterExtension
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules();

    /**
     * Test nested chunks
     * @throws SAXException if any XML error occurs
     * @throws IOException if any I/O error occurs
     */
    @Test
    void testNestedChunks() throws SAXException, IOException {
        String presetfile = TestUtils.getTestDataRoot() + "preset_chunk.xml";
        TaggingPresets taggingPresets = TaggingPresetsTest.initFromResource(presetfile);
        final Collection<TaggingPreset> presets = taggingPresets.getAllPresets();
        assertThat(presets, hasSize(1));
        final TaggingPreset abc = presets.iterator().next();

        final List<String> keys = abc.getAllItems(Key.class).stream().map(x -> x.getKey()).collect(Collectors.toList());
        assertEquals("[A1, A2, A3, B1, B2, B3, C1, C2, C3]", keys.toString());
    }

    /**
     * Test external entity resolving.
     * See #19286
     * @throws IOException in case of I/O error
     */
    @Test
    void testExternalEntityResolving() throws IOException {
        String presetfile = TestUtils.getTestDataRoot() + "preset_external_entity.xml";
        Exception e = assertThrows(SAXException.class, () -> {
            TaggingPresetReader.read(presetfile, true);
        });

        Assert.assertTrue(e.getMessage().contains("DOCTYPE is disallowed when"));
    }

    /**
     * Validate internal presets
     * See #9027
     * @throws SAXException if any XML error occurs
     * @throws IOException if any I/O error occurs
     */
    @Test
    void testReadDefaulPresets() throws SAXException, IOException {
        TaggingPresets taggingPresets = TaggingPresetsTest.initFromDefaultPresets();
        final Collection<TaggingPreset> presets = taggingPresets.getAllPresets();
        Assert.assertTrue("Default presets are empty", presets.size() > 0);
    }
}
