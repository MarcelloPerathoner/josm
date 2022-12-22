// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.ac;

import java.util.EventListener;

/**
 * The listener interface for receiving AutoCompEvent events.
 * <p>
 * The class that is interested in processing an {@link AutoCompEvent} implements this interface,
 * and the object created with that class is registered with an autocompleting component using the
 * autocompleting component's {@link AutoCompTextField#addAutoCompListener addAutoCompListener}
 * method.
 * <p>
 * Before the autocompletion searches for candidates, the listener's {@code autoCompBefore} method
 * is invoked. It can be used to initialize the {@link AutoCompComboBoxModel}. After the
 * autocompletion occured the listener's {@code autoCompPerformed} method is invoked. It is used eg.
 * for adjusting the selection of an {@link AutoCompComboBox} after its {@link AutoCompTextField}
 * has autocompleted.
 *
 * @since 18221
 */
public interface AutoCompListener extends EventListener {

    /**
     * Invoked before an autocomplete.  You can use this to change the model.
     *
     * @param e an {@link AutoCompEvent}
     */
    void autoCompBefore(AutoCompEvent e);

    /**
     * Invoked after an autocomplete happened.
     *
     * @param e an {@link AutoCompEvent}
     */
    void autoCompPerformed(AutoCompEvent e);
}
