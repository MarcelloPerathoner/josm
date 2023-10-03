// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmUtils;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresetSelector.PresetClassification;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresetSelector.PresetClassifications;
import org.openstreetmap.josm.tools.Logging;
import org.xml.sax.SAXException;

/**
 * Unit tests of {@link PresetClassifications} class.
 */
class PresetClassificationsTest {

    static final PresetClassifications classifications = new PresetClassifications();

    /**
     * Setup test.
     * @throws SAXException if any XML error occurs
     * @throws IOException if any I/O error occurs
     */
    @BeforeAll
    public static void setUp() throws IOException, SAXException {
        TaggingPresets taggingPresets = TaggingPresetsTest.initFromDefaultPresets();
        classifications.loadPresets(taggingPresets.getAllPresets());
    }

    private List<PresetClassification> getMatchingPresets(String searchText, OsmPrimitive w) {
        return classifications.getMatchingPresets(searchText, true, true, EnumSet.of(TaggingPresetType.forPrimitive(w)),
                Collections.singleton(w));
    }

    private List<String> getMatchingPresetNames(String searchText, OsmPrimitive w) {
        return getMatchingPresets(searchText, w).stream().map(x -> x.preset.getBaseName()).collect(Collectors.toList());
    }

    /**
     * Test building preset.
     */
    @Test
    void testBuilding() {
        final Way w = new Way();
        final Node n1 = new Node();
        w.addNode(n1);
        w.addNode(new Node());
        w.addNode(new Node());
        Logging.info(getMatchingPresetNames("building", w).toString());
        assertFalse(getMatchingPresetNames("building", w).contains("Building"), "unclosed way should not match building preset");
        w.addNode(n1);
        Logging.info(getMatchingPresetNames("building", w).toString());
        assertTrue(getMatchingPresetNames("building", w).contains("Building"), "closed way should match building preset");
    }

    /**
     * Test public transport tram relations presets.
     */
    @Test
    void testRelationsForTram() {
        final OsmPrimitive tram = OsmUtils.createPrimitive("way railway=tram");
        assertTrue(getMatchingPresetNames("route", tram).contains("Railway Route"),
                "railway=tram should match 'Railway Route' for relation creation");
        assertTrue(getMatchingPresetNames("route", tram).contains("Public Transport Route (Rail)"),
                "railway=tram should match 'Public Transport Route (Rail)' for relation creation");
        assertFalse(getMatchingPresetNames("route", tram).toString().contains("Bus"),
                "railway=tram should not match 'Bus'");
    }
}
