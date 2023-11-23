// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.logging.Level;

import javax.annotation.concurrent.Immutable;
import javax.swing.Icon;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.data.Version;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.LanguageInfo;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Platform;
import org.openstreetmap.josm.tools.PlatformManager;
import org.openstreetmap.josm.tools.Utils;

/**
 * Holds information about one plugin.
 * <p>
 * The information in this class is usually got reading the <b>plugin manifest</b> file
 * in a plugin jar. (That is the file {@code /META-INF/MANIFEST.MF} in the jar. A jar is
 * a basically a zip file.)
 * <p>
 * Another way to gather this information is by reading a <b>site manifest</b> file,
 * which is a concat of all the manifests of the plugins hosted by that site.  (The site
 * manifest is a JOSM-proprietary format.)
 * <p>
 * This class is <b>immutable</b>, and so it is thread-safe.
 *
 * @author imi
 * @since 153
 * @since xxx (rewritten)
 */
@Immutable
public final class PluginInformation {
    private static Icon emptyIcon = ImageProvider.getEmpty(ImageProvider.ImageSizes.LARGEICON);

    /** The name of the plugin. */
    private final String name;
    /** The URI of the jar file, local or upstream. */
    private final URI uri;
    /** The version of the plugin */
    private final ComparableVersion version;
    /** The plugin icon. */
    private final Icon icon;

    /** All manifest attributes. */
    private final Attributes attr;
    /** The libraries referenced in Class-Path manifest attribute. */
    private final List<URL> libraries;
    /** Invalid manifest entries */
    private final List<String> invalidManifestEntries;
    /** Older version information, if found */
    private final List<PluginInformation> oldVersions;

    /**
     * Creates a plugin information object for the plugin with name {@code name}.
     * Information about the plugin is extracted from the manifest file in the plugin jar
     * {@code file}.
     * @param file the plugin jar
     * @param name the plugin name
     * @throws PluginException if reading the manifest file fails
     */
    public PluginInformation(File file, String name, URI uri) throws PluginException {
        if (!PluginHandler.isValidJar(file)) {
            throw new PluginException(tr("Invalid jar file ''{0}''", file));
        }
        this.name = name;
        this.uri = uri;
        invalidManifestEntries = new ArrayList<>();
        try (
            InputStream fis = Files.newInputStream(file.toPath());
            JarInputStream jar = new JarInputStream(fis)
        ) {
            Manifest manifest = jar.getManifest();
            if (manifest == null)
                throw new PluginException(tr("The plugin file ''{0}'' does not include a Manifest.", file.toString()));
            attr = manifest.getMainAttributes();
            version = new ComparableVersion(getVersion());
            icon = initIcon(attr, file);
            libraries = buildLibraries(attr, file);
            oldVersions = buildOldVersions();
            checkManifest(attr);
        } catch (IOException | InvalidPathException e) {
            throw new PluginException(name, e);
        }
    }

    /**
     * Creates a plugin information object by reading plugin information in Manifest format
     * from the input stream {@code manifestStream}.
     *
     * @param manifestStream the stream to read the manifest from
     * @param name the plugin name
     * @param uri the URI of the plugin
     * @throws PluginException if the plugin information can't be read from the input stream
     */
    public PluginInformation(InputStream manifestStream, String name, URI uri) throws PluginException {
        this.name = name;
        this.uri = uri;
        invalidManifestEntries = new ArrayList<>();
        try {
            Manifest manifest = new Manifest();
            manifest.read(manifestStream);
            attr = manifest.getMainAttributes();
            icon = initIcon(attr, null);
            version = new ComparableVersion(getVersion());
            libraries = buildLibraries(attr, null);
            oldVersions = buildOldVersions();
            checkManifest(attr);
        } catch (IOException e) {
            throw new PluginException(name, e);
        }
    }

    /**
     * Creates a plugin information object from attributes.
     *
     * @param attributes the manifest attributes
     * @param name the plugin name
     * @param uri the URI of the plugin
     */
    public PluginInformation(Attributes attributes, String name, URI uri) {
        this.name = name;
        this.uri = uri;
        icon = initIcon(attributes, null);
        invalidManifestEntries = new ArrayList<>();
        attr = new Attributes(attributes);
        version = new ComparableVersion(getVersion());
        libraries = buildLibraries(attributes, null);
        oldVersions = buildOldVersions();
        checkManifest(attributes);
    }

    /**
     * Replies the name of the plugin.
     * @return The plugin name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the plugin class name.
     */
    public String getClassName() {
        return attr.getValue("Plugin-Class");
    }

    public String getDownloadLink() {
        return uri.toString();
    }

    public URI getUri() {
        return uri;
    }

    /** Returns the minimum JOSM version required by this plugin. */
    public int getMainVersion() {
        String s = attr.getValue("Plugin-Mainversion");
        if (s != null) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /** Returns the minimum Java version required by this plugin. */
    public int getMinJavaVersion() {
        String s = attr.getValue("Plugin-Minimum-Java-Version");
        if (s != null) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    public String getVersion() {
        String v = attr.getValue("Plugin-Version");
        return v != null ? v : "unknown";
    }

    public List<PluginInformation> getOldVersions() {
        return oldVersions;
    }

    public boolean isOldMode() {
        return "true".equals(attr.getValue("Plugin-Oldmode"));
    }

    /** The virtual plugin provided by this plugin, if native for a given platform. */
    public String getProvides() {
        return attr.getValue("Plugin-Provides");
    }

    /** Returns true if the plugin must be loaded early. */
    public boolean isEarly() {
        return Boolean.parseBoolean(attr.getValue("Plugin-Early"));
    }

    /** Returns the plugin stage, determining the loading sequence order of plugins. */
    public int getStage() {
        String s = attr.getValue("Plugin-Stage");
        if (s != null) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                // nothing to do here
            }
        }
        return 50;
    }


    /** The plugin platform on which it is meant to run (windows, osx, unixoid). */
    public String getPlatform() {
        return attr.getValue("Plugin-Platform");
    }

    /** The list of required plugins, separated by ';' (from plugin list). */
    public String getRequires() {
        return attr.getValue("Plugin-Requires");
    }

    /** Return true if the plugin can be loaded at any time and not just at program start. */
    public boolean canLoadAtRuntime() {
        return Boolean.parseBoolean(attr.getValue("Plugin-Canloadatruntime"));
    }

    public Attributes getAttributes() {
        return new Attributes(attr);
    }

    public List<URL> getLibraries() {
        return libraries;
    }

    public synchronized List<String> getInvalidManifestEntries() {
        return new ArrayList<>(invalidManifestEntries);
    }

    public synchronized String getLink() {
        String lang = LanguageInfo.getLanguageCodeManifest();
        String link = attr.getValue(lang + "Plugin-Link");
        if (link != null) return link;
        return attr.getValue("Plugin-Link");
    }

    public synchronized String getDescription() {
        String lang = LanguageInfo.getLanguageCodeManifest();
        String desc = attr.getValue(lang + "Plugin-Description");
        if (desc != null) return desc;
        return attr.getValue("Plugin-Description");
    }

    public synchronized String getAuthor() {
        return attr.getValue("Author");
    }

    /**
     * Checks for errors in the manifest
     */
    private final void checkManifest(Attributes attr) {
        String lang = LanguageInfo.getLanguageCodeManifest();

        String s = Optional.ofNullable(attr.getValue(lang+"Plugin-Link")).orElseGet(() -> attr.getValue("Plugin-Link"));
        if (s != null && !Utils.isValidUrl(s)) {
            Logging.info(tr("Invalid URL ''{0}'' in plugin {1}", s, getName()));
        }
        s = attr.getValue(lang+"Plugin-Description");
        if (s == null) {
            s = attr.getValue("Plugin-Description");
            if (s != null) {
                try {
                    tr(s);
                } catch (IllegalArgumentException e) {
                    Logging.debug(e);
                    Logging.info(tr("Invalid plugin description ''{0}'' in plugin {1}", s, getName()));
                }
            }
        }
        String version = attr.getValue("Plugin-Version");
        if (!Utils.isEmpty(version) && version.charAt(0) == '$') {
            invalidManifestEntries.add("Plugin-Version");
        }
        s = attr.getValue("Plugin-Mainversion");
        if (s != null) {
            try {
                Integer.parseInt(s);
            } catch (NumberFormatException e) {
                Logging.warn(tr("Invalid plugin main version ''{0}'' in plugin {1}", s, getName()));
                Logging.trace(e);
            }
        } else {
            Logging.warn(tr("Missing plugin main version in plugin {0}", getName()));
        }
        s = attr.getValue("Plugin-Minimum-Java-Version");
        if (s != null) {
            try {
                Integer.parseInt(s);
            } catch (NumberFormatException e) {
                Logging.warn(tr("Invalid Java version ''{0}'' in plugin {1}", s, getName()));
                Logging.trace(e);
            }
        }
    }

    private final List<URL> buildLibraries(Attributes attr, File file) {
        List<URL> libs = new ArrayList<>();
        try {
            if (file != null)
                libs.add(file.toURI().toURL());
        } catch (MalformedURLException e) {
            Logging.error("Malformed URL {0} in plugin {1}", file.toString(), getName());
        }
        String classPath = attr.getValue(Attributes.Name.CLASS_PATH);
        if (classPath != null) {
            for (String entry : classPath.split(" ", -1)) {
                File entryFile;
                if (new File(entry).isAbsolute() || file == null) {
                    entryFile = new File(entry);
                } else {
                    entryFile = new File(file.getParent(), entry);
                }
                try {
                    libs.add(entryFile.toURI().toURL());
                } catch (MalformedURLException e) {
                    Logging.error("Malformed URL {0} in plugin {1}", entryFile.toString(), getName());
                }
            }
        }
        return Collections.unmodifiableList(libs);
    }

    private final Icon initIcon(Attributes attr, File file) {
        String iconPath = attr.getValue("Plugin-Icon");
        if (iconPath != null) {
            if (file != null) {
                // extract icon from the plugin jar file
                return new ImageProvider(iconPath).setArchive(file).setMaxSize(ImageProvider.ImageSizes.LARGEICON)
                    .setOptional(true).get();
            } else if (iconPath.startsWith("data:")) {
                return new ImageProvider(iconPath).setMaxSize(ImageProvider.ImageSizes.LARGEICON)
                    .setOptional(true).get();
            }
        } else {
            return emptyIcon;
        }
        return null;
    }

    public List<PluginInformation> buildOldVersions() {
        List<PluginInformation> old = new ArrayList<>();
        for (Map.Entry<Object, Object> entry : attr.entrySet()) {
            String key = ((Attributes.Name) entry.getKey()).toString();
            if (key.endsWith("_Plugin-Url")) {
                try {
                    String oldVersion = key.substring(0, key.length() - 11);
                    String value = (String) entry.getValue();
                    int i = value.indexOf(';');
                    if (i <= 0) {
                        invalidManifestEntries.add(key);
                        continue;
                    }
                    Attributes attributes = new Attributes();
                    attributes.putValue("Plugin-Mainversion", oldVersion);
                    attributes.putValue("Plugin-Version", value.substring(0, i));
                    attributes.putValue("Plugin-Oldmode", "true");
                    old.add(new PluginInformation(attributes, getName(), new URI(value.substring(i + 1))));
                } catch (NumberFormatException | IndexOutOfBoundsException | URISyntaxException e) {
                    invalidManifestEntries.add(key);
                    Logging.error(e);
                }
            }
        }
        return Collections.unmodifiableList(old);
    }

    /*
    private String formatPluginRemoteVersion() {
        StringBuilder sb = new StringBuilder();
        if (Utils.isBlank(version)) {
            sb.append(tr("unknown"));
        } else {
            sb.append(version);
            if (oldmode) {
                sb.append('*');
            }
        }
        return sb.toString();
    }
    */

    /**
     * Returns true if this plugin is compatible with the running environment
     */
    public boolean isCompatible() {
        if (getMainVersion() > Version.getInstance().getVersion())
            return false;
        if (getMinJavaVersion() > Utils.getJavaVersion())
            return false;
        return isForCurrentPlatform();
    }

    /**
     * Determines if this plugin comes from an external, non-official source.
     * @return {@code true} if this plugin comes from an external, non-official source.
     * @since 18267
     */
    public boolean isExternal() {
        String downloadlink = getDownloadLink();
        return downloadlink != null
                && !downloadlink.startsWith(Preferences.getDefaultPluginSite())
                && !downloadlink.startsWith("https://josm.openstreetmap.de/osmsvn/applications/editors/josm/dist/")
                && !downloadlink.startsWith("https://github.com/JOSM/")
                && !downloadlink.startsWith("file:");
    }

    /**
     * Loads and instantiates the plugin.
     *
     * @param klass the plugin class
     * @param classLoader the class loader for the plugin
     * @return the instantiated and initialized plugin
     * @throws PluginException if the plugin cannot be loaded or instanciated
     * @since 12322
     */
    PluginProxy load(Class<?> klass, PluginClassLoader classLoader) throws PluginException {
        try {
            Constructor<?> c = klass.getConstructor(PluginInformation.class);
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(classLoader);
            try {
                return new PluginProxy(c.newInstance(this), this, classLoader);
            } finally {
                Thread.currentThread().setContextClassLoader(contextClassLoader);
            }
        } catch (ReflectiveOperationException e) {
            throw new PluginException(getName(), e);
        }
    }

    /**
     * Loads the class of the plugin.
     *
     * @param classLoader the class loader to use
     * @return the loaded class
     * @throws PluginException if the class cannot be loaded
     */
    Class<?> loadClass(ClassLoader classLoader) throws PluginException {
        if (getClassName() == null)
            return null;
        try {
            return Class.forName(getClassName(), true, classLoader);
        } catch (NoClassDefFoundError | ClassNotFoundException | ClassCastException e) {
            Logging.logWithStackTrace(Level.SEVERE, e,
                    "Unable to load class {0} from plugin {1} using classloader {2}", getClassName(), getName(), classLoader);
            throw new PluginException(getName(), e);
        }
    }

    /**
     * Try to find a plugin after some criteria. Extract the plugin-information
     * from the plugin and return it. The plugin is searched in the following way:
     *<ol>
     *<li>first look after an MANIFEST.MF in the package org.openstreetmap.josm.plugins.&lt;plugin name&gt;
     *    (After removing all fancy characters from the plugin name).
     *    If found, the plugin is loaded using the bootstrap classloader.</li>
     *<li>If not found, look for a jar file in the user specific plugin directory
     *    (~/.josm/plugins/&lt;plugin name&gt;.jar)</li>
     *<li>If not found and the environment variable JOSM_RESOURCES + "/plugins/" exist, look there.</li>
     *<li>Try for the java property josm.resources + "/plugins/" (set via java -Djosm.plugins.path=...)</li>
     *<li>If the environment variable ALLUSERSPROFILE and APPDATA exist, look in
     *    ALLUSERSPROFILE/&lt;the last stuff from APPDATA&gt;/JOSM/plugins.
     *    (*sic* There is no easy way under Windows to get the All User's application
     *    directory)</li>
     *<li>Finally, look in some typical unix paths:<ul>
     *    <li>/usr/local/share/josm/plugins/</li>
     *    <li>/usr/local/lib/josm/plugins/</li>
     *    <li>/usr/share/josm/plugins/</li>
     *    <li>/usr/lib/josm/plugins/</li></ul></li>
     *</ol>
     * If a plugin class or jar file is found earlier in the list but seem not to
     * be working, an PluginException is thrown rather than continuing the search.
     * This is so JOSM can detect broken user-provided plugins and do not go silently
     * ignore them.
     *
     * The plugin is not initialized. If the plugin is a .jar file, it is not loaded
     * (only the manifest is extracted). In the classloader-case, the class is
     * bootstraped (e.g. static {} - declarations will run. However, nothing else is done.
     *
     * @param pluginName The name of the plugin (in all lowercase). E.g. "lang-de"
     * @return Information about the plugin or <code>null</code>, if the plugin
     *         was nowhere to be found.
     * @throws PluginException In case of broken plugins.
     */
    public static PluginInformation findPlugin(String pluginName) throws PluginException {
        String name = pluginName;
        name = name.replaceAll("[-. ]", "");
        try (InputStream manifestStream = Utils.getResourceAsStream(
                PluginInformation.class, "/org/openstreetmap/josm/plugins/"+name+"/MANIFEST.MF")) {
            if (manifestStream != null) {
                return new PluginInformation(manifestStream, pluginName, null);
            }
        } catch (IOException e) {
            Logging.warn(e);
        }

        Collection<String> locations = getPluginLocations();

        String[] nameCandidates = {
                pluginName,
                pluginName + "-" + PlatformManager.getPlatform().getPlatform().name().toLowerCase(Locale.ENGLISH)};
        for (String s : locations) {
            for (String nameCandidate: nameCandidates) {
                File pluginFile = new File(s, nameCandidate + ".jar");
                if (pluginFile.exists()) {
                    return new PluginInformation(pluginFile, nameCandidate, pluginFile.toURI());
                }
            }
        }
        return null;
    }

    /**
     * Returns all possible plugin locations.
     * @return all possible plugin locations.
     */
    public static Collection<String> getPluginLocations() {
        Collection<String> locations = Preferences.getAllPossiblePreferenceDirs();
        Collection<String> all = new ArrayList<>(locations.size());
        for (String s : locations) {
            all.add(s+"plugins");
        }
        return all;
    }

    /**
     * Replies the plugin icon, scaled to LARGE_ICON size.
     * @return the plugin icon, scaled to LARGE_ICON size.
     */
    public Icon getScaledIcon() {
        return icon;
    }

    @Override
    public final String toString() {
        return getName();
    }

    private static List<String> getRequiredPlugins(String pluginList) {
        List<String> requiredPlugins = new ArrayList<>();
        if (pluginList != null) {
            for (String s : pluginList.split(";", -1)) {
                String plugin = s.trim();
                if (!plugin.isEmpty()) {
                    requiredPlugins.add(plugin);
                }
            }
        }
        return requiredPlugins;
    }

    /**
     * Replies the list of plugins required by the up-to-date version of this plugin.
     * @return List of plugins required. Empty if no plugin is required.
     * @since 5601
     */
    public List<String> getRequiredPlugins() {
        return getRequiredPlugins(getRequires());
    }

    /**
     * Determines if this plugin can be run on the current platform.
     * @return {@code true} if this plugin can be run on the current platform
     * @since 14384
     */
    boolean isForCurrentPlatform() {
        try {
            return getPlatform() == null || PlatformManager.getPlatform().getPlatform() == Platform.valueOf(getPlatform().toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            Logging.warn(e);
            return true;
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((uri == null) ? 0 : uri.hashCode());
        result = prime * result + ((attr == null) ? 0 : attr.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        PluginInformation other = (PluginInformation) obj;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (uri == null) {
            if (other.uri != null)
                return false;
        } else if (!uri.equals(other.uri))
            return false;
        if (attr == null) {
            if (other.attr != null)
                return false;
        } else if (!attr.equals(other.attr))
            return false;
        return true;
    }

    /**
     * Compares two plugins according to name + version
     * <p>
     * Caveat: this is <b>not</b> consistent with equals
     */
    public int compareTo(PluginInformation o) {
        int ret = getName().compareTo(o.getName());
        if (ret != 0) return ret;
        return version.compareTo(o.version);
    }

    public static final Comparator<PluginInformation> nameVersion = PluginInformation::compareTo;

    public static final Comparator<PluginInformation> alphabetical = Comparator.<PluginInformation, String>comparing(
        pi -> pi.getName() == null ? "" : pi.getName().toLowerCase(Locale.ENGLISH));
}
