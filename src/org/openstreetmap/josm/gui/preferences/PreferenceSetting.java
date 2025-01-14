// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.preferences;

/**
 * Base interface of Preferences settings, should not be directly implemented,
 * see {@link TabPreferenceSetting} and {@link SubPreferenceSetting}.
 */
public interface PreferenceSetting {
    /**
     * Add the GUI elements to the dialog. The elements should be initialized after
     * the current preferences.
     * @param gui the preferences tab pane
     */
    void addGui(PreferenceTabbedPane gui);

    /**
     * Called when OK is pressed to save the setting in the preferences file.
     * Returns true when restart is required.
     * @return {@code true} if restart is required
     */
    default boolean ok() {
        return false;
    }

    /**
     * Called when OK is pressed to save the setting in the preferences file.
     * <p>
     * If you return {@code true}, you may also append a HTML message to the
     * {@code StringBuilder}, that will be displayed in the "Restart" dialog. The
     * message should give a reason for the restart.  The message must not contain
     * {@code <html>} as outermost tag, as that is already provided for.
     *
     * @return {@code true} if restart is required
     */
    default boolean ok(StringBuilder sb) {
        return ok();
    }

    /**
     * Called to know if the preferences tab has only to be displayed in expert mode.
     * @return true if the tab has only to be displayed in expert mode, false otherwise.
     */
    boolean isExpert();
}
