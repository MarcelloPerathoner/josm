// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.preferences.display;

import static org.openstreetmap.josm.tools.I18n.tr;

import org.openstreetmap.josm.gui.help.HelpUtil;
import org.openstreetmap.josm.gui.preferences.DefaultTabPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceSettingFactory;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.tools.GBC;

/**
 * "GPS Points" drawing preferences.
 */
public class GPXPreference extends DefaultTabPreferenceSetting {

    /**
     * Factory used to create a new {@code GPXPreference}.
     */
    public static class Factory implements PreferenceSettingFactory {
        @Override
        public PreferenceSetting createPreferenceSetting() {
            return new GPXPreference();
        }
    }

    GPXPreference() {
        super("layer/gpx_small", tr("GPS Points"), tr("Settings that control the drawing of GPS tracks."));
    }

    private GPXSettingsPanel gpxPanel;

    @Override
    public void addGui(PreferenceTabbedPane gui) {
        gpxPanel = new GPXSettingsPanel();
        gui.addValidationListener(gpxPanel);
        gui.createPreferenceTab(this).add(decorateScrollable(gpxPanel), GBC.eol().fill());
    }

    @Override
    public boolean ok() {
        return gpxPanel.savePreferences();
    }

    @Override
    public boolean isExpert() {
        return false;
    }

    @Override
    public String getHelpContext() {
        return HelpUtil.ht("/Preferences/GPXPreference");
    }
}
