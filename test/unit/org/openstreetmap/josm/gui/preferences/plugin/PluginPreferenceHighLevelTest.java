// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.preferences.plugin;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.gui.HelpAwareOptionPaneTest;
import org.openstreetmap.josm.gui.NotificationManager;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.plugins.PluginHandler;
import org.openstreetmap.josm.plugins.PluginHandlerTest;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.plugins.PluginProxy;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.testutils.PluginServer;
import org.openstreetmap.josm.testutils.annotations.AssertionsInEDT;
import org.openstreetmap.josm.testutils.annotations.AssumeRevision;
import org.openstreetmap.josm.testutils.annotations.FullPreferences;
import org.openstreetmap.josm.testutils.annotations.Main;
import org.openstreetmap.josm.tools.Logging;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;

/**
 * Higher level tests of {@link PluginPreference} class.
 */
@AssumeRevision("Revision: 10000\n")
@AssertionsInEDT
@FullPreferences
@Main
class PluginPreferenceHighLevelTest {
    final String SUCCESS = "<html>The following plugin has been downloaded <strong>successfully</strong>:";
    final String RESTART = "You have to restart JOSM for some settings to take effect." +
                           "<br/><br/>Would you like to restart now?</html>";

    /**
     * Plugin server mock.
     */
    @RegisterExtension
    static WireMockExtension pluginServerRule = WireMockExtension.newInstance()
            .options(options().dynamicPort().usingFilesUnderDirectory(TestUtils.getTestDataRoot()))
            .build();

    /**
     * Setup test.
     * @throws ReflectiveOperationException never
     */
    @BeforeEach
    public void setUp(TestInfo testInfo) throws ReflectiveOperationException {

        // some other tests actually go ahead and load plugins (notably at time of writing,
        // MainApplicationTest#testUpdateAndLoadPlugins), which really isn't a reversible operation.
        // it is, however, possible to pretend to our tests temporarily that they *aren't* loaded by
        // setting the PluginHandler#pluginList to empty for the duration of this test. ideally these
        // other tests wouldn't be so badly behaved or would at least do this from a separate batch
        // but this works for now
        @SuppressWarnings("unchecked")
        final Collection<PluginProxy> pluginList = (Collection<PluginProxy>) TestUtils.getPrivateStaticField(
            PluginHandler.class,
            "pluginList"
        );
        this.originalPluginList = new ArrayList<>(pluginList);
        pluginList.clear();

        Config.getPref().putInt("pluginmanager.version", 999);
        Config.getPref().put("pluginmanager.lastupdate", "999");
        Config.getPref().putList("pluginmanager.sites",
            Collections.singletonList(pluginServerRule.url("/plugins"))
        );

        this.referenceDummyJarOld = new File(TestUtils.getTestDataRoot(), "__files/plugin/dummy_plugin.v31701.jar");
        this.referenceDummyJarNew = new File(TestUtils.getTestDataRoot(), "__files/plugin/dummy_plugin.v31772.jar");
        this.referenceBazJarOld = new File(TestUtils.getTestDataRoot(), "__files/plugin/baz_plugin.v6.jar"); // 6800
        this.referenceBazJarNew = new File(TestUtils.getTestDataRoot(), "__files/plugin/baz_plugin.v7.jar"); // 8001
        this.pluginDir = Preferences.main().getPluginsDirectory();
        this.targetDummyJar = new File(this.pluginDir, "dummy_plugin.jar");
        this.targetDummyJarNew = new File(this.pluginDir, "dummy_plugin.jar.new");
        this.targetBazJar = new File(this.pluginDir, "baz_plugin.jar");
        this.targetBazJarNew = new File(this.pluginDir, "baz_plugin.jar.new");
        this.pluginDir.mkdirs();

        Logging.info("********** Starting test: {0}", testInfo.getDisplayName());
    }

    /**
     * Tear down.
     * @throws ReflectiveOperationException never
     */
    @AfterEach
    public void tearDown(TestInfo testInfo) throws ReflectiveOperationException {
        HelpAwareOptionPaneTest.setRobot(null);
        Logging.info("********** Done test: {0}", testInfo.getDisplayName());
        // restore actual PluginHandler#pluginList
        @SuppressWarnings("unchecked")
        final Collection<PluginProxy> pluginList = (Collection<PluginProxy>) TestUtils.getPrivateStaticField(
            PluginHandler.class,
            "pluginList"
        );
        pluginList.clear();
        pluginList.addAll(this.originalPluginList);
    }

    private Collection<PluginProxy> originalPluginList;

    private File pluginDir;
    private File referenceDummyJarOld;
    private File referenceDummyJarNew;
    private File referenceBazJarOld;
    private File referenceBazJarNew;
    private File targetDummyJar;
    private File targetDummyJarNew;
    private File targetBazJar;
    private File targetBazJarNew;

    Comparator<PluginInformation> comp = Comparator.<PluginInformation, String>comparing(
            pi -> pi.getName() == null ? "" : pi.getName().toLowerCase(Locale.ENGLISH));

    private String getNames(Collection<PluginInformation> plugins) {
        return String.join(" ", plugins.stream().map(p -> p.getName()).sorted().toList());
    }

    private String getPlugins(Collection<PluginInformation> plugins) {
        return String.join(" ", plugins.stream().map(p -> p.getName() + ":" + p.getVersion()).sorted().toList());
    }

    private String getConfiguredPlugins() throws IOException {
        return getPlugins(PluginHandlerTest.getConfiguredPlugins());
    }

    private String getLoadedPlugins() {
        return getPlugins(PluginHandler.getLoadedPlugins());
    }

    private void configure(String plugins) {
        Config.getPref().putList("plugins", Arrays.asList(plugins.split("\\s+")));
    }

    private PreferenceTabbedPane getTabbedPane() {
        final PreferenceTabbedPane tabbedPane = new PreferenceTabbedPane();
        tabbedPane.buildGui();
        // PluginPreference is already added to PreferenceTabbedPane by default
        tabbedPane.selectTabByPref(PluginPreference.class);
        return tabbedPane;
    }

    /**
     * Tests choosing a new plugin to install without upgrading an already-installed plugin
     * @throws Exception never
     */
    @Test
    void testInstallWithoutUpdate(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        final PluginServer pluginServer = new PluginServer(
            new PluginServer.RemotePlugin(this.referenceDummyJarNew),
            new PluginServer.RemotePlugin(this.referenceBazJarOld),
            new PluginServer.RemotePlugin(null, Collections.singletonMap("Plugin-Version", "2"), "irrelevant_plugin")
        );
        pluginServer.applyToWireMockServer(wireMockRuntimeInfo);

        Files.copy(this.referenceDummyJarOld.toPath(), this.targetDummyJar.toPath());

        configure("dummy_plugin");
        final PreferenceTabbedPane tabbedPane = getTabbedPane();
        final PluginPreferenceModel model = tabbedPane.getPluginPreference().getModel();

        model.select("baz_plugin", true);
        assertEquals("baz_plugin dummy_plugin", getNames(model.getSelectedPlugins()));

        assertEquals("dummy_plugin:31701", getConfiguredPlugins());
        assertEquals("baz_plugin:6", getPlugins(model.getPluginsToDownload()));

        List<String> robotList = NotificationManager.getInstance().robotList;
        robotList.clear();
        tabbedPane.savePreferences();
        Awaitility.await().atMost(2000, MILLISECONDS).until(() -> !tabbedPane.getPluginPreference().working);

        assertEquals("baz_plugin:6 dummy_plugin:31701", getConfiguredPlugins());

        assertEquals(List.of(
            "Downloading plugin: baz_plugin",
            "Please restart JOSM to activate the plugins."
        ), robotList);

        // dummy_plugin jar shouldn't have been updated
        TestUtils.assertFileContentsEqual(this.referenceDummyJarOld, this.targetDummyJar);
        // baz_plugin jar should have been installed
        TestUtils.assertFileContentsEqual(this.referenceBazJarOld, this.targetBazJar);

        // neither of these .jar.new files should have been left hanging round
        assertFalse(targetDummyJarNew.exists());
        assertFalse(targetBazJarNew.exists());

        // the advertised version of dummy_plugin shouldn't have been fetched
        pluginServerRule.verify(0, WireMock.getRequestedFor(WireMock.urlEqualTo("/plugin/dummy_plugin.v31772.jar")));
        // but the advertized version of baz_plugin *should* have
        pluginServerRule.verify(1, WireMock.getRequestedFor(WireMock.urlEqualTo("/plugin/baz_plugin.v6.jar")));

        // pluginmanager.version has been set to the current version
        // questionably correct
        assertEquals(10000, Config.getPref().getInt("pluginmanager.version", 111));
        // however pluginmanager.lastupdate hasn't been updated
        // questionably correct
        assertEquals("999", Config.getPref().get("pluginmanager.lastupdate", "111"));
    }

    /**
     * Tests a plugin being disabled without applying available upgrades
     * @throws Exception never
     */
    @Test
    void testDisablePluginWithUpdatesAvailable(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        final PluginServer pluginServer = new PluginServer(
            new PluginServer.RemotePlugin(this.referenceDummyJarNew),
            new PluginServer.RemotePlugin(this.referenceBazJarNew),
            new PluginServer.RemotePlugin(null, null, "irrelevant_plugin")
        );
        pluginServer.applyToWireMockServer(wireMockRuntimeInfo);

        Files.copy(this.referenceDummyJarOld.toPath(), this.targetDummyJar.toPath());
        Files.copy(this.referenceBazJarOld.toPath(), this.targetBazJar.toPath());

        configure("baz_plugin dummy_plugin");
        final PreferenceTabbedPane tabbedPane = getTabbedPane();
        final PluginPreferenceModel model = tabbedPane.getPluginPreference().getModel();

        model.select("baz_plugin", false);
        assertEquals("dummy_plugin", getNames(model.getSelectedPlugins()));

        assertEquals("baz_plugin:6 dummy_plugin:31701", getConfiguredPlugins());
        List<String> robotList = NotificationManager.getInstance().robotList;
        robotList.clear();
        tabbedPane.savePreferences();
        Awaitility.await().atMost(2000, MILLISECONDS).until(() -> !tabbedPane.getPluginPreference().working);

        assertEquals("dummy_plugin:31701", getConfiguredPlugins());

        // This is the correct message! You might think we deactivated a plugin but what
        // really happended is: the dummy_plugin was *not* loaded, but the checkbox for
        // it remained checked.  Thus the implementation tried to load the dummy_plugin
        // at runtime and failed.
        assertEquals(List.of("Please restart JOSM to activate the plugins."), robotList);

        // dummy_plugin jar shouldn't have been updated
        TestUtils.assertFileContentsEqual(this.referenceDummyJarOld, this.targetDummyJar);
        // baz_plugin jar shouldn't have been deleted
        TestUtils.assertFileContentsEqual(this.referenceBazJarOld, this.targetBazJar);

        // neither of these .jar.new files have a reason to be here
        assertFalse(targetDummyJarNew.exists());
        assertFalse(targetBazJarNew.exists());

        // neither of the new jars have been fetched
        pluginServerRule.verify(0, WireMock.getRequestedFor(WireMock.urlEqualTo("/plugin/dummy_plugin.v31772.jar")));
        pluginServerRule.verify(0, WireMock.getRequestedFor(WireMock.urlEqualTo("/plugin/baz_plugin.v6.jar")));

        // pluginmanager.version has been set to the current version
        // questionably correct
        assertEquals(10000, Config.getPref().getInt("pluginmanager.version", 111));
        // however pluginmanager.lastupdate hasn't been updated
        // questionably correct
        assertEquals("999", Config.getPref().get("pluginmanager.lastupdate", "111"));
    }

    /**
     * Tests the effect of requesting a "plugin update" when everything is up to date
     * @throws Exception never
     */
    @Test
    void testUpdateWithNoAvailableUpdates(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        TestUtils.assumeWorkingJMockit();
        final PluginServer pluginServer = new PluginServer(
            new PluginServer.RemotePlugin(this.referenceDummyJarOld),
            new PluginServer.RemotePlugin(this.referenceBazJarOld),
            new PluginServer.RemotePlugin(null, Collections.singletonMap("Plugin-Version", "123"), "irrelevant_plugin")
        );
        pluginServer.applyToWireMockServer(wireMockRuntimeInfo);

        Files.copy(this.referenceDummyJarOld.toPath(), this.targetDummyJar.toPath());
        Files.copy(this.referenceBazJarOld.toPath(), this.targetBazJar.toPath());

        configure("baz_plugin dummy_plugin");
        final PreferenceTabbedPane tabbedPane = getTabbedPane();
        final PluginPreferenceModel model = tabbedPane.getPluginPreference().getModel();

        var messages = new ArrayList<Object>();
        HelpAwareOptionPaneTest.setRobot(o -> { messages.add(o); return 0; });

        assertEquals("baz_plugin:6 dummy_plugin:31701", getConfiguredPlugins());
        assertEquals("", getPlugins(model.getPluginsToUpdate()));

        TestUtils.click(tabbedPane, "updatePluginsButton");
        Awaitility.await().atMost(2000, MILLISECONDS).until(() -> !tabbedPane.getPluginPreference().working);

        assertEquals("baz_plugin:6 dummy_plugin:31701", getConfiguredPlugins());

        assertEquals(List.of(
            "All installed plugins are up to date. JOSM does not have to download newer versions."
        ), messages);

        // neither jar should have changed
        TestUtils.assertFileContentsEqual(this.referenceDummyJarOld, this.targetDummyJar);
        TestUtils.assertFileContentsEqual(this.referenceBazJarOld, this.targetBazJar);

        // no reason for any .jar.new files
        assertFalse(targetDummyJarNew.exists());
        assertFalse(targetBazJarNew.exists());

        // that should have been the only request to our PluginServer
        assertEquals(1, pluginServerRule.getAllServeEvents().size());
        pluginServerRule.verify(1, WireMock.getRequestedFor(WireMock.urlEqualTo("/plugins")));
        pluginServerRule.resetRequests();

        // pluginmanager.version has been set to the current version
        assertEquals(10000, Config.getPref().getInt("pluginmanager.version", 111));
        // pluginmanager.lastupdate hasn't been updated
        // questionably correct
        assertEquals("999", Config.getPref().get("pluginmanager.lastupdate", "111"));

        messages.clear();
        tabbedPane.savePreferences();
        Awaitility.await().atMost(2000, MILLISECONDS).until(() -> !tabbedPane.getPluginPreference().working);
        assertEquals(List.of(), messages);

        // both jars are still the original version
        TestUtils.assertFileContentsEqual(this.referenceDummyJarOld, this.targetDummyJar);
        TestUtils.assertFileContentsEqual(this.referenceBazJarOld, this.targetBazJar);

        // no reason for any .jar.new files
        assertFalse(targetDummyJarNew.exists());
        assertFalse(targetBazJarNew.exists());

        // none of PluginServer's URLs should have been touched
        assertEquals(0, pluginServerRule.getAllServeEvents().size());

        // pluginmanager.version has been set to the current version
        assertEquals(10000, Config.getPref().getInt("pluginmanager.version", 111));
        // pluginmanager.lastupdate hasn't been updated
        // questionably correct
        assertEquals("999", Config.getPref().get("pluginmanager.lastupdate", "111"));
    }

    /**
     * Tests installing a single plugin which is marked as "Canloadatruntime"
     * @throws Exception never
     */
    @Test
    void testInstallWithoutRestartRequired(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        TestUtils.assumeWorkingJMockit();

        final PluginServer pluginServer = new PluginServer(
            new PluginServer.RemotePlugin(this.referenceDummyJarNew),
            new PluginServer.RemotePlugin(this.referenceBazJarNew)
        );
        pluginServer.applyToWireMockServer(wireMockRuntimeInfo);

        configure("");
        final PreferenceTabbedPane tabbedPane = getTabbedPane();
        final PluginPreferenceModel model = tabbedPane.getPluginPreference().getModel();

        model.select("dummy_plugin", true);
        assertEquals("dummy_plugin", getNames(model.getSelectedPlugins()));

        assertEquals("dummy_plugin:31772", getPlugins(model.getPluginsToDownload()));
        assertEquals("", getConfiguredPlugins());
        // this cannot work as dummy_plugin:31772 throws java.lang.ClassNotFoundException: org.openstreetmap.josm.gui.NameFormatterHook
        // and has done so since 2017-08-25. See #15182 - move `NameFormatter*` from `gui` to `data.osm`
        PluginHandler.pluginListNotLoaded.clear();
        List<String> robotList = NotificationManager.getInstance().robotList;
        robotList.clear();
        tabbedPane.savePreferences();
        Awaitility.await().atMost(2000, MILLISECONDS).until(() -> !tabbedPane.getPluginPreference().working);
        assertEquals("dummy_plugin:31772", getConfiguredPlugins());
        assertEquals("dummy_plugin:31772", getLoadedPlugins());

        assertEquals(List.of(
            "Downloading plugin: dummy_plugin"
        ), robotList);

        // any .jar.new files should have been deleted
        assertFalse(targetDummyJarNew.exists());
        assertFalse(targetBazJarNew.exists());

        // dummy_plugin was fetched
        pluginServerRule.verify(1, WireMock.getRequestedFor(WireMock.urlEqualTo("/plugin/dummy_plugin.v31772.jar")));

        // the dummy_plugin jar has been installed
        TestUtils.assertFileContentsEqual(this.referenceDummyJarNew, this.targetDummyJar);
        // the baz_plugin jar has not
        assertFalse(this.targetBazJar.exists());

        // pluginmanager.version has been set to the current version
        assertEquals(10000, Config.getPref().getInt("pluginmanager.version", 111));
        // pluginmanager.lastupdate hasn't been updated
        // questionably correct
        assertEquals("999", Config.getPref().get("pluginmanager.lastupdate", "111"));
    }

    /**
     * Tests installing a single plugin which has multiple versions advertised, with our JOSM version
     * preventing us from using the latest version
     * @throws Exception on failure
     */
    @AssumeRevision("Revision: 7000\n")
    @Test
    void testInstallMultiVersion(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        TestUtils.assumeWorkingJMockit();

        final String bazOldServePath = "/baz/old.jar";
        final PluginServer pluginServer = new PluginServer(
            new PluginServer.RemotePlugin(this.referenceDummyJarNew),
            new PluginServer.RemotePlugin(this.referenceBazJarNew, Collections.singletonMap(
                "6800_Plugin-Url", "6;" + pluginServerRule.url(bazOldServePath)
            ))
        );
        pluginServer.applyToWireMockServer(wireMockRuntimeInfo);
        // need to actually serve this older jar from somewhere
        pluginServerRule.stubFor(
            WireMock.get(WireMock.urlEqualTo(bazOldServePath)).willReturn(
                WireMock.aResponse().withStatus(200).withHeader("Content-Type", "application/java-archive").withBodyFile(
                    "plugin/baz_plugin.v6.jar"
                )
            )
        );

        configure("");
        final PreferenceTabbedPane tabbedPane = getTabbedPane();
        final PluginPreferenceModel model = tabbedPane.getPluginPreference().getModel();

        model.select("baz_plugin", true);
        assertEquals("baz_plugin", getNames(model.getSelectedPlugins()));
        assertEquals("baz_plugin:6", getPlugins(model.getPluginsToDownload()));

        assertEquals("", getConfiguredPlugins());
        List<String> robotList = NotificationManager.getInstance().robotList;
        robotList.clear();
        tabbedPane.savePreferences();
        Awaitility.await().atMost(2000, MILLISECONDS).until(() -> !tabbedPane.getPluginPreference().working);
        assertEquals("baz_plugin:6", getConfiguredPlugins());

        assertEquals(List.of(
            "Downloading plugin: baz_plugin",
            "Please restart JOSM to activate the plugins."
        ), robotList);

        // any .jar.new files should have been deleted
        assertFalse(targetDummyJarNew.exists());
        assertFalse(targetBazJarNew.exists());

        // dummy_plugin was fetched
        pluginServerRule.verify(1, WireMock.getRequestedFor(WireMock.urlEqualTo(bazOldServePath)));

        // the "old" baz_plugin jar has been installed
        TestUtils.assertFileContentsEqual(this.referenceBazJarOld, this.targetBazJar);
        // the dummy_plugin jar has not
        assertFalse(this.targetDummyJar.exists());

        // pluginmanager.version has been set to the current version
        assertEquals(7000, Config.getPref().getInt("pluginmanager.version", 111));
        // pluginmanager.lastupdate hasn't been updated
        // questionably correct
        assertEquals("999", Config.getPref().get("pluginmanager.lastupdate", "111"));
    }
}
