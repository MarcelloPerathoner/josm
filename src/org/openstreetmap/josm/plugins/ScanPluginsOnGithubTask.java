// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.jar.Attributes;

import org.openstreetmap.josm.data.Version;
import org.openstreetmap.josm.tools.HttpClient;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

public class ScanPluginsOnGithubTask extends ScanPluginsTask {
    static final String BDURL = "browser_download_url";

    /**
     * Constructor
     * @param uri must have the format {@code github://owner/repo}
     */
    public ScanPluginsOnGithubTask(URI uri) {
        super(uri);
    }

    /**
     * Generate a list of plugin jars in a github repository
     * <p>
     * This function finds the plugin jars published as artifacts in the latest release.
     * <p>
     * Builds the list of plugin jars from the json response at
     * {@code https://api.github.com/repos/<owner>/<repo>/releases/latest}
     */
    @Override
    public void run() {
        try {
            final URL url = new URL("https", "api.github.com", "/repos/" + uri.getHost() + uri.getPath() + "/releases/latest");
            final HttpClient connection = HttpClient.create(url).useCache(false);
            final HttpClient.Response response = connection.connect();

            try (JsonReader reader = Json.createReader(response.getContentReader())) {
                JsonObject object = reader.readObject();
                JsonArray assets = object.getJsonArray("assets");
                // first look for a global "MANIFEST" asset
                for (JsonObject o : assets.getValuesAs(JsonObject.class)) {
                    String name = o.getString("name");
                    URI bdUrl = new URI(o.getString(BDURL));
                    if ("MANIFEST".equals(name)) {
                        result.addAll(scanRepositoryManifest(bdUrl));
                    }
                }
                // then look for "<pluginname>-updatesite" assets
                for (JsonObject o : assets.getValuesAs(JsonObject.class)) {
                    String name = o.getString("name");
                    URI bdUrl = new URI(o.getString(BDURL));
                    if (name.endsWith(".MANIFEST")) {
                        result.addAll(scanRepositoryManifest(bdUrl));
                    }
                }
                // as last resort scan the available jar files
                // there will be no metadata, just the name and url
                // fake the main version, assume the current version or it won't download
                Attributes attr = new Attributes();
                attr.put(new Attributes.Name("Plugin-Mainversion"), "" + Version.getInstance().getVersion());
                for (JsonObject o : assets.getValuesAs(JsonObject.class)) {
                    String name = o.getString("name");
                    String bdUrl = o.getString(BDURL);
                    if (name.endsWith(".jar") && !name.endsWith("-sources.jar") && !name.endsWith("-javadoc.jar")) {
                        try {
                            result.add(new PluginInformation(
                                attr, name.substring(0, name.length() - 4), new URI(bdUrl)));
                        } catch (URISyntaxException e) {
                            // nothing to do here
                        }
                    }
                }
            }
        } catch (IOException | URISyntaxException | NullPointerException e) {
            this.exception = e;
        }
    }
}
