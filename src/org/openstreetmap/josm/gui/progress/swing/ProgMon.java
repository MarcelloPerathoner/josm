package org.openstreetmap.josm.gui.progress.swing;

import javax.swing.Icon;

import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.tools.Logging;

public class ProgMon {
    /**
     * Interface for a ProgMon Display
     * <p>
     * To implement a display for the ProgMon you have to implement at least the
     * {@link #setCurrent} and {@link #setVisible} functions. All other functions are
     * optional.
     */
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

        /** Replaces the note with a new one. */
        default IProgMonDisplay replace(Notification newNotification) { return this; }

        /** Optional: Sets an error message */
        default IProgMonDisplay error(String text) { setText(text); return this; }

        /** Optional: Sets a warning message */
        default IProgMonDisplay warning(String text) { setText(text); return this; }

        /** Optional: Sets a success message */
        default IProgMonDisplay success(String text) { setText(text); return this; }

        /** Optional: Sets an info message */
        default IProgMonDisplay info(String text) { setText(text); return this; }
    }

    /**
     * A ProgMon wrapper around a {@link Notification}
     * <p>
     * You can call the functions in this wrapper from any thread. You should construct
     * the notfication and wrap it while running in the EDT.  Then you can pass the
     * interface to any thread you like.
     * <pre>
     * // wrap the notification
     * IProgMonDisplay progMon = new ProgMonNotification(
     *     new Notification("Downloading plugin: foo")
     *         .setIcon(icon)
     *         .withProgressBar()
     *         .setVisible(true)
     * )
     * // submit and forget
     * worker.submit(task.withProgressMonitor(progMon));
     * </pre>
     * <p>
     * Note that if the EDT is blocked, the updates provided by the task will not become
     * visible. If you are {@code wait}ing or {@code get}ting or {@code join}ing in the
     * EDT, or otherwise hogging the EDT, this will not work.
     */

    public static class ProgMonNotification implements IProgMonDisplay {
        final Notification notification;

        public ProgMonNotification(Notification notification) {
            this.notification = notification;
        }

        @Override
        public IProgMonDisplay setText(String text) {
            Logging.info("ProgMon setText to: {0}", text);
            GuiHelper.runInEDT(() -> notification.setText(text));
            return this;
        }

        @Override
        public IProgMonDisplay setIcon(Icon icon) {
            GuiHelper.runInEDT(() -> notification.setIcon(icon));
            return this;
        }

        @Override
        public IProgMonDisplay setMaximum(int value) {
            GuiHelper.runInEDT(() -> notification.setMaximum(value));
            return this;
        }

        @Override
        public IProgMonDisplay setCurrent(int value) {
            GuiHelper.runInEDT(() -> notification.setCurrent(value));
            return this;
        }

        @Override
        public IProgMonDisplay setVisible(boolean visible) {
            GuiHelper.runInEDT(() -> notification.setVisible(visible));
            return this;
        }

        @Override
        public IProgMonDisplay replace(Notification newNotification) {
            GuiHelper.runInEDT(() -> notification.replace(newNotification));
            return this;
        }

        @Override
        public IProgMonDisplay info(String text) {
            GuiHelper.runInEDT(() -> notification.info(text));
            return this;
        }

        @Override
        public IProgMonDisplay success(String text) {
            GuiHelper.runInEDT(() -> notification.success(text));
            return this;
        }

        @Override
        public IProgMonDisplay warning(String text) {
            GuiHelper.runInEDT(() -> notification.warning(text));
            return this;
        }

        @Override
        public IProgMonDisplay error(String text) {
            GuiHelper.runInEDT(() -> notification.error(text));
            return this;
        }
    }
}
