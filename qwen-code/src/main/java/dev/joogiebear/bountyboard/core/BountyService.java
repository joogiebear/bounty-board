package dev.joogiebear.bountyboard.core;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
    private int nextId;
    private final Map<CooldownKey, Instant> cooldowns = new HashMap<>();

    public BountyService(Settings settings, Economy economy, BountyStore store, Clock clock) {
        this.settings = settings;
        this.economy = economy;
        this.store = store;
        this.clock = clock;
        StoreData data = store.load();
        bounties.addAll(data.bounties());
        notices.addAll(data.notices());
        nextId = data.nextId();
    }

    public void updateSettings(Settings newSettings) {
        this.settings = newSettings;
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
        int effectiveHours;
        if (hours != null) {
            if (hours < 1 || hours > settings.maxHours()) {
                return new CreateResult(CreateStatus.INVALID_DURATION, null);
            }
            effectiveHours = hours;
        } else {
            effectiveHours = settings.defaultHours();
        }
        if (countActiveByPoster(poster) >= settings.maxActivePerPoster()) {
            return new CreateResult(CreateStatus.TOO_MANY_ACTIVE, null);
        }
        if (!economy.withdraw(poster, amount)) {
            return new CreateResult(CreateStatus.INSUFFICIENT_FUNDS, null);
        }
        Instant now = clock.instant();
        Bounty bounty = new Bounty(nextId, target, targetName, poster, posterName, amount,
                now, now.plus(Duration.ofHours(effectiveHours)), BountyStatus.ACTIVE, null, null);
        bounties.add(bounty);
        nextId++;
        save();
        return new CreateResult(CreateStatus.OK, bounty);
    }

    public CancelResult cancel(int id, UUID actor, boolean admin) {
        Bounty bounty = findBounty(id);
        if (bounty == null) {
            return new CancelResult(CancelStatus.NOT_FOUND, 0);
        }
        if (bounty.status() != BountyStatus.ACTIVE) {
            return new CancelResult(CancelStatus.NOT_ACTIVE, 0);
        }
        if (!bounty.poster().equals(actor) && !admin) {
            return new CancelResult(CancelStatus.NOT_OWNER, 0);
        }
        Instant now = clock.instant();
        long refunded = computeCancelRefund(bounty, admin, now);
        economy.deposit(bounty.poster(), refunded);
        replace(bounty, bounty.withResolved(BountyStatus.CANCELLED, null, now));
        save();
        return new CancelResult(CancelStatus.OK, refunded);
    }

    public ClaimResult onKill(UUID killer, String killerName, UUID victim) {
        if (killer.equals(victim)) {
            return new ClaimResult(List.of(), 0, false);
        }
        Instant now = clock.instant();
        List<Bounty> claimable = bounties.stream()
                .filter(b -> b.status() == BountyStatus.ACTIVE)
                .filter(b -> b.target().equals(victim))
                .filter(b -> !b.poster().equals(killer))
                .filter(b -> now.isBefore(b.expiresAt()))
                .sorted(Comparator.comparingInt(Bounty::id))
                .toList();
        if (claimable.isEmpty()) {
            return new ClaimResult(List.of(), 0, false);
        }
        CooldownKey key = new CooldownKey(killer, victim);
        Instant lastClaim = cooldowns.get(key);
        if (lastClaim != null && now.isBefore(lastClaim.plus(Duration.ofMinutes(settings.claimCooldownMinutes())))) {
            return new ClaimResult(List.of(), 0, true);
        }
        long total = claimable.stream().mapToLong(Bounty::amount).sum();
        long tax = total * settings.taxPercent() / 100;
        long payout = total - tax;
        economy.deposit(killer, payout);
        List<Bounty> claimed = new ArrayList<>();
        for (Bounty b : claimable) {
            Bounty updated = b.withResolved(BountyStatus.CLAIMED, killer, now);
            replace(b, updated);
            claimed.add(updated);
            notices.add(new Notice(b.poster(), NoticeType.CLAIMED, b.id(), b.amount(), killerName));
        }
        cooldowns.put(key, now);
        save();
        return new ClaimResult(claimed, payout, false);
    }

    public int expireDue() {
        Instant now = clock.instant();
        List<Bounty> toExpire = bounties.stream()
                .filter(b -> b.status() == BountyStatus.ACTIVE && !b.expiresAt().isAfter(now))
                .sorted(Comparator.comparingInt(Bounty::id))
                .toList();
        for (Bounty b : toExpire) {
            economy.deposit(b.poster(), b.amount());
            replace(b, b.withResolved(BountyStatus.EXPIRED, null, now));
            notices.add(new Notice(b.poster(), NoticeType.EXPIRED_REFUNDED, b.id(), b.amount(), b.targetName()));
        }
        Instant purgeBefore = now.minus(Duration.ofDays(settings.purgeResolvedAfterDays()));
        int purged = 0;
        List<Bounty> toPurge = bounties.stream()
                .filter(b -> b.status() != BountyStatus.ACTIVE && b.resolvedAt() != null && b.resolvedAt().isBefore(purgeBefore))
                .toList();
        for (Bounty b : toPurge) {
            bounties.remove(b);
            purged++;
        }
        if (!toExpire.isEmpty() || purged > 0) {
            save();
        }
        return toExpire.size();
    }

    public Optional<Bounty> get(int id) {
        return bounties.stream().filter(b -> b.id() == id).findFirst();
    }

    public List<Bounty> active() {
        Instant now = clock.instant();
        return bounties.stream()
                .filter(b -> b.status() == BountyStatus.ACTIVE && b.expiresAt().isAfter(now))
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
        return activeOn(target).stream().mapToLong(Bounty::amount).sum();
    }

    public Page<Bounty> page(int page, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be > 0");
        }
        List<Bounty> all = active();
        int total = all.size();
        int pages = Math.max(1, (total + size - 1) / size);
        int clampedPage = Math.max(1, Math.min(page, pages));
        int start = (clampedPage - 1) * size;
        int end = Math.min(start + size, total);
        return new Page<>(List.copyOf(all.subList(start, end)), clampedPage, pages, total);
    }

    public List<Notice> takeNotices(UUID player) {
        List<Notice> result = new ArrayList<>();
        List<Notice> remaining = new ArrayList<>();
        for (Notice n : notices) {
            if (n.player().equals(player)) {
                result.add(n);
            } else {
                remaining.add(n);
            }
        }
        if (!result.isEmpty()) {
            notices.clear();
            notices.addAll(remaining);
            save();
        }
        return result;
    }

    private long countActiveByPoster(UUID poster) {
        return bounties.stream()
                .filter(b -> b.status() == BountyStatus.ACTIVE && b.poster().equals(poster))
                .count();
    }

    private long computeCancelRefund(Bounty bounty, boolean admin, Instant now) {
        if (admin) {
            return bounty.amount();
        }
        if (!now.isAfter(bounty.createdAt().plus(Duration.ofMinutes(settings.cancelFullRefundMinutes())))) {
            return bounty.amount();
        }
        return bounty.amount() * settings.cancelRefundPercent() / 100;
    }

    private Bounty findBounty(int id) {
        return bounties.stream().filter(b -> b.id() == id).findFirst().orElse(null);
    }

    private void replace(Bounty old, Bounty updated) {
        int index = bounties.indexOf(old);
        bounties.set(index, updated);
    }

    private void save() {
        store.save(new StoreData(List.copyOf(bounties), List.copyOf(notices), nextId));
    }
}
