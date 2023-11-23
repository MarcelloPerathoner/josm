// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.preferences.plugin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.gui.HelpAwareOptionPane;
import org.openstreetmap.josm.gui.HelpAwareOptionPaneTest;
import org.openstreetmap.josm.gui.preferences.PreferencesTestUtils;
import org.openstreetmap.josm.plugins.JarDownloadTask;
import org.openstreetmap.josm.plugins.PluginException;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.testutils.annotations.AssertionsInEDT;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.mockers.HelpAwareOptionPaneMocker;

/**
 * Unit tests of {@link PluginPreference} class.
 */
@AssertionsInEDT
@BasicPreferences
public class PluginPreferenceTest {
    /**
     * Unit test of {@link PluginPreference#PluginPreference}.
     */
    @Test
    void testPluginPreference() {
        assertNotNull(new PluginPreference.Factory().createPreferenceSetting());
    }

    /**
     * Returns a dummy plugin information.
     * @return a dummy plugin information
     * @throws PluginException if an error occurs
     */
    public static PluginInformation getDummyPluginInformation() throws PluginException, MalformedURLException {
        File f = new File(TestUtils.getTestDataRoot() + "__files/plugin/dummy_plugin.v31772.jar");
        return new PluginInformation(f, "dummy_plugin", f.toURI());
    }

    public static PluginInformation getMissingBazPluginInformation() throws PluginException, MalformedURLException {
        File f = new File(TestUtils.getTestDataRoot() + "__files/plugin/nonexistent_plugin.jar");
        Attributes attr = new Attributes();
        return new PluginInformation(attr, "baz_plugin", f.toURI());
    }

    public static void assertRegex(String expected, String actual) {
        if (!actual.matches(expected))
            fail("\nexpected: <" + expected + ">\nbut was:  <" + actual + ">");
    }

    /**
     * Unit test of {@link PluginPreference#buildDownloadSummary}.
     * @throws Exception if an error occurs
     */
    @Test
    void testBuildDownloadSummary() throws Exception {
        JarDownloadTask task = new JarDownloadTask(getDummyPluginInformation());
        task.run();
        JarDownloadTask failedTask = new JarDownloadTask(getMissingBazPluginInformation());
        failedTask.run();

        assertEquals("", PluginPreference.buildDownloadSummary(List.of(), false));

        final String SUCCESS = "The following plugin has been downloaded <strong>successfully</strong>:<ul><li>dummy_plugin \\(31772\\)</li></ul>";
        final String FAIL    = "Downloading the following plugin has <strong>failed</strong>:<ul><li>baz_plugin<.*";

        assertRegex(SUCCESS,        PluginPreference.buildDownloadSummary(List.of(task), false));
        assertRegex(FAIL,           PluginPreference.buildDownloadSummary(List.of(failedTask), false));
        assertRegex(SUCCESS + FAIL, PluginPreference.buildDownloadSummary(List.of(task, failedTask), false));
    }

    /**
     * Unit test of {@link PluginPreference#notifyDownloadResults}.
     * @throws PluginException
     * @throws MalformedURLException
     */
    @Test
    void testNotifyDownloadResults() throws MalformedURLException, PluginException {
        List<Object> messages = new ArrayList<>();
        HelpAwareOptionPaneTest.setRobot(o -> { messages.add(o); return 0; });

        PluginPreference.notifyDownloadResults(null, List.of(), false);
        PluginPreference.notifyDownloadResults(null, List.of(), true);

        assertEquals(List.of(
            "<html></html>",
            "<html>Please restart JOSM to activate the downloaded plugins.</html>"
        ), messages);
    }

    /**
     * Unit test of {@link PluginPreference#addGui}.
     */
    @Test
    void testAddGui() {
        assertDoesNotThrow(() ->
            PreferencesTestUtils.doTestPreferenceSettingAddGui(new PluginPreference.Factory(), null)
        );
    }
}
