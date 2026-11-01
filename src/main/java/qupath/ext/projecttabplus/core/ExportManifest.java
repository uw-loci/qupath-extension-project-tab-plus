package qupath.ext.projecttabplus.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import qupath.lib.common.GeneralTools;

/**
 * The provenance record a GeoJSON export writes beside its files: versions, timestamp, options,
 * folder, and one line per written file. The image name is recorded only while QuPath is not
 * masking names; a masked export lists the entry ID and file name alone.
 *
 * @param qupathVersion the QuPath version
 * @param extensionVersion this extension's version
 * @param timestamp ISO-8601 time the export finished
 * @param folder the output folder as written
 * @param compress whether files were gzipped
 * @param prettyPrint whether the JSON was indented
 * @param excludeMeasurements whether per-object measurements were dropped
 * @param masked whether image names were masked, and so omitted here
 * @param files one record per file written
 */
public record ExportManifest(String qupathVersion, String extensionVersion, String timestamp, String folder,
                             boolean compress, boolean prettyPrint, boolean excludeMeasurements, boolean masked,
                             List<FileRecord> files) {

    /** The manifest file name, overwritten on every export into the same folder. */
    public static final String FILE_NAME = "export-manifest.json";

    /** Source of a file's objects: the saved data file. */
    public static final String SOURCE_SAVED = "saved";

    /** Source of a file's objects: the open viewer's unsaved objects. */
    public static final String SOURCE_LIVE = "live-unsaved";

    /**
     * One written file.
     *
     * @param entryId the project entry ID
     * @param fileName the file name inside the folder
     * @param imageName the image name, or null when names were masked
     * @param source {@link #SOURCE_SAVED} or {@link #SOURCE_LIVE}
     * @param objectCount objects written
     */
    public record FileRecord(String entryId, String fileName, String imageName, String source, int objectCount) {
    }

    public ExportManifest {
        files = Collections.unmodifiableList(new ArrayList<>(files));
    }

    /** @return the current time as ISO-8601 with offset */
    public static String now() {
        return OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /** @return this extension's version from the jar manifest, or {@code unknown} outside a jar */
    public static String currentExtensionVersion() {
        String v = GeneralTools.getPackageVersion(ExportManifest.class);
        return v == null || v.isBlank() ? "unknown" : v;
    }

    /** @return the manifest as JSON */
    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("qupathVersion", qupathVersion);
        root.addProperty("extensionVersion", extensionVersion);
        root.addProperty("timestamp", timestamp);
        root.addProperty("folder", folder);
        JsonObject options = new JsonObject();
        options.addProperty("compress", compress);
        options.addProperty("prettyPrint", prettyPrint);
        options.addProperty("excludeMeasurements", excludeMeasurements);
        options.addProperty("imageNamesMasked", masked);
        root.add("options", options);
        JsonArray arr = new JsonArray();
        for (FileRecord f : files) {
            JsonObject o = new JsonObject();
            o.addProperty("entryId", f.entryId());
            o.addProperty("fileName", f.fileName());
            if (!masked && f.imageName() != null) {
                o.addProperty("imageName", f.imageName());
            }
            o.addProperty("source", f.source());
            o.addProperty("objectCount", f.objectCount());
            arr.add(o);
        }
        root.add("files", arr);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(root) + System.lineSeparator();
    }

    /**
     * @param json the manifest text
     * @return the manifest
     * @throws IllegalArgumentException when the text is not a manifest
     */
    public static ExportManifest parse(String json) {
        JsonElement rootEl = JsonParser.parseString(json);
        if (!rootEl.isJsonObject()) {
            throw new IllegalArgumentException("manifest root is not an object");
        }
        JsonObject root = rootEl.getAsJsonObject();
        JsonObject options = root.has("options") && root.get("options").isJsonObject()
                ? root.getAsJsonObject("options") : new JsonObject();
        List<FileRecord> files = new ArrayList<>();
        if (root.has("files") && root.get("files").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("files")) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject o = el.getAsJsonObject();
                files.add(new FileRecord(string(o, "entryId"), string(o, "fileName"), string(o, "imageName"),
                        string(o, "source"), o.has("objectCount") ? o.get("objectCount").getAsInt() : 0));
            }
        }
        return new ExportManifest(string(root, "qupathVersion"), string(root, "extensionVersion"),
                string(root, "timestamp"), string(root, "folder"),
                bool(options, "compress"), bool(options, "prettyPrint"), bool(options, "excludeMeasurements"),
                bool(options, "imageNamesMasked"), files);
    }

    /**
     * Write the manifest into a folder as {@link #FILE_NAME}, through a temp file.
     *
     * @param folderPath the output folder
     * @return the manifest path
     * @throws IOException when it cannot be written
     */
    public Path write(Path folderPath) throws IOException {
        Path target = folderPath.resolve(FILE_NAME);
        Path tmp = folderPath.resolve(FILE_NAME + ".tmp");
        Files.writeString(tmp, toJson(), StandardCharsets.UTF_8);
        BulkJobs.replace(tmp, target);
        return target;
    }

    private static String string(JsonObject o, String key) {
        JsonElement el = o.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsString() : null;
    }

    private static boolean bool(JsonObject o, String key) {
        JsonElement el = o.get(key);
        return el != null && el.isJsonPrimitive() && el.getAsBoolean();
    }
}
