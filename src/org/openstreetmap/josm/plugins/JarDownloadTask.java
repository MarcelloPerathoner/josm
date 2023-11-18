// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.gui.progress.swing.ProgMon.IProgMonDisplay;
import org.openstreetmap.josm.tools.HttpClient;
import org.openstreetmap.josm.tools.HttpClient.Response;
import org.openstreetmap.josm.tools.Logging;

/**
 * A task for downloading plugin jars.
 * <p>
 * The downloaded jar file is directly stored in the configured plugin directory with
 * the added extension {@code .new}.
 * <p>
 * This task implements runnable, so it can be run in the current thread:
 * <pre>
 * new JarDownloadTask(pi).run();
 * </pre>
 * or executed by the main worker thread:
 * <pre>
 * MainApplication.worker.submit(new JarDownloadTask(pi));
 * </pre>
 * or executed by any thread you like:
 * <pre>
 * ForkJoinPool.commonPool().submit(new JarDownloadTask(pi));
 * </pre>
 * <p>
 * We also implement {@code Supplier<JarDownloadTask>} so that we can function as
 * supplier for a {@code CompletableFuture<JarDownloadTask>}:
 * <pre>
 * {@code CompletableFuture<JarDownloadTask> future =
 *     CompletableFuture.supplyAsync(new JarDownloadTask(pi));
 * // do some other work here while you wait ...
 * try {
 *     JarDownloadTask task = future.join();
 *     task.getStatus();
 * } catch(CancellationException | InterruptedException e) {
 *     // report or rethrow
 * }}
 * </pre>
 * @implNote
 * The {@code Supplier<>::get} returns
 * {@code this} to save us the trouble of a separate value-holding return class.
 *
 * @since xxx
 */
public class JarDownloadTask implements Runnable, Supplier<JarDownloadTask> {
    public static final String PLUGIN_MIME_TYPES = "application/java-archive, application/zip; q=0.9, application/octet-stream; q=0.5";
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    private final PluginInformation pluginInformation;
    private final File filename;
    private int status = 0;
    private Exception exception = null;
    private IProgMonDisplay progMonDisplay = null;

    /**
     * Start download tasks for the given plugins
     * <p>
     * The tasks run in parallel.
     * <p>Usage example:
     * <pre>{@code
     * List<CompletableFuture<JarDownloadTask>> futureList =
     *     JarDownloadTask.supplyAsync(toDownload);
     * // do some other work here while you wait ...
     * List<JarDownloadTask> taskList = JarDownloadTask.join(futureList);
     * List<PluginInformation> pluginList =
     *     taskList.stream().map(JarDownloadTask::getPluginInformation).toList();
     * }</pre>
     */
    public static List<CompletableFuture<JarDownloadTask>> supplyAsync(Collection<PluginInformation> toDownload) {
        return toDownload.stream().map(JarDownloadTask::new).map(CompletableFuture::supplyAsync).toList();
    }

    public static List<JarDownloadTask> create(Collection<PluginInformation> toDownload) {
        return toDownload.stream().map(JarDownloadTask::new).toList();
    }

    /**
     * Join running tasks
     * @see #supplyAsync
     */
    public static List<JarDownloadTask> join(Collection<CompletableFuture<JarDownloadTask>> taskList) {
        return taskList.stream().map(task -> task.join()).toList();
    }

    public static List<JarDownloadTask> successfulTasks(Collection<JarDownloadTask> taskList) {
        return taskList.stream().filter(task -> task.getStatus() == 200).toList();
    }

    public static List<JarDownloadTask> failedTasks(Collection<JarDownloadTask> taskList) {
        return taskList.stream().filter(task -> task.getStatus() != 200).toList();
    }

    /**
     * Creates the download task
     */
    public JarDownloadTask(PluginInformation pluginInformation) {
        this.pluginInformation = pluginInformation;
        this.filename = new File(
            Preferences.main().getPluginsDirectory(),
            pluginInformation.getName() + ".jar.new"
        );
    }

    @Override
    public JarDownloadTask get() {
        run();
        return this;
    }

    public PluginInformation getPluginInformation() {
        return pluginInformation;
    }

    public File getFilename() {
        return filename;
    }

    public int getStatus() {
        return status;
    }

    public Exception getException() {
        return exception;
    }

    public JarDownloadTask withProgressMonitor(IProgMonDisplay progressMonitor) {
        this.progMonDisplay = progressMonitor;
        return this;
    }

    /**
     * Downloads the jar file.
     */
     @Override
    public void run() {
        try {
            URL url = pluginInformation.getUri().toURL();
            Logging.debug("Download plugin {0} from {1}...", pluginInformation.getName(), url);
            if (progMonDisplay != null) {
                progMonDisplay.setText(tr("Downloading plugin: {0}", pluginInformation.getName()));
                progMonDisplay.setVisible(true);
            }
            if ("https".equals(url.getProtocol()) || "http".equals(url.getProtocol())) {
                HttpClient downloadConnection = HttpClient.create(url).setAccept(PLUGIN_MIME_TYPES);
                Response response = downloadConnection.connect();
                if (progMonDisplay != null && response.getContentLength() != -1) {
                    progMonDisplay.setMaximum((int) response.getContentLength() / 1024);
                    progMonDisplay.setUnit("KB");
                }

                try (InputStream in = response.getContent()) {
                    Files.deleteIfExists(filename.toPath());
                    copyWithProgressMonitor(in);
                }

                /* shorter alternative w/o progress monitor
                try (InputStream in = response.getContent()) {
                    Files.copy(in, filename.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                */
                status = response.getResponseCode();
            } else {
                // file:// URLs where HttpClient doesn't work
                try (InputStream in = url.openStream()) {
                    Files.deleteIfExists(filename.toPath());
                    copyWithProgressMonitor(in);
                }
                status = 200;
            }
        } catch (IOException e) {
            exception = e;
        } finally {
            if (progMonDisplay != null) {
                progMonDisplay.setVisible(false);
            }
        }
    }

    private void copyWithProgressMonitor(InputStream in) throws IOException {
        try (OutputStream out = Files.newOutputStream(
                filename.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            int transferred = 0;
            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer, 0, DEFAULT_BUFFER_SIZE)) >= 0) {
                out.write(buffer, 0, read);
                transferred += read;
                if (progMonDisplay != null) {
                    progMonDisplay.setCurrent(transferred);
                }
            }
        }
    }

    /**
     * Makes sure the plugin directory is there.
     * @return null if successful, an error message if not
     */
    public static String ensurePluginsDirectory() {
        File pluginDir = Preferences.main().getPluginsDirectory();
        if (!pluginDir.exists() && !pluginDir.mkdirs()) {
            String message = tr("Failed to create plugin directory ''{0}''", pluginDir.toString());
            Logging.error(message);
            return message;
        }
        return null;
    }
}
