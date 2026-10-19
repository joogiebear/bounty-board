package dev.joogiebear.bountyboard.core;

import static dev.joogiebear.bountyboard.core.HeldSupport.A;
import static dev.joogiebear.bountyboard.core.HeldSupport.B;
import static dev.joogiebear.bountyboard.core.HeldSupport.C;
import static dev.joogiebear.bountyboard.core.HeldSupport.D;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

class HeldFileStoreTest {

    @TempDir
    Path dir;

    @BeforeEach
    void up() { MockBukkit.mock(); }

    @AfterEach
    void down() { MockBukkit.unmock(); }

    private static final Instant MILLIS = Instant.ofEpochMilli(1_767_225_600_123L);

    private static StoreData sample() {
        Bounty active = new Bounty(1, B, "Bob", A, "Alice", 100, MILLIS, MILLIS.plusSeconds(3600), BountyStatus.ACTIVE, null, null);
        Bounty claimed = new Bounty(2, C, "Carol", A, "Alice", 5_000_000_000L, MILLIS.plusMillis(7), MILLIS.plusSeconds(60),
                BountyStatus.CLAIMED, D, MILLIS.plusMillis(999));
        Bounty expired = new Bounty(3, C, "Carol", D, "Dave", 1, MILLIS, MILLIS.plusMillis(1), BountyStatus.EXPIRED, null, MILLIS.plusMillis(1));
        Bounty cancelled = new Bounty(4, D, "Dave", B, "Bob", Long.MAX_VALUE, MILLIS, MILLIS.plusSeconds(5), BountyStatus.CANCELLED, null, MILLIS.plusSeconds(2));
        return new StoreData(List.of(active, claimed, expired, cancelled), List.of(
                new Notice(A, NoticeType.CLAIMED, 2, 5_000_000_000L, "Dave"),
                new Notice(D, NoticeType.EXPIRED_REFUNDED, 3, 1, "Carol"),
                new Notice(A, NoticeType.EXPIRED_REFUNDED, 1, 100, "Bob")), 42);
    }

    private static final StoreData EMPTY = new StoreData(List.of(), List.of(), 1);

    private List<Path> files() throws IOException {
        try (Stream<Path> s = Files.list(dir)) { return s.sorted().toList(); }
    }

    @Test
    void missingFileGivesEmptyData() {
        FileBountyStore store = new FileBountyStore(dir.resolve("nope.yml"));
        assertEquals(EMPTY, store.load());
    }

    @Test
    void roundTripPreservesEveryField() {
        Path file = dir.resolve("data.yml");
        new FileBountyStore(file).save(sample());
        StoreData back = new FileBountyStore(file).load();
        assertEquals(sample(), back);
        assertNull(back.bounties().get(0).claimedBy());
        assertNull(back.bounties().get(0).resolvedAt());
        assertEquals(MILLIS.plusMillis(999), back.bounties().get(1).resolvedAt());
        assertEquals(Long.MAX_VALUE, back.bounties().get(3).amount());
        assertEquals(42, back.nextId());
    }

    @Test
    void emptyDataRoundTripKeepsNextId() {
        Path file = dir.resolve("data.yml");
        new FileBountyStore(file).save(new StoreData(List.of(), List.of(), 77));
        assertEquals(new StoreData(List.of(), List.of(), 77), new FileBountyStore(file).load());
    }

    @Test
    void unicodeAndAwkwardNamesSurvive() {
        String[] names = {"Zoë", "日本語プレイヤー", "😀Gamer😀", "a: b", "quote\"s'n", "#hash", "- dash", "multi\nline", "  spaced  ", "{braces}", "yes", "123", "null", "~"};
        List<Bounty> bounties = new ArrayList<>();
        List<Notice> notices = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            bounties.add(new Bounty(i + 1, new UUID(5, i), names[i], new UUID(6, i), names[names.length - 1 - i], 10 + i, MILLIS, MILLIS.plusSeconds(60),
                    BountyStatus.ACTIVE, null, null));
            notices.add(new Notice(new UUID(6, i), NoticeType.CLAIMED, i + 1, 10 + i, names[i]));
        }
        StoreData data = new StoreData(bounties, notices, names.length + 1);
        Path file = dir.resolve("data.yml");
        new FileBountyStore(file).save(data);
        assertEquals(data, new FileBountyStore(file).load());
    }

    @Test
    void saveCreatesParentDirectoriesAndLeavesNoTempFiles() throws IOException {
        Path file = dir.resolve("a").resolve("b").resolve("data.yml");
        FileBountyStore store = new FileBountyStore(file);
        store.save(sample());
        store.save(sample());
        store.save(new StoreData(List.of(), List.of(), 3));
        assertTrue(Files.isRegularFile(file));
        try (Stream<Path> s = Files.list(file.getParent())) {
            assertEquals(List.of(file.getFileName()), s.map(Path::getFileName).toList(), "no temp or backup files may be left behind");
        }
        assertEquals(new StoreData(List.of(), List.of(), 3), store.load());
    }

    @Test
    void saveOverwritesPreviousContentCompletely() {
        Path file = dir.resolve("data.yml");
        FileBountyStore store = new FileBountyStore(file);
        store.save(sample());
        StoreData small = new StoreData(List.of(sample().bounties().get(0)), List.of(), 2);
        store.save(small);
        assertEquals(small, store.load());
    }

    @Test
    void largeDataRoundTrips() {
        List<Bounty> bounties = new ArrayList<>();
        List<Notice> notices = new ArrayList<>();
        for (int i = 1; i <= 5000; i++) {
            bounties.add(new Bounty(i, new UUID(1, i), "Target" + i, new UUID(2, i), "Poster" + i, i * 1000L, MILLIS.plusMillis(i), MILLIS.plusSeconds(i),
                    i % 3 == 0 ? BountyStatus.CLAIMED : BountyStatus.ACTIVE, i % 3 == 0 ? new UUID(3, i) : null, i % 3 == 0 ? MILLIS.plusMillis(i + 5) : null));
            if (i % 2 == 0) notices.add(new Notice(new UUID(2, i), NoticeType.EXPIRED_REFUNDED, i, i * 1000L, "Target" + i));
        }
        StoreData data = new StoreData(bounties, notices, 5001);
        Path file = dir.resolve("big.yml");
        new FileBountyStore(file).save(data);
        assertEquals(data, new FileBountyStore(file).load());
    }

    private void assertQuarantined(Path file, String name) throws IOException {
        StoreData data = new FileBountyStore(file).load();
        assertEquals(EMPTY, data);
        assertFalse(Files.exists(file), "the bad file must be moved away");
        List<Path> moved = files();
        assertEquals(1, moved.size());
        String n = moved.get(0).getFileName().toString();
        assertTrue(n.matches(java.util.regex.Pattern.quote(name) + "\\.corrupt-\\d+"), n);
    }

    @Test
    void syntaxErrorIsRenamedAndEmptyDataReturned() throws IOException {
        Path file = dir.resolve("data.yml");
        Files.writeString(file, "bounties: [unclosed\n  - {{{ : ::\n\t\tbad");
        long before = System.currentTimeMillis();
        assertQuarantined(file, "data.yml");
        String n = files().get(0).getFileName().toString();
        long stamp = Long.parseLong(n.substring(n.lastIndexOf('-') + 1));
        assertTrue(stamp >= before - 5_000 && stamp <= System.currentTimeMillis() + 5_000, "epoch millis expected, got " + stamp);
    }

    @Test
    void binaryGarbageIsQuarantinedWithoutThrowing() throws IOException {
        Path file = dir.resolve("data.yml");
        byte[] junk = new byte[4096];
        new java.util.Random(7).nextBytes(junk);
        junk[0] = (byte) 0xFF;
        junk[1] = 0x00;
        Files.write(file, junk);
        StoreData data = new FileBountyStore(file).load();
        assertEquals(EMPTY, data);
    }

    @Test
    void afterQuarantineTheStoreIsUsableAgain() throws IOException {
        Path file = dir.resolve("data.yml");
        Files.writeString(file, "bounties: [unclosed\n  - {{{ : ::");
        FileBountyStore store = new FileBountyStore(file);
        assertEquals(EMPTY, store.load());
        assertEquals(EMPTY, store.load(), "the moved-away file is not quarantined twice");
        assertEquals(1, files().size());
        store.save(sample());
        assertEquals(sample(), store.load());
        assertEquals(2, files().size());
    }

    @Test
    void emptyFileNeverThrows() throws IOException {
        Path file = dir.resolve("data.yml");
        Files.writeString(file, "");
        StoreData data = new FileBountyStore(file).load();
        assertNotNull(data);
        assertEquals(1, data.nextId());
        assertTrue(data.bounties().isEmpty());
        assertTrue(data.notices().isEmpty());
    }

    @Test
    void truncatedFileNeverThrowsAndStoreRecovers() throws IOException {
        Path file = dir.resolve("data.yml");
        FileBountyStore store = new FileBountyStore(file);
        store.save(sample());
        String text = Files.readString(file, StandardCharsets.UTF_8);
        Files.writeString(file, text.substring(0, text.length() / 2));
        StoreData data = store.load();
        assertNotNull(data);
        assertNotNull(data.bounties());
        assertNotNull(data.notices());
        assertTrue(data.nextId() >= 1);
        store.save(sample());
        assertEquals(sample(), store.load());
    }

    @Test
    void wrongShapeFileNeverThrows() throws IOException {
        for (String text : new String[] {"just a string", "- 1\n- 2\n", "bounties: nope\nnotices: 5\nnext-id: abc\n", "bounties:\n  - id: x\n    amount: y\n"}) {
            Path file = dir.resolve("shape.yml");
            Files.deleteIfExists(file);
            Files.writeString(file, text);
            StoreData data = new FileBountyStore(file).load();
            assertNotNull(data, text);
            assertNotNull(data.bounties(), text);
            assertNotNull(data.notices(), text);
            assertTrue(data.nextId() >= 1, text);
        }
    }
}
