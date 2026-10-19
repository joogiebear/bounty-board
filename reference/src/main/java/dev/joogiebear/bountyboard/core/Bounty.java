package dev.joogiebear.bountyboard.core;

import java.time.Instant;
import java.util.UUID;

/** An immutable bounty. claimedBy and resolvedAt are null while ACTIVE. */
public record Bounty(int id, UUID target, String targetName, UUID poster, String posterName, long amount,
                     Instant createdAt, Instant expiresAt, BountyStatus status, UUID claimedBy, Instant resolvedAt) {

    public Bounty withResolved(BountyStatus newStatus, UUID newClaimedBy, Instant newResolvedAt) {
        return new Bounty(id, target, targetName, poster, posterName, amount, createdAt, expiresAt, newStatus, newClaimedBy, newResolvedAt);
    }
}
