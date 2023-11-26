// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.widgets;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.util.List;

import javax.swing.JToggleButton;

import org.openstreetmap.josm.tools.Logging;

/**
 * A model that turns a JToggleButton into a 4-state button.
 * <p>
 * Caveat: The semantic changes a bit:
 * {@code isSelected()} now means: the box is ticked
 * and you must also check {@code isUndefined()} to learn the full state.
 * <p>
 * The LaF UI reads the states: armed, pressed, and selected.  When the UI does that, we
 * fake states that make the UI look what we want.  When the JCheckBox operates the
 * model through setArmed() and setPressed() it sees the real states.
 * <p>
 * This hack is a blatant misuse of the armed and pressed states.  Since some LaF (eg.
 * GTK) use these states to give visual feedback even outside the "tick" area, this hack
 * will not blend in well with those LaF: the undefined states may produce extraneous
 * visual artifacts.
 * @see javax.swing.plaf.basic.BasicButtonListener
 * @see javax.swing.DefaultButtonModel
 */
public class QuadStateButtonModel extends JToggleButton.ToggleButtonModel {
    /**
     * The 4 possible states of this checkbox.
     */
    public enum State {
        /** Not selected: the property is explicitly switched off */
        NOT_SELECTED,
        /** Selected: the property is explicitly switched on */
        SELECTED,
        /** Unset: do not set this property on the selected objects */
        UNSET,
        /** Partial: different selected objects have different values, do not change */
        PARTIAL
    }

    /**
     * Identifies the "undefined" bit in the bitmask, which
     * indicates that the checkbox is in an undefined state.
     */
    public static final int UNDEFINED = 1 << 5;
    /**
     * If set, report faked states for the sake of the LaF UI.
     */
    boolean fakeState = true;

    private final List<State> allowedStates;

    /**
     * Constructs a 3- or 4-state toggle button model.
     *
     * @param initialState the intial state
     * @param allowedStates the allowed states
     */
    public QuadStateButtonModel(State initialState, List<State> allowedStates) {
        this.allowedStates = allowedStates;
        setState(initialState);
    }

    /**
     * Checks if the button is undefined.
     */
    public boolean isUndefined() {
        return (stateMask & UNDEFINED) != 0;
    }

    /**
     * Sets the undefined state of the button.
     * @param b the new undefined state
     */
    public void setUndefined(boolean b) {
        if (b) {
            stateMask |= UNDEFINED;
        } else {
            stateMask &= ~UNDEFINED;
        }

        // Send ChangeEvent
        fireStateChanged();

        // Send ItemEvent
        fireItemStateChanged(
                new ItemEvent(this,
                                ItemEvent.ITEM_STATE_CHANGED,
                                this,
                                this.isSelected() ? ItemEvent.SELECTED : ItemEvent.DESELECTED));
    }

    /**
     * Set when the button (or key) is pressed, unset when released.
     * <p>
     * This is the only function where state changes. It changes only when armed on
     * button release.
     */
    @Override
    public void setPressed(boolean b) {
        Logging.info("setPressed {0}", b);

        if ((super.isPressed() == b) || !isEnabled()) {
            return;
        }

        if (b) {
            stateMask |= ARMED; // or it will hang in UNDEFINED states, see: BasicButtonListener#mousePressed
            stateMask |= PRESSED;
        } else {
            stateMask &= ~PRESSED;
        }

        if (!b && super.isArmed()) {
            // cycle allowed states
            int index = allowedStates.indexOf(getState());
            setState(allowedStates.get((index + 1) % allowedStates.size()));
        }

        fireStateChanged();
    }

    /**
     * Set when the mouse enters, unset when the mouse exits.
     */
    @Override
    public void setArmed(boolean b) {
        Logging.info("setArmed {0}", b);
        fakeState = false;
        super.setArmed(b);
        fakeState = true;
    }

    @Override
    public boolean isPressed() {
        if (fakeState)
            return isUndefined();
        return super.isPressed();
    }

    @Override
    public boolean isArmed() {
        if (fakeState)
            return isUndefined();
        return super.isArmed();
    }

    /**
     * Disable rollover (hover) because our misuse of the armed state turns on the
     * rollover visual as long as the box is in an undefined state.
     */
    @Override
    public boolean isRollover() {
        return false;
    }

    /**
     * Returns the current state.
     * <p>
     * @return The current state
     */
    public State getState() {
        if (isSelected()) {
            if (isUndefined())
                return State.PARTIAL;
            else
                return State.SELECTED;
        } else {
            if (isUndefined())
                return State.UNSET;
            else
                return State.NOT_SELECTED;
        }
    }

    public void setState(State state) {
        Logging.info("setState {0}", state);
        if (state == State.SELECTED) {
            stateMask |= SELECTED;
            stateMask &= ~UNDEFINED;
        }
        if (state == State.NOT_SELECTED) {
            stateMask &= ~SELECTED;
            stateMask &= ~UNDEFINED;
        }
        if (state == State.PARTIAL) {
            stateMask |= SELECTED;
            stateMask |= UNDEFINED;
        }
        if (state == State.UNSET) {
            stateMask &= ~SELECTED;
            stateMask |= UNDEFINED;
        }

        int modifiers = 0;
        AWTEvent currentEvent = EventQueue.getCurrentEvent();
        if (currentEvent instanceof InputEvent) {
            modifiers = ((InputEvent) currentEvent).getModifiers();
        } else if (currentEvent instanceof ActionEvent) {
            modifiers = ((ActionEvent) currentEvent).getModifiers();
        }
        fireActionPerformed(
            new ActionEvent(this, ActionEvent.ACTION_PERFORMED,
                            getActionCommand(),
                            EventQueue.getMostRecentEventTime(),
                            modifiers));
    }
}
