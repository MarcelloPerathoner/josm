// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.preferences.server;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.beans.PropertyChangeListener;

import javax.swing.JSeparator;

import org.openstreetmap.josm.gui.help.HelpUtil;
import org.openstreetmap.josm.gui.preferences.DefaultTabPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceSettingFactory;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.gui.widgets.VerticallyScrollablePanel;
import org.openstreetmap.josm.tools.GBC;

/**
 * Connection preferences, including authentication and proxy sub-preferences.
 */
public final class ServerAccessPreference extends DefaultTabPreferenceSetting {

    /**
     * Factory used to create a new {@code ServerAccessPreference}.
     */
    public static class Factory implements PreferenceSettingFactory {
        @Override
        public PreferenceSetting createPreferenceSetting() {
            return new ServerAccessPreference();
        }
    }

    /** indicates whether to use the default OSM URL or not */
    private final OsmApiUrlInputPanel pnlApiUrlPreferences = new OsmApiUrlInputPanel();
    private final AuthenticationPreferencesPanel pnlAuthPreferences = new AuthenticationPreferencesPanel();
    /** the panel for messages notifier preferences */
    private final FeaturesPanel pnlFeaturesPreferences = new FeaturesPanel();
    private final OverpassServerPanel pnlOverpassPreferences = new OverpassServerPanel();

    private ServerAccessPreference() {
        super(/* ICON(preferences/) */ "connection", tr("OSM Server"), tr("Connection Settings for the OSM server."));
    }

    /**
     * Adds a listener that will be notified of API URL change.
     * @param listener the listener
     * @since 6523
     */
    public void addApiUrlChangeListener(PropertyChangeListener listener) {
        pnlApiUrlPreferences.addPropertyChangeListener(listener);
    }

    @Override
    public void addGui(PreferenceTabbedPane gui) {
        VerticallyScrollablePanel panel = new VerticallyScrollablePanel(new GridBagLayout());

        GBC eop = GBC.eop().fill(GridBagConstraints.HORIZONTAL);

        panel.add(pnlApiUrlPreferences, eop);
        panel.add(new JSeparator(), eop);
        panel.add(pnlAuthPreferences, eop);
        panel.add(new JSeparator(), eop);
        panel.add(pnlFeaturesPreferences, eop);
        panel.add(new JSeparator(), eop);
        panel.add(pnlOverpassPreferences, eop);

        pnlApiUrlPreferences.initFromPreferences();
        pnlAuthPreferences.initFromPreferences();
        pnlFeaturesPreferences.initFromPreferences();
        pnlOverpassPreferences.initFromPreferences();
        addApiUrlChangeListener(pnlAuthPreferences);

        HelpUtil.setHelpContext(panel, HelpUtil.ht("/Preferences/Connection"));

        gui.createPreferenceTab(this).add(decorateScrollable(panel), GBC.eol().fill());
    }

    /**
     * Saves the values to the preferences
     */
    @Override
    public boolean ok() {
        pnlApiUrlPreferences.saveToPreferences();
        pnlAuthPreferences.saveToPreferences();
        // save message notifications preferences. To be done after authentication preferences.
        pnlFeaturesPreferences.saveToPreferences();
        pnlOverpassPreferences.saveToPreferences();
        return false;
    }

    @Override
    public String getHelpContext() {
        return HelpUtil.ht("/Preferences/Connection");
    }
}
