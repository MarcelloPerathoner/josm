// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.Action;
import javax.swing.JToggleButton;

import org.openstreetmap.josm.actions.ExpertToggleAction;
import org.openstreetmap.josm.actions.ExpertToggleAction.ExpertModeChangeListener;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.CheckParameterUtil;
import org.openstreetmap.josm.tools.Destroyable;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.ImageResource;

/**
 * Just a toggle button, with smaller border and icon only to display in
 * MapFrame toolbars.
 * Also provides methods for storing hidden state in preferences
 * FIXME: code duplicated in IconButton.java
 * @author imi, akks
 */
public class IconToggleButton extends JToggleButton implements HideableButton, PropertyChangeListener, Destroyable, ExpertModeChangeListener {

    private transient ShowHideButtonListener listener;
    private boolean hideIfDisabled;
    private final boolean isExpert;

    /**
     * Construct the toggle button with the given action.
     * @param action associated action
     */
    public IconToggleButton(Action action) {
        this(action, false);
    }

    /**
     * Sets a toolbar-sized icon from resource if present on the action
     * @param action the action
     */
    private void setIconFromResource(Action action) {
        ImageResource imageResource = (ImageResource) action.getValue(ImageResource.IMAGE_RESOURCE_KEY);
        if (imageResource != null) {
            setIcon(imageResource.getPaddedIcon(ImageProvider.ImageSizes.TOOLBAR.getImageDimension()));
            setHideActionText(true);
        }
    }

    @Override
    protected void configurePropertiesFromAction(Action action) {
        super.configurePropertiesFromAction(action);
        setIconFromResource(action);
    }

    @Override
    protected void actionPropertyChanged(Action action, String propertyName) {
        // called async when resource has loaded
        super.actionPropertyChanged(action, propertyName);
        if (ImageResource.IMAGE_RESOURCE_KEY.equals(propertyName)) {
            setIconFromResource(action);
        }
    }

    /**
     * Construct the toggle button with the given action.
     * @param action associated action
     * @param isExpert {@code true} if it's reserved to expert mode
     */
    public IconToggleButton(Action action, boolean isExpert) {
        super(action);
        CheckParameterUtil.ensureParameterNotNull(action, "action");
        this.isExpert = isExpert;
        setText(null);

        Object o = action.getValue(Action.SHORT_DESCRIPTION);
        if (o != null) {
            setToolTipText(o.toString());
        }

        action.addPropertyChangeListener(this);

        ExpertToggleAction.addExpertModeChangeListener(this);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("active".equals(evt.getPropertyName())) {
            setSelected((Boolean) evt.getNewValue());
            requestFocusInWindow();
        } else if ("selected".equals(evt.getPropertyName())) {
            setSelected((Boolean) evt.getNewValue());
        }
    }

    @Override
    public void destroy() {
        Action action = getAction();
        if (action instanceof Destroyable) {
            ((Destroyable) action).destroy();
        }
        if (action != null) {
            action.removePropertyChangeListener(this);
        }
    }

    String getPreferenceKey() {
        String s = (String) getSafeActionValue("toolbar");
        if (s == null && getAction() != null) {
            s = getAction().getClass().getName();
        }
        return "sidetoolbar.hidden."+s;
    }

    @Override
    public void expertChanged(boolean isExpert) {
        applyButtonHiddenPreferences();
    }

    @Override
    public void applyButtonHiddenPreferences() {
        boolean alwaysHideDisabled = Config.getPref().getBoolean("sidetoolbar.hideDisabledButtons", false);
        if (!isEnabled() && (hideIfDisabled || alwaysHideDisabled)) {
            setVisible(false);  // hide because of disabled button
        } else {
            boolean hiddenFlag = false;
            String hiddenFlagStr = Config.getPref().get(getPreferenceKey(), null);
            if (hiddenFlagStr == null) {
                if (isExpert && !ExpertToggleAction.isExpert()) {
                    hiddenFlag = true;
                }
            } else {
                hiddenFlag = Boolean.parseBoolean(hiddenFlagStr);
            }
            setVisible(!hiddenFlag); // show or hide, do what preferences say
        }
    }

    @Override
    public void setButtonHidden(boolean b) {
        setVisible(!b);
        if (listener != null) { // if someone wants to know about changes of visibility
            if (!b) listener.buttonShown(); else listener.buttonHidden();
        }
        if ((b && isExpert && !ExpertToggleAction.isExpert()) ||
            (!b && isExpert && ExpertToggleAction.isExpert())) {
            Config.getPref().put(getPreferenceKey(), null);
        } else {
            Config.getPref().putBoolean(getPreferenceKey(), b);
        }
    }

    /**
     * This function should be called for plugins that want to enable auto-hiding
     * custom buttons when they are disabled (because of incorrect layer, for example)
     * @param b hide if disabled
     */
    public void setAutoHideDisabledButton(boolean b) {
        hideIfDisabled = b;
        if (b && !isEnabled()) {
            setVisible(false);
        }
    }

    @Override
    public void showButton() {
        setButtonHidden(false);
    }

    @Override
    public void hideButton() {
        setButtonHidden(true);
    }

    @Override
    public String getActionName() {
        return (String) getSafeActionValue(Action.NAME);
    }

    @Override
    public boolean isButtonVisible() {
        return isVisible();
    }

    @Override
    public boolean isExpert() {
        return isExpert;
    }

    @Override
    public void setShowHideButtonListener(ShowHideButtonListener l) {
        listener = l;
    }

    protected final Object getSafeActionValue(String key) {
        // Mac OS X Aqua L&F can call accessors from constructor, so getAction() can be null in those cases
        return getAction() != null ? getAction().getValue(key) : null;
    }
}
