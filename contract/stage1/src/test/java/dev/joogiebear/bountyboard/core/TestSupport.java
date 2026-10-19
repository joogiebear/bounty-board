package dev.joogiebear.bountyboard.core;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shared fakes for the core tests. */
final class TestSupport {

    static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    static final UUID KIM = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    static final UUID ZED = UUID.fromString("00000000-0000-0000-0000-00000000000d");
    static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private TestSupport() {}

    static final class FakeEconomy implements Economy {
        final Map<UUID, Long> balances = new HashMap<>();
        int withdrawCalls;
        int depositCalls;

        FakeEconomy set(UUID player, long amount) {
            balances.put(player, amount);
            return this;
        }

        @Override
        public long balance(UUID player) {
            return balances.getOrDefault(player, 0L);
        }

        @Override
        public boolean withdraw(UUID player, long amount) {
            withdrawCalls++;
            long have = balance(player);
            if (have < amount) {
                return false;
            }
            balances.put(player, have - amount);
            return true;
        }

        @Override
        public void deposit(UUID player, long amount) {
            depositCalls++;
            balances.put(player, balance(player) + amount);
        }
    }

    static final class MemoryStore implements BountyStore {
        StoreData data = new StoreData(List.of(), List.of(), 1);
        int saves;
        int loads;

        MemoryStore() {}

        MemoryStore(StoreData initial) {
            this.data = initial;
        }

        @Override
        public StoreData load() {
            loads++;
            return data;
        }

        @Override
        public void save(StoreData newData) {
            saves++;
            data = newData;
        }
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        void set(Instant instant) {
            now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
