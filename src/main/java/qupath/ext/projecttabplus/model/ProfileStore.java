package qupath.ext.projecttabplus.model;

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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads and writes {@code profiles.json}: a versioned list of display profiles, per user.
 * Writes go to a sibling temp file then an atomic move, so a crash mid-write cannot truncate
 * the live file. Unknown fields are ignored and bad values fall back to defaults, so a file
 * from a later version still loads.
 */
public final class ProfileStore {

    private static final Logger logger = LoggerFactory.getLogger(ProfileStore.class);

    /** The profile that always exists. */
    public static final String DEFAULT_NAME = "Default";

    /** The schema version this build writes. */
    public static final int VERSION = 1;

    private static final String DIR_NAME = "project-tab-plus";
    private static final String FILE_NAME = "profiles.json";

    private final Path file;
    private final Object lock = new Object();

    /** @param file the profiles file */
    public ProfileStore(Path file) {
        this.file = file;
    }

    /**
     * @param userDir the QuPath user directory
     * @return {@code <userDir>/project-tab-plus/profiles.json}
     */
    public static Path defaultFile(Path userDir) {
        return userDir.resolve(DIR_NAME).resolve(FILE_NAME);
    }

    /** @return the profiles file */
    public Path getFile() {
        return file;
    }

    /**
     * Load the profiles. A missing or unreadable file yields the Default profile alone; the
     * Default profile is inserted at the front when the file lacks it.
     *
     * @return the profiles, Default first
     */
    public List<DisplayProfile> load() {
        List<DisplayProfile> profiles = new ArrayList<>();
        synchronized (lock) {
            if (Files.exists(file)) {
                try {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    profiles.addAll(parse(json));
                } catch (IOException | RuntimeException e) {
                    logger.warn("Could not read {}: {}", file, e.getMessage());
                }
            }
        }
        ensureDefault(profiles);
        return profiles;
    }

    /**
     * Save the profiles, atomically.
     *
     * @param profiles the profiles
     * @throws IOException when the file cannot be written
     */
    public void save(List<DisplayProfile> profiles) throws IOException {
        List<DisplayProfile> copy = new ArrayList<>(profiles);
        ensureDefault(copy);
        String json = toJson(copy);
        synchronized (lock) {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailure) {
                // Network shares and FAT may not support an atomic move; the temp file was
                // written in full before the move, so a plain replace is still safe.
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * @param json the file contents
     * @return the profiles in the file, Default not enforced
     */
    public static List<DisplayProfile> parse(String json) {
        List<DisplayProfile> profiles = new ArrayList<>();
        JsonElement rootEl = JsonParser.parseString(json);
        if (!rootEl.isJsonObject()) {
            throw new IllegalArgumentException("profiles.json root is not an object");
        }
        JsonObject root = rootEl.getAsJsonObject();
        int version = root.has("version") && root.get("version").isJsonPrimitive()
                ? root.get("version").getAsInt() : VERSION;
        if (version > VERSION) {
            logger.warn("profiles.json is version {}, this build writes version {}; unknown settings are ignored",
                    version, VERSION);
        }
        JsonElement arr = root.get("profiles");
        if (arr == null || !arr.isJsonArray()) {
            return profiles;
        }
        for (JsonElement el : arr.getAsJsonArray()) {
            if (!el.isJsonObject()) {
                continue;
            }
            DisplayProfile p = parseProfile(el.getAsJsonObject());
            if (p != null && profiles.stream().noneMatch(q -> q.getName().equals(p.getName()))) {
                profiles.add(p);
            }
        }
        return profiles;
    }

    private static DisplayProfile parseProfile(JsonObject o) {
        String name = stringOrNull(o, "name");
        if (name == null || name.isBlank()) {
            logger.warn("Skipping a profile with no name in profiles.json");
            return null;
        }
        DisplayProfile p = new DisplayProfile(name.trim());
        p.setGrouping(stringList(o, "grouping"));
        p.setFields(stringList(o, "fields"));
        p.setExpandOnLoad(DisplayProfile.ExpandOnLoad.parse(stringOrNull(o, "expandOnLoad"), p.getExpandOnLoad()));
        p.setThumbnailSize(DisplayProfile.ThumbnailSize.parse(stringOrNull(o, "thumbnailSize"), p.getThumbnailSize()));
        p.setIndicators(DisplayProfile.IndicatorStyle.parse(stringOrNull(o, "indicators"), p.getIndicators()));
        return p;
    }

    private static String stringOrNull(JsonObject o, String key) {
        JsonElement el = o.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsString() : null;
    }

    private static List<String> stringList(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        JsonElement el = o.get(key);
        if (el != null && el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray()) {
                if (item.isJsonPrimitive()) {
                    out.add(item.getAsString());
                }
            }
        }
        return out;
    }

    /**
     * @param profiles the profiles
     * @return the file contents
     */
    public static String toJson(List<DisplayProfile> profiles) {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        JsonArray arr = new JsonArray();
        for (DisplayProfile p : profiles) {
            JsonObject o = new JsonObject();
            o.addProperty("name", p.getName());
            JsonArray grouping = new JsonArray();
            p.getGrouping().forEach(grouping::add);
            o.add("grouping", grouping);
            o.addProperty("expandOnLoad", p.getExpandOnLoad().token());
            JsonArray fields = new JsonArray();
            p.getFields().forEach(fields::add);
            o.add("fields", fields);
            o.addProperty("thumbnailSize", p.getThumbnailSize().name());
            o.addProperty("indicators", p.getIndicators().name());
            arr.add(o);
        }
        root.add("profiles", arr);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(root) + System.lineSeparator();
    }

    private static void ensureDefault(List<DisplayProfile> profiles) {
        int idx = -1;
        for (int i = 0; i < profiles.size(); i++) {
            if (DEFAULT_NAME.equals(profiles.get(i).getName())) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            profiles.add(0, new DisplayProfile(DEFAULT_NAME));
        } else if (idx > 0) {
            profiles.add(0, profiles.remove(idx));
        }
    }
}
