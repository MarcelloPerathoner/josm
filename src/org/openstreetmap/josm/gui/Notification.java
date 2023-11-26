// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.geom.RoundRectangle2D;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.JTextComponent;

import org.openstreetmap.josm.gui.help.HelpBrowser;
import org.openstreetmap.josm.gui.help.HelpUtil;
import org.openstreetmap.josm.gui.widgets.JMultilineLabel;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;

/**
 * A small popup that vanishes after a given time.
 * <p>
 * The popup is non-modal and minimally disrupts the user's workflow.  The popup can
 * show either a text message and an optional progressbar, or a custom panel.  The
 * {@link NotificationManager} displays the notifications on an layered pane.
 * <p>
 * Example:
 * <pre>
 *      Notification note = new Notification("Hello, World!");
 *      note.setIcon(JOptionPane.INFORMATION_MESSAGE); // optional
 *      note.setDuration(Notification.TIME_SHORT); // optional
 *      note.setVisible(true);
 * </pre>
 */
public class Notification extends JPanel {
    public static final Icon iconInfo = ImageProvider.get("info", ImageProvider.ImageSizes.XLARGEICON);
    public static final Icon iconSuccess = ImageProvider.get("dialogs/validator", ImageProvider.ImageSizes.XLARGEICON);
    public static final Icon iconWarning = ImageProvider.get("data/warning", ImageProvider.ImageSizes.XLARGEICON);
    public static final Icon iconError = ImageProvider.get("data/error", ImageProvider.ImageSizes.XLARGEICON);

    static final Color PANEL_INFO_BACKGROUND = new Color(224, 236, 249);
    static final Color PANEL_SUCCESS_BACKGROUND = new Color(128, 255, 128);
    static final Color PANEL_WARNING_BACKGROUND = new Color(255, 255, 128);
    static final Color PANEL_ERROR_BACKGROUND = new Color(255, 128, 128);

    /** A small gap, eg. the gap between notification panels */
    private static final int INSET = ImageProvider.adj(5);

    /**
     * Default width of a notification
     */
    public static final int DEFAULT_CONTENT_WIDTH = 350;

    // some standard duration values (in milliseconds)

    /**
     * Very short and very easy to grasp message (3 s).
     * E.g. "Please select at least one node".
     */
    public static final int TIME_SHORT = Config.getPref().getInt("notification-time-short-ms", 3000);

    /**
     * Short message of one or two lines (5 s).
     */
    public static final int TIME_DEFAULT = Config.getPref().getInt("notification-time-default-ms", 5000);

    /**
     * Somewhat longer message (10 s).
     */
    public static final int TIME_LONG = Config.getPref().getInt("notification-time-long-ms", 10_000);

    /**
     * Long text.
     * (Make sure is still sensible to show as a notification)
     */
    public static final int TIME_VERY_LONG = Config.getPref().getInt("notification-time-very_long-ms", 20_000);

    private Icon icon;
    private String message;
    private String helpTopic;
    private Component content;

    private JLabel iconLabel;
    private JMultilineLabel messageLabel;
    private JProgressBar progressBar;

    private int duration = Notification.TIME_DEFAULT;
    private int remaining;
    private boolean pinned;
    private NotificationManager manager;

    /**
     * Constructs a new {@code Notification} without content.
     */
    public Notification() {
        manager = NotificationManager.getInstance();
        setBackground(PANEL_INFO_BACKGROUND);
    }

    /**
     * Constructs a new {@code Notification} with the given textual content.
     * @param message The text to display
     */
    public Notification(String message) {
        this();
        setText(message);
    }

    /**
     * Sets a custom content.
     * <p>
     * If set this takes precedence over the text message and progressbar.
     *
     * @param content any Component to be shown
     *
     * @return the current Object, for convenience
     * @see #setContent(java.lang.String)
     */
    public Notification setContent(Component content) {
        this.content = content;
        return this;
    }

    /**
     * @deprecated use setText instead
     */
    @Deprecated
    public Notification setContent(String text) {
        return setText(text);
    }

    /**
     * Set the notification text. (Convenience method)
     *
     * @param text the message String. Will be wrapped in &lt;html&gt;, so
     * you can use &lt;br&gt; and other markup directly.
     *
     * @return the current Object, for convenience
     * @see #Notification(java.lang.String)
     */
    public Notification setText(String text) {
        this.message = text;
        if (messageLabel == null)
            messageLabel = new JMultilineLabel(text);
        else
            messageLabel.setText(text);
        return this;
    }

    public String getText() {
        return message;
    }

    /**
     * Set an icon to display on the left part of the message window.
     *
     * @param icon the icon (null means no icon is displayed)
     * @return the current Object, for convenience
     */
    public Notification setIcon(Icon icon) {
        this.icon = icon;
        if (iconLabel == null)
            iconLabel = new JLabel();
        iconLabel.setIcon(icon);
        return this;
    }

    /**
     * Sets the given progressbar
     * <p>
     * The progressbar starts in indeterminate mode and turns to determinate mode if you
     * set a maximum value.
     */
    public Notification setProgressBar(JProgressBar progressBar) {
        this.progressBar = progressBar;
        progressBar.setIndeterminate(true);
        return this;
    }

    /**
     * Sets a progressbar
     * <p>
     * The progressbar starts in indeterminate mode and turns to determinate mode if you
     * set a maximum value.
     */
    public Notification withProgressBar() {
        return setProgressBar(new JProgressBar());
    }

    /**
     * Sets a notification manager
     * <p>
     * Allows displaying notifications in a different JFrame
     *
     * @param manager the new manager
     * @return this
     */
    public Notification setManager(NotificationManager manager) {
        this.manager = manager;
        return this;
    }

    /**
     * Sets the current value in the progessbar.
     * <p>
     * This function also resets the remaing time to the initial time, so that the
     * Notification will disappear {@code duration} milliseconds after the last time
     * the value was changed.
     * @param value the value
     * @return this
     */
    public Notification setCurrent(int value) {
        if (progressBar != null)
            progressBar.setValue(value);
        // reload while progressbar is active
        remaining = getDuration();
        return this;
    }

    /**
     * Sets the maximum value for the progressbar.
     */
    public Notification setMaximum(int value) {
        if (progressBar != null) {
            progressBar.setMaximum(value);
            progressBar.setIndeterminate(false);
        }
        return this;
    }

    /**
     * Set the time after which the message is hidden.
     *
     * @param duration the time (in milliseconds)
     * Preset values {@link #TIME_SHORT}, {@link #TIME_DEFAULT}, {@link #TIME_LONG}
     * and {@link #TIME_VERY_LONG} can be used.
     * @return the current Object, for convenience
     */
    public Notification setDuration(int duration) {
        this.duration = duration;
        this.remaining = duration;
        return this;
    }

    public Notification info(String text) {
        setText(text);
        setIcon(iconInfo);
        setBackground(PANEL_INFO_BACKGROUND);
        return this;
    }

    public Notification success(String text) {
        setText(text);
        setIcon(iconSuccess);
        setBackground(PANEL_SUCCESS_BACKGROUND);
        return this;
    }

    public Notification warning(String text) {
        setText(text);
        setIcon(iconWarning);
        setBackground(PANEL_WARNING_BACKGROUND);
        return this;
    }

    public Notification error(String text) {
        setText(text);
        setIcon(iconError);
        setBackground(PANEL_ERROR_BACKGROUND);
        return this;
    }

    public Notification replace(Notification newNotification) {
        manager.replaceNotification(this, newNotification);
        return this;
    }

    /**
     * Decrements the remaining time
     * @param time in milliseconds
     * @return this
     */
    Notification tick(int time) {
        this.remaining -= time;
        return this;
    }

    void updateBackground(boolean isHovering) {
        float[] bg = getBackground().getRGBComponents(null);
        if (isHovering && bg[3] != 0.5f) {
            setBackground(new Color(bg[0], bg[1], bg[2], 1.0f));
        }
        if (!isHovering && bg[3] != 1.0f) {
            setBackground(new Color(bg[0], bg[1], bg[2], 0.7f));
        }
    }

    /**
     * Set an icon to display on the left part of the message window by
     * choosing from the default JOptionPane icons.
     *
     * @param messageType one of the following: JOptionPane.ERROR_MESSAGE,
     * JOptionPane.INFORMATION_MESSAGE, JOptionPane.WARNING_MESSAGE,
     * JOptionPane.QUESTION_MESSAGE, JOptionPane.PLAIN_MESSAGE
     * @return the current Object, for convenience
     */
    public Notification setIcon(int messageType) {
        switch (messageType) {
            case JOptionPane.ERROR_MESSAGE:
                return setIcon(UIManager.getIcon("OptionPane.errorIcon"));
            case JOptionPane.INFORMATION_MESSAGE:
                return setIcon(UIManager.getIcon("OptionPane.informationIcon"));
            case JOptionPane.WARNING_MESSAGE:
                return setIcon(UIManager.getIcon("OptionPane.warningIcon"));
            case JOptionPane.QUESTION_MESSAGE:
                return setIcon(UIManager.getIcon("OptionPane.questionIcon"));
            case JOptionPane.PLAIN_MESSAGE:
                return setIcon(null);
            default:
                throw new IllegalArgumentException("Unknown message type!");
        }
    }

    /**
     * Display a help button at the bottom of the notification window.
     * @param helpTopic the help topic
     * @return the current Object, for convenience
     */
    public Notification setHelpTopic(String helpTopic) {
        this.helpTopic = helpTopic;
        return this;
    }

    /**
     * Gets the content component to use.
     * @return The content
     */
    public Component getContent() {
        return content;
    }

    /**
     * Gets the full time the notification should be displayed
     * @return The time to display the notification
     */
    public int getDuration() {
        return duration;
    }

    /**
     * Gets the remaining time the notification should be displayed
     * @return The time to display the notification
     */
    public int getRemaining() {
        return remaining;
    }

    /**
     * Gets the icon that should be displayed next to the notification
     * @return The icon to display
     */
    public Icon getIcon() {
        return icon;
    }

    /**
     * Gets the help topic for this notification
     * @return The help topic
     */
    public String getHelpTopic() {
        return helpTopic;
    }

    public boolean isPinned() {
        return pinned;
    }

    /**
     * Display the notification.
     */
    @Override
    public void setVisible(boolean show) {
        if (show) {
            build();
            remaining = getDuration();
            manager.addNotification(this);
        } else {
            manager.removeNotification(this);
        }
    }

    /**
     * Display the notification.
     * @deprecated use setVisible(true) instead
     */
    @Deprecated
    public void show() {
        setVisible(true);
    }

    /**
     * Display the notification by replacing the given queued/displaying notification
     * @param oldNotification the notification to replace
     * @since 17628
     */
    public void replaceExisting(Notification oldNotification) {
        manager.replaceNotification(oldNotification, this);
    }

    /**
     * Build the panel
     */
    private void build() {
        JButton btnClose = new JButton(new CloseAction());
        btnClose.setContentAreaFilled(false);

        JToggleButton btnPinned = new JToggleButton(new PinAction());
        btnPinned.setContentAreaFilled(false);

        JButton btnHelp = null;
        if (getHelpTopic() != null) {
            btnHelp = new JButton(new ShowNoteHelpAction(this));
            btnHelp.setContentAreaFilled(false);
            HelpUtil.setHelpContext(btnHelp, getHelpTopic());
        }

        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(INSET, INSET, INSET, INSET));

        JPanel hBoxes = this;
        hBoxes.setLayout(new BoxLayout(hBoxes, BoxLayout.LINE_AXIS));
        if (getIcon() != null) {
            hBoxes.add(new JLabel(getIcon()));
        }
        if (getContent() != null) {
            hBoxes.add(getContent());
        } else {
            JPanel vBoxes = new JPanel();
            vBoxes.setLayout(new BoxLayout(vBoxes, BoxLayout.PAGE_AXIS));
            vBoxes.setOpaque(false);
            if (message != null) {
                messageLabel = new JMultilineLabel(message);
                messageLabel.setMaxWidth(DEFAULT_CONTENT_WIDTH);
                messageLabel.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
                messageLabel.setForeground(Color.BLACK);
                vBoxes.add(messageLabel);
            }
            if (progressBar != null) {
                vBoxes.add(progressBar);
            }
            hBoxes.add(vBoxes);
        }

        if (btnHelp != null)
            hBoxes.add(btnHelp);
        hBoxes.add(btnPinned);
        hBoxes.add(btnClose);
    }

    /**
     * Paints rounded corners
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics;
        g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(getBackground());
        float lineWidth = 1.4f;
        float r = ImageProvider.adj(20);
        Shape rect = new RoundRectangle2D.Double(
                lineWidth/2d,
                lineWidth/2d,
                getWidth() - lineWidth,
                getHeight() - lineWidth,
                r, r);

        g.fill(rect);
        g.setColor(getForeground());
        g.setStroke(new BasicStroke(lineWidth));
        g.draw(rect);
        super.paintComponent(graphics);
    }

    private Object getContentTextOrComponent() {
        return content instanceof JTextComponent ? ((JTextComponent) content).getText() : content;
    }

    @Override
    public String toString() {
        return "Notification{" +
                "content=" + getContentTextOrComponent() +
                ", duration=" + duration +
                ", helpTopic='" + helpTopic + '\'' +
                '}';
    }

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
            manager.removeNotification(Notification.this);
        }
    }
}
