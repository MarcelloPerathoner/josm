// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.preferences;

import static org.openstreetmap.josm.gui.preferences.PreferenceUtils.SMALLGAP;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.ScrollPaneConstants;

import org.openstreetmap.josm.gui.help.HelpUtil;
import org.openstreetmap.josm.gui.widgets.VerticallyScrollablePanel;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.Logging;

/**
 * Abstract base class for {@link TabPreferenceSetting} implementations.
 *
 * Support for common functionality, like icon, title and adding a tab ({@link SubPreferenceSetting}).
 */
public abstract class DefaultTabPreferenceSetting extends DefaultPreferenceSetting implements TabPreferenceSetting {
    private final String iconName;
    private final String description;
    private final String title;
    private final JTabbedPane tabpane;
    private final Map<SubPreferenceSetting, Component> subSettingMap;

    /**
     * Constructs a new {@code DefaultTabPreferenceSetting}.
     */
    protected DefaultTabPreferenceSetting() {
        this(null, null, null);
    }

    protected DefaultTabPreferenceSetting(String iconName, String title, String description) {
        this(iconName, title, description, false);
    }

    protected DefaultTabPreferenceSetting(String iconName, String title, String description, boolean isExpert) {
        this(iconName, title, description, isExpert, null);
    }

    protected DefaultTabPreferenceSetting(String iconName, String title, String description, boolean isExpert, JTabbedPane tabpane) {
        super(isExpert);
        this.iconName = iconName;
        this.description = description;
        this.title = title;
        this.tabpane = tabpane;
        this.subSettingMap = tabpane != null ? new HashMap<>() : null;
        if (tabpane != null) {
            tabpane.addMouseWheelListener(new PreferenceTabbedPane.WheelListener(tabpane));
        }
    }

    @Override
    public String getIconName() {
        return iconName;
    }

    @Override
    public String getTooltip() {
        if (getDescription() != null) {
            return "<html>"+getDescription()+"</html>";
        } else {
            return null;
        }
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getTitle() {
        return title;
    }

    /**
     * Get the inner tab pane, if any.
     * @return The JTabbedPane contained in this tab preference settings, or null if none is set.
     * @since 5631
     */
    public final JTabbedPane getTabPane() {
        return tabpane;
    }

    /**
     * Gives a panel a suitably uniform look
     * <p>
     * Usage:
     * <pre>
     *   JPanel panel = new JPanel(...);
     *   ... add gui stuff to panel ...
     *   JPanel tab = gui.createPreferenceTab(this, false);
     *   tab.add(decorate(panel), GBC.eol().fill());
     * </pre>
     * @param component the component to decorate
     * @return the given component for chaining
     */
    public static JComponent decorate(JComponent component) {
        // Border border = UIManager.getBorder("ScrollPane.viewportBorder");
        /*Border border = new JTabbedPane().getBorder();
        if (border == null) {
            border = BorderFactory.createLineBorder(component.getForeground());
        }
        Insets i = UIManager.getInsets("TabbedPane.contentBorderInsets");
        if (i == null) {
            i = new Insets(SMALLGAP, SMALLGAP, SMALLGAP, SMALLGAP);
        }
        component.setBorder(BorderFactory.createCompoundBorder(
            border,
            BorderFactory.createEmptyBorder(i.top, i.left, i.bottom, i.right)
        ));*/
        component.setBorder(BorderFactory.createEmptyBorder(SMALLGAP, SMALLGAP, SMALLGAP, SMALLGAP));
        // component.setOpaque(false);
        // makeChildrenTransparent(component);
        return component;
    }

    /**
     * Decorates a JPanel and embeds it in a scrollpane
     * <p>
     * Usage:
     * <pre>
     *   JPanel panel = new VerticallyScrollablePanel(...);
     *   ... add gui stuff to panel ...
     *   JPanel tab = gui.createPreferenceTab(this, false);
     *   tab.add(decorateScrollablePanel(panel), GBC.eol().fill());
     * </pre>
     * @return a JScrollPane with the panel
     */
    public static JScrollPane decorateScrollable(VerticallyScrollablePanel panel) {
        JScrollPane scrollPane = new JScrollPane(panel,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setViewportBorder(null);
        return (JScrollPane) decorate(scrollPane);
    }

    static void makeChildrenTransparent(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof Container) {
                makeChildrenTransparent((Container) child);
            }
            if (child instanceof JPanel || child instanceof JToggleButton) {
                ((JComponent) child).setOpaque(false);
            }
        }
    }

    /**
     * Returns an inset that aligns checkbox with indented checkbox
     * @return the insets
     */
    public static Insets getIndent() {
        JCheckBox checkBox = new JCheckBox();
        Dimension dimension = checkBox.getMinimumSize();
        Insets insets = checkBox.getInsets();
        int left = dimension.width - insets.left - insets.right + checkBox.getIconTextGap();
        return new Insets(0, left, 0, 0);
    }

    /**
     * Returns an inset that aligns checkbox with indented text
     * @return the insets
     */
    public static Insets getIndentForText() {
        JCheckBox checkBox = new JCheckBox();
        Dimension dimension = checkBox.getMinimumSize();
        Insets insets = checkBox.getInsets();
        int left = dimension.width - insets.right + checkBox.getIconTextGap();
        return new Insets(0, left, 0, 0);
    }

    /**
     * Adds an indent to a given inset (eg. another indent)
     * @return the insets
     */
    public static Insets addInsets(Insets i1, Insets i2) {
        return new Insets(i1.top + i2.top, i1.left + i2.left, i1.bottom + i2.bottom, i1.right + i2.right);
    }

    public static Component hGlue() {
        return GBC.glue(SMALLGAP, 0);
    }

    public static Component vSkip() {
        return GBC.skip(0, SMALLGAP);
    }

    protected final void createPreferenceTabWithScrollPane(PreferenceTabbedPane gui, JPanel panel) {
        panel.setOpaque(false);

        GBC a = GBC.eol().insets(-5, 0, 0, 0);
        a.anchor = GridBagConstraints.EAST;

        JPanel tab = gui.createPreferenceTab(this, true);
        tab.setOpaque(false);
        tab.add(panel, GBC.eol().fill(GridBagConstraints.BOTH));
        tab.add(GBC.glue(0, SMALLGAP), a);
    }

    @Override
    public boolean selectSubTab(SubPreferenceSetting subPref) {
        if (tabpane != null && subPref != null) {
            Component tab = getSubTab(subPref);
            if (tab != null) {
                try {
                    tabpane.setSelectedComponent(tab);
                    return true;
                } catch (IllegalArgumentException e) {
                    // Ignore exception and return false below
                    Logging.debug(Logging.getErrorMessage(e));
                }
            }
        }
        return false;
    }

    @Override
    public final void addSubTab(SubPreferenceSetting sub, String title, Component component) {
        addSubTab(sub, title, component, null);
    }

    @Override
    public final void addSubTab(SubPreferenceSetting sub, String title, Component component, String tip) {
        if (tabpane != null && component != null) {
            tabpane.addTab(title, null, component, tip);
            registerSubTab(sub, component);
        }
    }

    @Override
    public final void registerSubTab(SubPreferenceSetting sub, Component component) {
        if (subSettingMap != null && sub != null && component != null) {
            subSettingMap.put(sub, component);
        }
    }

    @Override
    public final Component getSubTab(SubPreferenceSetting sub) {
        return subSettingMap != null ? subSettingMap.get(sub) : null;
    }

    @Override
    public Class<? extends SubPreferenceSetting> getSelectedSubTab() {
        if (tabpane == null || subSettingMap == null) {
            return null;
        }
        final Component selected = tabpane.getSelectedComponent();
        return subSettingMap.entrySet().stream()
                .filter(e -> e.getValue() == selected)
                .map(e -> e.getKey().getClass())
                .findFirst().orElse(null);
    }

    /**
     * Determines whether this tab may be hidden (since it does not contain any relevant content)
     * @return whether this tab may be hidden
     */
    protected boolean canBeHidden() {
        return false;
    }

    @Override
    public String getHelpContext() {
        return HelpUtil.ht("/Action/Preferences");
    }
}
