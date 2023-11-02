// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.preferences.plugin;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.Icon;
import javax.swing.JOptionPane;
import javax.swing.table.AbstractTableModel;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.openstreetmap.josm.plugins.PluginHandler;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Utils;

/**
 * The model for the plugin list table.
 * <p>
 * Definitions:
 * <ul>
 * <li><b>Installed</b>: A plugin whose jar is stored in the configured plugins directory.
 * Information about an installed plugin is read from the {@code META-INF/MANIFEST.MF}
 * file in the .jar file itself.
 *
 * <li><b>Selected</b>: A plugin that is ticked in the table.
 *
 * <li><b>Configured</b>: A plugin set to load in the Config. A plugin must be installed
 * before it can be configured. Configured plugins that are not installed will error
 * during program startup.
 *
 * <li><b>Loaded</b>: A plugin currently loaded into memory. A plugin must be configured
 * before it will load.
 *
 * <li><b>Upstream</b>: A plugin that is offered for download at one or more of
 * the configured plugin repositories. Information about an upstream plugin
 * is usually got by downloading the <b>repository manifest</b> file from the repository.
 * The repository manifest is a digest of all {@code META-INF/MANIFEST.MF} files
 * of all plugins offered on that repository.
 *
 * <li><b>Compatible</b>: A plugin that <i>according to its own metadata</i> should
 * work well in the current environment. The reasons for incompatibility are: JOSM too old,
 * Java too old, or wrong OS. A plugin may contain links to older versions of itself
 * with laxer requirements.
 *
 * <li><b>Latest candidate</b>: The latest version of a given plugin offered for download
 * on any of the configured download sites. May not be compatible.
 *
 * <li><b>Update candidate</b>: The latest compatible version of a given plugin offered
 * for download on any of the configured download sites.
 * May not be the latest version.
 * </ul>
 */
public class PluginPreferenceModel extends AbstractTableModel implements Consumer<Collection<PluginInformation>> {
    /** The list of all installed plugins */
    private final List<PluginInformation> installedPlugins = new ArrayList<>();
    /** The list of all upstream plugins */
    private final List<PluginInformation> upstreamPlugins = new ArrayList<>();
    /** The names of the selected plugins (aka. ticked checkboxes) */
    private final Set<String> selectedPlugins;
    /** The table rows in the model */
    private final List<TableEntry> entries = new ArrayList<>();

    public static List<String> getNames(Collection<PluginInformation> plugins) {
        return plugins.stream().map(PluginInformation::getName).toList();
    }

    /**
     * Constructs a new {@code PluginPreferencesModel}.
     */
    public PluginPreferenceModel() {
        selectedPlugins = new HashSet<>(PluginHandler.getConfiguredPluginNames());
    }

    @Override
    public void accept(Collection<PluginInformation> plugins) {
        addUpstreamPlugins(plugins);
    }

    /**
     * Returns the list of all plugins available upstream.
     * @return the list of plugins
     */
    List<PluginInformation> getUpstreamPlugins() {
        synchronized (upstreamPlugins) {
            return new ArrayList<>(upstreamPlugins);
        }
    }

    /**
     * Returns the list of all locally installed plugins
     * @return the list of plugins
     */
    List<PluginInformation> getInstalledPlugins() {
        synchronized (installedPlugins) {
            return new ArrayList<>(installedPlugins);
        }
    }

    /**
     * Returns the list of all loaded plugins
     * @return the list of plugins
     */
    List<PluginInformation> getLoadedPlugins() {
        return PluginHandler.getLoadedPlugins();
    }

    /**
     * Adds to the list of upstream plugins.
     * <p>
     * Usually this list is populated at program startup from the metadata in the
     * installed plugin jars and the cached "site manifest" files. The user may
     * click "download list" from the plugin preference panel to update this list.
     * <p>
     * Note: it is the callers responsibility to call {@code fireTableDataChanged()} in
     * the EDT some time after calling this function to update the JTable.
     *
     * @param plugins The plugins available upstream
     */
    public void addUpstreamPlugins(Collection<PluginInformation> plugins) {
        synchronized (upstreamPlugins) {
            upstreamPlugins.addAll(plugins);
        }
    }

    /**
     * Adds to the list of locally installed plugins.
     * <p>
     * Usually this list is updated once at program startup and then after each
     * successfull plugin download. Note: a plugin must be "installed" in the plugins
     * directory, and from there it can be "loaded" into memory.
     * <p>
     * Note: it is the callers responsibility to call {@code fireTableDataChanged()} in
     * the EDT some time after calling this function to update the JTable.
     *
     * @param plugins The locally installed plugins
     */
    public void addInstalledPlugins(Collection<PluginInformation> plugins) {
        synchronized (installedPlugins) {
            installedPlugins.addAll(plugins);
        }
    }

    /**
     * Clears the list of locally installed plugins.
     * <p>
     * Note: it is the callers responsibility to call {@code fireTableDataChanged()} in
     * the EDT some time after calling this function to update the JTable.
     */
    public void clearInstalledPlugins() {
        synchronized (installedPlugins) {
            installedPlugins.clear();
        }
    }

    /**
     * You must call this from the EDT after every data update.
     */
    @Override
    public void fireTableDataChanged() {
        entries.clear();
        entries.addAll(pair(getLoadedPlugins(), installedPlugins, upstreamPlugins));
        super.fireTableDataChanged();
    }

    /**
     * Replies the list of all update plugins
     * <p>
     * This list contains the "update" plugin for each plugin.
     *
     * @return the list of plugin information objects
     */
    List<PluginInformation> getUpdatePlugins() {
        return entries.stream()
                .map(e -> e.update)
                .filter(p -> p != null)
                .toList();
    }

    /**
     * Replies the list of selected plugin information objects
     * <p>
     * This list contains the "update" plugin for each selected plugin.
     *
     * @return the list of selected plugin information objects
     */
    List<PluginInformation> getSelectedPlugins() {
        return entries.stream()
                .filter(e -> (e.update != null) && selectedPlugins.contains(e.getName()))
                .map(e -> e.update)
                .toList();
    }

    Collection<String> getSelectedPluginNamesWithDependencies() {
        Set<String> plugins = new HashSet<>(selectedPlugins);
        plugins.addAll(getDependencies(getUpdatePlugins(), selectedPlugins));
        return plugins;
    }

    /**
     * Replies the names of the currently selected plugins
     *
     * @return the names of the selected plugins
     */
    List<String> getSelectedPluginNames() {
        return new ArrayList<>(selectedPlugins);
    }

    /**
     * Returns all dependencies of the given plugins recursively.
     * <p>
     * Note: The result does not include the original plugins.
     *
     * @param pis the given plugins
     * @return the dependencies of the given plugins
     */
    static Collection<String> getDependencies(Collection<PluginInformation> pis, Collection<String> names) {
        Map<String, PluginInformation> providers = new HashMap<>();
        pis.forEach(pi -> {
            if (pi != null && pi.isCompatible()) {
                providers.put(pi.getName(), pi);
                if (pi.getProvides() != null)
                    providers.put(pi.getProvides(), pi);
            }
        });

        // collect the dependencies
        Set<String> collector = new HashSet<>();
        Logging.info("getDependencies for: {0}", String.join(", ", names));
        names.forEach(name -> getDependenciesRecursive(collector, providers, name));
        Logging.info("getDependencies found: {0}", String.join(", ", collector));
        return collector;
    }

    /**
     * Recursive part of {@link getDependencies}
     */
    private static void getDependenciesRecursive(
            Set<String> collector, Map<String, PluginInformation> providers, String name) {
        final PluginInformation pi = providers.get(name);
        if (pi == null)
            return;
        for (String dep : pi.getRequiredPlugins()) {
            if (!providers.containsKey(dep)) {
                throw new IllegalArgumentException(
                    tr("The required dependency ''{0}'' for plugin ''{1}'' was not found.", dep, name));
            }
            dep = providers.get(dep).getName();
            if (!collector.contains(dep)) {
                collector.add(dep);
                getDependenciesRecursive(collector, providers, dep);
            }
        }
    }

    /**
     * Returns the selected plugins that have a compatible update available.
     * <p>
     * These are updated if the user hits the "update" button.
     *
     * @return the list of plugins
     */
    public List<PluginInformation> getPluginsToUpdate() {
        Set<String> selected = new HashSet<>(getSelectedPluginNamesWithDependencies());
        return entries.stream()
            .filter(e -> selected.contains(e.getName()))
            .filter(TableEntry::hasUpdate)
            .map(e -> e.update)
            .filter(p -> p != null)
            .toList();
    }

    /**
     * Returns the selected plugins that must be downloaded.
     * <p>
     * These are downloaded if the user hits the "ok" button.
     *
     * @return the list of plugins
     */
    List<PluginInformation> getPluginsToDownload() {
        // which plugins where activated
        Set<String> activatedPluginNames = new HashSet<>(getSelectedPluginNamesWithDependencies());
        // traditionally updates to installed plugins have been handled separately with
        // the update button. here we handle only those plugins that where selected
        // during this preference session
        activatedPluginNames.removeAll(PluginHandler.getConfiguredPluginNames());

        Logging.info("getPluginsToDownload() activated plugin names: {0}", String.join(", ", activatedPluginNames));

        return entries.stream()
            .filter(e -> activatedPluginNames.contains(e.getName()))
            .filter(TableEntry::hasUpdate)
            .map(e -> e.update)
            .filter(p -> p != null)
            .toList();
    }

    /**
     * Returns plugins that are installed and selected but not currently loaded
     * <p>
     * These plugins should be dynamically loaded, else prompt for restart
     */
    List<PluginInformation> getPluginsToActivate() {
        List<String> loadedPluginNames = getNames(PluginHandler.getLoadedPlugins());
        return PluginHandler.getInstalledPlugins().stream()
            .filter(pi -> selectedPlugins.contains(pi.getName()))
            .filter(pi -> !loadedPluginNames.contains(pi.getName()))
            .toList();
    }

    /**
     * Returns plugins that are loaded but not currently selected
     * <p>
     * These plugins should be dynamically unloaded, else prompt for restart
     */
    List<PluginInformation> getPluginsToDeactivate() {
        return PluginHandler.getLoadedPlugins().stream()
            .filter(p -> !selectedPlugins.contains(p.getName())).toList();
    }

    /**
     * Selects or unselects the plugin by name.
     *
     * @param name the name of the plugin
     * @param selected true, if selected; false, otherwise
     */
    void select(String name, boolean selected) {
        if (selected) {
            try {
                getDependencies(getUpdatePlugins(), List.of(name));
                selectedPlugins.add(name);
            } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(null, e.getMessage());
            }
        } else {
            selectedPlugins.remove(name);
        }
    }

    /**
     * Replies true if the plugin with name <code>name</code> is currently
     * selected in the plugin model
     *
     * @param name the plugin name
     * @return true if the plugin is selected; false, otherwise
     */
    boolean isSelected(String name) {
        return selectedPlugins.contains(name);
    }

    class TableEntry {
        /** The loaded plugin */
        PluginInformation loaded;
        /** The installed plugin (and maybe loaded) */
        PluginInformation installed;
        /** The best compatible plugin candidate available */
        PluginInformation update;
        /** The latest plugin candidate available */
        PluginInformation latest;

        TableEntry(PluginInformation loaded, PluginInformation installed, PluginInformation upstream) {
            this.loaded = loaded;
            this.installed = installed;
            this.update = upstream != null ? getHighestCompatibleVersion(upstream) : null;
            this.latest = upstream;
        }

        String getName() {
            return (latest != null ? latest : installed).getName();
        }

        String getNameAsHtml() {
            return "<html><b>" + getName() + "</b></html>";
        }

        /**
         * Replies the description as HTML document, including a link to a web page with
         * more information, provided such a link is available.
         *
         * @return the description as HTML document
         */
        String getDescriptionAsHtml() {
            PluginInformation data = latest != null ? latest : installed;
            List<String> msg = new ArrayList<>();
            msg.add(data.getDescription() == null ? tr("no description available") :
                    Utils.escapeReservedCharactersHTML(data.getDescription()));
            if (data.getLink() != null) {
                msg.add(" <a href=\"" + data.getLink() + "\">" + tr("More info...") + "</a>");
            }
            if (!data.getRequiredPlugins().isEmpty()) {
                msg.add(tr("<b>Requires:</b> {0}", String.join("; ", data.getRequiredPlugins())));
            }
            if (data.getProvides() != null) {
                msg.add(tr("<b>Provides:</b> {0}", data.getProvides()));
            }
            if (update != null && update.getDownloadLink() != null) {
                if (update.isExternal())
                    msg.add(tr("<b>Plugin provided by an external source!</b>"));
                msg.add(tr("<b>Update from:</b> {0}", update.getDownloadLink()));
            }
            if (update != latest) {
                msg.add(tr("<b>The latest version available is incompatible with your system!</b>"));
            }
            return "<p>" + String.join("</p><p>", msg) + "</p>";
        }

        Icon getIcon() {
            if (latest != null && latest.getScaledIcon() != null)
                return latest.getScaledIcon();
            return installed != null ? installed.getScaledIcon() : null;
        }

        String getLoadedVersionAsHtml() {
            if (loaded != null) {
                return hasUpdate()
                    ? "<html><b style=\"color: red\">" + loaded.getVersion() + "</b></html>"
                    : loaded.getVersion();
            }
            return null;
        }

        String getInstalledVersionAsHtml() {
            if (installed != null) {
                return hasUpdate()
                    ? "<html><b style=\"color: red\">" + installed.getVersion() + "</b></html>"
                    : installed.getVersion();
            }
            return null;
        }

        String getUpdateVersionAsHtml() {
            if (update != null) {
                return update.getVersion();
            }
            return "";
        }

        String getLatestVersionAsHtml() {
            if (latest != null) {
                return latest.isCompatible()
                    ? latest.getVersion()
                    : "<html><s>" + latest.getVersion() + "</s></html>";
            }
            return "";
        }

        /**
         * Returns true if this entry matches the given word.
         * @param word the word to search for
         * @return true if a match was found
         */
        boolean matches(String word) {
            return matches(word, getName())
                || matches(word, getDescriptionAsHtml())
                || (latest != null && matches(word, latest.getVersion()))
                || (update != null && matches(word, update.getVersion()))
                || (installed != null && matches(word, installed.getVersion()));
        }

        private boolean matches(String filter, String value) {
            if (filter == null) return true;
            if (value == null) return false;
            return value.toLowerCase(Locale.ENGLISH).contains(filter.toLowerCase(Locale.ENGLISH));
        }

        /**
         * Merges an upstream candidate into latest
         * <p>
         * The "latest" candidate may not be compatible for this environment.
         * <p>
         * This function sets the "better" of two upstream candidates. Currently the
         * newer version is the better one. But in future we could add other criteria,
         * like the reputation of the upstream site.
         */
        TableEntry mergeLatest(final PluginInformation newUpstream) {
            if (newUpstream == null)
                return this;

            // find best upstream
            if (this.latest == null) {
                this.latest = newUpstream;
                return this;
            }
            String v1 = this.latest.getVersion();
            String v2 = newUpstream.getVersion();
            if (v1 == null && v2 != null) {
                this.latest = newUpstream;
                return this;
            }
            if (v2 != null && new ComparableVersion(v1).compareTo(new ComparableVersion(v2)) < 0) {
                this.latest = newUpstream;
            }
            return this;
        }

        /**
         * Merges an upstream candidate into update
         * <p>
         * The "update" candidate is the latest <b>compatible</b> candidate.
         * <p>
         * This function sets the "better" of two update candidates. Currently the
         * newer version is the better one. But in future we could add other criteria,
         * like the reputation of the upstream site.
         */
        TableEntry mergeUpdate(final PluginInformation newUpstream) {
            if (newUpstream == null)
                return this;
            // find best update
            final PluginInformation newUpdate = getHighestCompatibleVersion(newUpstream);
            if (newUpdate == null)
                return this;
            if (this.update == null) {
                this.update = newUpdate;
                return this;
            }
            String v1 = this.update.getVersion();
            String v2 = newUpdate.getVersion();
            if (v1 == null && v2 != null) {
                this.update = newUpdate;
                return this;
            }
            if (v2 != null && new ComparableVersion(v1).compareTo(new ComparableVersion(v2)) < 0) {
                this.update = newUpdate;
            }
            return this;
        }

        /**
         * Sets the loaded plugin.
         */
        TableEntry setLoaded(final PluginInformation loaded) {
            this.loaded = loaded;
            return this;
        }

        /**
         * Returns the highest compatible version of a given plugin.
         * <p>
         * Note: May return the given PluginInformation.
         * @param pi the plugin
         * @return an info representing the highest compatible version or null
         */
        PluginInformation getHighestCompatibleVersion(PluginInformation pi) {
            if (pi.isCompatible())
                return pi;
            return pi.getOldVersions().stream()
                .filter(PluginInformation::isCompatible)
                .max(PluginInformation.nameVersion).orElse(null);
        }

        boolean isSelected() {
            return selectedPlugins.contains(getName());
        }

        /**
         * Returns true if there is a <b>compatible</b> update available.
         * <p>
         * Also returns true if the plugin is not installed locally.
         *
         * @return true if there is a newer version
         */
        public boolean hasUpdate() {
            if (update == null) return false;
            if (update.getVersion() == null) return false;
            if (update.getDownloadLink() == null) return false;

            if (installed == null) return true;
            if (installed.getVersion() == null) return true;
            ComparableVersion installedVersion = new ComparableVersion(installed.getVersion());
            ComparableVersion updateVersion = new ComparableVersion(update.getVersion());
            return installedVersion.compareTo(updateVersion) < 0;
        }
    }

    /**
     * Returns the {@link TableEntry} at the given index.
     * @param rowIndex the row index
     * @return the table entry
     */
    public TableEntry getTableEntryAt(int rowIndex) {
        return entries.get(rowIndex);
    }

    /**
     * Pairs local plugins with upstream plugins.
     * <p>
     * This function pairs every installed plugin with the respective "best compatible" and
     * "best" upstream candidates. "Best" is currently defined as: The highest version
     * of a given plugin. "Best compatible" must also be compatible with the current
     * environment. See also: {@link PluginInformation#isCompatible isCompatible()}
     * <p>
     * If there is no suitable partner for loaded, installed, update or latest,
     * a table entry with {@code null}s is returned.
     *
     * @param installed the list of locally installed plugins
     * @param upstream the list of upstream plugins
     * @return a list of {@link TableEntry}s
     */
    private Collection<TableEntry> pair(Collection<PluginInformation> loaded,
            Collection<PluginInformation> installed, Collection<PluginInformation> upstream) {

        LinkedHashMap<String, TableEntry> installedMap = new LinkedHashMap<>();
        for (var pi : installed) {
            installedMap.computeIfAbsent(pi.getName(), k -> new TableEntry(null, pi, null));
        }
        for (var pi : loaded) {
            installedMap.compute(pi.getName(), (k, v) -> {
                if (v == null) {
                    return new TableEntry(pi, null, null);
                } else {
                    v.setLoaded(pi);
                    return v;
                }
            });
        }
        for (var pi : upstream) {
            installedMap.compute(pi.getName(), (k, v) -> {
                if (v == null) {
                    return new TableEntry(null, null, pi);
                } else {
                    v.mergeLatest(pi);
                    v.mergeUpdate(pi);
                    return v;
                }
            });
        }
        return installedMap.values();
    }

    /*
     * The TableModel interface
     */

    @Override
    public int getRowCount() {
        return entries.size();
    }

    @Override
    public int getColumnCount() {
        // checkbox, icon, name, loaded, installed, update, latest
        return 7;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        // only the checkbox is editable
        return columnIndex == 0;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        switch (columnIndex) {
            case 0: return Boolean.class;
            case 1: return Icon.class;
            default: return String.class;
        }
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        TableEntry e = entries.get(rowIndex);
        switch(columnIndex) {
            case 0: return e.isSelected();
            case 1: return e.getIcon();
            case 2: return e.getNameAsHtml();
            case 3: return e.getLoadedVersionAsHtml();
            case 4: return e.getInstalledVersionAsHtml();
            case 5: return e.getUpdateVersionAsHtml();
            case 6: return e.getLatestVersionAsHtml();
            default: return null;
        }
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= getRowCount() || aValue == null)
            return;
        if (columnIndex == 0) {
            String name = entries.get(rowIndex).getName();
            select(name, (Boolean) aValue);
        }
    }
}
