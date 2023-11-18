// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.testutils.PluginServer;
import org.openstreetmap.josm.testutils.annotations.AssumeRevision;
import org.openstreetmap.josm.testutils.annotations.FullPreferences;
import org.openstreetmap.josm.testutils.annotations.Main;
import org.openstreetmap.josm.testutils.mockers.ExtendedDialogMocker;
import org.openstreetmap.josm.testutils.mockers.HelpAwareOptionPaneMocker;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;

/**
 * Test parts of {@link PluginHandler} class when the reported JOSM version is too old for the plugin.
 */
@AssumeRevision("Revision: 6000\n")
@FullPreferences
@Main
class PluginHandlerJOSMTooOldTest {
    /**
     * Plugin server mock.
     */
    @RegisterExtension
    static WireMockExtension pluginServerRule = WireMockExtension.newInstance().options(
        options().dynamicPort().usingFilesUnderDirectory(TestUtils.getTestDataRoot())
    ).build();

    /**
     * Setup test.
     */
    @BeforeEach
    public void setUp() {
        Config.getPref().putInt("pluginmanager.version", 999);
        Config.getPref().put("pluginmanager.lastupdate", "999");
        Config.getPref().putList("pluginmanager.sites",
                Collections.singletonList(pluginServerRule.url("/plugins"))
        );

        this.referenceDummyJarOld = new File(TestUtils.getTestDataRoot(), "__files/plugin/dummy_plugin.v31701.jar");
        this.referenceDummyJarNew = new File(TestUtils.getTestDataRoot(), "__files/plugin/dummy_plugin.v31772.jar");
        this.referenceBazJarOld   = new File(TestUtils.getTestDataRoot(), "__files/plugin/baz_plugin.v6.jar");
        this.referenceBazJarNew   = new File(TestUtils.getTestDataRoot(), "__files/plugin/baz_plugin.v7.jar");
        this.referenceQuxJar345   = new File(TestUtils.getTestDataRoot(), "__files/plugin/qux_plugin.v345.jar"); // 6999
        this.referenceQuxJar346   = new File(TestUtils.getTestDataRoot(), "__files/plugin/qux_plugin.v346.jar"); // 7499
        this.referenceQuxJar432   = new File(TestUtils.getTestDataRoot(), "__files/plugin/qux_plugin.v432.jar"); // 7500
        this.referenceQuxJar435   = new File(TestUtils.getTestDataRoot(), "__files/plugin/qux_plugin.v435.jar"); // 8111
        this.pluginDir = Preferences.main().getPluginsDirectory();
        this.targetDummyJar = new File(this.pluginDir, "dummy_plugin.jar");
        this.targetDummyJarNew = new File(this.pluginDir, "dummy_plugin.jar.new");
        this.targetBazJar = new File(this.pluginDir, "baz_plugin.jar");
        this.targetBazJarNew = new File(this.pluginDir, "baz_plugin.jar.new");
        this.targetQuxJar = new File(this.pluginDir, "qux_plugin.jar");
        this.targetQuxJarNew = new File(this.pluginDir, "qux_plugin.jar.new");
        this.pluginDir.mkdirs();
    }

    private File pluginDir;
    private File referenceDummyJarOld;
    private File referenceDummyJarNew;
    private File referenceBazJarOld;
    private File referenceBazJarNew;
    private File referenceQuxJar345;
    private File referenceQuxJar346;
    private File referenceQuxJar432;
    private File referenceQuxJar435;
    private File targetDummyJar;
    private File targetDummyJarNew;
    private File targetBazJar;
    private File targetBazJarNew;
    private File targetQuxJar;
    private File targetQuxJarNew;

    private final String bazPluginVersionReqString = u202f("JOSM version 8\u202F001 required for plugin baz_plugin.");
    private final String dummyPluginVersionReqString = u202f("JOSM version 7\u202F001 required for plugin dummy_plugin.");
    private final String dummyPluginFailedString = "<html>Updating the following plugin has failed:<ul><li>dummy_plugin</li></ul>"
        + "Please open the Preference Dialog after JOSM has started and try to update it manually.</html>";

    private static String u202f(String s) {
        return s.replace('\u202F', DecimalFormatSymbols.getInstance().getGroupingSeparator());
    }
    private String getConfiguredPlugins() throws IOException {
        List<PluginInformation> l = PluginHandlerTest.getConfiguredPlugins();
        return String.join(" ", l.stream().map(p -> p.getName() + ":" + p.getVersion()).sorted().toList());
    }

    /**
     * test update of plugins when those plugins turn out to require a higher JOSM version, but the
     * user chooses to update them anyway.
     * @throws IOException never
     */
    @Test
    @Disabled("Should never even consider installing an incompatible plugin")
    void testUpdatePluginsDownloadBoth(WireMockRuntimeInfo wireMockRuntimeInfo) throws IOException {
        TestUtils.assumeWorkingJMockit();
        final PluginServer pluginServer = new PluginServer(
            new PluginServer.RemotePlugin(this.referenceDummyJarNew),
            new PluginServer.RemotePlugin(this.referenceBazJarNew)
        );
        pluginServer.applyToWireMockServer(wireMockRuntimeInfo);
        Config.getPref().putList("plugins", Arrays.asList("dummy_plugin", "baz_plugin"));

        final ExtendedDialogMocker edMocker = new ExtendedDialogMocker();
        edMocker.getMockResultMap().put(this.bazPluginVersionReqString, "Download Plugin");
        edMocker.getMockResultMap().put(this.dummyPluginVersionReqString, "Download Plugin");

        Files.copy(this.referenceDummyJarOld.toPath(), this.targetDummyJar.toPath());
        Files.copy(this.referenceBazJarOld.toPath(), this.targetBazJar.toPath());

        assertEquals("baz_plugin:6 dummy_plugin:31701", getConfiguredPlugins());
        final Collection<PluginInformation> updatedPlugins = PluginHandler.updatePlugins(
            MainApplication.getMainFrame(),
            null,
            null,
            false
        );
        assertEquals("baz_plugin:7 dummy_plugin:31772", getConfiguredPlugins());

        assertEquals(
                Arrays.asList(
                        this.dummyPluginVersionReqString,
                        this.bazPluginVersionReqString
                ),
            edMocker.getInvocationLog().stream().map(
                invocationEntry -> invocationEntry[1]
            ).sorted().collect(Collectors.toList())
        );

        assertEquals(2, updatedPlugins.size());

        assertFalse(targetDummyJarNew.exists());
        assertFalse(targetBazJarNew.exists());

        TestUtils.assertFileContentsEqual(this.referenceDummyJarNew, this.targetDummyJar);
        TestUtils.assertFileContentsEqual(this.referenceBazJarNew, this.targetBazJar);

        pluginServerRule.verify(1, WireMock.getRequestedFor(WireMock.urlEqualTo("/plugins")));
        pluginServerRule.verify(1, WireMock.getRequestedFor(WireMock.urlEqualTo("/plugin/dummy_plugin.v31772.jar")));
        pluginServerRule.verify(1, WireMock.getRequestedFor(WireMock.urlEqualTo("/plugin/baz_plugin.v7.jar")));

        assertEquals(6000, Config.getPref().getInt("pluginmanager.version", 111));
        // not mocking the time so just check it's not its original value
        assertNotEquals("999", Config.getPref().get("pluginmanager.lastupdate", "999"));
    }

    /**
     * test update of plugins when those plugins turn out to require a higher JOSM version, but the
     * user chooses to update one and skip the other.
     * @throws IOException never
     */
    @Test
    @Disabled("Should never even consider installing an incompatible plugin")
    void testUpdatePluginsSkipOne(WireMockRuntimeInfo wireMockRuntimeInfo) throws IOException {
        TestUtils.assumeWorkingJMockit();
        final PluginServer pluginServer = new PluginServer(
            new PluginServer.RemotePlugin(this.referenceDummyJarNew),
            new PluginServer.RemotePlugin(this.referenceBazJarNew)
        );
        pluginServer.applyToWireMockServer(wireMockRuntimeInfo);
        Config.getPref().putList("plugins", Arrays.asList("dummy_plugin", "baz_plugin"));

        final ExtendedDialogMocker edMocker = new ExtendedDialogMocker();
        edMocker.getMockResultMap().put(this.bazPluginVersionReqString, "Download Plugin");
        edMocker.getMockResultMap().put(this.dummyPluginVersionReqString, "Skip Download");
        final HelpAwareOptionPaneMocker haMocker = new HelpAwareOptionPaneMocker();
        haMocker.getMockResultMap().put(this.dummyPluginFailedString, "OK");

        Files.copy(this.referenceDummyJarOld.toPath(), this.targetDummyJar.toPath());
        Files.copy(this.referenceBazJarOld.toPath(), this.targetBazJar.toPath());

        final Collection<PluginInformation> updatedPlugins = PluginHandler.updatePlugins(
            MainApplication.getMainFrame(),
            null,
            null,
            false
        );

        assertEquals(
                Arrays.asList(
                        dummyPluginVersionReqString,
                        bazPluginVersionReqString
                ),
            edMocker.getInvocationLog().stream().map(
                invocationEntry -> invocationEntry[1]
            ).sorted().collect(Collectors.toList())
        );

        assertEquals(Collections.singletonList(this.dummyPluginFailedString),
            haMocker.getInvocationLog().stream().map(
                invocationEntry -> invocationEntry[1]
            ).sorted().collect(Collectors.toList())
        );

        assertEquals(2, updatedPlugins.size());

        assertEquals("baz_plugin:7 dummy_plugin:31701", getConfiguredPlugins());

        assertFalse(targetDummyJarNew.exists());
        assertFalse(targetBazJarNew.exists());

        TestUtils.assertFileContentsEqual(this.referenceDummyJarOld, this.targetDummyJar);
        TestUtils.assertFileContentsEqual(this.referenceBazJarNew, this.targetBazJar);

        pluginServerRule.verify(1, WireMock.getRequestedFor(WireMock.urlEqualTo("/plugins")));
        pluginServerRule.verify(0, WireMock.getRequestedFor(WireMock.urlEqualTo("/plugin/dummy_plugin.v31772.jar")));
        pluginServerRule.verify(1, WireMock.getRequestedFor(WireMock.urlEqualTo("/plugin/baz_plugin.v7.jar")));

        // shouldn't have been updated
        assertEquals(999, Config.getPref().getInt("pluginmanager.version", 111));
        assertEquals("999", Config.getPref().get("pluginmanager.lastupdate", "999"));
    }

    /**
     * When the plugin list suggests that the jar file at the provided URL *doesn't* require a newer JOSM
     * but in fact the plugin served *does*, it should not be installed.
     *
     * @throws IOException never
     */
    @Test
    void testUpdatePluginsUnexpectedlyJOSMTooOld(WireMockRuntimeInfo wireMockRuntimeInfo) throws IOException {
        TestUtils.assumeWorkingJMockit();
        final PluginServer pluginServer = new PluginServer(
            new PluginServer.RemotePlugin(this.referenceDummyJarNew),
            new PluginServer.RemotePlugin(this.referenceBazJarNew, Collections.singletonMap("Plugin-Mainversion", "5500"))
        );
        pluginServer.applyToWireMockServer(wireMockRuntimeInfo);
        Config.getPref().putList("plugins", Collections.singletonList("baz_plugin"));

        // setting up blank ExtendedDialogMocker which would raise an exception if any attempt to show
        // and ExtendedDialog were made
        new ExtendedDialogMocker();

        Files.copy(this.referenceBazJarOld.toPath(), this.targetBazJar.toPath());

        assertEquals("baz_plugin:6", getConfiguredPlugins());
        final List<PluginInformation> updatedPlugins = new ArrayList<>(PluginHandler.updatePlugins(
            MainApplication.getMainFrame(),
            null,
            null,
            false
        ));
        assertEquals("baz_plugin:6", getConfiguredPlugins());

        assertEquals(1, updatedPlugins.size()); // we have downloaded it, even if it doesn't install

        assertFalse(targetBazJarNew.exists());

        TestUtils.assertFileContentsEqual(this.referenceBazJarOld, this.targetBazJar);

        pluginServerRule.verify(1, WireMock.getRequestedFor(WireMock.urlEqualTo("/plugins")));

        // should have been updated
        assertEquals(6000, Config.getPref().getInt("pluginmanager.version", 111));
        assertNotEquals("999", Config.getPref().get("pluginmanager.lastupdate", "999"));
    }

    /**
     * When a plugin advertises several versions for compatibility with older JOSMs, but
     * even the oldest of those is newer than our JOSM version, nothing can be safely
     * installed. If a compatible version is already installed, keep that one.
     *
     * @throws IOException never
     */
    @Test
    @AssumeRevision("Revision: 7200\n")
    void testUpdatePluginsMultiVersionInsufficient(WireMockRuntimeInfo wireMockRuntimeInfo) throws IOException {
        TestUtils.assumeWorkingJMockit();

        final PluginServer pluginServer = new PluginServer(
            new PluginServer.RemotePlugin(this.referenceBazJarOld),
            new PluginServer.RemotePlugin(this.referenceQuxJar432, Collections.singletonMap(
                "7499_Plugin-Url", "346;" + pluginServerRule.url("/dont/bother.jar")
            ))
        );
        pluginServer.applyToWireMockServer(wireMockRuntimeInfo);
        Config.getPref().putList("plugins", Arrays.asList("qux_plugin", "baz_plugin"));

        new ExtendedDialogMocker(Collections.singletonMap(u202f("JOSM version 7\u202F500 required for plugin qux_plugin."), "Download Plugin"));

        Files.copy(this.referenceQuxJar345.toPath(), this.targetQuxJar.toPath()); // mainversion 6999
        Files.copy(this.referenceBazJarOld.toPath(), this.targetBazJar.toPath());

        assertEquals("baz_plugin:6 qux_plugin:345", getConfiguredPlugins());
        final Collection<PluginInformation> updatedPlugins = PluginHandler.updatePlugins(
            MainApplication.getMainFrame(),
            null,
            null,
            false
        );
        assertEquals("baz_plugin:6 qux_plugin:345", getConfiguredPlugins());
        assertEquals(2, updatedPlugins.size());

        assertFalse(targetQuxJarNew.exists());
        TestUtils.assertFileContentsEqual(this.referenceBazJarOld, this.targetBazJar);
        TestUtils.assertFileContentsEqual(this.referenceQuxJar345, this.targetQuxJar);

        assertEquals(1, pluginServerRule.getAllServeEvents().size());
        pluginServerRule.verify(1, WireMock.getRequestedFor(WireMock.urlEqualTo("/plugins")));

        assertEquals(7200, Config.getPref().getInt("pluginmanager.version", 111));
        // not mocking the time so just check it's not its original value
        assertNotEquals("999", Config.getPref().get("pluginmanager.lastupdate", "999"));
    }
}
