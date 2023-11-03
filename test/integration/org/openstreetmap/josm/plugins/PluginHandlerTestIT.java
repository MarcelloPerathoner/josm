// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.platform.commons.util.ReflectionUtils;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.gpx.GpxData;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MainApplicationTest;
import org.openstreetmap.josm.gui.layer.GpxLayer;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.annotations.Main;
import org.openstreetmap.josm.testutils.annotations.Projection;
import org.openstreetmap.josm.testutils.annotations.Territories;
import org.openstreetmap.josm.tools.Destroyable;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Utils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Integration tests of {@link PluginHandler} class.
 */
@BasicPreferences
@Main
@Projection
@Territories
@Timeout(value = 10, unit = TimeUnit.MINUTES)
public class PluginHandlerTestIT {

    private static final List<String> errorsToIgnore = new ArrayList<>();
    /**
     * Setup test.
     */
    @RegisterExtension
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public static JOSMTestRules test = new JOSMTestRules().https();

    /**
     * Setup test
     *
     * @throws IOException in case of I/O error
     */
    @BeforeAll
    public static void beforeClass() throws IOException {
        errorsToIgnore.addAll(TestUtils.getIgnoredErrorMessages(PluginHandlerTestIT.class));
    }

    /**
     * Test that available plugins rules can be loaded.
     */
    @Test
    void testValidityOfAvailablePlugins() {
        loadAllPlugins();

        Map<String, Throwable> loadingExceptions = PluginHandler.pluginLoadingExceptions.entrySet().stream()
                .filter(e -> !(Utils.getRootCause(e.getValue()) instanceof HeadlessException))
                .collect(Collectors.toMap(Map.Entry::getKey, e -> Utils.getRootCause(e.getValue())));

        List<PluginInformation> loadedPlugins = PluginHandler.getPlugins();
        Map<String, List<String>> invalidManifestEntries = loadedPlugins.stream().filter(pi -> !pi.invalidManifestEntries.isEmpty())
                .collect(Collectors.toMap(pi -> pi.name, pi -> pi.invalidManifestEntries));

        // Add/remove layers twice to test basic plugin good behaviour
        Map<String, Throwable> layerExceptions = new HashMap<>();
        for (int i = 0; i < 2; i++) {
            OsmDataLayer layer = new OsmDataLayer(new DataSet(), "Layer "+i, null);
            testPlugin(MainApplication.getLayerManager()::addLayer, layer, layerExceptions, loadedPlugins);
            testPlugin(MainApplication.getLayerManager()::removeLayer, layer, layerExceptions, loadedPlugins);
        }
        for (int i = 0; i < 2; i++) {
            GpxLayer layer = new GpxLayer(new GpxData(), "Layer "+i);
            testPlugin(MainApplication.getLayerManager()::addLayer, layer, layerExceptions, loadedPlugins);
            testPlugin(MainApplication.getLayerManager()::removeLayer, layer, layerExceptions, loadedPlugins);
        }

        Map<String, String> testCodeHashCollisions = checkForHashCollisions();

        Map<String, Throwable> noRestartExceptions = new HashMap<>();
        testCompletelyRestartlessPlugins(loadedPlugins, noRestartExceptions);

        debugPrint(invalidManifestEntries);
        debugPrint(loadingExceptions);
        debugPrint(layerExceptions);
        debugPrint(noRestartExceptions);
        debugPrint(testCodeHashCollisions);

        invalidManifestEntries = filterKnownErrors(invalidManifestEntries);
        loadingExceptions = filterKnownErrors(loadingExceptions);
        layerExceptions = filterKnownErrors(layerExceptions);
        noRestartExceptions = filterKnownErrors(noRestartExceptions);
        testCodeHashCollisions = filterKnownErrors(testCodeHashCollisions);

        String msg = errMsg("invalidManifestEntries", invalidManifestEntries) + '\n' +
                errMsg("loadingExceptions", loadingExceptions) + '\n' +
                errMsg("layerExceptions", layerExceptions) + '\n' +
                errMsg("noRestartExceptions", noRestartExceptions) + '\n' +
                errMsg("testCodeHashCollisions", testCodeHashCollisions);
        assertTrue(invalidManifestEntries.isEmpty()
                && loadingExceptions.isEmpty()
                && layerExceptions.isEmpty()
                && noRestartExceptions.isEmpty()
                && testCodeHashCollisions.isEmpty(), msg);

        /*
        try (PrintWriter out = new PrintWriter("/tmp/stacktrace.txt")) {
            Thread.getAllStackTraces().forEach((thread, traces) -> {
                out.println(thread.toString());
                for(StackTraceElement trace : traces) {
                    out.println("  " + trace.toString());
                }
            });
        } catch (FileNotFoundException e) {
        }
        */
    }

    private static String errMsg(String type, Map<String, ?> map) {
        return type + ": " + Arrays.toString(map.entrySet().toArray());
    }

    private static void testCompletelyRestartlessPlugins(List<PluginInformation> loadedPlugins,
            Map<String, Throwable> noRestartExceptions) {
        final List<LogRecord> records = new ArrayList<>();
        Handler tempHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() { /* Do nothing */ }

            @Override
            public void close() throws SecurityException { /* Do nothing */ }
        };
        Logging.getLogger().addHandler(tempHandler);
        try {
            List<PluginInformation> restartable = loadedPlugins.parallelStream()
                    .filter(info -> PluginHandler.getPlugin(info.name) instanceof Destroyable)
                    .collect(Collectors.toList());
            // ensure good plugin behavior with regards to Destroyable (i.e., they can be
            // removed and readded)
            for (int i = 0; i < 2; i++) {
                assertFalse(PluginHandler.removePlugins(restartable), () -> Logging.getLastErrorAndWarnings().toString());
                List<PluginInformation> notRemovedPlugins = restartable.stream()
                        .filter(info -> PluginHandler.getPlugins().contains(info)).collect(Collectors.toList());
                assertTrue(notRemovedPlugins.isEmpty(), notRemovedPlugins::toString);
                loadPlugins(restartable);
            }

            //assertTrue(PluginHandler.removePlugins(loadedPlugins), () -> Logging.getLastErrorAndWarnings().toString());
            assertTrue(restartable.parallelStream().noneMatch(info -> PluginHandler.getPlugins().contains(info)));
        } catch (Exception | LinkageError t) {
            Throwable root = Utils.getRootCause(t);
            root.printStackTrace();
            noRestartExceptions.put(findFaultyPlugin(loadedPlugins, root), root);
            records.removeIf(record -> Objects.equals(Utils.getRootCause(record.getThrown()), root));
        } catch (AssertionError assertionError) {
            noRestartExceptions.put("Plugin load/unload failed", assertionError);
        } finally {
            Logging.getLogger().removeHandler(tempHandler);
            for (LogRecord record : records) {
                if (record.getThrown() != null) {
                    Throwable root = Utils.getRootCause(record.getThrown());
                    root.printStackTrace();
                    noRestartExceptions.put(findFaultyPlugin(loadedPlugins, root), root);
                }
            }
        }
    }

    private static Map<String, String> checkForHashCollisions() {
        Map<Integer, List<String>> codes = new HashMap<>();
        for (Class<?> clazz : ReflectionUtils.findAllClassesInPackage("org.openstreetmap",
                org.openstreetmap.josm.data.validation.Test.class::isAssignableFrom, s -> true)) {
            if (org.openstreetmap.josm.data.validation.Test.class.isAssignableFrom(clazz)
            && !Objects.equals(org.openstreetmap.josm.data.validation.Test.class, clazz)) {
                // clazz.getName().hashCode() is how the base error codes are calculated since r18636
                // We want to avoid cases where the hashcode is too close, so we want to
                // ensure that there is at least 1m available codes after the hashCode.
                // This is needed since some plugins pick some really large number, and count up from there.
                int hashCeil = (int) Math.ceil(clazz.getName().hashCode() / 1_000_000d);
                int hashFloor = (int) Math.floor(clazz.getName().hashCode() / 1_000_000d);
                codes.computeIfAbsent(hashCeil, k -> new ArrayList<>()).add(clazz.getName());
                codes.computeIfAbsent(hashFloor, k -> new ArrayList<>()).add(clazz.getName());
            }
        }
        return codes.entrySet().stream().filter(entry -> entry.getValue().size() > 1).collect(
                Collectors.toMap(entry -> entry.getKey().toString(), entry -> String.join(", ", entry.getValue())));
    }

    private static <T> Map<String, T> filterKnownErrors(Map<String, T> errorMap) {
        return errorMap.entrySet().parallelStream()
                .filter(entry -> !errorsToIgnore.contains(convertEntryToString(entry)))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }

    private static void debugPrint(Map<String, ?> invalidManifestEntries) {
        System.out.println(invalidManifestEntries.entrySet()
                .stream()
                .map(PluginHandlerTestIT::convertEntryToString)
                .collect(Collectors.joining(", ")));
    }

    private static String convertEntryToString(Entry<String, ?> entry) {
        return entry.getKey() + "=\"" + entry.getValue() + "\"";
    }

    /**
     * Downloads and loads all JOSM plugins.
     */
    public static void loadAllPlugins() {
        // Download complete list of plugins
        Collection<String> onlinePluginSites = Arrays.asList(
            "file:///home/highlander/prj/josm-plugins-mapillary/build/dist/",
            "file:///home/highlander/prj/josm-plugins-mapwithai/build/dist/"
        );

        ReadRemotePluginInformationTask pluginInfoDownloadTask = new ReadRemotePluginInformationTask(
                onlinePluginSites);
                // Preferences.main().getOnlinePluginSites());

        pluginInfoDownloadTask.run();
        List<PluginInformation> plugins = pluginInfoDownloadTask.getAvailablePlugins();
        System.out.println("Original plugin list contains " + plugins.size() + " plugins");
        assertFalse(plugins.isEmpty(), plugins::toString);
        PluginInformation info = plugins.get(0);
        assertFalse(info.getName().isEmpty(), info::toString);
        assertFalse(info.getClass().getName().isEmpty(), info::toString);

        // Filter deprecated and unmaintained ones, or those not responsive enough to match our continuous integration needs
        //
        // mapwithai: if something goes wrong during initialization of this plugin, the plugin
        // waits forever for an event that will never happen and the test suite will never
        // complete. See: data.mapwithai.MapWithAILayerInfo::103
        List<String> uncooperatingPlugins = Arrays.asList("ebdirigo", "scoutsigns", "josm-config");
        Set<String> deprecatedPlugins = PluginHandler.getDeprecatedAndUnmaintainedPlugins();
        for (Iterator<PluginInformation> it = plugins.iterator(); it.hasNext();) {
            PluginInformation pi = it.next();
            if (deprecatedPlugins.contains(pi.name) || uncooperatingPlugins.contains(pi.name)) {
                System.out.println("Ignoring " + pi.name + " (deprecated, unmaintained, or uncooperative)");
                it.remove();
            }
        }

        // On Java < 11 and headless mode, filter plugins requiring JavaFX as Monocle is not available
        int javaVersion = Utils.getJavaVersion();
        if (GraphicsEnvironment.isHeadless() && javaVersion < 11) {
            for (Iterator<PluginInformation> it = plugins.iterator(); it.hasNext();) {
                PluginInformation pi = it.next();
                if (pi.getRequiredPlugins().contains("javafx")) {
                    System.out.println("Ignoring " + pi.name + " (requiring JavaFX and we're using Java < 11 in headless mode)");
                    it.remove();
                }
            }
        }

        // Skip unofficial plugins in headless mode, too much work for us for little added-value
        /*
        if (GraphicsEnvironment.isHeadless()) {
            for (Iterator<PluginInformation> it = plugins.iterator(); it.hasNext();) {
                PluginInformation pi = it.next();
                if (pi.isExternal()) {
                    System.out.println("Ignoring " + pi.name + " (unofficial plugin in headless mode)");
                    it.remove();
                }
            }
        }
        */

        System.out.println("Filtered plugin list contains " + plugins.size() + " plugins");

        // Download plugins
        MainApplicationTest.downloadPlugins(plugins);

        loadPlugins(plugins);
    }

    static void loadPlugins(List<PluginInformation> plugins) {
        // Load early plugins
        PluginHandler.loadEarlyPlugins(null, plugins, null);

        // Load late plugins
        PluginHandler.loadLatePlugins(null, plugins, null);
    }

    void testPlugin(Consumer<Layer> consumer, Layer layer,
            Map<String, Throwable> layerExceptions, Collection<PluginInformation> loadedPlugins) {
        try {
            consumer.accept(layer);
        } catch (Exception | LinkageError t) {
            Throwable root = Utils.getRootCause(t);
            root.printStackTrace();
            layerExceptions.put(findFaultyPlugin(loadedPlugins, root), root);
        }
    }

    private static String findFaultyPlugin(Collection<PluginInformation> plugins, Throwable root) {
        for (PluginInformation p : plugins) {
            try {
                ClassLoader cl = PluginHandler.getPluginClassLoader(p.getName());
                assertNotNull(cl);
                String pluginPackage = cl.loadClass(p.className).getPackage().getName();
                for (StackTraceElement e : root.getStackTrace()) {
                    try {
                        String stackPackage = cl.loadClass(e.getClassName()).getPackage().getName();
                        if (stackPackage.startsWith(pluginPackage)) {
                            return p.name;
                        }
                    } catch (ClassNotFoundException ex) {
                        System.err.println(ex.getMessage());
                        continue;
                    }
                }
            } catch (ClassNotFoundException ex) {
                System.err.println(ex.getMessage());
                continue;
            }
        }
        return "<unknown>";
    }
}
