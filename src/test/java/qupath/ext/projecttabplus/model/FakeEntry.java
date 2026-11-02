package qupath.ext.projecttabplus.model;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.projects.ResourceManager.Manager;

/** A project entry with no disk behind it, enough for the model and filter tests. */
public final class FakeEntry implements ProjectImageEntry<BufferedImage> {

    private final String id;
    private String name;
    private final Map<String, String> metadata = new LinkedHashMap<>();
    private final List<URI> uris = new ArrayList<>();
    private boolean hasData;
    private Path entryPath;

    public FakeEntry(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public FakeEntry meta(String key, String value) {
        metadata.put(key, value);
        return this;
    }

    public FakeEntry data(boolean has) {
        this.hasData = has;
        return this;
    }

    public FakeEntry uri(URI uri) {
        uris.add(uri);
        return this;
    }

    public FakeEntry entryPath(Path path) {
        this.entryPath = path;
        return this;
    }

    public EntryInfo info() {
        return EntryInfo.from(this, null, uri -> false);
    }

    public ImageRow row() {
        return new ImageRow(info(), this);
    }

    /** A row showing a short name, as the pane builds it while name rules are on. */
    public ImageRow row(String shortName) {
        return new ImageRow(info().withDisplayName(shortName), this);
    }

    @Override
    public String getID() {
        return id;
    }

    @Override
    public void setImageName(String name) {
        this.name = name;
    }

    @Override
    public String getImageName() {
        return name;
    }

    @Override
    public String getOriginalImageName() {
        return name;
    }

    @Override
    public Path getEntryPath() {
        return entryPath;
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public void setDescription(String description) {
    }

    @Override
    public ServerBuilder<BufferedImage> getServerBuilder() {
        return null;
    }

    @Override
    public ImageData<BufferedImage> readImageData() throws IOException {
        throw new IOException("fake");
    }

    @Override
    public void saveImageData(ImageData<BufferedImage> imageData) throws IOException {
        throw new IOException("fake");
    }

    @Override
    public PathObjectHierarchy readHierarchy() throws IOException {
        throw new IOException("fake");
    }

    @Override
    public boolean hasImageData() {
        return hasData;
    }

    @Override
    public String getSummary() {
        return name;
    }

    @Override
    public BufferedImage getThumbnail() {
        return null;
    }

    @Override
    public void setThumbnail(BufferedImage img) {
    }

    @Override
    public Manager<ImageServer<BufferedImage>> getImages() {
        return null;
    }

    @Override
    public Set<String> getTags() {
        return Set.of();
    }

    @Override
    public Map<String, String> getMetadata() {
        return metadata;
    }

    @Override
    public Collection<URI> getURIs() {
        return uris;
    }

    @Override
    public boolean updateURIs(Map<URI, URI> replacements) {
        return false;
    }
}
