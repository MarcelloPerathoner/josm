// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.preferences.server;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import org.openstreetmap.josm.actions.ExpertToggleAction;
import org.openstreetmap.josm.data.UserIdentityManager;
import org.openstreetmap.josm.data.oauth.OAuthAccessTokenHolder;
import org.openstreetmap.josm.data.oauth.OAuthVersion;
import org.openstreetmap.josm.data.preferences.JosmUrls;
import org.openstreetmap.josm.gui.help.HelpUtil;
import org.openstreetmap.josm.gui.widgets.VerticallyScrollablePanel;
import org.openstreetmap.josm.io.OsmApi;
import org.openstreetmap.josm.io.auth.CredentialsManager;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.Logging;

/**
 * This is the preference panel for the authentication method and the authentication parameters.
 * @since 2745
 */
public class AuthenticationPreferencesPanel extends VerticallyScrollablePanel implements PropertyChangeListener {

    /** indicates whether we use basic authentication */
    private final JRadioButton rbBasicAuthentication = new JRadioButton();
    /** indicates whether we use OAuth 1.0a as authentication scheme */
    private final JRadioButton rbOAuth = new JRadioButton();
    /** indicates whether we use OAuth 2.0 as authentication scheme */
    private final JRadioButton rbOAuth20 = new JRadioButton();
    /** the panel which contains the authentication parameters for the respective authentication scheme */
    private final JPanel pnlAuthenticationParameters = new JPanel(new BorderLayout());
    /** the panel for the basic authentication parameters */
    private BasicAuthenticationPreferencesPanel pnlBasicAuthPreferences;
    /** the panel for the OAuth 1.0a authentication parameters */
    private OAuthAuthenticationPreferencesPanel pnlOAuthPreferences;
    /** the panel for the OAuth 2.0 authentication parameters */
    private OAuthAuthenticationPreferencesPanel pnlOAuth20Preferences;

    /** Used to determine which API we are using for disabling/enabling Basic Auth/OAuth 1.0a */
    private String apiUrl = OsmApi.getOsmApi().getServerUrl();
    /** ExpertToggleAction uses weak references; we don't want this listener to be garbage collected */
    private final ExpertToggleAction.ExpertModeChangeListener expertModeChangeListener = isExpert -> {
        final String authMethod = OsmApi.getAuthMethod();
        final boolean defaultApi = JosmUrls.getInstance().getDefaultOsmApiUrl().equals(apiUrl);
        rbBasicAuthentication.setEnabled(rbBasicAuthentication.isSelected() || "basic".equals(authMethod) || isExpert || !defaultApi);
        rbOAuth.setEnabled(rbOAuth.isSelected() || "oauth".equals(authMethod) || isExpert || !defaultApi);
    };

    /**
     * Constructs a new {@code AuthenticationPreferencesPanel}.
     */
    public AuthenticationPreferencesPanel() {
        build();
        initFromPreferences();
        HelpUtil.setHelpContext(this, HelpUtil.ht("/Preferences/Connection#AuthenticationSettings"));
    }

    /**
     * builds the UI
     */
    protected final void build() {
        setLayout(new GridBagLayout());
        AuthenticationMethodChangeListener authChangeListener = new AuthenticationMethodChangeListener();

        // -- radio button for basic authentication
        add(rbBasicAuthentication, GBC.eol());
        rbBasicAuthentication.setText(tr("Use Basic Authentication"));
        rbBasicAuthentication.setToolTipText(tr("Select to use HTTP basic authentication with your OSM username and password"));
        rbBasicAuthentication.addItemListener(authChangeListener);

        //-- radio button for OAuth 1.0a
        add(rbOAuth, GBC.eol());
        rbOAuth.setText(tr("Use OAuth {0}", "1.0a"));
        rbOAuth.setToolTipText(tr("Select to use OAuth {0} as authentication mechanism", "1.0a"));
        rbOAuth.addItemListener(authChangeListener);

        //-- radio button for OAuth 2.0
        add(rbOAuth20, GBC.eol());
        rbOAuth20.setText(tr("Use OAuth {0}", "2.0"));
        rbOAuth20.setToolTipText(tr("Select to use OAuth {0} as authentication mechanism", "2.0"));
        rbOAuth20.addItemListener(authChangeListener);

        //-- radio button for OAuth
        ButtonGroup bg = new ButtonGroup();
        bg.add(rbBasicAuthentication);
        bg.add(rbOAuth);
        bg.add(rbOAuth20);

        //-- add the panel which will hold the authentication parameters
        add(pnlAuthenticationParameters, GBC.eol().fill(GridBagConstraints.HORIZONTAL));

        //-- the two panels for authentication parameters
        pnlBasicAuthPreferences = new BasicAuthenticationPreferencesPanel();
        pnlOAuthPreferences = new OAuthAuthenticationPreferencesPanel(OAuthVersion.OAuth10a);
        pnlOAuth20Preferences = new OAuthAuthenticationPreferencesPanel(OAuthVersion.OAuth20);

        ExpertToggleAction.addExpertModeChangeListener(expertModeChangeListener, true);

        rbOAuth20.setSelected(true);
        pnlAuthenticationParameters.add(pnlOAuth20Preferences, BorderLayout.CENTER);
    }

    /**
     * Initializes the panel from preferences
     */
    public final void initFromPreferences() {
        final String authMethod = OsmApi.getAuthMethod();
        switch (authMethod) {
            case "basic":
                rbBasicAuthentication.setSelected(true);
                break;
            case "oauth":
                rbOAuth.setSelected(true);
                break;
            case "oauth20":
                rbOAuth20.setSelected(true);
                break;
            default:
                Logging.warn(tr("Unsupported value in preference ''{0}'', got ''{1}''. Using authentication method ''Basic Authentication''.",
                        "osm-server.auth-method", authMethod));
                rbBasicAuthentication.setSelected(true);
        }
        pnlBasicAuthPreferences.initFromPreferences();
        pnlOAuthPreferences.initFromPreferences();
        pnlOAuth20Preferences.initFromPreferences();
    }

    /**
     * Saves the current values to the preferences
     */
    public final void saveToPreferences() {
        // save the authentication method
        String authMethod;
        if (rbBasicAuthentication.isSelected()) {
            authMethod = "basic";
        } else if (rbOAuth.isSelected()) {
            authMethod = "oauth";
        } else if (rbOAuth20.isSelected()) {
            authMethod = "oauth20";
        } else {
            throw new IllegalStateException("One of OAuth 2.0, OAuth 1.0a, or Basic authentication must be checked");
        }
        final boolean initUser = Config.getPref().put("osm-server.auth-method", authMethod);
        if ("basic".equals(authMethod)) {
            // save username and password and clear the OAuth token
            pnlBasicAuthPreferences.saveToPreferences();
            OAuthAccessTokenHolder.getInstance().clear();
            OAuthAccessTokenHolder.getInstance().save(CredentialsManager.getInstance());
        } else if ("oauth".equals(authMethod)) {
            // clear the password in the preferences
            pnlBasicAuthPreferences.clearPassword();
            pnlBasicAuthPreferences.saveToPreferences();
            pnlOAuthPreferences.saveToPreferences();
        } else { // oauth20
            // clear the password in the preferences
            pnlBasicAuthPreferences.clearPassword();
            pnlBasicAuthPreferences.saveToPreferences();
            pnlOAuth20Preferences.saveToPreferences();
        }
        if (initUser) {
            if ("basic".equals(authMethod)) {
                UserIdentityManager.getInstance().initFromPreferences();
            } else {
                UserIdentityManager.getInstance().initFromOAuth();
            }
        }
        ExpertToggleAction.removeExpertModeChangeListener(this.expertModeChangeListener);
    }

    /**
     * Listens to changes in the authentication method
     */
    class AuthenticationMethodChangeListener implements ItemListener {
        @Override
        public void itemStateChanged(ItemEvent e) {
            pnlAuthenticationParameters.removeAll();
            if (rbBasicAuthentication.isSelected()) {
                pnlAuthenticationParameters.add(pnlBasicAuthPreferences, BorderLayout.CENTER);
                pnlBasicAuthPreferences.revalidate();
            } else if (rbOAuth.isSelected()) {
                pnlAuthenticationParameters.add(pnlOAuthPreferences, BorderLayout.CENTER);
                pnlOAuthPreferences.saveToPreferences();
                pnlOAuthPreferences.initFromPreferences();
                pnlOAuthPreferences.revalidate();
            } else if (rbOAuth20.isSelected()) {
                pnlAuthenticationParameters.add(pnlOAuth20Preferences, BorderLayout.CENTER);
                pnlOAuth20Preferences.saveToPreferences();
                pnlOAuth20Preferences.initFromPreferences();
                pnlOAuth20Preferences.revalidate();
            }
            repaint();
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (pnlOAuthPreferences != null) {
            pnlOAuthPreferences.propertyChange(evt);
        }
        if (pnlOAuth20Preferences != null) {
            pnlOAuth20Preferences.propertyChange(evt);
        }
        if (OsmApiUrlInputPanel.API_URL_PROP.equals(evt.getPropertyName())) {
            this.apiUrl = (String) evt.getNewValue();
            this.expertModeChangeListener.expertChanged(ExpertToggleAction.isExpert());
        }
    }
}
