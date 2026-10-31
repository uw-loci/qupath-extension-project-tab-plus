package qupath.ext.projecttabplus.core;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.commands.ProjectCommands;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Loads entry thumbnails on one daemon thread. The first {@code getThumbnail()} read is a disk
 * read, so it happens here and never on the FX thread; a missing thumbnail is built from the
 * server once, and an entry whose server fails is remembered until {@link #clearFailed()}.
 */
public final class ThumbnailLoader {

    private static final Logger logger = LoggerFactory.getLogger(ThumbnailLoader.class);

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("ptp-thumbnail", true));
    private final Map<String, Image> cache = new ConcurrentHashMap<>();
    private final Set<String> failed = ConcurrentHashMap.newKeySet();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    /**
     * @param entryId an entry ID
     * @return the cached thumbnail, or null when not loaded yet
     */
    public Image cached(String entryId) {
        return cache.get(entryId);
    }

    /**
     * @param entryId an entry ID
     * @return true when building a thumbnail for the entry failed
     */
    public boolean hasFailed(String entryId) {
        return failed.contains(entryId);
    }

    /**
     * Load the thumbnail in the background; no-op when cached, failed or already loading.
     *
     * @param entry the entry
     * @param onLoaded called on the FX thread with the entry ID once the image is cached, or when
     *                 the load failed (check {@link #hasFailed})
     */
    public void request(ProjectImageEntry<BufferedImage> entry, Consumer<String> onLoaded) {
        String id = entry.getID();
        if (cache.containsKey(id) || failed.contains(id) || !inFlight.add(id)) {
            return;
        }
        executor.submit(() -> {
            try {
                BufferedImage img = entry.getThumbnail();
                if (img == null) {
                    try (ImageServer<BufferedImage> server = entry.getServerBuilder().build()) {
                        img = ProjectCommands.getThumbnailRGB(server);
                        entry.setThumbnail(img);
                    }
                }
                if (img != null) {
                    cache.put(id, SwingFXUtils.toFXImage(img, null));
                } else {
                    failed.add(id);
                }
            } catch (Exception e) {
                logger.warn("Could not build a thumbnail for {}: {}", entry.getImageName(), e.getMessage());
                failed.add(id);
            } finally {
                inFlight.remove(id);
                Platform.runLater(() -> onLoaded.accept(id));
            }
        });
    }

    /** @param entryId drop the cached thumbnail so the next request reloads it */
    public void invalidate(String entryId) {
        cache.remove(entryId);
        failed.remove(entryId);
    }

    /** Forget which entries failed, so broken thumbnails get one more try. */
    public void clearFailed() {
        failed.clear();
    }

    /** Drop everything (project switch). */
    public void clear() {
        cache.clear();
        failed.clear();
    }

    /** Stop the loader thread. */
    public void shutdown() {
        executor.shutdownNow();
    }
}
