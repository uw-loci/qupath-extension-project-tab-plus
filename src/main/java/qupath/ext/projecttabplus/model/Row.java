package qupath.ext.projecttabplus.model;

/** A value in the Project+ tree: either a group or an image. */
public sealed interface Row permits GroupRow, ImageRow {
}
