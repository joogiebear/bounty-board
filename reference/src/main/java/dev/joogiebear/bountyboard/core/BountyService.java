package dev.joogiebear.bountyboard.core;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** The bounty rules. See DESIGN.md, section "BountyService". */
public class BountyService {

    private record CooldownKey(UUID killer, UUID victim) {}

    private Settings settings;
    private final Economy economy;
    private final BountyStore store;
    private final Clock clock;
    private final List<Bounty> bounties = new ArrayList<>();
    private final List<Notice> notices = new ArrayList<>();
    private final Map<CooldownKey, Instant> lastClaims = new HashMap<>();
    private int nextId;

    public BountyService(Settings settings, Economy economy, BountyStore store, Clock clock) {
        this.settings = settings;
        this.economy = economy;
        this.store = store;
        this.clock = clock;
        StoreData data = store.load();
        bounties.addAll(data.bounties());
        bounties.sort(Comparator.comparingInt(Bounty::id));
        notices.addAll(data.notices());
        this.nextId = data.nextId();
    }

    /** Replaces the settings (used by the reload command). */
    public void updateSettings(Settings newSettings) {
        this.settings = newSettings;
    }

    /** Writes the current state to the store. */
    public void save() {
        store.save(new StoreData(List.copyOf(bounties), List.copyOf(notices), nextId));
    }

    public CreateResult create(UUID poster, String posterName, UUID target, String targetName, long amount, Integer hours) {
        if (poster.equals(target)) {
            return new CreateResult(CreateStatus.SELF_TARGET, null);
        }
        if (amount < settings.minAmount()) {
            return new CreateResult(CreateStatus.AMOUNT_TOO_LOW, null);
        }
        if (amount > settings.maxAmount()) {
            return new CreateResult(CreateStatus.AMOUNT_TOO_HIGH, null);
        }
        int h = hours == null ? settings.defaultHours() : hours;
        if (hours != null && (h < 1 || h > settings.maxHours())) {
            return new CreateResult(CreateStatus.INVALID_DURATION, null);
        }
        long activeCount = bounties.stream()
                .filter(b -> b.status() == BountyStatus.ACTIVE && b.poster().equals(poster))
                .count();
        if (activeCount >= settings.maxActivePerPoster()) {
            return new CreateResult(CreateStatus.TOO_MANY_ACTIVE, null);
        }
        if (!economy.withdraw(poster, amount)) {
            return new CreateResult(CreateStatus.INSUFFICIENT_FUNDS, null);
        }
        Instant now = clock.instant();
        Bounty bounty = new Bounty(nextId++, target, targetName, poster, posterName, amount, now,
                now.plus(h, ChronoUnit.HOURS), BountyStatus.ACTIVE, null, null);
        bounties.add(bounty);
        save();
        return new CreateResult(CreateStatus.OK, bounty);
    }

    public CancelResult cancel(int id, UUID actor, boolean admin) {
        Optional<Bounty> found = get(id);
        if (found.isEmpty()) {
            return new CancelResult(CancelStatus.NOT_FOUND, 0);
        }
        Bounty bounty = found.get();
        if (bounty.status() != BountyStatus.ACTIVE) {
            return new CancelResult(CancelStatus.NOT_ACTIVE, 0);
        }
        if (!admin && !bounty.poster().equals(actor)) {
            return new CancelResult(CancelStatus.NOT_OWNER, 0);
        }
        Instant now = clock.instant();
        long refund;
        if (admin) {
            refund = bounty.amount();
        } else if (!now.isAfter(bounty.createdAt().plus(settings.cancelFullRefundMinutes(), ChronoUnit.MINUTES))) {
            refund = bounty.amount();
        } else {
            refund = bounty.amount() * settings.cancelRefundPercent() / 100;
        }
        replace(bounty, bounty.withResolved(BountyStatus.CANCELLED, null, now));
        economy.deposit(bounty.poster(), refund);
        save();
        return new CancelResult(CancelStatus.OK, refund);
    }

    public ClaimResult onKill(UUID killer, String killerName, UUID victim) {
        ClaimResult none = new ClaimResult(List.of(), 0, false);
        if (killer.equals(victim)) {
            return none;
        }
        Instant now = clock.instant();
        List<Bounty> claimable = new ArrayList<>();
        for (Bounty b : bounties) {
            if (b.status() == BountyStatus.ACTIVE && b.target().equals(victim)
                    && !b.poster().equals(killer) && now.isBefore(b.expiresAt())) {
                claimable.add(b);
            }
        }
        if (claimable.isEmpty()) {
            return none;
        }
        CooldownKey key = new CooldownKey(killer, victim);
        Instant last = lastClaims.get(key);
        if (last != null && Duration.between(last, now).compareTo(Duration.ofMinutes(settings.claimCooldownMinutes())) < 0) {
            return new ClaimResult(List.of(), 0, true);
        }
        long total = 0;
        for (Bounty b : claimable) {
            total += b.amount();
        }
        long tax = total * settings.taxPercent() / 100;
        long payout = total - tax;
        economy.deposit(killer, payout);
        List<Bounty> claimed = new ArrayList<>();
        for (Bounty b : claimable) {
            Bounty resolved = b.withResolved(BountyStatus.CLAIMED, killer, now);
            replace(b, resolved);
            claimed.add(resolved);
            notices.add(new Notice(b.poster(), NoticeType.CLAIMED, b.id(), b.amount(), killerName));
        }
        lastClaims.put(key, now);
        save();
        return new ClaimResult(List.copyOf(claimed), payout, false);
    }

    public int expireDue() {
        Instant now = clock.instant();
        int expired = 0;
        for (Bounty b : new ArrayList<>(bounties)) {
            if (b.status() == BountyStatus.ACTIVE && !b.expiresAt().isAfter(now)) {
                replace(b, b.withResolved(BountyStatus.EXPIRED, null, now));
                economy.deposit(b.poster(), b.amount());
                notices.add(new Notice(b.poster(), NoticeType.EXPIRED_REFUNDED, b.id(), b.amount(), b.targetName()));
                expired++;
            }
        }
        int purged = 0;
        Instant cutoff = now.minus(settings.purgeResolvedAfterDays(), ChronoUnit.DAYS);
        Iterator<Bounty> it = bounties.iterator();
        while (it.hasNext()) {
            Bounty b = it.next();
            if (b.status() != BountyStatus.ACTIVE && b.resolvedAt() != null && b.resolvedAt().isBefore(cutoff)) {
                it.remove();
                purged++;
            }
        }
        if (expired > 0 || purged > 0) {
            save();
        }
        return expired;
    }

    public Optional<Bounty> get(int id) {
        return bounties.stream().filter(b -> b.id() == id).findFirst();
    }

    public List<Bounty> active() {
        Instant now = clock.instant();
        return bounties.stream()
                .filter(b -> b.status() == BountyStatus.ACTIVE && now.isBefore(b.expiresAt()))
                .sorted(Comparator.comparingLong(Bounty::amount).reversed()
                        .thenComparing(Bounty::createdAt)
                        .thenComparingInt(Bounty::id))
                .toList();
    }

    public List<Bounty> activeOn(UUID target) {
        return active().stream().filter(b -> b.target().equals(target)).toList();
    }

    public List<Bounty> activeBy(UUID poster) {
        return active().stream().filter(b -> b.poster().equals(poster)).toList();
    }

    public long totalOn(UUID target) {
        long sum = 0;
        for (Bounty b : activeOn(target)) {
            sum += b.amount();
        }
        return sum;
    }

    public Page<Bounty> page(int page, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be > 0");
        }
        List<Bounty> all = active();
        int total = all.size();
        int pages = Math.max(1, (int) Math.ceil(total / (double) size));
        int clamped = Math.min(Math.max(page, 1), pages);
        int from = Math.min((clamped - 1) * size, total);
        int to = Math.min(from + size, total);
        return new Page<>(List.copyOf(all.subList(from, to)), clamped, pages, total);
    }

    public List<Notice> takeNotices(UUID player) {
        List<Notice> mine = new ArrayList<>();
        for (Notice n : notices) {
            if (n.player().equals(player)) {
                mine.add(n);
            }
        }
        if (mine.isEmpty()) {
            return mine;
        }
        notices.removeAll(mine);
        save();
        return mine;
    }

    private void replace(Bounty old, Bounty updated) {
        int index = bounties.indexOf(old);
        bounties.set(index, updated);
    }
}
