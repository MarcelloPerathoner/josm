// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.preferences.display;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JCheckBox;
import javax.swing.JLabel;

import org.openstreetmap.josm.actions.ExpertToggleAction;
import org.openstreetmap.josm.data.preferences.BooleanProperty;
import org.openstreetmap.josm.gui.autofilter.AutoFilterManager;
import org.openstreetmap.josm.gui.autofilter.AutoFilterRule;
import org.openstreetmap.josm.gui.help.HelpUtil;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.preferences.DefaultTabPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceSettingFactory;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.gui.widgets.JosmComboBox;
import org.openstreetmap.josm.gui.widgets.VerticallyScrollablePanel;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.GBC;

/**
 * "OSM Data" drawing preferences.
 */
public class DrawingPreference extends DefaultTabPreferenceSetting {

    /**
     * Factory used to create a new {@code DrawingPreference}.
     */
    public static class Factory implements PreferenceSettingFactory {
        @Override
        public PreferenceSetting createPreferenceSetting() {
            return new DrawingPreference();
        }
    }

    /**
     * Property controlling whether to draw boundaries of downloaded data
     * @since 14648
     */
    public static final BooleanProperty SOURCE_BOUNDS_PROP = new BooleanProperty("draw.data.downloaded_area", true);

    private final JCheckBox directionHint = new JCheckBox(tr("Draw Direction Arrows"));
    private final JCheckBox headArrow = new JCheckBox(tr("Only on the head of a way."));
    private final JCheckBox onewayArrow = new JCheckBox(tr("Draw oneway arrows."));
    private final JCheckBox segmentOrderNumber = new JCheckBox(tr("Draw segment order numbers"));
    private final JCheckBox segmentOrderNumberOnSelectedWay = new JCheckBox(tr("Draw segment order numbers on selected way"));
    private final JCheckBox sourceBounds = new JCheckBox(tr("Draw boundaries of downloaded data"));
    private final JCheckBox virtualNodes = new JCheckBox(tr("Draw virtual nodes in select mode"));
    private final JCheckBox inactive = new JCheckBox(tr("Draw inactive layers in other color"));
    private final JCheckBox discardableKeys = new JCheckBox(tr("Display discardable keys"));
    private final JCheckBox autoFilters = new JCheckBox(tr("Use auto filters"));
    private final JLabel lblRule = new JLabel(tr("Rule"));
    private final JosmComboBox<AutoFilterRule> autoFilterRules = new JosmComboBox<>(
            AutoFilterManager.getInstance().getAutoFilterRules().toArray(new AutoFilterRule[] {}));

    // Options that affect performance
    private final JCheckBox useHighlighting = new JCheckBox(tr("Highlight target ways and nodes"));
    private final JCheckBox drawHelperLine = new JCheckBox(tr("Draw rubber-band helper line"));
    private final JCheckBox useAntialiasing = new JCheckBox(tr("Smooth map graphics (antialiasing)"));
    private final JCheckBox useWireframeAntialiasing = new JCheckBox(tr("Smooth map graphics in wireframe mode (antialiasing)"));
    private final JCheckBox outlineOnly = new JCheckBox(tr("Draw only outlines of areas"));
    private final JCheckBox hideLabelsWhileDragging = new JCheckBox(tr("Hide labels while dragging the map"));

    DrawingPreference() {
        super("layer/osmdata_small", tr("OSM Data"), tr("Settings that control the drawing of OSM data."));
    }

    @Override
    public void addGui(PreferenceTabbedPane gui) {
        VerticallyScrollablePanel panel = new VerticallyScrollablePanel(new GridBagLayout());

        // directionHint
        directionHint.addActionListener(e -> {
            if (directionHint.isSelected()) {
                headArrow.setSelected(Config.getPref().getBoolean("draw.segment.head_only", false));
            } else {
                headArrow.setSelected(false);
            }
            headArrow.setEnabled(directionHint.isSelected());
        });
        directionHint.setToolTipText(tr("Draw direction hints for way segments."));
        directionHint.setSelected(Config.getPref().getBoolean("draw.segment.direction", false));

        // only on the head of a way
        headArrow.setToolTipText(tr("Only on the head of a way."));
        headArrow.setSelected(Config.getPref().getBoolean("draw.segment.head_only", false));
        headArrow.setEnabled(directionHint.isSelected());

        // draw oneway arrows
        onewayArrow.setToolTipText(tr("Draw arrows in the direction of oneways and other directed features."));
        onewayArrow.setSelected(Config.getPref().getBoolean("draw.oneway", true));

        // segment order number
        segmentOrderNumber.setToolTipText(tr("Draw the order numbers of all segments within their way."));
        segmentOrderNumber.setSelected(Config.getPref().getBoolean("draw.segment.order_number", false));
        segmentOrderNumberOnSelectedWay.setToolTipText(tr("Draw the order numbers of all segments within their way."));
        segmentOrderNumberOnSelectedWay.setSelected(Config.getPref().getBoolean("draw.segment.order_number.on_selected", false));

        // downloaded area
        sourceBounds.setToolTipText(tr("Draw the boundaries of data loaded from the server."));
        sourceBounds.setSelected(SOURCE_BOUNDS_PROP.get());

        // virtual nodes
        virtualNodes.setToolTipText(tr("Draw virtual nodes in select mode for easy way modification."));
        virtualNodes.setSelected(Config.getPref().getInt("mappaint.node.virtual-size", 8) != 0);

        // background layers in inactive color
        inactive.setToolTipText(tr("Draw the inactive data layers in a different color."));
        inactive.setSelected(Config.getPref().getBoolean("draw.data.inactive_color", true));

        // antialiasing
        useAntialiasing.setToolTipText(tr("Apply antialiasing to the map view resulting in a smoother appearance."));
        useAntialiasing.setSelected(Config.getPref().getBoolean("mappaint.use-antialiasing", true));

        // wireframe mode antialiasing
        useWireframeAntialiasing.setToolTipText(tr("Apply antialiasing to the map view in wireframe mode resulting in a smoother appearance."));
        useWireframeAntialiasing.setSelected(Config.getPref().getBoolean("mappaint.wireframe.use-antialiasing", false));

        // highlighting
        useHighlighting.setToolTipText(tr("Highlight target nodes and ways while drawing or selecting"));
        useHighlighting.setSelected(Config.getPref().getBoolean("draw.target-highlight", true));

        drawHelperLine.setToolTipText(tr("Draw rubber-band helper line"));
        drawHelperLine.setSelected(Config.getPref().getBoolean("draw.helper-line", true));

        // outlineOnly
        outlineOnly.setToolTipText(tr("This option suppresses the filling of areas, overriding anything specified in the selected style."));
        outlineOnly.setSelected(Config.getPref().getBoolean("draw.data.area_outline_only", false));

        // hideLabelsWhileDragging
        hideLabelsWhileDragging.setToolTipText(tr("This option hides the textual labels of OSM objects while dragging the map."));
        hideLabelsWhileDragging.setSelected(OsmDataLayer.PROPERTY_HIDE_LABELS_WHILE_DRAGGING.get());

        // discardable keys
        discardableKeys.setToolTipText(tr("Display keys which have been deemed uninteresting to the point that they can be silently removed."));
        discardableKeys.setSelected(Config.getPref().getBoolean("display.discardable-keys", false));

        // auto filters
        autoFilters.setToolTipText(tr("Display buttons to automatically filter numeric values of a predefined tag"));
        autoFilters.setSelected(AutoFilterManager.PROP_AUTO_FILTER_ENABLED.get());
        autoFilters.addActionListener(e -> {
            lblRule.setEnabled(autoFilters.isSelected());
            autoFilterRules.setEnabled(autoFilters.isSelected());
        });
        autoFilterRules.setToolTipText("Rule defining which tag will provide automatic filters, below a certain zoom level");
        autoFilterRules.setSelectedItem(AutoFilterManager.getInstance().getAutoFilterRule(AutoFilterManager.PROP_AUTO_FILTER_RULE.get()));

        JLabel performanceLabel = new JLabel(tr("Options that affect drawing performance"));
        Insets indent = getIndent();
        Insets indent2 = addInsets(indent, indent);
        GBC eol = GBC.eol().insets(indent).weight(1, 0); // or all items will be centered because none is filling
        GBC eop = GBC.eop().insets(indent);

        panel.add(new JLabel(tr("Segment drawing options")), GBC.eol());
        panel.add(directionHint, eol);
        panel.add(headArrow, GBC.eol().insets(indent2));
        panel.add(onewayArrow, eol);
        panel.add(segmentOrderNumber, eol);
        panel.add(segmentOrderNumberOnSelectedWay, eop);

        panel.add(new JLabel(tr("Select and draw mode options")), GBC.eol());
        panel.add(virtualNodes, eol);
        panel.add(drawHelperLine, eop);

        panel.add(performanceLabel, GBC.eol());
        panel.add(useAntialiasing, eol);
        panel.add(useWireframeAntialiasing, eol);
        panel.add(useHighlighting, eol);
        panel.add(outlineOnly, eol);
        panel.add(hideLabelsWhileDragging, eop);

        panel.add(new JLabel(tr("Other options")), GBC.eol());
        panel.add(sourceBounds, eol);
        panel.add(inactive, eol);
        panel.add(discardableKeys, eol);
        panel.add(autoFilters, eol);
        panel.add(lblRule, GBC.std().insets(addInsets(getIndentForText(), new Insets(0, 0, 0, 10))));
        panel.add(autoFilterRules, GBC.eol());

        ExpertToggleAction.addVisibilitySwitcher(performanceLabel);
        ExpertToggleAction.addVisibilitySwitcher(useAntialiasing);
        ExpertToggleAction.addVisibilitySwitcher(useWireframeAntialiasing);
        ExpertToggleAction.addVisibilitySwitcher(useHighlighting);
        ExpertToggleAction.addVisibilitySwitcher(outlineOnly);
        ExpertToggleAction.addVisibilitySwitcher(hideLabelsWhileDragging);
        ExpertToggleAction.addVisibilitySwitcher(discardableKeys);

        gui.createPreferenceTab(this).add(decorateScrollable(panel), GBC.eol().fill());
    }

    @Override
    public boolean ok() {
        OsmDataLayer.PROPERTY_HIDE_LABELS_WHILE_DRAGGING.put(hideLabelsWhileDragging.isSelected());
        Config.getPref().putBoolean("draw.data.area_outline_only", outlineOnly.isSelected());
        Config.getPref().putBoolean("draw.segment.direction", directionHint.isSelected());
        Config.getPref().putBoolean("draw.segment.head_only", headArrow.isSelected());
        Config.getPref().putBoolean("draw.oneway", onewayArrow.isSelected());
        Config.getPref().putBoolean("draw.segment.order_number", segmentOrderNumber.isSelected());
        Config.getPref().putBoolean("draw.segment.order_number.on_selected", segmentOrderNumberOnSelectedWay.isSelected());
        SOURCE_BOUNDS_PROP.put(sourceBounds.isSelected());
        Config.getPref().putBoolean("draw.data.inactive_color", inactive.isSelected());
        Config.getPref().putBoolean("mappaint.use-antialiasing", useAntialiasing.isSelected());
        Config.getPref().putBoolean("mappaint.wireframe.use-antialiasing", useWireframeAntialiasing.isSelected());
        Config.getPref().putBoolean("draw.target-highlight", useHighlighting.isSelected());
        Config.getPref().putBoolean("draw.helper-line", drawHelperLine.isSelected());
        Config.getPref().putBoolean("display.discardable-keys", discardableKeys.isSelected());
        AutoFilterManager.PROP_AUTO_FILTER_ENABLED.put(autoFilters.isSelected());
        AutoFilterManager.PROP_AUTO_FILTER_RULE.put(((AutoFilterRule) autoFilterRules.getSelectedItem()).getKey());
        int vn = Config.getPref().getInt("mappaint.node.virtual-size", 8);
        if (virtualNodes.isSelected()) {
            if (vn < 1) {
                vn = 8;
            }
        } else {
            vn = 0;
        }
        Config.getPref().putInt("mappaint.node.virtual-size", vn);
        return false;
    }

    @Override
    public boolean isExpert() {
        return false;
    }

    @Override
    public String getHelpContext() {
        return HelpUtil.ht("/Preferences/DrawingPreference");
    }
}
