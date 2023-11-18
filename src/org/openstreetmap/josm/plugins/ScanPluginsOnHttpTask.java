// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins;

import java.net.URI;
import java.net.URISyntaxException;

public class ScanPluginsOnHttpTask extends ScanPluginsTask {
    private static final String PLACEHOLDER = "%<(.*)>";

    public ScanPluginsOnHttpTask(URI uri) {
        super(uri);
    }

    /**
     * Replaces PLACEHOLDER with list of plugins
     * <p>
     * Replaces {@code %<plugins=>} with {@code plugins=a,b,c}
     */
    private URI fixUri(URI uri) throws URISyntaxException {
        String pluginString = String.join(",", PluginHandler.getConfigPluginNames());
        String site = uri.toString();
        if (!pluginString.isEmpty()) {
            site = site.replaceAll(PLACEHOLDER, "$1" + pluginString);
        } else {
            site = site.replaceAll(PLACEHOLDER, "");
        }
        return new URI(site);
    }

    @Override
    public void run() {
        try {
            result.addAll(scanRepositoryManifest(fixUri(uri)));
        } catch (URISyntaxException e) {
            this.exception = e;
        }
    }
}
