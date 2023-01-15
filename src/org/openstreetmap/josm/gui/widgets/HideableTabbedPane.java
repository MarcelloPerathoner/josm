// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.widgets;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.LayoutManager;

import javax.swing.Icon;
import javax.swing.JTabbedPane;
import javax.swing.OverlayLayout;
import javax.swing.border.Border;

/**
 * A {@link JTabbedPane} extension that completely hides the tab area and border if it contains less than 2 tabs.
 * @since 17314
 */
public class HideableTabbedPane extends JTabbedPane {
    transient LayoutManager savedLayout;
    transient Border savedBorder;

    /**
     * Creates an empty <code>HideableTabbedPane</code> with a default tab placement of <code>JTabbedPane.TOP</code>.
     * @see #addTab
     */
    public HideableTabbedPane() {
        this(TOP, WRAP_TAB_LAYOUT);
    }

    /**
     * Creates an empty <code>HideableTabbedPane</code> with the specified tab placement of either:
     * <code>JTabbedPane.TOP</code>, <code>JTabbedPane.BOTTOM</code>, <code>JTabbedPane.LEFT</code>, or <code>JTabbedPane.RIGHT</code>.
     *
     * @param tabPlacement the placement for the tabs relative to the content
     * @see #addTab
     */
    public HideableTabbedPane(int tabPlacement) {
        this(tabPlacement, WRAP_TAB_LAYOUT);
    }

    /**
     * Creates an empty <code>TabbedPane</code> with the specified tab placement and tab layout policy. Tab placement may be either:
     * <code>JTabbedPane.TOP</code>, <code>JTabbedPane.BOTTOM</code>, <code>JTabbedPane.LEFT</code>, or <code>JTabbedPane.RIGHT</code>.
     * Tab layout policy may be either: <code>JTabbedPane.WRAP_TAB_LAYOUT</code> or <code>JTabbedPane.SCROLL_TAB_LAYOUT</code>.
     *
     * @param tabPlacement the placement for the tabs relative to the content
     * @param tabLayoutPolicy the policy for laying out tabs when all tabs will not fit on one run
     * @exception IllegalArgumentException if tab placement or tab layout policy are not one of the above supported values
     * @see #addTab
     */
    public HideableTabbedPane(int tabPlacement, int tabLayoutPolicy) {
        super(tabPlacement, tabLayoutPolicy);
        savedLayout = getLayout();
        savedBorder = getBorder();
    }

    /**
     * Sets the correct LayoutManager to draw no tabs.
     */
    protected void hideTabs() {
        // Hide all tab components otherwise they still show because they are not drawn
        // by the JTabbedPane
        for (int i = 0; i < getTabCount(); ++i) {
            Component c = getTabComponentAt(i);
            if (c != null)
                c.setVisible(false);
        }
        // this layout will provide a working getPreferredSize, etc.
        // there will be max. one child visible while this layout is active
        setLayout(new OverlayLayout(this));
        setBorder(null);
    }

    /**
     * Sets the correct LayoutManager to draw tabs.
     */
    protected void showTabs() {
        setLayout(savedLayout);
        setBorder(savedBorder);
        for (int i = 0; i < getTabCount(); ++i) {
            Component c = getTabComponentAt(i);
            if (c != null)
                c.setVisible(true);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Paints the tabbar when there are more than one tabs, else paints nothing.  Note
     * that the children are not painted by this function.
     */
    @Override
    protected void paintComponent(Graphics g) {
        if (getTabCount() > 1) {
            super.paintComponent(g);
        }
    }

    @Override
    public void doLayout() {
        if (getTabCount() == 1) {
            // FlatLAF (and maybe others) dies if it doesn't get its internal tab array
            // initialized even if we are not going to draw the component
            savedLayout.layoutContainer(this); // always tab layout
        }
        super.doLayout(); // tab or overlay layout
    }

    @Override
    public void insertTab(String title, Icon icon, Component component, String tip, int index) {
        super.insertTab(title, icon, component, tip, index);
        if (getTabCount() == 1) {
            hideTabs();
        } else {
            showTabs();
        }
    }

    @Override
    public void removeTabAt(int index) {
        super.removeTabAt(index);
        if (getTabCount() == 1) {
            hideTabs();
        } else {
            showTabs();
        }
    }

    @Override
    public void setTabComponentAt(int index, Component component) {
        super.setTabComponentAt(index, component);
        if (getTabCount() == 1) {
            hideTabs();
        } else {
            showTabs();
        }
    }
}