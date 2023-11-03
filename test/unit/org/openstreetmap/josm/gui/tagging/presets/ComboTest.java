// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;

import javax.swing.JPanel;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.osm.OsmUtils;
import org.openstreetmap.josm.testutils.annotations.I18n;
import org.openstreetmap.josm.testutils.annotations.Main;
import org.openstreetmap.josm.testutils.annotations.TaggingPresets;

/**
 * Unit tests of {@link Combo} class.
 */
@Main
@TaggingPresets
@I18n("de")
class ComboTest {
    /**
     * Unit test for {@link Combo#addToPanel}.
     */
    @Test
    void testAddToPanel() {
        TestUtils.assumeWorkingJMockit();
        JPanel p = new JPanel();
        assertTrue(TaggingPresetsTest.build("check").addToPanel(p, TaggingPresetsTest.createMockInstance()));
        assertTrue(p.getComponentCount() > 0);
    }

    void is(String expected, Combo combo, TaggingPreset.Instance presetInstance) {
        JPanel p = new JPanel();
        combo.addToPanel(p, presetInstance);
        Combo.Instance instance = (Combo.Instance) presetInstance.getInstance(combo);
        assertEquals(expected, instance.getSelectedItem().getValue());
    }

    /**
     * Unit test for {@link ComboMultiSelect#useLastAsDefault} and {@link ComboMultiSelect.Instance#getInitialValue}
     */
    @Test
    void testUseLastAsDefault() {
        TestUtils.assumeWorkingJMockit();
        InteractiveItem.LAST_VALUES.clear();
        InteractiveItem.LAST_VALUES.put("addr:country", "AT");
        Combo.PROP_FILL_DEFAULT.put(false);

        // CHECKSTYLE.OFF: SingleSpaceSeparator
        TaggingPreset.Instance way       = TaggingPresetsTest.createMockInstance(OsmUtils.createPrimitive("way"));
        TaggingPreset.Instance wayTagged = TaggingPresetsTest.createMockInstance(OsmUtils.createPrimitive("way highway=residential"));
        TaggingPreset.Instance wayAT     = TaggingPresetsTest.createMockInstance(OsmUtils.createPrimitive("way addr:country=AT"));
        TaggingPreset.Instance waySI     = TaggingPresetsTest.createMockInstance(OsmUtils.createPrimitive("way addr:country=SI"));
        TaggingPreset.Instance waysATSI  = TaggingPresetsTest.createMockInstance(OsmUtils.createPrimitive("way addr:country=AT"),
                                                                                 OsmUtils.createPrimitive("way addr:country=SI"));
        // CHECKSTYLE.ON: SingleSpaceSeparator

        String desc = "combo key=addr:country values_from=java.util.Locale#getISOCountries";
        Combo combo = (Combo) TaggingPresetsTest.build(desc);
        combo.endElement();
        is("", combo, way);
        is("", combo, wayTagged);
        is("AT", combo, wayAT);
        is("SI", combo, waySI);
        is(Combo.DIFFERENT, combo, waysATSI);

        combo = (Combo) TaggingPresetsTest.build(desc + " default=AT");
        combo.endElement();
        is("AT", combo, way);
        is("", combo, wayTagged);
        is("AT", combo, wayAT);
        is("SI", combo, waySI);
        is(Combo.DIFFERENT, combo, waysATSI);

        Combo.PROP_FILL_DEFAULT.put(true);

        is("AT", combo, way);
        is("AT", combo, wayTagged);
        is("AT", combo, wayAT);
        is("SI", combo, waySI);
        is(Combo.DIFFERENT, combo, waysATSI);

        Combo.PROP_FILL_DEFAULT.put(false);

        combo = (Combo) TaggingPresetsTest.build(desc + " use_last_as_default=true");
        combo.endElement();

        is("AT", combo, way);
        is("", combo, wayTagged);
        is("AT", combo, wayAT);
        is("SI", combo, waySI);
        is(Combo.DIFFERENT, combo, waysATSI);

        combo = (Combo) TaggingPresetsTest.build(desc + " use_last_as_default=force");
        combo.endElement();

        is("AT", combo, way);
        is("AT", combo, wayTagged);
        is("AT", combo, wayAT);
        is("SI", combo, waySI);
        is(Combo.DIFFERENT, combo, waysATSI);

        InteractiveItem.LAST_VALUES.clear();
    }

    @Test
    void testColor() {
        TestUtils.assumeWorkingJMockit();
        TaggingPreset.Instance presetInstance = TaggingPresetsTest.createMockInstance();
        Combo combo = (Combo) TaggingPresetsTest.build("combo key=colour values=red;green;blue;black values_context=color delimiter=;");
        combo.endElement();
        combo.addToPanel(new JPanel(), presetInstance);
        Combo.Instance comboInstance = (Combo.Instance) presetInstance.getInstance(combo);

        assertEquals(5, comboInstance.combobox.getItemCount());

        PresetListEntry.Instance i = comboInstance.findValue("red");
        comboInstance.combobox.setSelectedItem(i);
        assertEquals("red", comboInstance.getSelectedItem().getValue());
        assertEquals("Rot", comboInstance.getSelectedItem().toString());
        assertEquals(new Color(0xFF0000), comboInstance.getColor());

        i = comboInstance.findValue("green");
        comboInstance.combobox.setSelectedItem(i);
        assertEquals("green", i.getValue());
        assertEquals("Grün", i.toString());
        assertEquals(new Color(0x008000), comboInstance.getColor());

        comboInstance.combobox.setSelectedItem("#135");
        assertEquals("#135", comboInstance.getSelectedItem().getValue());
        assertEquals(new Color(0x113355), comboInstance.getColor());

        comboInstance.combobox.setSelectedItem("#123456");
        assertEquals("#123456", comboInstance.getSelectedItem().getValue());
        assertEquals(new Color(0x123456), comboInstance.getColor());

        comboInstance.setColor(new Color(0x448822));
        assertEquals("#448822", comboInstance.getSelectedItem().getValue());
    }
}
