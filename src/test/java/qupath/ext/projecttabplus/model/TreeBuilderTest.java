package qupath.ext.projecttabplus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import qupath.ext.projecttabplus.model.GroupingKey.Builtin;

class TreeBuilderTest {

    private static List<ImageRow> images() {
        return List.of(
                new FakeEntry("1", "a_HE.svs").meta("Patient", "12").meta("Stain", "HE").row(),
                new FakeEntry("2", "a_PPM.tif").meta("Patient", "12").meta("Stain", "PPM").row(),
                new FakeEntry("3", "b_HE.svs").meta("Patient", "13").meta("Stain", "HE").row(),
                new FakeEntry("4", "c.svs").row());
    }

    private static GroupingSpec spec(String... keys) {
        return new GroupingSpec(List.of(keys).stream().map(GroupingKey::metadata).toList());
    }

    @Test
    void flatSpecKeepsProjectOrder() {
        TreeBuilder.BuiltTree t = TreeBuilder.build(images(), GroupingSpec.FLAT, null);
        assertEquals(4, t.root().children().size());
        assertEquals(4, t.shown());
        assertEquals("1", ((ImageRow) t.root().children().get(0)).id());
        assertEquals("4", ((ImageRow) t.root().children().get(3)).id());
        assertEquals("", t.pathById().get("1"));
    }

    @Test
    void twoLevelsBucketOncePerLevelWithCounts() {
        TreeBuilder.BuiltTree t = TreeBuilder.build(images(), spec("Patient", "Stain"), null);
        List<Row> top = t.root().children();
        assertEquals(3, top.size());
        GroupRow p12 = (GroupRow) top.get(0);
        assertEquals("12", p12.value());
        assertEquals(2, p12.imageCount());
        assertEquals(2, p12.children().size());
        GroupRow he = (GroupRow) p12.children().get(0);
        assertEquals("HE", he.value());
        assertEquals(1, he.imageCount());
        assertInstanceOf(ImageRow.class, he.children().get(0));
        assertEquals(he.path(), t.pathById().get("1"));
        assertTrue(he.path().contains("12"));
    }

    @Test
    void unassignedGroupIsLast() {
        TreeBuilder.BuiltTree t = TreeBuilder.build(images(), spec("Patient"), null);
        List<Row> top = t.root().children();
        assertEquals(GroupingKey.UNASSIGNED, ((GroupRow) top.get(top.size() - 1)).value());
        assertEquals(1, ((GroupRow) top.get(top.size() - 1)).imageCount());
    }

    @Test
    void filterDropsEmptyGroups() {
        TreeBuilder.BuiltTree t = TreeBuilder.build(images(), spec("Patient", "Stain"),
                r -> "PPM".equals(r.info().metadata().get("Stain")));
        assertEquals(1, t.shown());
        assertEquals(4, t.total());
        assertEquals(1, t.root().children().size());
        GroupRow p12 = (GroupRow) t.root().children().get(0);
        assertEquals(1, p12.children().size());
        assertEquals("PPM", ((GroupRow) p12.children().get(0)).value());
    }

    @Test
    void builtinLevelsUseNameFileAndId() {
        GroupingSpec byName = new GroupingSpec(List.of(GroupingKey.builtin(Builtin.IMAGE_NAME)));
        TreeBuilder.BuiltTree t = TreeBuilder.build(images(), byName, null);
        assertEquals(4, t.root().children().size());
        assertEquals("a_HE.svs", ((GroupRow) t.root().children().get(0)).value());

        GroupingSpec byType = new GroupingSpec(List.of(GroupingKey.builtin(Builtin.IMAGE_TYPE)));
        t = TreeBuilder.build(images(), byType, null);
        assertEquals(1, t.root().children().size());
        assertEquals(GroupingKey.UNASSIGNED, ((GroupRow) t.root().children().get(0)).value());
    }

    @Test
    void groupCountsPerLevel() {
        assertEquals(List.of(3, 4), TreeBuilder.groupCounts(images(), spec("Patient", "Stain")));
        assertEquals(List.of(), TreeBuilder.groupCounts(images(), GroupingSpec.FLAT));
    }

    @Test
    void sortKeyMapsToBuiltins() {
        assertEquals(GroupingKey.builtin(Builtin.IMAGE_NAME), GroupingKey.fromSortKey("SORT_KEY[IMAGE_NAME]"));
        assertEquals(GroupingKey.builtin(Builtin.FILE_NAME), GroupingKey.fromSortKey("SORT_KEY[URI]"));
        assertEquals(GroupingKey.builtin(Builtin.ENTRY_ID), GroupingKey.fromSortKey("SORT_KEY[ENTRY_ID]"));
        assertEquals(GroupingKey.metadata("Patient"), GroupingKey.fromSortKey("Patient"));
        assertTrue(GroupingSpec.fromSortKey(null).isFlat());
        assertTrue(GroupingSpec.fromSortKey("  ").isFlat());
    }

    @Test
    void maskedNamesHideNameLevels() {
        GroupingSpec s = new GroupingSpec(List.of(GroupingKey.builtin(Builtin.IMAGE_NAME),
                GroupingKey.metadata("Stain"), GroupingKey.builtin(Builtin.FILE_NAME)));
        assertEquals(List.of(GroupingKey.metadata("Stain")), s.effective(true).levels());
        assertEquals(3, s.effective(false).levels().size());
    }

    @Test
    void serializedRoundTrip() {
        GroupingKey k = GroupingKey.builtin(Builtin.IMAGE_TYPE);
        assertEquals("builtin:imageType", k.serialized());
        assertEquals(k, GroupingKey.parse("builtin:imageType"));
        assertEquals(GroupingKey.metadata("builtin:nope"), GroupingKey.parse("builtin:nope"));
        assertEquals(GroupingKey.metadata("Stain"), GroupingKey.parse("Stain"));
    }

    @Test
    void fileNameLabelsMatchTheBuiltInTab() throws Exception {
        assertEquals(EntryInfo.UNDEFINED, EntryInfo.fileNameFromUris(List.of()));
        assertEquals("slide.svs", EntryInfo.fileNameFromUris(List.of(new java.net.URI("file:///data/slide.svs"))));
        assertEquals("[remote] host/x/y.tif", EntryInfo.fileNameFromUris(List.of(new java.net.URI("http://host/x/y.tif"))));
        assertEquals(EntryInfo.MULTIPLE_URIS, EntryInfo.fileNameFromUris(List.of(
                new java.net.URI("file:///a.tif"), new java.net.URI("file:///b.tif"))));
    }
}
