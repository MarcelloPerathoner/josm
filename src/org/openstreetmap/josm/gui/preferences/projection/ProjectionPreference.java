// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.preferences.projection;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;

import org.openstreetmap.josm.actions.ExpertToggleAction;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.SystemOfMeasurement;
import org.openstreetmap.josm.data.coor.conversion.CoordinateFormatManager;
import org.openstreetmap.josm.data.coor.conversion.ICoordinateFormat;
import org.openstreetmap.josm.data.preferences.ListProperty;
import org.openstreetmap.josm.data.preferences.StringProperty;
import org.openstreetmap.josm.data.projection.CustomProjection;
import org.openstreetmap.josm.data.projection.Projection;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;
import org.openstreetmap.josm.data.projection.Projections;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.help.HelpUtil;
import org.openstreetmap.josm.gui.preferences.DefaultTabPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceSettingFactory;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.gui.widgets.JosmComboBox;
import org.openstreetmap.josm.gui.widgets.VerticallyScrollablePanel;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.JosmRuntimeException;
import org.openstreetmap.josm.tools.Logging;

/**
 * Projection preferences.
 * <p>
 * How to add new Projections:
 *  - Find EPSG code for the projection.
 *  - Look up the parameter string for Proj4, e.g. on <a href="https://spatialreference.org">https://spatialreference.org</a>/
 *      and add it to the file 'data/projection/epsg' in JOSM trunk
 *  - Search for official references and verify the parameter values. These
 *      documents are often available in the local language only.
 *  - Use {@link #registerProjectionChoice}, to make the entry known to JOSM.
 * <p>
 * In case there is no EPSG code:
 *  - override {@link AbstractProjectionChoice#getProjection()} and provide
 *    a manual implementation of the projection. Use {@link CustomProjection}
 *    if possible.
 */
public class ProjectionPreference extends DefaultTabPreferenceSetting {

    /**
     * Factory used to create a new {@code ProjectionPreference}.
     */
    public static class Factory implements PreferenceSettingFactory {
        @Override
        public PreferenceSetting createPreferenceSetting() {
            return new ProjectionPreference();
        }
    }

    private static final List<ProjectionChoice> projectionChoices = new ArrayList<>();
    private static final Map<String, ProjectionChoice> projectionChoicesById = new HashMap<>();

    /**
     * WGS84: Directly use latitude / longitude values as x/y.
     */
    public static final ProjectionChoice wgs84 = registerProjectionChoice(tr("WGS84 Geographic"), "core:wgs84", 4326);

    /**
     * Mercator Projection.
     * <p>
     * The center of the mercator projection is always the 0 grad coordinate.
     * <p>
     * See also <a href="https://pubs.usgs.gov/bul/1532/report.pdf">USGS Bulletin 1532</a>
     * initially EPSG used 3785 but that has been superseded by 3857, see <a href="https://www.epsg-registry.org/">epsg-registry.org</a>
     */
    public static final ProjectionChoice mercator = registerProjectionChoice(tr("Mercator"), "core:mercator", 3857);

    /**
     * Lambert conic conform 4 zones using the French geodetic system NTF.
     * <p>
     * This newer version uses the grid translation NTF&lt;-&gt;RGF93 provided by IGN for a submillimetric accuracy.
     * (RGF93 is the French geodetic system similar to WGS84 but not mathematically equal)
     * <p>
     * Source: <a href="https://geodesie.ign.fr/contenu/fichiers/Changement_systeme_geodesique.pdf">Changement_systeme_geodesique.pdf</a>
     */
    public static final ProjectionChoice lambert = new LambertProjectionChoice();

    /**
     * French regions in the Caribbean Sea and Indian Ocean.
     * <p>
     * Using the UTM transvers Mercator projection and specific geodesic settings.
     */
    public static final ProjectionChoice utm_france_dom = new UTMFranceDOMProjectionChoice();

    /**
     * Lambert Conic Conform 9 Zones projection.
     * <p>
     * As specified by the IGN in this document
     * <a href="https://geodesie.ign.fr/contenu/fichiers/documentation/rgf93/cc9zones.pdf">cc9zones.pdf</a>
     */
    public static final ProjectionChoice lambert_cc9 = new LambertCC9ZonesProjectionChoice();

    static {

        /* ***********************
         * Global projections.
         */

        /* *
         * UTM.
         */
        registerProjectionChoice(new UTMProjectionChoice());

        /* ***********************
         * Regional - alphabetical order by country code.
         */

        /*
         * Belgian Lambert 72 projection.
         * <p>
         * As specified by the Belgian IGN in this document:
         * http://www.ngi.be/Common/Lambert2008/Transformation_Geographic_Lambert_FR.pdf
         *
         * @author Don-vip
         */
        registerProjectionChoice(tr("Belgian Lambert 1972"), "core:belgianLambert1972", 31370);     // BE

        /*
         * Belgian Lambert 2008 projection.
         * <p>
         * As specified by the Belgian IGN in this document:
         * http://www.ngi.be/Common/Lambert2008/Transformation_Geographic_Lambert_FR.pdf
         *
         * @author Don-vip
         */
        registerProjectionChoice(tr("Belgian Lambert 2008"), "core:belgianLambert2008", 3812);      // BE

        /*
         * SwissGrid CH1903 / L03, see https://en.wikipedia.org/wiki/Swiss_coordinate_system.
         * <p>
         * Actually, what we have here, is CH1903+ (EPSG:2056), but without
         * the additional false easting of 2000km and false northing 1000 km.
         * <p>
         * To get to CH1903, a shift file is required. So currently, there are errors
         * up to 1.6m (depending on the location).
         */
        registerProjectionChoice(new SwissGridProjectionChoice());                                  // CH

        registerProjectionChoice(new GaussKruegerProjectionChoice());                               // DE

        /*
         * Estonian Coordinate System of 1997.
         * <p>
         * Thanks to Johan Montagnat and its geoconv java converter application
         * (https://www.i3s.unice.fr/~johan/gps/ , published under GPL license)
         * from which some code and constants have been reused here.
         */
        registerProjectionChoice(tr("Lambert Zone (Estonia)"), "core:lambertest", 3301);            // EE

        /*
         * Lambert conic conform 4 zones using the French geodetic system NTF.
         * <p>
         * This newer version uses the grid translation NTF<->RGF93 provided by IGN for a submillimetric accuracy.
         * (RGF93 is the French geodetic system similar to WGS84 but not mathematically equal)
         * <p>
         * Source: https://geodesie.ign.fr/contenu/fichiers/Changement_systeme_geodesique.pdf
         * @author Pieren
         */
        registerProjectionChoice(lambert);                                                          // FR

        /*
         * Lambert 93 projection.
         * <p>
         * As specified by the IGN in this document
         * https://geodesie.ign.fr/contenu/fichiers/documentation/rgf93/Lambert-93.pdf
         * @author Don-vip
         */
        registerProjectionChoice(tr("Lambert 93 (France)"), "core:lambert93", 2154);                // FR

        /*
         * Lambert Conic Conform 9 Zones projection.
         * <p>
         * As specified by the IGN in this document
         * https://geodesie.ign.fr/contenu/fichiers/documentation/rgf93/cc9zones.pdf
         * @author Pieren
         */
        registerProjectionChoice(lambert_cc9);                                                      // FR

        /*
         * French departements in the Caribbean Sea and Indian Ocean.
         * <p>
         * Using the UTM transvers Mercator projection and specific geodesic settings.
         */
        registerProjectionChoice(utm_france_dom);                                                   // FR

        /*
         * LKS-92/ Latvia TM projection.
         * <p>
         * Based on data from spatialreference.org.
         * https://spatialreference.org/ref/epsg/3059/
         *
         * @author Viesturs Zarins
         */
        registerProjectionChoice(tr("LKS-92 (Latvia TM)"), "core:tmerclv", 3059);                   // LV

        /*
         * Netherlands RD projection
         *
         * @author vholten
         */
        registerProjectionChoice(tr("Rijksdriehoekscoördinaten (Netherlands)"), "core:dutchrd", 28992); // NL

        /*
         * PUWG 1992 and 2000 are the official cordinate systems in Poland.
         * <p>
         * They use the same math as UTM only with different constants.
         *
         * @author steelman
         */
        registerProjectionChoice(new PuwgProjectionChoice());                                       // PL

        /*
         * SWEREF99 projections. Official coordinate system in Sweden.
         */
        registerProjectionChoice(tr("SWEREF99 TM / EPSG:3006 (Sweden)"), "core:sweref99tm", 3006);  // SE
        registerProjectionChoice(tr("SWEREF99 13 30 / EPSG:3008 (Sweden)"), "core:sweref99", 3008); // SE

        /* ***********************
         * Projection by Code.
         */
        registerProjectionChoice(new CodeProjectionChoice());

        /* ***********************
         * Custom projection.
         */
        registerProjectionChoice(new CustomProjectionChoice());
    }

    /**
     * Registers a new projection choice.
     * @param c projection choice
     */
    public static void registerProjectionChoice(ProjectionChoice c) {
        projectionChoices.add(c);
        projectionChoicesById.put(c.getId(), c);
        for (String code : c.allCodes()) {
            Projections.registerProjectionSupplier(code, () -> {
                Collection<String> pref = c.getPreferencesFromCode(code);
                c.setPreferences(pref);
                try {
                    return c.getProjection();
                } catch (JosmRuntimeException | IllegalArgumentException | IllegalStateException e) {
                    Logging.log(Logging.LEVEL_WARN, "Unable to get projection "+code+" with "+c+':', e);
                    return null;
                }
            });
        }
    }

    /**
     * Registers a new projection choice.
     * @param name short name of the projection choice as shown in the GUI
     * @param id short name of the projection choice as shown in the GUI
     * @param epsg the unique numeric EPSG identifier for the projection
     * @return the registered {@link ProjectionChoice}
     */
    private static ProjectionChoice registerProjectionChoice(String name, String id, Integer epsg) {
        ProjectionChoice pc = new SingleProjectionChoice(name, id, "EPSG:"+epsg);
        registerProjectionChoice(pc);
        return pc;
    }

    /**
     * Returns the list of projection choices.
     * @return the list of projection choices
     */
    public static List<ProjectionChoice> getProjectionChoices() {
        return Collections.unmodifiableList(projectionChoices);
    }

    private static String projectionChoice;

    private static final StringProperty PROP_PROJECTION_DEFAULT = new StringProperty("projection.default", mercator.getId());
    private static final StringProperty PROP_COORDINATES = new StringProperty("coordinates", null);
    private static final ListProperty PROP_SUB_PROJECTION_DEFAULT = new ListProperty("projection.default.sub", null);

    /**
     * Combobox with all projections available
     */
    private final JosmComboBox<ProjectionChoice> projectionCombo;

    /**
     * Combobox with all coordinate display possibilities
     */
    private final JosmComboBox<ICoordinateFormat> coordinatesCombo;

    /**
     * Combobox with all system of measurements
     */
    private final JosmComboBox<SystemOfMeasurement> unitsCombo = new JosmComboBox<>(
            SystemOfMeasurement.ALL_SYSTEMS.values().stream()
                    .sorted(Comparator.comparing(SystemOfMeasurement::toString))
                    .toArray(SystemOfMeasurement[]::new));

    /**
     * This variable holds the JPanel with the projection's preferences. If the
     * selected projection does not implement this, it will be set to an empty
     * Panel.
     */
    private JPanel projSubPrefPanel;
    private final JPanel projSubPrefPanelWrapper = new JPanel(new GridBagLayout());

    private final JLabel projectionCodeLabel = new JLabel(tr("Projection code"));
    private final JLabel projectionCode = new JLabel();
    private final JLabel projectionNameLabel = new JLabel(tr("Projection name"));
    private final JLabel projectionName = new JLabel();
    private final JLabel bounds = new JLabel();

    /**
     * This is the panel holding all projection preferences
     */
    private final VerticallyScrollablePanel projPanel = new VerticallyScrollablePanel(new GridBagLayout());

    /**
     * Constructs a new {@code ProjectionPreference}.
     */
    public ProjectionPreference() {
        super(/* ICON(preferences/) */ "map", tr("Map Projection"), tr("Map Projection"));
        this.projectionCombo = new JosmComboBox<>(
            projectionChoices.toArray(new ProjectionChoice[0]));
        this.coordinatesCombo = new JosmComboBox<>(
                CoordinateFormatManager.getCoordinateFormats().toArray(new ICoordinateFormat[0]));
    }

    @Override
    public void addGui(PreferenceTabbedPane gui) {
        final ProjectionChoice pc = setupProjectionCombo();

        IntStream.range(0, coordinatesCombo.getItemCount())
                .filter(i -> coordinatesCombo.getItemAt(i).getId().equals(PROP_COORDINATES.get())).findFirst()
                .ifPresent(coordinatesCombo::setSelectedIndex);

        unitsCombo.setSelectedItem(SystemOfMeasurement.getSystemOfMeasurement());

        Insets rightMargin = new Insets(0, 0, 0, 10);
        GBC std = GBC.std().insets(rightMargin);
        GBC eol = GBC.eol();
        Insets indent = addInsets(getIndent(), rightMargin);
        GBC ind = GBC.std().insets(indent);

        projPanel.add(new JLabel(tr("Projection method")), std);
        projPanel.add(projectionCombo, GBC.eol().weight(1, 0));
        projPanel.add(vSkip(), eol);

        projPanel.add(projectionCodeLabel, ind);
        projPanel.add(projectionCode, eol);

        projPanel.add(projectionNameLabel, ind);
        projPanel.add(projectionName, eol);

        projPanel.add(new JLabel(tr("Bounds")), ind);
        projPanel.add(bounds, eol);
        projPanel.add(vSkip(), eol);

        projPanel.add(projSubPrefPanelWrapper, GBC.eol().insets(indent));

        projectionCodeLabel.setLabelFor(projectionCode);
        projectionNameLabel.setLabelFor(projectionName);

        JButton btnSetAsDefault = new JButton(tr("Set as default"));
        projPanel.add(btnSetAsDefault, GBC.eop());
        btnSetAsDefault.addActionListener(e -> {
            ProjectionChoice pc2 = (ProjectionChoice) projectionCombo.getSelectedItem();
            String id = pc2.getId();
            Collection<String> prefs = pc2.getPreferences(projSubPrefPanel);
            setProjection(id, prefs, true);
            pc2.setPreferences(prefs);
            Projection proj = pc2.getProjection();
            new ExtendedDialog(gui, tr("Default projection"), tr("OK"))
                    .setButtonIcons("ok")
                    .setIcon(JOptionPane.INFORMATION_MESSAGE)
                    .setContent(tr("Default projection has been set to ''{0}''", proj.toCode()))
                    .showDialog();
        });
        ExpertToggleAction.addVisibilitySwitcher(btnSetAsDefault);

        projPanel.add(new JSeparator(), GBC.eop());

        projPanel.add(new JLabel(tr("Display coordinates as")), std);
        projPanel.add(coordinatesCombo, eol);
        projPanel.add(vSkip(), eol);

        projPanel.add(new JLabel(tr("System of measurement")), std);
        projPanel.add(unitsCombo, eol);

        gui.createPreferenceTab(this).add(decorateScrollable(projPanel), GBC.eol().fill());

        selectedProjectionChanged(pc);
    }

    private void updateMeta(ProjectionChoice pc) {
        pc.setPreferences(pc.getPreferences(projSubPrefPanel));
        Projection proj = pc.getProjection();
        projectionCode.setText(proj.toCode());
        projectionName.setText(proj.toString());
        Bounds b = proj.getWorldBoundsLatLon();
        ICoordinateFormat cf = CoordinateFormatManager.getDefaultFormat();
        bounds.setText(cf.lonToString(b.getMin()) + ", " + cf.latToString(b.getMin()) + " : " +
                cf.lonToString(b.getMax()) + ", " + cf.latToString(b.getMax()));
        boolean showCode = true;
        boolean showName = false;
        if (pc instanceof SubPrefsOptions) {
            showCode = ((SubPrefsOptions) pc).showProjectionCode();
            showName = ((SubPrefsOptions) pc).showProjectionName();
        }
        projectionCodeLabel.setVisible(showCode);
        projectionCode.setVisible(showCode);
        projectionNameLabel.setVisible(showName);
        projectionName.setVisible(showName);
    }

    @Override
    public boolean ok() {
        ProjectionChoice pc = (ProjectionChoice) projectionCombo.getSelectedItem();

        String id = pc.getId();
        Collection<String> prefs = pc.getPreferences(projSubPrefPanel);

        setProjection(id, prefs, false);

        ICoordinateFormat selectedItem = (ICoordinateFormat) coordinatesCombo.getSelectedItem();
        if (selectedItem != null && PROP_COORDINATES.put(selectedItem.getId())) {
            CoordinateFormatManager.setCoordinateFormat(selectedItem);
        }

        SystemOfMeasurement.setSystemOfMeasurement(((SystemOfMeasurement) unitsCombo.getSelectedItem()));

        return false;
    }

    /**
     * Set default projection.
     */
    public static void setProjection() {
        setProjection(PROP_PROJECTION_DEFAULT.get(), PROP_SUB_PROJECTION_DEFAULT.get(), false);
    }

    /**
     * Set projection.
     * @param id id of the selected projection choice
     * @param pref the configuration for the selected projection choice
     * @param makeDefault true, if it is to be set as permanent default
     * false, if it is to be set for the current session
     * @since 12306
     */
    public static void setProjection(String id, Collection<String> pref, boolean makeDefault) {
        ProjectionChoice pc = projectionChoicesById.get(id);

        if (pc == null) {
            JOptionPane.showMessageDialog(
                    MainApplication.getMainFrame(),
                    tr("The projection {0} could not be activated. Using Mercator", id),
                    tr("Error"),
                    JOptionPane.ERROR_MESSAGE
            );
            pref = null;
            pc = mercator;
        }
        id = pc.getId();
        Config.getPref().putList("projection.sub."+id, pref == null ? null : new ArrayList<>(pref));
        if (makeDefault) {
            PROP_PROJECTION_DEFAULT.put(id);
            PROP_SUB_PROJECTION_DEFAULT.put(pref == null ? null : new ArrayList<>(pref));
        } else {
            projectionChoice = id;
        }
        pc.setPreferences(pref);
        Projection proj = pc.getProjection();
        ProjectionRegistry.setProjection(proj);
    }

    /**
     * Handles all the work related to update the projection-specific
     * preferences
     * @param pc the choice class representing user selection
     */
    private void selectedProjectionChanged(final ProjectionChoice pc) {
        // Don't try to update if we're still starting up
        int size = projPanel.getComponentCount();
        if (size < 1)
            return;

        final ActionListener listener = e -> updateMeta(pc);

        // Replace old panel with new one
        projSubPrefPanelWrapper.removeAll();
        projSubPrefPanel = pc.getPreferencePanel(listener);
        projSubPrefPanelWrapper.add(projSubPrefPanel, GBC.eop().fill());
        projPanel.revalidate();
        projSubPrefPanel.repaint();
        updateMeta(pc);
    }

    /**
     * Sets up projection combobox with default values and action listener
     * @return the choice class for user selection
     */
    private ProjectionChoice setupProjectionCombo() {
        String pcId = getCurrentProjectionChoiceId();
        ProjectionChoice pc = null;
        for (int i = 0; i < projectionCombo.getItemCount(); ++i) {
            ProjectionChoice pc1 = projectionCombo.getItemAt(i);
            pc1.setPreferences(getSubprojectionPreference(pc1.getId()));
            if (pc1.getId().equals(pcId)) {
                projectionCombo.setSelectedIndex(i);
                selectedProjectionChanged(pc1);
                pc = pc1;
            }
        }
        // If the ProjectionChoice from the preferences is not available, it
        // should have been set to Mercator at JOSM start.
        if (pc == null)
            throw new JosmRuntimeException("Couldn't find the current projection in the list of available projections!");

        projectionCombo.addActionListener(e -> {
            ProjectionChoice pc1 = (ProjectionChoice) projectionCombo.getSelectedItem();
            selectedProjectionChanged(pc1);
        });
        return pc;
    }

    /**
     * Get the id of the projection choice that is currently set.
     * @return id of the projection choice that is currently set
     */
    public static String getCurrentProjectionChoiceId() {
        return projectionChoice != null ? projectionChoice : PROP_PROJECTION_DEFAULT.get();
    }

    /**
     * Get the preferences that have been selected the last time for the given
     * projection choice.
     * @param pcId id of the projection choice
     * @return projection choice parameters that have been selected by the user
     * the last time; null if user has never selected the given projection choice
     */
    public static Collection<String> getSubprojectionPreference(String pcId) {
        return Config.getPref().getList("projection.sub."+pcId, null);
    }

    @Override
    public boolean isExpert() {
        return false;
    }

    /**
     * Selects the given projection.
     * @param projection The projection to select.
     * @since 5604
     */
    public void selectProjection(ProjectionChoice projection) {
        if (projectionCombo != null && projection != null) {
            projectionCombo.setSelectedItem(projection);
        }
    }

    @Override
    public String getHelpContext() {
        return HelpUtil.ht("/Preferences/Map");
    }
}
