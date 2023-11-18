// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.AbstractAction;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.openstreetmap.josm.gui.help.HelpBrowser;
import org.openstreetmap.josm.gui.help.HelpUtil;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;

/**
 * Manages {@link Notification}s, i.e.&nbsp;displays them on screen.
 *
 * Don't use this class directly, but use {@link Notification#show()}.
 *
 * If multiple messages are sent in a short period of time, they are put in
 * a queue and displayed one after the other.
 *
 * The user can stop the timer (freeze the message) by moving the mouse cursor
 * above the panel. As a visual cue, the background color changes from
 * semi-transparent to opaque while the timer is frozen.
 */
class NotificationManager {
    /** The interval between ticks in milliseconds */
    private static final int TICK_INTERVAL = 500;
    /** A small gap, eg. the gap between notification panels */
    private static final int SMALLGAP = ImageProvider.adj(10);
    private static final Color PANEL_SEMITRANSPARENT = new Color(224, 236, 249, 230);
    private static final Color PANEL_OPAQUE = new Color(224, 236, 249);

    /** A timer that ticks every TICK_INTERVAL */
    private final Timer timer;
    private final CopyOnWriteArrayList<NotificationPanel> model;

    private static NotificationManager instance;

    public static synchronized NotificationManager getInstance() {
        if (instance == null) {
            instance = new NotificationManager();
        }
        return instance;
    }

    NotificationManager() {
        model = new CopyOnWriteArrayList<>();
        timer = new Timer(TICK_INTERVAL, e -> this.tick());
        timer.setRepeats(true);
        timer.start();
    }

    private void tick() {
        // proceed in reverse order because we may delete an index
        Point mouse = MouseInfo.getPointerInfo().getLocation();
        for (int index = model.size() - 1; index >= 0; --index) {
            NotificationPanel panel = model.get(index);
            Point pt = new Point(mouse);
            SwingUtilities.convertPointFromScreen(pt, panel.getParent());
            if (!panel.pinned && !panel.contains(pt)) {
                panel.setBackground(PANEL_SEMITRANSPARENT);
                panel.duration -= TICK_INTERVAL;
                if (panel.duration < 0) {
                    model.remove(index);
                    GuiHelper.runInEDT(() -> panel.getParent().remove(panel));
                    redraw();
                }
            } else {
                Logging.info("Swallowed Tick");
                panel.setBackground(PANEL_OPAQUE);
            }
        }
    }

    /**
     * Adds a notification
     * <p>
     * Duplicate notifications are not added.
     *
     * @param note The note to show.
     * @see Notification#show()
     */
    void addNotification(Notification note) {
        // drop this note if it is a duplicate
        /*
        for (int index = 0; index < model.size(); ++index) {
            NotificationPanel panel = model.get(index);
            if (Objects.equals(note, panel.notification)) {
                panel.duration = note.getDuration();
                Logging.debug("Dropping duplicate notification {0}", note);
                return;
            }
        }
        */
        model.add(new NotificationPanel(note));
        redraw();
    }

    /**
     * Replaces a notification
     */
    void replaceNotification(Notification old, Notification newNote) {
        for (int index = model.size() - 1; index >= 0; --index) {
            NotificationPanel panel = model.get(index);
            if (panel.notification.equals(old)) {
                model.set(index, new NotificationPanel(newNote));
                GuiHelper.runInEDT(() -> panel.getParent().remove(panel));
                redraw();
            }
        }
    }

    /**
     * Removes a notification
     */
    void removeNotification(Notification old) {
        for (int index = model.size() - 1; index >= 0; --index) {
            NotificationPanel panel = model.get(index);
            if (panel.notification.equals(old)) {
                model.remove(index);
                GuiHelper.runInEDT(() -> panel.getParent().remove(panel));
                redraw();
            }
        }
        redraw();
    }

    /**
     * Positions the list into the bottom-left corner of the map view.
     * <p>
     * Must be called after every list size change.
     * <p>
     * @implNote This implementation uses no layout manager because explicit positioning
     * has proved simpler and more robust.
     */
    private void redraw() {
        GuiHelper.runInEDT(() -> {
            JLayeredPane parentWindow = MainApplication.getMainFrame().getLayeredPane();
            if (parentWindow != null) {
                Point pos;
                MapFrame map = MainApplication.getMap();
                if (MainApplication.isDisplayingMapView() && map.mapView.getHeight() > 0) {
                    MapView mv = map.mapView;
                    // offset it from the bottom left of the mapview
                    pos = new Point(SMALLGAP, mv.getHeight() - SMALLGAP);
                    pos = SwingUtilities.convertPoint(mv, pos, parentWindow);
                } else {
                    // offset it from the bottom left of the main frame
                    pos = new Point(SMALLGAP, parentWindow.getHeight() - SMALLGAP);
                }

                for (NotificationPanel p : model) {
                    if (p.getParent() != parentWindow)
                        parentWindow.add(p, JLayeredPane.POPUP_LAYER, 0);
                    Dimension d = p.getPreferredSize();
                    p.setBounds(pos.x, pos.y - d.height, d.width, d.height);
                    pos.y -= d.height;
                    pos.y -= SMALLGAP;
                }
                parentWindow.repaint();

                Logging.info("redraw notification list with {0} elements at ({1}:{2})",
                    model.size(), pos.x, pos.y);
            }
        });
    }

    private class NotificationPanel extends RoundedPanel {

        static final class ShowNoteHelpAction extends AbstractAction {
            private final Notification note;

            ShowNoteHelpAction(Notification note) {
                super(tr("Help"));
                putValue(SHORT_DESCRIPTION, tr("Show help information"));
                new ImageProvider("help").getResource().attachImageIcon(this, true);
                this.note = note;
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                SwingUtilities.invokeLater(() -> HelpBrowser.setUrlForHelpTopic(note.getHelpTopic()));
            }
        }

        final class PinAction extends AbstractAction {
            PinAction() {
                super(null);
                putValue(SHORT_DESCRIPTION, tr("Pin this notification"));
                new ImageProvider("dialogs/pin").getResource().attachImageIcon(this, true);
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                Logging.info("Pin");
                pinned = true;
            }
        }

        final class CloseAction extends AbstractAction {
            CloseAction() {
                super(null);
                putValue(SHORT_DESCRIPTION, tr("Close this notification"));
                new ImageProvider("misc/close").getResource().attachImageIcon(this, true);
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                Logging.info("Close");
                removeNotification(notification);
            }
        }

        final Notification notification;
        int duration;
        boolean pinned = false;

        NotificationPanel(Notification notification) {
            this.notification = notification;
            duration = notification.getDuration();
            build(notification);
        }

        private void build(final Notification note) {
            JButton btnClose = new JButton(new CloseAction());
            btnClose.setContentAreaFilled(false);

            JToggleButton btnPinned = new JToggleButton(new PinAction());
            btnPinned.setContentAreaFilled(false);

            JButton btnHelp = null;
            if (note.getHelpTopic() != null) {
                btnHelp = new JButton(new ShowNoteHelpAction(note));
                btnHelp.setContentAreaFilled(false);
                HelpUtil.setHelpContext(btnHelp, note.getHelpTopic());
            }

            setOpaque(false);
            setBackground(PANEL_SEMITRANSPARENT);
            setForeground(Color.BLACK);
            setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));

            if (note.getIcon() != null)
                add(new JLabel(note.getIcon()));
            add(note.getContent());
            if (btnHelp != null)
                add(btnHelp);
            add(btnPinned);
            add(btnClose);
        }
    }

    /**
     * A panel with rounded edges and line border.
     */
    public static class RoundedPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics;
            g.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(getBackground());
            float lineWidth = 1.4f;
            Shape rect = new RoundRectangle2D.Double(
                    lineWidth/2d + getInsets().left,
                    lineWidth/2d + getInsets().top,
                    getWidth() - lineWidth - getInsets().left - getInsets().right,
                    getHeight() - lineWidth - getInsets().top - getInsets().bottom,
                    20, 20);

            g.fill(rect);
            g.setColor(getForeground());
            g.setStroke(new BasicStroke(lineWidth));
            g.draw(rect);
            super.paintComponent(graphics);
        }
    }
}
