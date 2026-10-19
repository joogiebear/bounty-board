package dev.joogiebear.bountyboard.core;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shared fakes for the held-out tests. */
public final class HeldSupport {
    public static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    public static final UUID A = new UUID(0, 1);
    public static final UUID B = new UUID(0, 2);
    public static final UUID C = new UUID(0, 3);
    public static final UUID D = new UUID(0, 4);
    public static final UUID E = new UUID(0, 5);

    private HeldSupport() {}

    public static final class MutableClock extends Clock {
        private Instant now;

        public MutableClock(Instant now) { this.now = now; }

        public void advance(Duration d) { now = now.plus(d); }

        public void set(Instant i) { now = i; }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }

        @Override public Clock withZone(ZoneId zone) { return this; }

        @Override public Instant instant() { return now; }
    }

    public static final class MapEconomy implements Economy {
        public final Map<UUID, Long> balances = new HashMap<>();
        public int deposits;
        public int withdrawals;

        public MapEconomy fund(UUID u, long a) { balances.put(u, a); return this; }

        @Override public long balance(UUID p) { return balances.getOrDefault(p, 0L); }

        @Override public boolean withdraw(UUID p, long amount) {
            long b = balance(p);
            if (b < amount) return false;
            balances.put(p, b - amount);
            withdrawals++;
            return true;
        }

        @Override public void deposit(UUID p, long amount) {
            balances.put(p, balance(p) + amount);
            deposits++;
        }
    }

    public static final class MemStore implements BountyStore {
        public StoreData data = new StoreData(List.of(), List.of(), 1);
        public final List<StoreData> saved = new ArrayList<>();

        @Override public StoreData load() { return data; }

        @Override public void save(StoreData d) { saved.add(d); data = d; }

        public StoreData last() { return saved.get(saved.size() - 1); }
    }

    public static final class SB {
        long min = 10, max = 1_000_000;
        int act = 3, defH = 24, maxH = 72, tax = 5, cd = 10, full = 5, pct = 80, purge = 7;

        public SB min(long v) { min = v; return this; }
        public SB max(long v) { max = v; return this; }
        public SB act(int v) { act = v; return this; }
        public SB defH(int v) { defH = v; return this; }
        public SB maxH(int v) { maxH = v; return this; }
        public SB tax(int v) { tax = v; return this; }
        public SB cd(int v) { cd = v; return this; }
        public SB full(int v) { full = v; return this; }
        public SB pct(int v) { pct = v; return this; }
        public SB purge(int v) { purge = v; return this; }

        public Settings build() { return new Settings(min, max, act, defH, maxH, tax, cd, full, pct, purge); }
    }

    public static SB sb() { return new SB(); }

    /** A service over fakes; A-D start with 1,000,000,000 coins. */
    public static final class Fixture {
        public final MutableClock clock = new MutableClock(T0);
        public final MapEconomy eco = new MapEconomy();
        public final MemStore store = new MemStore();
        public final Settings settings;
        public BountyService svc;

        public Fixture() { this(Settings.defaults()); }

        public Fixture(Settings s) {
            settings = s;
            for (UUID u : new UUID[] {A, B, C, D}) eco.fund(u, 1_000_000_000L);
            svc = new BountyService(s, eco, store, clock);
        }

        public Bounty post(UUID poster, UUID target, long amount, Integer hours) {
            CreateResult r = svc.create(poster, name(poster), target, name(target), amount, hours);
            if (r.status() != CreateStatus.OK) throw new AssertionError("create failed: " + r.status());
            return r.bounty();
        }

        public Bounty post(UUID poster, UUID target, long amount) { return post(poster, target, amount, null); }

        public BountyService reload() {
            svc = new BountyService(settings, eco, store, clock);
            return svc;
        }
    }

    public static String name(UUID u) {
        if (u.equals(A)) return "Alice";
        if (u.equals(B)) return "Bob";
        if (u.equals(C)) return "Carol";
        if (u.equals(D)) return "Dave";
        return "P" + u.getLeastSignificantBits();
    }
}
