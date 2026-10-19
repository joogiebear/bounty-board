package dev.joogiebear.bountyboard.core;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The bounty rules. See DESIGN.md, section "BountyService". */
public class BountyService {

    public BountyService(Settings settings, Economy economy, BountyStore store, Clock clock) {
        throw new UnsupportedOperationException("TODO");
    }

    public CreateResult create(UUID poster, String posterName, UUID target, String targetName, long amount, Integer hours) {
        throw new UnsupportedOperationException("TODO");
    }

    public CancelResult cancel(int id, UUID actor, boolean admin) {
        throw new UnsupportedOperationException("TODO");
    }

    public ClaimResult onKill(UUID killer, String killerName, UUID victim) {
        throw new UnsupportedOperationException("TODO");
    }

    public int expireDue() {
        throw new UnsupportedOperationException("TODO");
    }

    public Optional<Bounty> get(int id) {
        throw new UnsupportedOperationException("TODO");
    }

    public List<Bounty> active() {
        throw new UnsupportedOperationException("TODO");
    }

    public List<Bounty> activeOn(UUID target) {
        throw new UnsupportedOperationException("TODO");
    }

    public List<Bounty> activeBy(UUID poster) {
        throw new UnsupportedOperationException("TODO");
    }

    public long totalOn(UUID target) {
        throw new UnsupportedOperationException("TODO");
    }

    public Page<Bounty> page(int page, int size) {
        throw new UnsupportedOperationException("TODO");
    }

    public List<Notice> takeNotices(UUID player) {
        throw new UnsupportedOperationException("TODO");
    }
}
