package qupath.ext.projecttabplus.core;

import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.GeneralTools;

/**
 * Moves a file to the operating system trash through {@link GeneralTools#moveToTrash}, which
 * carries QuPath's Windows event-thread workaround. Never deletes permanently.
 */
public final class FileTrash {

    private static final Logger logger = LoggerFactory.getLogger(FileTrash.class);

    /** What happened. */
    public enum Outcome {
        MOVED, UNSUPPORTED, FAILED
    }

    /**
     * @param outcome what happened
     * @param detail a short reason for the dialog
     */
    public record Result(Outcome outcome, String detail) {
    }

    private FileTrash() {
    }

    /** @return true when this system reports a trash that can take files */
    public static boolean isSupported() {
        try {
            return Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * @param path the file to move
     * @return the result; UNSUPPORTED when the system has no trash, FAILED when the move did not
     *         happen (the file may be in use)
     */
    public static Result moveToTrash(Path path) {
        if (!isSupported()) {
            return new Result(Outcome.UNSUPPORTED, "the operating system reports no trash support");
        }
        try {
            boolean moved = GeneralTools.moveToTrash(path.toFile());
            if (moved && !Files.exists(path)) {
                return new Result(Outcome.MOVED, "");
            }
            if (moved) {
                // The API said yes but the file is still here; treat it as not moved.
                logger.warn("moveToTrash reported success but {} still exists", path);
            }
            return new Result(Outcome.FAILED, "the file is in use by another program or could not be moved");
        } catch (RuntimeException e) {
            logger.warn("Could not move {} to trash: {}", path, e.getMessage());
            return new Result(Outcome.FAILED, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }
}
