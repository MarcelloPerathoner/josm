// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins;

import java.io.File;
import java.io.IOException;
import java.net.URI;

import org.openstreetmap.josm.tools.Logging;

/**
 * Scans a local directory for plugins.
 */
public class ScanPluginsInDirectoryTask extends ScanPluginsTask {
    /**
     * Constructor
     * @param uri must have the format {@code file:///path/to/directory/}
     */
    public ScanPluginsInDirectoryTask(URI uri) {
        super(uri);
    }

    /**
     * Generate a list of plugin jars from a local directory
     * <p>
     * The directory is scanned recursively.
     */
    @Override
    public void run() {
        try {
            Logging.info("ScanPluginsInDirectoryTask: Looking for plugins in local directory: {0}", uri.toString());
            result = PluginHandler.getPluginJarsInDirectory(new File(uri));
            status = 200;
        } catch (IOException e) {
            status = 500;
            exception = e;
        }
    }
}
