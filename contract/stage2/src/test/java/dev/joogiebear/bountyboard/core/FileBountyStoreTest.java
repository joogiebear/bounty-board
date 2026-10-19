package dev.joogiebear.bountyboard.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileBountyStoreTest {

    @TempDir
    Path dir;

    static final UUID A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID C = UUID.fromString("33333333-3333-3333-3333-333333333333");

    static Bounty active(int id, String targetName, String posterName, long amount) {
        return new Bounty(id, B, targetName, A, posterName, amount, Instant.ofEpochMilli(1_760_000_000_123L),
                Instant.ofEpochMilli(1_760_086_400_123L), BountyStatus.ACTIVE, null, null);
    }

    static StoreData sample() {
        Bounty claimed = new Bounty(2, C, "Kim", A, "Alex", 250, Instant.ofEpochMilli(1_759_000_000_000L),
                Instant.ofEpochMilli(1_759_086_400_000L), BountyStatus.CLAIMED, B, Instant.ofEpochMilli(1_759_000_500_999L));
        Bounty expired = new Bounty(3, B, "Steve", C, "Zed", 10, Instant.ofEpochMilli(1_758_000_000_000L),
                Instant.ofEpochMilli(1_758_003_600_000L), BountyStatus.EXPIRED, null, Instant.ofEpochMilli(1_758_003_600_001L));
        Bounty cancelled = new Bounty(4, C, "Kim", B, "Steve", 999_999_999_999L, Instant.ofEpochMilli(1_757_000_000_000L),
                Instant.ofEpochMilli(1_757_086_400_000L), BountyStatus.CANCELLED, null, Instant.ofEpochMilli(1_757_000_100_000L));
        List<Notice> notices = List.of(
                new Notice(A, NoticeType.CLAIMED, 2, 250, "Kim"),
                new Notice(C, NoticeType.EXPIRED_REFUNDED, 3, 10, "Steve"),
                new Notice(A, NoticeType.EXPIRED_REFUNDED, 1, 100, "yes"));
        return new StoreData(List.of(active(1, "Steve", "Alex", 100), claimed, expired, cancelled), notices, 5);
    }

    @Test
    void aMissingFileLoadsAsEmpty() {
        FileBountyStore store = new FileBountyStore(dir.resolve("data.yml"));
        assertEquals(new StoreData(List.of(), List.of(), 1), store.load());
    }

    @Test
    void roundTripsEveryField() {
        FileBountyStore store = new FileBountyStore(dir.resolve("data.yml"));
        StoreData data = sample();
        store.save(data);
        assertEquals(data, new FileBountyStore(dir.resolve("data.yml")).load());
    }

    @Test
    void roundTripsAwkwardNames() {
        List<String> names = List.of("yes", "no", "123", "#hash", ": colon", "a: b", "quote\"s", "it's", "  padded  ", "ünïcödé", "multi word", "true", "null", "~", "-dash", "[x]", "{y}");
        List<Bounty> bounties = new ArrayList<>();
        List<Notice> notices = new ArrayList<>();
        int id = 1;
        for (String n : names) {
            bounties.add(new Bounty(id, B, n, A, n, 10 + id, Instant.ofEpochMilli(1_000L * id), Instant.ofEpochMilli(2_000L * id), BountyStatus.ACTIVE, null, null));
            notices.add(new Notice(A, NoticeType.CLAIMED, id, id, n));
            id++;
        }
        StoreData data = new StoreData(bounties, notices, id);
        FileBountyStore store = new FileBountyStore(dir.resolve("names.yml"));
        store.save(data);
        assertEquals(data, store.load());
    }

    @Test
    void keepsNullClaimedByAndResolvedAtNull() {
        FileBountyStore store = new FileBountyStore(dir.resolve("data.yml"));
        store.save(sample());
        Bounty first = store.load().bounties().get(0);
        assertNull(first.claimedBy());
        assertNull(first.resolvedAt());
        assertEquals(BountyStatus.ACTIVE, first.status());
    }

    @Test
    void keepsListOrder() {
        FileBountyStore store = new FileBountyStore(dir.resolve("data.yml"));
        StoreData loaded;
        store.save(sample());
        loaded = store.load();
        assertEquals(List.of(1, 2, 3, 4), loaded.bounties().stream().map(Bounty::id).toList());
        assertEquals(List.of(2, 3, 1), loaded.notices().stream().map(Notice::bountyId).toList());
        assertEquals(5, loaded.nextId());
    }

    @Test
    void createsMissingParentDirectories() {
        Path file = dir.resolve("a").resolve("b").resolve("data.yml");
        new FileBountyStore(file).save(sample());
        assertTrue(Files.exists(file));
    }

    @Test
    void savingAgainReplacesTheContentAndLeavesNoTempFiles() throws IOException {
        Path file = dir.resolve("data.yml");
        FileBountyStore store = new FileBountyStore(file);
        store.save(sample());
        StoreData smaller = new StoreData(List.of(active(9, "Steve", "Alex", 50)), List.of(), 10);
        store.save(smaller);
        assertEquals(smaller, store.load());
        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(List.of("data.yml"), files.map(p -> p.getFileName().toString()).toList());
        }
    }

    @Test
    void aGarbageFileIsMovedAsideAndLoadsAsEmpty() throws IOException {
        Path file = dir.resolve("data.yml");
        Files.writeString(file, "bounties: [unclosed\n  - {{{ not: yaml: at all\n");
        FileBountyStore store = new FileBountyStore(file);
        StoreData loaded = assertDoesNotThrow(store::load);
        assertEquals(new StoreData(List.of(), List.of(), 1), loaded);
        assertFalse(Files.exists(file));
        try (Stream<Path> files = Files.list(dir)) {
            List<String> names = files.map(p -> p.getFileName().toString()).toList();
            assertEquals(1, names.size());
            assertTrue(names.get(0).matches("data\\.yml\\.corrupt-\\d+"), names.get(0));
        }
        // the store works normally afterwards
        store.save(sample());
        assertEquals(sample(), store.load());
    }

    @Test
    void aBinaryFileIsAlsoMovedAside() throws IOException {
        Path file = dir.resolve("data.yml");
        byte[] junk = new byte[256];
        for (int i = 0; i < junk.length; i++) {
            junk[i] = (byte) (i * 7 + 3);
        }
        Files.write(file, junk);
        StoreData loaded = assertDoesNotThrow(() -> new FileBountyStore(file).load());
        assertEquals(new StoreData(List.of(), List.of(), 1), loaded);
    }

    @Test
    void handlesALargeDataSet() {
        List<Bounty> many = new ArrayList<>();
        for (int i = 1; i <= 1000; i++) {
            many.add(active(i, "Steve" + i, "Alex", i));
        }
        StoreData data = new StoreData(many, List.of(), 1001);
        FileBountyStore store = new FileBountyStore(dir.resolve("big.yml"));
        store.save(data);
        assertEquals(data, store.load());
    }
}
