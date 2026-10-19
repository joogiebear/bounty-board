package dev.joogiebear.bountyboard.core;

import static dev.joogiebear.bountyboard.core.HeldSupport.A;
import static dev.joogiebear.bountyboard.core.HeldSupport.B;
import static dev.joogiebear.bountyboard.core.HeldSupport.C;
import static dev.joogiebear.bountyboard.core.HeldSupport.D;
import static dev.joogiebear.bountyboard.core.HeldSupport.E;
import static dev.joogiebear.bountyboard.core.HeldSupport.T0;
import static dev.joogiebear.bountyboard.core.HeldSupport.sb;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.joogiebear.bountyboard.core.HeldSupport.Fixture;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HeldServiceTest {

    private static final long START = 1_000_000_000L;

    // ---------------------------------------------------------------- create

    @Test
    void selfTargetWinsOverEveryOtherCheck() {
        Fixture f = new Fixture();
        CreateResult r = f.svc.create(A, "Alice", A, "Alice", 1, 0);
        assertEquals(CreateStatus.SELF_TARGET, r.status());
        assertNull(r.bounty());
    }

    @Test
    void amountTooLowWinsOverInvalidDuration() {
        Fixture f = new Fixture();
        assertEquals(CreateStatus.AMOUNT_TOO_LOW, f.svc.create(A, "Alice", B, "Bob", 1, 0).status());
    }

    @Test
    void invalidDurationWinsOverTooManyActive() {
        Fixture f = new Fixture(sb().act(1).build());
        f.post(A, B, 100);
        assertEquals(CreateStatus.INVALID_DURATION, f.svc.create(A, "Alice", B, "Bob", 100, 0).status());
    }

    @Test
    void tooManyActiveWinsOverInsufficientFunds() {
        Fixture f = new Fixture(sb().act(2).build());
        f.post(A, B, 100);
        f.post(A, B, 100);
        f.eco.fund(A, 0);
        assertEquals(CreateStatus.TOO_MANY_ACTIVE, f.svc.create(A, "Alice", B, "Bob", 100, null).status());
    }

    @Test
    void amountBoundaries() {
        Fixture f = new Fixture(sb().min(10).max(500).build());
        assertEquals(CreateStatus.AMOUNT_TOO_LOW, f.svc.create(A, "Alice", B, "Bob", 9, null).status());
        assertEquals(CreateStatus.OK, f.svc.create(A, "Alice", B, "Bob", 10, null).status());
        assertEquals(CreateStatus.OK, f.svc.create(A, "Alice", B, "Bob", 500, null).status());
        assertEquals(CreateStatus.AMOUNT_TOO_HIGH, f.svc.create(A, "Alice", B, "Bob", 501, null).status());
        assertEquals(CreateStatus.AMOUNT_TOO_LOW, f.svc.create(A, "Alice", B, "Bob", -50, null).status());
        assertEquals(CreateStatus.AMOUNT_TOO_LOW, f.svc.create(A, "Alice", B, "Bob", Long.MIN_VALUE, null).status());
        assertEquals(CreateStatus.AMOUNT_TOO_HIGH, f.svc.create(C, "Carol", B, "Bob", Long.MAX_VALUE, null).status());
    }

    @Test
    void durationBoundaries() {
        Fixture f = new Fixture(sb().act(10).maxH(72).build());
        assertEquals(CreateStatus.INVALID_DURATION, f.svc.create(A, "Alice", B, "Bob", 100, 0).status());
        assertEquals(CreateStatus.INVALID_DURATION, f.svc.create(A, "Alice", B, "Bob", 100, -1).status());
        assertEquals(CreateStatus.INVALID_DURATION, f.svc.create(A, "Alice", B, "Bob", 100, 73).status());
        assertEquals(CreateStatus.INVALID_DURATION, f.svc.create(A, "Alice", B, "Bob", 100, Integer.MAX_VALUE).status());
        CreateResult one = f.svc.create(A, "Alice", B, "Bob", 100, 1);
        CreateResult max = f.svc.create(A, "Alice", B, "Bob", 100, 72);
        assertEquals(CreateStatus.OK, one.status());
        assertEquals(CreateStatus.OK, max.status());
        assertEquals(T0.plus(Duration.ofHours(1)), one.bounty().expiresAt());
        assertEquals(T0.plus(Duration.ofHours(72)), max.bounty().expiresAt());
    }

    @Test
    void failedCreateChangesNothing() {
        Fixture f = new Fixture();
        f.svc.create(A, "Alice", A, "Alice", 100, null);
        f.svc.create(A, "Alice", B, "Bob", 1, null);
        f.svc.create(A, "Alice", B, "Bob", 100, 999);
        f.svc.create(E, "Eve", B, "Bob", 100, null); // E has no money
        assertEquals(0, f.store.saved.size());
        assertEquals(0, f.eco.withdrawals);
        assertEquals(START, f.eco.balance(A));
        Bounty first = f.post(A, B, 100);
        assertEquals(1, first.id(), "failed creates must not consume ids");
    }

    @Test
    void successfulCreateEscrowsAndFillsAllFields() {
        Fixture f = new Fixture();
        f.clock.advance(Duration.ofSeconds(90));
        CreateResult r = f.svc.create(A, "Alice", B, "Bob", 250, null);
        assertEquals(CreateStatus.OK, r.status());
        Bounty b = r.bounty();
        assertEquals(1, b.id());
        assertEquals(B, b.target());
        assertEquals("Bob", b.targetName());
        assertEquals(A, b.poster());
        assertEquals("Alice", b.posterName());
        assertEquals(250, b.amount());
        assertEquals(T0.plusSeconds(90), b.createdAt());
        assertEquals(T0.plusSeconds(90).plus(Duration.ofHours(24)), b.expiresAt());
        assertEquals(BountyStatus.ACTIVE, b.status());
        assertNull(b.claimedBy());
        assertNull(b.resolvedAt());
        assertEquals(START - 250, f.eco.balance(A));
        assertEquals(1, f.store.saved.size());
        assertEquals(2, f.store.last().nextId());
        assertEquals(List.of(b), f.store.last().bounties());
    }

    @Test
    void sameTargetSamePosterAllowedAndIdsIncrease() {
        Fixture f = new Fixture();
        Bounty one = f.post(A, B, 100);
        Bounty two = f.post(A, B, 100);
        assertEquals(1, one.id());
        assertEquals(2, two.id());
        assertEquals(2, f.svc.activeOn(B).size());
    }

    @Test
    void onlyActiveBountiesCountTowardThePosterLimit() {
        Fixture f = new Fixture(sb().act(2).build());
        Bounty one = f.post(A, B, 100);
        f.post(A, B, 100);
        assertEquals(CreateStatus.TOO_MANY_ACTIVE, f.svc.create(A, "Alice", B, "Bob", 100, null).status());
        assertEquals(CancelStatus.OK, f.svc.cancel(one.id(), A, false).status());
        assertEquals(CreateStatus.OK, f.svc.create(A, "Alice", B, "Bob", 100, null).status());
        // another poster is unaffected by A's limit
        assertEquals(CreateStatus.OK, f.svc.create(C, "Carol", B, "Bob", 100, null).status());
    }

    @Test
    void fundsExactlyEqualToAmountAreEnough() {
        Fixture f = new Fixture();
        f.eco.fund(E, 99);
        assertEquals(CreateStatus.INSUFFICIENT_FUNDS, f.svc.create(E, "Eve", B, "Bob", 100, null).status());
        assertEquals(99, f.eco.balance(E));
        f.eco.fund(E, 100);
        assertEquals(CreateStatus.OK, f.svc.create(E, "Eve", B, "Bob", 100, null).status());
        assertEquals(0, f.eco.balance(E));
    }

    // ---------------------------------------------------------------- cancel

    @Test
    void cancelUnknownIdsAreNotFound() {
        Fixture f = new Fixture();
        f.post(A, B, 100);
        for (int id : new int[] {0, -1, 2, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            assertEquals(CancelStatus.NOT_FOUND, f.svc.cancel(id, A, true).status());
        }
        assertEquals(1, f.store.saved.size(), "only the create saved");
    }

    @Test
    void notActiveIsCheckedBeforeOwner() {
        Fixture f = new Fixture();
        Bounty b = f.post(A, B, 100);
        assertEquals(CancelStatus.OK, f.svc.cancel(b.id(), A, false).status());
        assertEquals(CancelStatus.NOT_ACTIVE, f.svc.cancel(b.id(), C, false).status());
        assertEquals(CancelStatus.NOT_ACTIVE, f.svc.cancel(b.id(), A, false).status());
        assertEquals(CancelStatus.NOT_ACTIVE, f.svc.cancel(b.id(), C, true).status());
    }

    @Test
    void nonOwnerCannotCancelAndNothingChanges() {
        Fixture f = new Fixture();
        Bounty b = f.post(A, B, 100);
        int saves = f.store.saved.size();
        CancelResult r = f.svc.cancel(b.id(), C, false);
        assertEquals(CancelStatus.NOT_OWNER, r.status());
        assertEquals(0, r.refunded());
        assertEquals(saves, f.store.saved.size());
        assertEquals(BountyStatus.ACTIVE, f.svc.get(b.id()).orElseThrow().status());
        assertEquals(START - 100, f.eco.balance(A));
    }

    @Test
    void cancelRefundWindowIsInclusiveAtTheBoundary() {
        Fixture f = new Fixture();
        Bounty early = f.post(A, B, 99);
        Bounty late = f.post(A, B, 99);
        f.clock.advance(Duration.ofMinutes(5));
        CancelResult full = f.svc.cancel(early.id(), A, false);
        assertEquals(99, full.refunded(), "exactly at createdAt + 5 minutes still refunds in full");
        f.clock.advance(Duration.ofSeconds(1));
        CancelResult partial = f.svc.cancel(late.id(), A, false);
        assertEquals(79, partial.refunded(), "floor(99 * 80 / 100) = 79");
        assertEquals(START - 99 - 99 + 99 + 79, f.eco.balance(A));
    }

    @Test
    void cancelRecordsStateAndSavesOnce() {
        Fixture f = new Fixture();
        Bounty b = f.post(A, B, 100);
        f.clock.advance(Duration.ofMinutes(1));
        int before = f.store.saved.size();
        assertEquals(CancelStatus.OK, f.svc.cancel(b.id(), A, false).status());
        assertEquals(before + 1, f.store.saved.size());
        Bounty cancelled = f.svc.get(b.id()).orElseThrow();
        assertEquals(BountyStatus.CANCELLED, cancelled.status());
        assertEquals(T0.plus(Duration.ofMinutes(1)), cancelled.resolvedAt());
        assertNull(cancelled.claimedBy());
        assertEquals(cancelled, f.store.last().bounties().get(0));
    }

    @Test
    void adminCancelRefundsPosterInFullEvenLate() {
        Fixture f = new Fixture();
        Bounty b = f.post(A, B, 101);
        f.clock.advance(Duration.ofHours(10));
        long actorBefore = f.eco.balance(D);
        CancelResult r = f.svc.cancel(b.id(), D, true);
        assertEquals(CancelStatus.OK, r.status());
        assertEquals(101, r.refunded());
        assertEquals(START, f.eco.balance(A));
        assertEquals(actorBefore, f.eco.balance(D), "refund goes to the poster, not the admin");
    }

    @Test
    void ownerWithAdminFlagStillGetsFullRefund() {
        Fixture f = new Fixture();
        Bounty b = f.post(A, B, 100);
        f.clock.advance(Duration.ofHours(1));
        assertEquals(100, f.svc.cancel(b.id(), A, true).refunded());
    }

    @Test
    void expiredButUnprocessedBountyCanStillBeCancelled() {
        Fixture f = new Fixture();
        Bounty b = f.post(A, B, 100, 1);
        f.clock.advance(Duration.ofHours(2));
        CancelResult r = f.svc.cancel(b.id(), A, false);
        assertEquals(CancelStatus.OK, r.status());
        assertEquals(80, r.refunded());
    }

    @Test
    void zeroWindowAndZeroPercentSettings() {
        Fixture f = new Fixture(sb().full(0).pct(0).build());
        Bounty one = f.post(A, B, 100);
        assertEquals(100, f.svc.cancel(one.id(), A, false).refunded(), "now == createdAt is inside a 0 minute window");
        Bounty two = f.post(A, B, 100);
        f.clock.advance(Duration.ofSeconds(1));
        CancelResult r = f.svc.cancel(two.id(), A, false);
        assertEquals(CancelStatus.OK, r.status());
        assertEquals(0, r.refunded());
        assertEquals(START - 100, f.eco.balance(A));
    }

    // ---------------------------------------------------------------- claim

    @Test
    void killingYourselfOrAnUnbountiedPlayerClaimsNothing() {
        Fixture f = new Fixture();
        f.post(A, B, 100);
        ClaimResult self = f.svc.onKill(B, "Bob", B);
        ClaimResult none = f.svc.onKill(C, "Carol", D);
        for (ClaimResult r : List.of(self, none)) {
            assertTrue(r.claimed().isEmpty());
            assertEquals(0, r.payout());
            assertFalse(r.onCooldown());
        }
        assertEquals(1, f.store.saved.size(), "claims that claim nothing do not save");
        assertEquals(0, f.eco.deposits);
    }

    @Test
    void postersOwnKillDoesNotClaimTheirBounty() {
        Fixture f = new Fixture();
        f.post(A, B, 100);
        ClaimResult r = f.svc.onKill(A, "Alice", B);
        assertTrue(r.claimed().isEmpty());
        assertFalse(r.onCooldown());
        assertEquals(BountyStatus.ACTIVE, f.svc.get(1).orElseThrow().status());
    }

    @Test
    void taxIsFlooredAndPayoutIsTheRest() {
        Fixture f = new Fixture();
        f.post(A, B, 10);
        f.post(D, B, 15);
        ClaimResult r = f.svc.onKill(C, "Carol", B);
        assertEquals(24, r.payout(), "total 25, tax floor(1.25)=1");
        assertEquals(START + 24, f.eco.balance(C));

        Fixture g = new Fixture();
        g.post(A, B, 19);
        assertEquals(19, g.svc.onKill(C, "Carol", B).payout(), "floor(0.95) = 0 tax");
    }

    @Test
    void taxExtremes() {
        Fixture zero = new Fixture(sb().tax(0).build());
        zero.post(A, B, 77);
        assertEquals(77, zero.svc.onKill(C, "Carol", B).payout());
        Fixture all = new Fixture(sb().tax(100).build());
        all.post(A, B, 77);
        ClaimResult r = all.svc.onKill(C, "Carol", B);
        assertEquals(1, r.claimed().size());
        assertEquals(0, r.payout());
        assertEquals(BountyStatus.CLAIMED, all.svc.get(1).orElseThrow().status());
    }

    @Test
    void taxUsesTheSumNotEachBounty() {
        Fixture f = new Fixture(sb().tax(10).build());
        f.post(A, B, 15); // 10% of 15 floors to 1 on its own
        f.post(D, B, 15);
        assertEquals(27, f.svc.onKill(C, "Carol", B).payout(), "total 30, tax 3");
    }

    @Test
    void multipleBountiesClaimTogetherInIdOrderWithNotices() {
        Fixture f = new Fixture();
        f.post(A, B, 50);   // id 1
        f.post(D, B, 100);  // id 2
        f.post(A, B, 75);   // id 3
        f.clock.advance(Duration.ofMinutes(1));
        ClaimResult r = f.svc.onKill(C, "Carol", B);
        assertEquals(List.of(1, 2, 3), r.claimed().stream().map(Bounty::id).toList());
        assertEquals(214, r.payout()); // 225 - floor(11.25)
        for (Bounty b : r.claimed()) {
            assertEquals(BountyStatus.CLAIMED, b.status());
            assertEquals(C, b.claimedBy());
            assertEquals(T0.plus(Duration.ofMinutes(1)), b.resolvedAt());
        }
        assertEquals(1, f.eco.deposits);
        assertEquals(List.of(
                new Notice(A, NoticeType.CLAIMED, 1, 50, "Carol"),
                new Notice(D, NoticeType.CLAIMED, 2, 100, "Carol"),
                new Notice(A, NoticeType.CLAIMED, 3, 75, "Carol")), f.store.last().notices());
        assertEquals(4, f.store.saved.size(), "3 creates + exactly one save for the claim");
    }

    @Test
    void killerKeepsTheirOwnBountyActiveWhenClaimingOthers() {
        Fixture f = new Fixture();
        f.post(C, B, 100); // killer's own bounty, id 1
        f.post(A, B, 100); // id 2
        ClaimResult r = f.svc.onKill(C, "Carol", B);
        assertEquals(List.of(2), r.claimed().stream().map(Bounty::id).toList());
        assertEquals(BountyStatus.ACTIVE, f.svc.get(1).orElseThrow().status());
        assertEquals(95, r.payout());
    }

    @Test
    void bountyIsClaimableUpToButNotAtItsExpiry() {
        Fixture f = new Fixture();
        f.post(A, B, 100, 1);
        f.post(A, D, 100, 1);
        f.clock.advance(Duration.ofHours(1).minusSeconds(1));
        assertEquals(1, f.svc.onKill(C, "Carol", B).claimed().size());
        f.clock.advance(Duration.ofSeconds(1));
        ClaimResult late = f.svc.onKill(C, "Carol", D);
        assertTrue(late.claimed().isEmpty());
        assertFalse(late.onCooldown());
        assertEquals(BountyStatus.ACTIVE, f.svc.get(2).orElseThrow().status(), "expiry itself is expireDue's job");
    }

    @Test
    void cooldownBlocksAClaimStrictlyBeforeTheLimit() {
        Fixture f = new Fixture();
        f.post(A, B, 100);
        assertEquals(1, f.svc.onKill(C, "Carol", B).claimed().size());
        f.post(D, B, 100);
        f.clock.advance(Duration.ofMinutes(10).minusSeconds(1));
        int saves = f.store.saved.size();
        int deposits = f.eco.deposits;
        ClaimResult blocked = f.svc.onKill(C, "Carol", B);
        assertTrue(blocked.onCooldown());
        assertTrue(blocked.claimed().isEmpty());
        assertEquals(0, blocked.payout());
        assertEquals(saves, f.store.saved.size());
        assertEquals(deposits, f.eco.deposits);
        assertEquals(BountyStatus.ACTIVE, f.svc.get(2).orElseThrow().status());
        f.clock.advance(Duration.ofSeconds(1));
        ClaimResult ok = f.svc.onKill(C, "Carol", B);
        assertFalse(ok.onCooldown(), "exactly claimCooldownMinutes later is no longer on cooldown");
        assertEquals(1, ok.claimed().size());
    }

    @Test
    void cooldownIsPerKillerAndPerVictim() {
        Fixture f = new Fixture();
        f.post(A, B, 100);
        f.svc.onKill(C, "Carol", B);
        f.post(A, B, 100);
        f.post(A, D, 100);
        assertEquals(1, f.svc.onKill(E, "Eve", B).claimed().size(), "another killer is not on cooldown");
        f.post(A, B, 100);
        assertEquals(1, f.svc.onKill(C, "Carol", D).claimed().size(), "another victim is not on cooldown");
        assertTrue(f.svc.onKill(C, "Carol", B).onCooldown());
    }

    @Test
    void cooldownWithNothingToClaimIsNotReportedAsCooldown() {
        Fixture f = new Fixture();
        f.post(A, B, 100);
        f.svc.onKill(C, "Carol", B);
        ClaimResult r = f.svc.onKill(C, "Carol", B);
        assertFalse(r.onCooldown());
        assertTrue(r.claimed().isEmpty());
    }

    @Test
    void zeroMinuteCooldownNeverBlocks() {
        Fixture f = new Fixture(sb().cd(0).build());
        f.post(A, B, 100);
        f.svc.onKill(C, "Carol", B);
        f.post(A, B, 100);
        ClaimResult r = f.svc.onKill(C, "Carol", B);
        assertFalse(r.onCooldown());
        assertEquals(1, r.claimed().size());
    }

    @Test
    void cooldownIsNotPersistedAcrossAServiceReload() {
        Fixture f = new Fixture();
        f.post(A, B, 100);
        f.svc.onKill(C, "Carol", B);
        f.post(A, B, 100);
        BountyService fresh = f.reload();
        ClaimResult r = fresh.onKill(C, "Carol", B);
        assertFalse(r.onCooldown());
        assertEquals(1, r.claimed().size());
    }

    @Test
    void claimedBountiesCannotBeClaimedAgainOrCancelled() {
        Fixture f = new Fixture();
        f.post(A, B, 100);
        f.svc.onKill(C, "Carol", B);
        f.clock.advance(Duration.ofHours(1));
        assertTrue(f.svc.onKill(D, "Dave", B).claimed().isEmpty());
        assertEquals(CancelStatus.NOT_ACTIVE, f.svc.cancel(1, A, true).status());
        assertTrue(f.svc.active().isEmpty());
    }

    // ---------------------------------------------------------------- expiry

    @Test
    void expiresExactlyAtExpiresAtRefundsFullAndNotifies() {
        Fixture f = new Fixture();
        Bounty b = f.post(A, B, 123, 2);
        f.clock.advance(Duration.ofHours(2).minusMillis(1));
        assertEquals(0, f.svc.expireDue());
        assertEquals(1, f.store.saved.size());
        f.clock.advance(Duration.ofMillis(1));
        assertEquals(1, f.svc.expireDue());
        Bounty e = f.svc.get(b.id()).orElseThrow();
        assertEquals(BountyStatus.EXPIRED, e.status());
        assertEquals(b.expiresAt(), e.resolvedAt());
        assertNull(e.claimedBy());
        assertEquals(START, f.eco.balance(A));
        assertEquals(List.of(new Notice(A, NoticeType.EXPIRED_REFUNDED, 1, 123, "Bob")), f.svc.takeNotices(A));
        assertEquals(0, f.svc.expireDue(), "a second run finds nothing");
    }

    @Test
    void expireDueRefundsFullAmountToEachPosterAndKeepsNoticeOrder() {
        Fixture f = new Fixture();
        f.post(A, B, 100, 1);
        f.post(D, C, 200, 1);
        f.post(A, C, 300, 5);
        f.clock.advance(Duration.ofHours(1));
        assertEquals(2, f.svc.expireDue());
        assertEquals(START, f.eco.balance(D));
        assertEquals(START - 300, f.eco.balance(A));
        assertEquals(3, f.svc.get(3).orElseThrow().id());
        assertEquals(BountyStatus.ACTIVE, f.svc.get(3).orElseThrow().status());
        assertEquals(List.of(new Notice(A, NoticeType.EXPIRED_REFUNDED, 1, 100, "Bob")), f.svc.takeNotices(A));
        assertEquals(List.of(new Notice(D, NoticeType.EXPIRED_REFUNDED, 2, 200, "Carol")), f.svc.takeNotices(D));
    }

    @Test
    void expireDueSavesOnlyWhenSomethingChanged() {
        Fixture f = new Fixture();
        f.post(A, B, 100, 1);
        int saves = f.store.saved.size();
        assertEquals(0, f.svc.expireDue());
        assertEquals(saves, f.store.saved.size());
        f.clock.advance(Duration.ofHours(1));
        f.svc.expireDue();
        assertEquals(saves + 1, f.store.saved.size());
    }

    @Test
    void purgeRemovesResolvedBountiesOnlyAfterMoreThanTheConfiguredDays() {
        Fixture f = new Fixture();
        Bounty b = f.post(A, B, 100);
        f.svc.cancel(b.id(), A, false); // resolvedAt = T0
        int saves = f.store.saved.size();
        f.clock.advance(Duration.ofDays(7));
        assertEquals(0, f.svc.expireDue());
        assertTrue(f.svc.get(1).isPresent(), "exactly 7 days is not more than 7 days");
        assertEquals(saves, f.store.saved.size());
        f.clock.advance(Duration.ofSeconds(1));
        assertEquals(0, f.svc.expireDue(), "purging is not counted as expiring");
        assertTrue(f.svc.get(1).isEmpty());
        assertEquals(saves + 1, f.store.saved.size());
        assertTrue(f.store.last().bounties().isEmpty());
        assertEquals(2, f.store.last().nextId(), "purging never recycles ids");
    }

    @Test
    void purgeCoversClaimedAndExpiredButNeverActiveAndNotJustExpiredOnes() {
        Fixture f = new Fixture();
        f.post(A, B, 100);          // 1 will be claimed at T0
        f.post(A, D, 100, 72);      // 2 expires at +72h
        f.post(A, C, 100, 72);
        f.svc.onKill(C, "Carol", B);
        f.clock.advance(Duration.ofDays(7).plusSeconds(1));
        assertEquals(2, f.svc.expireDue(), "2 and 3 expire now");
        assertTrue(f.svc.get(1).isEmpty(), "claimed and old: purged");
        assertEquals(BountyStatus.EXPIRED, f.svc.get(2).orElseThrow().status(), "just resolved: kept");
        assertEquals(BountyStatus.EXPIRED, f.svc.get(3).orElseThrow().status());
    }

    @Test
    void zeroDayPurgeKeepsBountiesResolvedThisInstant() {
        Fixture f = new Fixture(sb().purge(0).build());
        Bounty b = f.post(A, B, 100);
        f.svc.cancel(b.id(), A, false);
        f.svc.expireDue();
        assertTrue(f.svc.get(1).isPresent());
        f.clock.advance(Duration.ofSeconds(1));
        f.svc.expireDue();
        assertTrue(f.svc.get(1).isEmpty());
    }

    // ---------------------------------------------------------------- queries

    @Test
    void activeIsSortedByAmountThenCreatedThenIdAndHidesExpired() {
        Fixture f = new Fixture(sb().act(10).build());
        f.post(A, B, 50);          // 1
        f.post(A, C, 100);      // 2
        f.clock.advance(Duration.ofSeconds(1));
        f.post(D, B, 100);      // 3 later createdAt
        f.post(A, D, 100);   // 4 same createdAt as 3
        Bounty expiring = f.post(D, C, 999, 1);   // 5
        assertEquals(List.of(5, 2, 3, 4, 1), f.svc.active().stream().map(Bounty::id).toList());
        f.clock.advance(Duration.ofHours(1));
        assertEquals(List.of(2, 3, 4, 1), f.svc.active().stream().map(Bounty::id).toList());
        assertEquals(BountyStatus.ACTIVE, f.svc.get(expiring.id()).orElseThrow().status());
    }

    @Test
    void activeOnActiveByAndTotalOnFilterWithTheSameOrder() {
        Fixture f = new Fixture(sb().act(10).build());
        f.post(A, B, 50);   // 1
        f.post(C, B, 70);   // 2
        f.post(A, C, 90);   // 3
        f.post(A, B, 70);   // 4
        assertEquals(List.of(2, 4, 1), f.svc.activeOn(B).stream().map(Bounty::id).toList());
        assertEquals(List.of(3, 4, 1), f.svc.activeBy(A).stream().map(Bounty::id).toList());
        assertEquals(190, f.svc.totalOn(B));
        assertEquals(0, f.svc.totalOn(D));
        assertTrue(f.svc.activeBy(D).isEmpty());
        f.svc.cancel(2, C, false);
        assertEquals(120, f.svc.totalOn(B), "cancelled bounties do not count");
    }

    @Test
    void totalOnIgnoresExpiredButUnprocessedBounties() {
        Fixture f = new Fixture();
        f.post(A, B, 100, 1);
        f.post(D, B, 40, 2);
        f.clock.advance(Duration.ofHours(1));
        assertEquals(40, f.svc.totalOn(B));
        assertEquals(1, f.svc.activeOn(B).size());
    }

    @Test
    void pageMathAndClamping() {
        Fixture f = new Fixture(sb().act(10).build());
        for (int i = 1; i <= 5; i++) f.post(A, B, 10 * i);
        Page<Bounty> p1 = f.svc.page(1, 2);
        assertEquals(List.of(50L, 40L), p1.items().stream().map(Bounty::amount).toList());
        assertEquals(3, p1.pages());
        assertEquals(5, p1.total());
        assertEquals(1, p1.page());
        Page<Bounty> p3 = f.svc.page(3, 2);
        assertEquals(List.of(10L), p3.items().stream().map(Bounty::amount).toList());
        assertEquals(1, f.svc.page(0, 2).page());
        assertEquals(1, f.svc.page(-7, 2).page());
        Page<Bounty> high = f.svc.page(Integer.MAX_VALUE, 2);
        assertEquals(3, high.page());
        assertEquals(List.of(10L), high.items().stream().map(Bounty::amount).toList());
        assertEquals(1, f.svc.page(1, 5).pages());
        assertEquals(2, f.svc.page(1, 4).pages());
        assertEquals(5, f.svc.page(1, 1000).items().size());
    }

    @Test
    void emptyPageHasOnePage() {
        Fixture f = new Fixture();
        Page<Bounty> p = f.svc.page(1, 45);
        assertTrue(p.items().isEmpty());
        assertEquals(1, p.pages());
        assertEquals(1, p.page());
        assertEquals(0, p.total());
        assertEquals(1, f.svc.page(9, 45).page());
    }

    @Test
    void pageSizeMustBePositive() {
        Fixture f = new Fixture();
        assertThrows(IllegalArgumentException.class, () -> f.svc.page(1, 0));
        assertThrows(IllegalArgumentException.class, () -> f.svc.page(1, -3));
    }

    @Test
    void pageBoundaryAt45And46() {
        Fixture f = new Fixture();
        for (int i = 0; i < 45; i++) {
            UUID p = new UUID(1, i);
            f.eco.fund(p, 1000);
            assertEquals(CreateStatus.OK, f.svc.create(p, "P" + i, B, "Bob", 10 + i, null).status());
        }
        assertEquals(1, f.svc.page(1, 45).pages());
        assertEquals(45, f.svc.page(1, 45).items().size());
        UUID extra = new UUID(1, 999);
        f.eco.fund(extra, 1000);
        f.svc.create(extra, "Extra", B, "Bob", 10, null);
        Page<Bounty> second = f.svc.page(2, 45);
        assertEquals(2, second.pages());
        assertEquals(46, second.total());
        assertEquals(1, second.items().size());
        assertEquals("Extra", second.items().get(0).posterName());
    }

    @Test
    void takeNoticesReturnsOnlyThatPlayersInOrderAndRemovesThem() {
        Fixture f = new Fixture();
        f.post(A, B, 100, 1);
        f.post(D, B, 60, 1);
        f.post(A, C, 80, 1);
        f.clock.advance(Duration.ofHours(1));
        f.svc.expireDue();
        int saves = f.store.saved.size();
        List<Notice> mine = f.svc.takeNotices(A);
        assertEquals(List.of(
                new Notice(A, NoticeType.EXPIRED_REFUNDED, 1, 100, "Bob"),
                new Notice(A, NoticeType.EXPIRED_REFUNDED, 3, 80, "Carol")), mine);
        assertEquals(saves + 1, f.store.saved.size());
        assertEquals(List.of(new Notice(D, NoticeType.EXPIRED_REFUNDED, 2, 60, "Bob")), f.store.last().notices());
        assertTrue(f.svc.takeNotices(A).isEmpty());
        assertTrue(f.svc.takeNotices(E).isEmpty());
        assertEquals(saves + 1, f.store.saved.size(), "empty takes do not save");
    }

    // ---------------------------------------------------------------- load / snapshots

    @Test
    void loadedStateIsUsedAndNextIdContinues() {
        Fixture f = new Fixture();
        Bounty old = new Bounty(7, B, "Bob", A, "Alice", 500, T0.minusSeconds(60), T0.plus(Duration.ofHours(2)),
                BountyStatus.ACTIVE, null, null);
        Bounty done = new Bounty(3, C, "Carol", A, "Alice", 40, T0.minusSeconds(900), T0.plus(Duration.ofHours(1)),
                BountyStatus.CLAIMED, D, T0.minusSeconds(600));
        Notice n = new Notice(A, NoticeType.CLAIMED, 3, 40, "Dave");
        f.store.data = new StoreData(List.of(old, done), List.of(n), 12);
        BountyService svc = f.reload();
        assertEquals(0, f.store.saved.size(), "loading does not save");
        assertEquals(List.of(old), svc.active());
        assertEquals(done, svc.get(3).orElseThrow());
        assertEquals(500, svc.totalOn(B));
        assertEquals(12, svc.create(A, "Alice", B, "Bob", 100, null).bounty().id());
        assertEquals(13, f.store.last().nextId());
        assertEquals(3, f.store.last().bounties().size());
        assertEquals(List.of(n), svc.takeNotices(A));
    }

    @Test
    void loadedBountyCanBeClaimedAndExpired() {
        Fixture f = new Fixture();
        f.store.data = new StoreData(List.of(
                new Bounty(1, B, "Bob", A, "Alice", 100, T0.minusSeconds(60), T0.plusSeconds(60), BountyStatus.ACTIVE, null, null),
                new Bounty(2, C, "Carol", A, "Alice", 100, T0.minusSeconds(60), T0.plusSeconds(60), BountyStatus.ACTIVE, null, null)),
                List.of(), 3);
        BountyService svc = f.reload();
        assertEquals(95, svc.onKill(D, "Dave", B).payout());
        f.clock.advance(Duration.ofSeconds(60));
        assertEquals(1, svc.expireDue());
        assertEquals(BountyStatus.EXPIRED, svc.get(2).orElseThrow().status());
    }

    @Test
    void savedSnapshotsAreNotAffectedByLaterOperations() {
        Fixture f = new Fixture();
        f.post(A, B, 100);
        StoreData first = f.store.last();
        List<Bounty> copy = new ArrayList<>(first.bounties());
        f.post(D, B, 100);
        f.svc.cancel(1, A, false);
        assertEquals(copy, first.bounties(), "an earlier snapshot must not change afterwards");
        assertEquals(1, first.bounties().size());
        assertEquals(2, first.nextId());
        assertEquals(3, f.store.last().nextId());
    }

    @Test
    void snapshotContainsResolvedAndActiveBountiesAndNotices() {
        Fixture f = new Fixture();
        f.post(A, B, 100);
        f.post(D, C, 100);
        f.svc.onKill(E, "Eve", B);
        StoreData d = f.store.last();
        assertEquals(2, d.bounties().size());
        assertEquals(BountyStatus.CLAIMED, d.bounties().get(0).status());
        assertEquals(BountyStatus.ACTIVE, d.bounties().get(1).status());
        assertEquals(1, d.notices().size());
        assertEquals(3, d.nextId());
    }
}
