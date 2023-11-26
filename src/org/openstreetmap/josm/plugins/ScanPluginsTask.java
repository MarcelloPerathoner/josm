// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.io.CachedFile;

/**
 * Scans a plugin repository.
 * <p>
 * Scans a plugin repository and returns a list of {@link PluginInformation}.
 * Subclasses of this class implement scanning of repositories hosted on HTTP, GitHub,
 * and in local directories.
 * <p>
 * We also implement {@code Supplier<>} so we can function as supplier for a
 * {@code CompletableFuture<>}. The {@code Supplier<>::get} returns {@code this} to
 * save us the trouble of a separate value-holding return class.
 * <p>
 * Wait for result, then process it:
 * <pre>{@code
 * ScanPluginsTask task = create(uri);
 * CompletableFuture<ScanPluginsTask> future = CompletableFuture.supplyAsync(task);
 * // do some lenthy work here ...
 * try {
 *     task = future.join();
 *     task.getPluginInformations();
 * } catch(CancellationException | InterruptedException e) {
 *     // report or rethrow
 * }}</pre>
 *
 * Process results asynchronously:
 *
 * <pre>{@code
 * CompletableFuture.supplyAsync(ScanPluginsTask.create(uri))
 *     .thenAccept(task -> doSomethingWith(task.getPluginInformations()));
 * }
 * </pre>
 */

public abstract class ScanPluginsTask implements Runnable, Supplier<ScanPluginsTask> {
    /** The resource URI.<p>Note: we need an URI because an URL does not grok {@code github://owner/repo} */
    protected URI uri;
    protected Collection<PluginInformation> result = new ArrayList<>();
    protected int status = 0;
    protected Exception exception = null;

    /**
     * Factory method for plugin repository scanners.
     */
    public static ScanPluginsTask create(URI uri) {
        if ("http".equals(uri.getScheme()))
            return new ScanPluginsOnHttpTask(uri);
        if ("https".equals(uri.getScheme()))
            return new ScanPluginsOnHttpTask(uri);
        if ("github".equals(uri.getScheme()))
            return new ScanPluginsOnGithubTask(uri);
        if ("file".equals(uri.getScheme()))
            return new ScanPluginsInDirectoryTask(uri);
        return null;
    }

    /**
     * Scans all configured plugin repositories.
     * <p>
     * This function scans all configured plugin repositories and returns a list of
     * futures, one per repository.
     */
    public static List<CompletableFuture<ScanPluginsTask>> supplyAsync() {
        Collection<URI> repositories = Preferences.main().getOnlinePluginSites().stream().map(s -> {
            try {
                return new URI(s);
            } catch (URISyntaxException ex) {
                return null;
            }
        }).filter(o -> o != null).toList();

        List<ScanPluginsTask> taskList = repositories.stream()
            .map(ScanPluginsTask::create).filter(o -> o != null).toList();
        return taskList.stream().map(CompletableFuture::supplyAsync).toList();
    }

    /**
     * Joins running tasks
     * @see #supplyAsync
     */
    public static List<ScanPluginsTask> join(Collection<CompletableFuture<ScanPluginsTask>> taskList) {
        return taskList.stream().map(task -> task.join()).toList();
    }

    /**
     * Constructor
     * @param uri must have the format {@code github://owner/repo}
     */
    protected ScanPluginsTask(URI uri) {
        this.uri = uri;
    }

    @Override
    public ScanPluginsTask get() {
        run();
        return this;
    }

    public Collection<PluginInformation> getPluginInformations() {
        return result.stream()
            .filter(Predicate.not(PluginHandler::isDeprecated))
            .filter(Predicate.not(PluginHandler::isUnmaintained))
            .toList();
    }

    public URI getUri() {
        return uri;
    }

    public Exception getException() {
        return exception;
    }

    public int getStatus() {
        return status;
    }

    /**
     * Downloads and scans the manifest of a repository
     * <p>
     * The manifest file is cached on disk.
     *
     * @param uri the site URI
     * @return the list of infos
     */
    protected Collection<PluginInformation> scanRepositoryManifest(URI uri) {
        try (CachedFile cf = new CachedFile(uri.toString()); InputStream is = cf.getInputStream()) {
            status = 200;
            return new PluginListParser().parse(is, uri);
        } catch (IOException | PluginListParseException e) {
            status = 500;
            exception = e;
        }
        return Collections.emptyList();
    }
}
