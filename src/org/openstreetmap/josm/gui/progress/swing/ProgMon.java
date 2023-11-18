package org.openstreetmap.josm.gui.progress.swing;

import javax.swing.Icon;
import javax.swing.JProgressBar;

import org.openstreetmap.josm.gui.Notification;

/**
 * task = new Task();  // implements IProgMonTask
 * mon = new ProgMon(task);
 * task.setProgMon(mon); // let the task hold its monitor
 */
public class ProgMon {
    public interface IProgMonDisplay {
        /** Optional: Sets the dialog title */
        default IProgMonDisplay setTitle(String title) { return this; }

        /** Optional: Sets a text message */
        default IProgMonDisplay setText(String text) { return this; }

        /** Optional: Sets an icon */
        default IProgMonDisplay setIcon(Icon icon) { return this; }

        /** Optional: Sets the unit, eg. "KB", "MB", "nodes", "ways", ... */
        default IProgMonDisplay setUnit(String unit) { return this; }

        /** Optional: Sets the maximum value. Do not set a maximum value if is indefinite. */
        default IProgMonDisplay setMaximum(int value) { return this; }

        /** Sets the current value. */
        IProgMonDisplay setCurrent(int value);

        /** Shows or hides the display. */
        IProgMonDisplay setVisible(boolean visible);
    }

    public static class ProgMonNotification implements IProgMonDisplay {
        Notification notification;
        JProgressBar progressBar;

        public ProgMonNotification(Notification notification) {
            this.notification = notification;
            progressBar = new JProgressBar();
            notification.setContent(progressBar);
        }

        @Override
        public IProgMonDisplay setText(String text) {
            progressBar.setString(text);
            return this;
        }

        @Override
        public IProgMonDisplay setIcon(Icon icon) {
            notification.setIcon(icon);
            return this;
        }

        @Override
        public IProgMonDisplay setMaximum(int value) {
            progressBar.setMaximum(value);
            return this;
        }

        @Override
        public IProgMonDisplay setCurrent(int value) {
            progressBar.setValue(value);
            notification.setDuration(Notification.TIME_DEFAULT);
            return this;
        }

        @Override
        public IProgMonDisplay setVisible(boolean visible) {
            if (visible)
                notification.show();
            return this;
        }
    }
}
