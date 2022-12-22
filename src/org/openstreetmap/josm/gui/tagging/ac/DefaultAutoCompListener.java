// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.ac;

import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

/**
 * A default autocompletion listener.
 * @param <E> the type of the {@code AutoCompComboBox<E>} or {@code AutoCompTextField<E>}
 */
public class DefaultAutoCompListener<E> implements AutoCompListener, PopupMenuListener {
    protected void updateAutoCompModel(AutoCompComboBoxModel<E> model) {
    }

    @Override
    public void autoCompBefore(AutoCompEvent e) {
        AutoCompTextField<E> tf = toTextField(e);
        String savedText = tf.getText();
        updateAutoCompModel(tf.getModel());
        tf.setText(savedText);
    }

    @Override
    public void autoCompPerformed(AutoCompEvent e) {
    }

    @Override
    public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        AutoCompComboBox<E> cb = toComboBox(e);
        String savedText = cb.getText();
        boolean save = cb.disableActionEvents;
        cb.disableActionEvents = true;
        updateAutoCompModel(cb.getModel());
        cb.setText(savedText);
        cb.disableActionEvents = save;
    }

    @Override
    public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
    }

    @Override
    public void popupMenuCanceled(PopupMenuEvent e) {
    }

    /**
     * Returns the AutoCompTextField that sent the request.
     * @param e The AutoCompEvent
     * @return the AutoCompTextField
     */
    @SuppressWarnings("unchecked")
    public AutoCompTextField<E> toTextField(AutoCompEvent e) {
        return (AutoCompTextField<E>) e.getSource();
    }

    /**
     * Returns the AutoCompComboBox that sent the request.
     * @param e The AutoCompEvent
     * @return the AutoCompComboBox
     */
    @SuppressWarnings("unchecked")
    public AutoCompComboBox<E> toComboBox(PopupMenuEvent e) {
        return (AutoCompComboBox<E>) e.getSource();
    }
}
