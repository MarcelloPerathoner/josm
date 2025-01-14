// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.preferences;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.openstreetmap.josm.data.preferences.sources.ExtendedSourceEntry;

/**
 * Super class of parameterized source entry integration tests.
 */
public abstract class AbstractExtendedSourceEntryTestCase {

    private static final Pattern RESOURCE_PATTERN = Pattern.compile("resource://(.+)");
    private static final Pattern JOSM_WIKI_PATTERN = Pattern.compile("https://josm.openstreetmap.de/josmfile\\?page=(.+)&zip=1");
    private static final Pattern GITHUB_PATTERN = Pattern.compile("https://raw.githubusercontent.com/([^/]+)/([^/]+)/([^/]+)/(.+)");

    protected static final List<String> errorsToIgnore = new ArrayList<>();

    protected static List<Object[]> getTestParameters(Collection<ExtendedSourceEntry> entries) {
        return entries.stream().map(x -> new Object[] {x.getDisplayName(), cleanUrl(x.url), x}).collect(Collectors.toList());
    }

    private static String cleanUrl(String url) {
        Matcher wiki = JOSM_WIKI_PATTERN.matcher(url);
        if (wiki.matches()) {
            return "https://josm.openstreetmap.de/wiki/" + wiki.group(1);
        }
        Matcher github = GITHUB_PATTERN.matcher(url);
        if (github.matches()) {
            return String.format("https://github.com/%s/%s/blob/%s/%s", github.group(1), github.group(2), github.group(3), github.group(4));
        }
        Matcher resource = RESOURCE_PATTERN.matcher(url);
        if (resource.matches()) {
            return "https://josm.openstreetmap.de/browser/trunk/" + resource.group(1);
        }
        return url;
    }

    protected final void handleException(ExtendedSourceEntry source, Throwable e, Set<String> errors, List<String> ignoredErrors) {
        e.printStackTrace();
        String s = source.url + " => [" + e.getClass() + "]" + e.getMessage();
        if (isIgnoredSubstring(source, s)) {
            ignoredErrors.add(s);
        } else {
            errors.add(s);
        }
    }

    protected boolean isIgnoredSubstring(ExtendedSourceEntry source, String substring) {
        return errorsToIgnore.parallelStream().anyMatch(x ->
            (substring != null && substring.contains(x)) || (source.url != null && source.url.contains(x)));
    }
}
