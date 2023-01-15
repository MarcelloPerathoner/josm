// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.openstreetmap.josm.gui.dialogs.properties.HelpAction;
import org.openstreetmap.josm.gui.widgets.UrlLabel;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.LanguageInfo;

/**
 * Hyperlink type.
 * @since 8863
 */
final class Link extends TextItem {

    /** The link to display. */
    private final String href;
    /** The localized version of {@link #href}. */
    private final String localeHref;
    /** The OSM wiki page to display. */
    private final String wiki;

    /**
     * Private constructor. Use {@link #fromXML} instead.
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on attribute error
     */
    private Link(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
        href = attributes.get("href");
        localeHref = attributes.get("locale_href");
        wiki = attributes.get("wiki");
    }

    /**
     * Create a {@code Link} from an XML element's attributes.
     * @param attributes the XML attributes
     * @return the {@code Link}
     * @throws IllegalArgumentException on invalid attributes
     */
    static Link fromXML(Map<String, String> attributes) throws IllegalArgumentException {
        return new Link(attributes);
    }

    @Override
    String getDefaultText() {
        return tr("More information about this feature");
    }

    @Override
    boolean addToPanel(JComponent p, Composite.Instance parentInstance) {
        UrlLabel label = buildUrlLabel();
        if (label != null) {
            label.applyComponentOrientation(TaggingPresetDialog.getDefaultComponentOrientation());
            p.add(label, GBC.eol().insets(0, 10, 0, 0).anchor(GBC.LINE_START));
        }
        return false;
    }

    @Override
    boolean isInteractive() {
        return true;
    }

    private UrlLabel buildUrlLabel() {
        final String url = getUrl();
        if (wiki != null) {
            UrlLabel urlLabel = new UrlLabel(url, localeText, 2) {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        // Open localized page if exists
                        HelpAction.displayHelp(Arrays.asList(
                                LanguageInfo.getWikiLanguagePrefix(LanguageInfo.LocaleType.OSM_WIKI) + wiki,
                                wiki));
                    } else {
                        super.mouseClicked(e);
                    }
                }
            };
            addIcon(urlLabel);
            return urlLabel;
        } else if (href != null || localeHref != null) {
            UrlLabel urlLabel = new UrlLabel(url, localeText, 2);
            addIcon(urlLabel);
            return urlLabel;
        }
        return null;
    }

    /**
     * Returns the link URL.
     * @return the link URL or null
     * @since 15423
     */
    public String getUrl() {
        if (wiki != null) {
            return Config.getUrls().getOSMWiki() + "/wiki/" + wiki;
        }
        if (localeHref != null) {
            return localeHref;
        }
        return href; // may be null
    }

    @Override
    String fieldsToString() {
        return super.fieldsToString()
                + (wiki != null ? "wiki=" + wiki + ", " : "")
                + (href != null ? "href=" + href + ", " : "")
                + (localeHref != null ? "locale_href=" + localeHref + ", " : "");
    }
}
