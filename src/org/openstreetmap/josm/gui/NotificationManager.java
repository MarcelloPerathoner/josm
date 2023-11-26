// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.annotation.concurrent.GuardedBy;
import javax.swing.JLayeredPane;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;

/**
 * Displays {@link Notification}s on screen.
 * <p>
 * The notifications will appear stacked in the bottom-left corner of the parent
 * component and will disappear after a given timeout.
 * <p>
 * The user can stop the timer (freeze the message) by moving the mouse cursor
 * above the panel. As a visual cue, the background color changes from
 * semi-transparent to opaque while the timer is frozen.
 */
public class NotificationManager {
    /** The interval between ticks in milliseconds */
    private static final int TICK_INTERVAL = 500;
    /** A small gap, eg. the gap between notification panels */
    private static final int SMALLGAP = ImageProvider.adj(10);

    /** A timer that ticks every TICK_INTERVAL */
    private final Timer timer;
    /** The parent component. If null, the main window is used. */
    private final Component parent;
    /** The current notifications */
    @GuardedBy("model")
    private final ArrayList<Notification> model;
    /** A singleton instance for the global manager attached to the main window */
    private static NotificationManager instance;

    /** Testing */
    private final boolean isHeadless;
    /** A list that contains the texts of all notifications */
    public final List<String> robotList = new ArrayList<>();

    /**
     * Returns the notification manager for the main application window.
     *
     * @return the manager
     */
    public static synchronized NotificationManager getInstance() {
        if (instance == null) {
            instance = new NotificationManager(null);
        }
        return instance;
    }

    /**
     * Constructs a new NotificationManager
     * <p>
     * The notifications will appear stacked in the bottom-left corner of the parent
     * component. Note that the parent component is not the real parent of the
     * notification panels, but is only used for position calculations.
     * <p>
     * The real parent will be the {@link JLayeredPane} of the nearest
     * {@link RootPaneContainer}, that is the closest ancestor of the parent component
     * that is either a {@code JDialog} or a {@code JFrame}. Be careful not to attach 2
     * managers to the same window, eg the main window, because they will fight.
     * <p>
     * If the parent is {@code null}, the main window will be used, either the map view
     * or the motd screen.
     *
     * @param parent the parent component
     */
    public NotificationManager(Component parent) {
        this.parent = parent;
        model = new ArrayList<>();
        timer = new Timer(TICK_INTERVAL, e -> this.tick());
        timer.setRepeats(true);
        timer.start();
        isHeadless = GraphicsEnvironment.isHeadless();
    }

    private void tick() {
        if (isHeadless)
            return;
        Point mouse = MouseInfo.getPointerInfo().getLocation();
        synchronized(model) {
            for (Iterator<Notification> iter = model.iterator(); iter.hasNext();) {
                Notification notification = iter.next();
                Point pt = new Point(mouse);
                SwingUtilities.convertPointFromScreen(pt, notification);
                boolean hit = notification.contains(pt);
                notification.updateBackground(hit);
                if (!notification.isPinned() && !hit) {
                    notification.tick(TICK_INTERVAL);
                    if (notification.getRemaining() < 0) {
                        iter.remove();
                        GuiHelper.runInEDT(() -> {
                            notification.getParent().remove(notification);
                            redraw();
                        });
                    }
                }
            }
        }
    }

    /**
     * Adds a notification
     *
     * @param notification The note to add
     */
    void addNotification(Notification notification) {
        synchronized(model) {
            model.add(notification);
        }
        if (isHeadless) {
            robotList.add(notification.getText());
        }
        GuiHelper.runInEDT(this::redraw);
    }

    /**
     * Replaces a notification
     * <p>
     * Note: oldNote must be the <i>same</i> Object, not an equals one.
     */
    void replaceNotification(Notification oldNote, Notification newNote) {
        synchronized(model) {
            int index = model.indexOf(oldNote);
            if (index != -1) {
                model.set(index, newNote);
            }
        }
        GuiHelper.runInEDT(() -> {
            oldNote.getParent().remove(oldNote);
            redraw();
        });
    }

    /**
     * Removes a notification
     * <p>
     * Note: oldNote must be the <i>same</i> Object, not an equals one.
     */
    void removeNotification(Notification oldNote) {
        synchronized(model) {
            model.remove(oldNote);
        }
        GuiHelper.runInEDT(() -> {
            oldNote.getParent().remove(oldNote);
            redraw();
        });
    }

    /**
     * Removes all notifications
     */
    void clearNotifications() {
        List<Notification> copy;
        synchronized(model) {
            copy = List.copyOf(model);
            model.clear();
        }
        GuiHelper.runInEDT(() -> {
            for (Notification notification : copy) {
                notification.getParent().remove(notification);
            }
            redraw();
        });
    }

    /**
     * Positions the list into the bottom-left corner of the map view.
     * <p>
     * Must run in the EDT. Must be called after every list change.
     * <p>
     * @implNote This implementation uses no layout manager because explicit positioning
     * has proved simpler and more robust.
     */
    private void redraw() {
        if (isHeadless)
            return;

        JLayeredPane layeredPane = null;
        // actually not the real parent, but the component we base our calculations on
        // the real parent is the layeredPane
        Component p = parent;
        if (p != null) {
            Window window = SwingUtilities.getWindowAncestor(parent);
            if (window instanceof RootPaneContainer frame)
                layeredPane = frame.getLayeredPane();
        }
        if (layeredPane == null) {
            layeredPane = MainApplication.getMainFrame().getLayeredPane();
            p = MainApplication.getMainFrame();
            if (MainApplication.isDisplayingMapView()) {
                // offset it from the bottom left of the mapview
                MapFrame map = MainApplication.getMap();
                MapView mv = map.mapView;
                if (mv.getHeight() > 0) {
                    p = mv;
                }
            }
        }
        if (layeredPane != null && p != null) {
            Point pos = null;
            pos = new Point(SMALLGAP, p.getHeight() - SMALLGAP);
            pos = SwingUtilities.convertPoint(p, pos, layeredPane);

            List<Notification> copy;
            synchronized(model) {
                copy = List.copyOf(model);
            }
            for (Notification panel : copy) {
                if (panel.getParent() != layeredPane) {
                    layeredPane.add(panel, JLayeredPane.POPUP_LAYER, 0);
                    layeredPane.revalidate(); // !!!
                }
                Dimension d = panel.getPreferredSize();
                panel.setBounds(pos.x, pos.y - d.height, d.width, d.height);
                pos.y -= d.height;
                pos.y -= SMALLGAP;
            }
            layeredPane.repaint();

            Logging.info("redraw notification list with {0} elements at ({1}:{2})",
                model.size(), pos.x, pos.y);
        }
    }
}
