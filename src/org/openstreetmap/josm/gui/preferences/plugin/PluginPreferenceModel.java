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
 * <li><b>Local</b>: A plugin that is installed locally.
 * The plugin .jar file is stored in the configured plugins directory.
 * Information about a local plugin is read from the
 * {@code META-INF/MANIFEST.MF} file in the .jar file itself.
 *
 * <li><b>Upstream</b>: A plugin that is offered for download at one or more of
 * the configured plugin repositories. Information about an upstream plugin
 * is usually got by downloading the <b>repository manifest</b> file from the repository.
 * The repository manifest is a concatenation of all {@code META-INF/MANIFEST.MF} files
 * of all plugins offered on that repository.
 *
 * <li><b>Compatible</b>: A plugin that <i>according to its own metadata</i> should
 * work well in the current environment. The reasons for incompatibility are: JOSM too old,
 * Java too old, or wrong OS. A plugin may contain links to older versions of itself
 * with laxer requirements.
 *
 * <li><b>Installed</b>: A plugin whose jar is stored in the configured plugins directory.
 *
 * <li><b>Loaded</b>: A plugin currently loaded into memory.
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
    /** The list of all local plugins */
    private final List<PluginInformation> localPlugins = new ArrayList<>();
    /** The list of all upstream plugins */
    private final List<PluginInformation> upstreamPlugins = new ArrayList<>();
    /** The names of the selected plugins (aka. ticked checkboxes) */
    private final Set<String> selectedPlugins;
    /** The table rows in the model */
    private final List<TableEntry> entries = new ArrayList<>();

    /**
     * Constructs a new {@code PluginPreferencesModel}.
     */
    public PluginPreferenceModel() {
        selectedPlugins = new HashSet<>(PluginHandler.getConfigPluginNames());
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
        synchronized(upstreamPlugins) {
            return new ArrayList<>(upstreamPlugins);
        }
    }

    /**
     * Returns the list of all plugins available locally (downloaded ones).
     * @return the list of plugins
     */
    List<PluginInformation> getLocalPlugins() {
        synchronized(localPlugins) {
            return new ArrayList<>(localPlugins);
        }
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
        synchronized(upstreamPlugins) {
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
    public void addLocalPlugins(Collection<PluginInformation> plugins) {
        synchronized(localPlugins) {
            localPlugins.addAll(plugins);
        }
    }

    /**
     * Clears the list of locally installed plugins.
     * <p>
     * Note: it is the callers responsibility to call {@code fireTableDataChanged()} in
     * the EDT some time after calling this function to update the JTable.
     */
    public void clearLocalPlugins() {
        synchronized(localPlugins) {
            localPlugins.clear();
        }
    }

    /**
     * You must call this from the EDT after every data update.
     */
    @Override
    public void fireTableDataChanged() {
        entries.clear();
        entries.addAll(pair(localPlugins, upstreamPlugins));
        super.fireTableDataChanged();
    }

    /**
     * Replies the list of all updateable plugins
     * <p>
     * This list contains the "update" plugin for each plugin.
     *
     * @return the list of plugin information objects
     */
    List<PluginInformation> getUpdatablePlugins() {
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

    /**
     * Replies the names of the selected plugins
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
     * Replies the plugins that have a newer compatible version available.
     * <p>
     * The list also contains all dependencies that have an update.
     *
     * @return the set of plugins waiting for update
     */
    public List<PluginInformation> getPluginsScheduledForUpdate() {
        Collection<String> dependencies = getDependencies(getUpdatablePlugins(), selectedPlugins);
        dependencies.addAll(selectedPlugins);

        return entries.stream()
            .filter(e -> dependencies.contains(e.getName()))
            .filter(TableEntry::hasUpdate)
            .map(e -> e.update)
            .filter(p -> p != null)
            .toList();
    }

    /**
     * Replies the set of plugins waiting for download.
     * <p>
     * These are downloaded if the user hits ok on the preference pane.
     *
     * @return the set of plugins waiting for download
     */
    List<PluginInformation> getPluginsScheduledForDownload() {
        // FIXME: make clear the semantics of the "ok" and the "update" buttons. ie.
        // should updates automatically be included if the user hits "ok"?
        Collection<String> dependencies = getDependencies(getUpdatablePlugins(), selectedPlugins);
        dependencies.addAll(selectedPlugins);

        return entries.stream()
            .filter(e -> dependencies.contains(e.getName()))
            .filter(TableEntry::isDownloadRequired)
            .map(e -> e.update)
            .filter(p -> p != null)
            .toList();
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
                selectedPlugins.add(name);
                getDependencies(getUpdatablePlugins(), selectedPlugins).forEach(selectedPlugins::add);
            } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(null, e.getMessage());
            }
        } else {
            selectedPlugins.remove(name);
        }
        super.fireTableDataChanged();
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
        /** The local plugin (installed and maybe loaded) */
        PluginInformation local;
        /** The best compatible plugin candidate available */
        PluginInformation update;
        /** The latest plugin candidate available */
        PluginInformation latest;

        TableEntry(PluginInformation local, PluginInformation upstream) {
            this.local = local;
            this.update = upstream != null ? getHighestCompatibleVersion(upstream) : null;
            this.latest = upstream;
        }

        String getName() {
            return (latest != null ? latest : local).getName();
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
            PluginInformation data = latest != null ? latest : local;
            List<String> msg = new ArrayList<>();
            msg.add(data.getDescription() == null ? tr("no description available") :
                    Utils.escapeReservedCharactersHTML(data.getDescription()));
            if (data.getLink() != null) {
                msg.add(" <a href=\"" + data.getLink() + "\">" + tr("More info...") + "</a>");
            }
            if(!data.getRequiredPlugins().isEmpty()) {
                msg.add(tr("<b>Requires:</b> {0}", String.join("; ", data.getRequiredPlugins())));
            }
            if(data.getProvides() != null) {
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
            return local != null ? local.getScaledIcon() : null;
        }

        String getLocalVersionAsHtml() {
            if (local != null) {
                return hasUpdate()
                    ? "<html><b style=\"color: red\">" + local.getVersion() + "</b></html>"
                    : local.getVersion();
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
                || (local != null && matches(word, local.getVersion()));
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
         * Replies true if this this plugin should be downloaded because it is not
         * available locally.
         *
         * @return true if the plugin should be downloaded
         */
        public boolean isDownloadRequired() {
            // cannot download
            if (latest == null) return false;
            if (latest.getDownloadLink() == null) return false;
            // must download
            if (local == null) return true;
            return local.getVersion() == null;
        }

        /**
         * Replies true if there is a <b>compatible</b> later version of this plugin
         * available.
         *
         * @return true if there is a newer version
         */
        public boolean hasUpdate() {
            if (update == null) return false;
            if (update.getVersion() == null) return false;
            if (update.getDownloadLink() == null) return false;

            if (local == null) return true;
            if (local.getVersion() == null) return true;
            ComparableVersion localVersion = new ComparableVersion(local.getVersion());
            ComparableVersion updateVersion = new ComparableVersion(update.getVersion());
            return localVersion.compareTo(updateVersion) < 0;
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
     * This function pairs every local plugin with the respective "best compatible" and
     * "best" upstream candidates. "Best" is currently defined as: The highest version
     * of a given plugin. "Best compatible" must also be compatible with the current
     * environment. See also: {@link PluginInformation#isCompatible isCompatible()}
     * <p>
     * If there is no suitable partner for local, update or latest, a table entry with
     * {@code null}s is returned.
     *
     * @param local the list of local plugins
     * @param upstream the list of upstream plugins
     * @return a list of {@link TableEntry}s
     */
    private Collection<TableEntry> pair(Collection<PluginInformation> local, Collection<PluginInformation> upstream) {
        LinkedHashMap<String, TableEntry> map = new LinkedHashMap<>();
        for(var pi : local) {
            map.computeIfAbsent(pi.getName(), k -> new TableEntry(pi, null));
        }
        for(var pi : upstream) {
            map.compute(pi.getName(), (k, v) -> {
                if (v == null) {
                    return new TableEntry(null, pi);
                } else {
                    v.mergeLatest(pi);
                    v.mergeUpdate(pi);
                    return v;
                }
            });
        }
        return map.values();
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
        // checkbox, icon, name, local, update, latest
        return 6;
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
            case 3: return e.getLocalVersionAsHtml();
            case 4: return e.getUpdateVersionAsHtml();
            case 5: return e.getLatestVersionAsHtml();
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
