// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.openstreetmap.josm.tools.I18n.tr;
import static org.openstreetmap.josm.tools.I18n.trc;

import java.awt.Component;
import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;

import org.openstreetmap.josm.data.osm.search.SearchCompiler;
import org.openstreetmap.josm.data.osm.search.SearchParseError;
import org.openstreetmap.josm.data.osm.search.SearchCompiler.Match;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.tools.AlphanumComparator;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.ImageResource;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.template_engine.ParseError;
import org.openstreetmap.josm.tools.template_engine.TemplateEntry;
import org.openstreetmap.josm.tools.template_engine.TemplateParser;

/**
 * Utility class for tagging presets.
 */
public final class TaggingPresetUtils {

    private TaggingPresetUtils() {}

    /**
     * Replaces ' with ''
     * @param s input
     * @return output
     */
    public static String fixPresetString(String s) {
        return s == null ? s : s.replace("'", "''");
    }

    /**
     * Parse and compile a template.
     *
     * @param pattern The template pattern.
     * @return the compiled template
     * @throws IllegalArgumentException If an error occured while parsing.
     */
    public static TemplateEntry parseTemplate(String pattern) throws IllegalArgumentException { // NOPMD
        if (pattern == null)
            return null;
        try {
            return new TemplateParser(pattern).parse();
        } catch (ParseError e) {
            Logging.error("Error while parsing " + pattern + ": " + e.getMessage());
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Sets the match_expression additional criteria for matching primitives.
     *
     * @param filter The search pattern
     * @return the compiled expression

     * @throws IllegalArgumentException on search pattern parse error
     * @see <a href="https://josm.openstreetmap.de/wiki/TaggingPresets#Attributes">JOSM wiki</a>
     */

    public static Match parseSearchExpression(String filter) throws IllegalArgumentException {
        if (filter == null)
            return null;
        try {
            return SearchCompiler.compile(filter);
        } catch (SearchParseError e) {
            Logging.error("Error while parsing" + filter + ": " + e.getMessage());
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Loads the icon asynchronously and puts it on the action.
     * <p>
     * The image resource is loaded in the background, and then the EDT is invoked to put the icon
     * on the action.
     * @param iconName the iconname
     * @param action the action where to put the icon
     *
     * @return a future completed when the icon is put on the action
     */
    static CompletableFuture<ImageResource> loadIcon(String iconName, AbstractAction action) {
        if (action != null && iconName != null && TaggingPresetReader.isLoadIcons()) {
            return new ImageProvider(iconName)
                .setDirs(TaggingPresets.ICON_SOURCES.get())
                .setId("presets")
                .setArchive(TaggingPresetReader.getZipIcons())
                .setOptional(true)
                .getResourceFuture()
                .thenCompose((imageResource) -> {
                    CompletableFuture<ImageResource> future = new CompletableFuture<>();
                    SwingUtilities.invokeLater(() -> {
                        if (imageResource != null)
                            imageResource.attachImageIcon(action, true);
                        future.complete(imageResource);
                    });
                    return future;
                });
        }
        return CompletableFuture.<ImageResource>completedFuture(null);
    }

    /**
     * Loads a tagging preset icon
     * @param iconName the icon name
     * @param zipIcons zip file where the image is located
     * @param maxSize maximum image size (or null)
     * @return the requested image or null if the request failed
     */
    public static ImageIcon loadImageIcon(String iconName, File zipIcons, Integer maxSize) {
        final Collection<String> s = TaggingPresets.ICON_SOURCES.get();
        ImageProvider imgProv = new ImageProvider(iconName).setDirs(s).setId("presets").setArchive(zipIcons).setOptional(true);
        if (maxSize != null && maxSize > 0) {
            imgProv.setMaxSize(maxSize);
        }
        return imgProv.get();
    }

    /**
     * Localizes the preset name
     *
     * @param localeText the locale name from the attributes
     * @param text the unlocalized name
     * @param textContext the localization context
     * @return The name that should be displayed to the user.
     */
    public static String buildLocaleString(String localeText, String text, String textContext) {
        if (localeText != null)
            return localeText;
        text = fixPresetString(text);
        if (textContext != null) {
            return trc(textContext, text);
        } else {
            return tr(text);
        }
    }

    /**
     * Determine whether the given preset items match the tags
     * @param data the preset items
     * @param tags the tags to match
     * @return whether the given preset items match the tags
     * @since 9932
     */
    public static boolean matches(Iterable<? extends KeyedItem> data, Map<String, String> tags) {
        boolean atLeastOnePositiveMatch = false;
        for (KeyedItem item : data) {
            Boolean m = item.matches(tags);
            if (m != null && !m)
                return false;
            else if (m != null) {
                atLeastOnePositiveMatch = true;
            }
        }
        return atLeastOnePositiveMatch;
    }

    /**
     * allow escaped comma in comma separated list:
     * "A\, B\, C,one\, two" --&gt; ["A, B, C", "one, two"]
     * @param delimiter the delimiter, e.g. a comma. separates the entries and
     *      must be escaped within one entry
     * @param s the string
     * @return splitted items
     */
    public static List<String> splitEscaped(char delimiter, String s) {
        if (s == null)
            return null; // NOSONAR

        List<String> result = new ArrayList<>();
        boolean backslash = false;
        StringBuilder item = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (backslash) {
                item.append(ch);
                backslash = false;
            } else if (ch == '\\') {
                backslash = true;
            } else if (ch == delimiter) {
                result.add(item.toString());
                item.setLength(0);
            } else {
                item.append(ch);
            }
        }
        if (item.length() > 0) {
            result.add(item.toString());
        }
        return result;
    }

    /**
     * Parse a {@code String} into an {@code boolean}.
     * @param s the string to parse
     * @return the int
     */
    public static boolean parseBoolean(String s) {
        return s != null
                && !"0".equals(s)
                && !s.startsWith("off")
                && !s.startsWith("false")
                && !s.startsWith("no");
    }

    /**
     * Wait for all preset icons to load
     * @param presets presets collection
     * @param timeout timeout in seconds
     * @throws InterruptedException if any thread is interrupted
     * @throws ExecutionException if any thread throws
     * @throws TimeoutException on timeout
     */
    public static void waitForIconsLoaded(Collection<TaggingPreset> presets, long timeout)
        throws InterruptedException, ExecutionException, TimeoutException {

        @SuppressWarnings("unchecked")
        CompletableFuture<ImageResource>[] futures =
            presets.stream().map(tp -> tp.iconFuture).toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).get(timeout, TimeUnit.SECONDS);
    }

    /**
     * Sorts the menu items using the translated item text
     * @param menu menu to sort
     */
    public static void sortMenu(JMenu menu) {
        Component[] items = menu.getMenuComponents();
        PresetTextComparator comp = new PresetTextComparator();
        List<JMenuItem> sortarray = new ArrayList<>();
        int lastSeparator = 0;
        for (int i = 0; i < items.length; i++) {
            Object item = items[i];
            if (item instanceof JMenu) {
                sortMenu((JMenu) item);
            }
            if (item instanceof JMenuItem) {
                sortarray.add((JMenuItem) item);
                if (i == items.length-1) {
                    handleMenuItem(menu, comp, sortarray, lastSeparator);
                    sortarray = new ArrayList<>();
                    lastSeparator = 0;
                }
            } else if (item instanceof JSeparator) {
                handleMenuItem(menu, comp, sortarray, lastSeparator);
                sortarray = new ArrayList<>();
                lastSeparator = i;
            }
        }
    }

    private static void handleMenuItem(JMenu menu, PresetTextComparator comp, List<JMenuItem> sortarray, int lastSeparator) {
        sortarray.sort(comp);
        int pos = 0;
        for (JMenuItem menuItem : sortarray) {
            int oldPos;
            if (lastSeparator == 0) {
                oldPos = pos;
            } else {
                oldPos = pos+lastSeparator+1;
            }
            menu.add(menuItem, oldPos);
            pos++;
        }
    }

    private static class PresetTextComparator implements Comparator<JMenuItem>, Serializable {
        private static final long serialVersionUID = 1L;
        @Override
        public int compare(JMenuItem o1, JMenuItem o2) {
            if (MainApplication.getMenu().presetSearchAction.equals(o1.getAction()))
                return -1;
            else if (MainApplication.getMenu().presetSearchAction.equals(o2.getAction()))
                return 1;
            else
                return AlphanumComparator.getInstance().compare(o1.getText(), o2.getText());
        }
    }
}
