package dev.joogiebear.bountyboard.core;

import static dev.joogiebear.bountyboard.core.TestSupport.ALEX;
import static dev.joogiebear.bountyboard.core.TestSupport.KIM;
import static dev.joogiebear.bountyboard.core.TestSupport.STEVE;
import static dev.joogiebear.bountyboard.core.TestSupport.T0;
import static dev.joogiebear.bountyboard.core.TestSupport.ZED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.joogiebear.bountyboard.core.TestSupport.FakeEconomy;
import dev.joogiebear.bountyboard.core.TestSupport.MemoryStore;
import dev.joogiebear.bountyboard.core.TestSupport.MutableClock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BountyServiceTest {

    FakeEconomy eco;
    MemoryStore store;
    MutableClock clock;
    BountyService service;

    @BeforeEach
    void setUp() {
        eco = new FakeEconomy().set(ALEX, 1000).set(STEVE, 1000).set(KIM, 1000).set(ZED, 1000);
        store = new MemoryStore();
        clock = new MutableClock(T0);
        service = new BountyService(Settings.defaults(), eco, store, clock);
    }

    CreateResult post(UUID poster, String posterName, UUID target, String targetName, long amount) {
        return service.create(poster, posterName, target, targetName, amount, null);
    }

    CreateResult alexOnSteve(long amount) {
        return post(ALEX, "Alex", STEVE, "Steve", amount);
    }

    // ---- create ----

    @Test
    void createsABountyAndTakesTheEscrow() {
        CreateResult r = alexOnSteve(100);
        assertEquals(CreateStatus.OK, r.status());
        Bounty b = r.bounty();
        assertEquals(1, b.id());
        assertEquals(ALEX, b.poster());
        assertEquals("Alex", b.posterName());
        assertEquals(STEVE, b.target());
        assertEquals("Steve", b.targetName());
        assertEquals(100, b.amount());
        assertEquals(T0, b.createdAt());
        assertEquals(T0.plus(Duration.ofHours(24)), b.expiresAt());
        assertEquals(BountyStatus.ACTIVE, b.status());
        assertNull(b.claimedBy());
        assertNull(b.resolvedAt());
        assertEquals(900, eco.balance(ALEX));
        assertEquals(1, store.saves);
        assertEquals(1, store.data.bounties().size());
        assertEquals(2, store.data.nextId());
    }

    @Test
    void idsCountUpFromOne() {
        assertEquals(1, alexOnSteve(100).bounty().id());
        assertEquals(2, alexOnSteve(100).bounty().id());
        assertEquals(3, post(KIM, "Kim", STEVE, "Steve", 100).bounty().id());
        assertEquals(4, store.data.nextId());
    }

    @Test
    void usesTheGivenDuration() {
        Bounty b = service.create(ALEX, "Alex", STEVE, "Steve", 100, 6).bounty();
        assertEquals(T0.plus(Duration.ofHours(6)), b.expiresAt());
    }

    @Test
    void rejectsPostingOnYourself() {
        CreateResult r = post(ALEX, "Alex", ALEX, "Alex", 100);
        assertEquals(CreateStatus.SELF_TARGET, r.status());
        assertNull(r.bounty());
        assertEquals(1000, eco.balance(ALEX));
        assertEquals(0, store.saves);
    }

    @Test
    void enforcesTheAmountLimits() {
        assertEquals(CreateStatus.AMOUNT_TOO_LOW, alexOnSteve(9).status());
        assertEquals(CreateStatus.OK, alexOnSteve(10).status());
        eco.set(KIM, 5_000_000);
        assertEquals(CreateStatus.OK, post(KIM, "Kim", STEVE, "Steve", 1_000_000).status());
        assertEquals(CreateStatus.AMOUNT_TOO_HIGH, post(KIM, "Kim", STEVE, "Steve", 1_000_001).status());
    }

    @Test
    void enforcesTheDurationLimits() {
        assertEquals(CreateStatus.INVALID_DURATION, service.create(ALEX, "Alex", STEVE, "Steve", 100, 0).status());
        assertEquals(CreateStatus.INVALID_DURATION, service.create(ALEX, "Alex", STEVE, "Steve", 100, -1).status());
        assertEquals(CreateStatus.INVALID_DURATION, service.create(ALEX, "Alex", STEVE, "Steve", 100, 73).status());
        assertEquals(CreateStatus.OK, service.create(ALEX, "Alex", STEVE, "Steve", 100, 72).status());
        assertEquals(CreateStatus.OK, service.create(ALEX, "Alex", STEVE, "Steve", 100, 1).status());
    }

    @Test
    void checksRunInTheDocumentedOrder() {
        // self target beats everything else
        assertEquals(CreateStatus.SELF_TARGET, service.create(ALEX, "Alex", ALEX, "Alex", 5, 0).status());
        // too low beats a bad duration
        assertEquals(CreateStatus.AMOUNT_TOO_LOW, service.create(ALEX, "Alex", STEVE, "Steve", 5, 0).status());
        // too high beats insufficient funds
        assertEquals(CreateStatus.AMOUNT_TOO_HIGH, post(ALEX, "Alex", STEVE, "Steve", 5_000_000).status());
        // a bad duration beats too many active
        for (int i = 0; i < 3; i++) {
            assertEquals(CreateStatus.OK, alexOnSteve(10).status());
        }
        assertEquals(CreateStatus.INVALID_DURATION, service.create(ALEX, "Alex", STEVE, "Steve", 100, 0).status());
        // too many active beats insufficient funds
        eco.set(ALEX, 0);
        assertEquals(CreateStatus.TOO_MANY_ACTIVE, alexOnSteve(100).status());
    }

    @Test
    void limitsActiveBountiesPerPoster() {
        for (int i = 0; i < 3; i++) {
            assertEquals(CreateStatus.OK, alexOnSteve(10).status());
        }
        assertEquals(CreateStatus.TOO_MANY_ACTIVE, alexOnSteve(10).status());
        assertEquals(CreateStatus.OK, post(KIM, "Kim", STEVE, "Steve", 10).status());
        service.cancel(2, ALEX, false);
        assertEquals(CreateStatus.OK, alexOnSteve(10).status());
    }

    @Test
    void claimedAndExpiredBountiesFreeTheSlot() {
        for (int i = 0; i < 3; i++) {
            alexOnSteve(10);
        }
        service.onKill(KIM, "Kim", STEVE);
        assertEquals(CreateStatus.OK, alexOnSteve(10).status());
    }

    @Test
    void failedInsufficientFundsChangesNothing() {
        eco.set(ALEX, 50);
        CreateResult r = alexOnSteve(100);
        assertEquals(CreateStatus.INSUFFICIENT_FUNDS, r.status());
        assertNull(r.bounty());
        assertEquals(50, eco.balance(ALEX));
        assertEquals(0, store.saves);
        eco.set(ALEX, 100);
        assertEquals(1, alexOnSteve(100).bounty().id());
        assertEquals(0, eco.balance(ALEX));
    }

    @Test
    void allowsSeveralBountiesOnTheSameTarget() {
        assertEquals(CreateStatus.OK, alexOnSteve(50).status());
        assertEquals(CreateStatus.OK, alexOnSteve(60).status());
        assertEquals(2, service.activeOn(STEVE).size());
    }

    // ---- cancel ----

    @Test
    void cancelNotFound() {
        CancelResult r = service.cancel(99, ALEX, false);
        assertEquals(CancelStatus.NOT_FOUND, r.status());
        assertEquals(0, store.saves);
    }

    @Test
    void cancelByAnotherPlayerIsRefused() {
        alexOnSteve(100);
        int saves = store.saves;
        assertEquals(CancelStatus.NOT_OWNER, service.cancel(1, STEVE, false).status());
        assertEquals(saves, store.saves);
        assertEquals(BountyStatus.ACTIVE, service.get(1).orElseThrow().status());
    }

    @Test
    void cancelWithinTheGraceWindowRefundsEverything() {
        alexOnSteve(100);
        clock.advance(Duration.ofMinutes(5));
        int saves = store.saves;
        CancelResult r = service.cancel(1, ALEX, false);
        assertEquals(CancelStatus.OK, r.status());
        assertEquals(100, r.refunded());
        assertEquals(1000, eco.balance(ALEX));
        Bounty b = service.get(1).orElseThrow();
        assertEquals(BountyStatus.CANCELLED, b.status());
        assertEquals(clock.instant(), b.resolvedAt());
        assertNull(b.claimedBy());
        assertEquals(saves + 1, store.saves);
    }

    @Test
    void cancelAfterTheGraceWindowRefundsEightyPercentRoundedDown() {
        alexOnSteve(100);
        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        CancelResult r = service.cancel(1, ALEX, false);
        assertEquals(80, r.refunded());
        assertEquals(980, eco.balance(ALEX));

        FakeEconomy eco2 = new FakeEconomy().set(ALEX, 100);
        MutableClock clock2 = new MutableClock(T0);
        BountyService s2 = new BountyService(Settings.defaults(), eco2, new MemoryStore(), clock2);
        s2.create(ALEX, "Alex", STEVE, "Steve", 15, null);
        clock2.advance(Duration.ofHours(1));
        assertEquals(12, s2.cancel(1, ALEX, false).refunded());
    }

    @Test
    void adminCancelRefundsEverythingToThePoster() {
        alexOnSteve(100);
        clock.advance(Duration.ofHours(10));
        CancelResult r = service.cancel(1, KIM, true);
        assertEquals(CancelStatus.OK, r.status());
        assertEquals(100, r.refunded());
        assertEquals(1000, eco.balance(ALEX));
        assertEquals(1000, eco.balance(KIM));
    }

    @Test
    void cancelOfAResolvedBountyIsNotActive() {
        alexOnSteve(100);
        service.cancel(1, ALEX, false);
        assertEquals(CancelStatus.NOT_ACTIVE, service.cancel(1, ALEX, false).status());
        assertEquals(CancelStatus.NOT_ACTIVE, service.cancel(1, KIM, false).status()); // not-active is checked before ownership
        alexOnSteve(100);
        service.onKill(KIM, "Kim", STEVE);
        assertEquals(CancelStatus.NOT_ACTIVE, service.cancel(2, ALEX, false).status());
    }

    @Test
    void anExpiredButUnprocessedBountyCanStillBeCancelled() {
        alexOnSteve(100);
        clock.advance(Duration.ofHours(25));
        CancelResult r = service.cancel(1, ALEX, false);
        assertEquals(CancelStatus.OK, r.status());
        assertEquals(80, r.refunded());
    }

    // ---- onKill ----

    @Test
    void aKillWithoutBountiesClaimsNothing() {
        ClaimResult r = service.onKill(KIM, "Kim", STEVE);
        assertTrue(r.claimed().isEmpty());
        assertEquals(0, r.payout());
        assertFalse(r.onCooldown());
        assertEquals(0, store.saves);
    }

    @Test
    void claimsABountyAndPaysTheKillerAfterTax() {
        alexOnSteve(100);
        clock.advance(Duration.ofMinutes(30));
        int saves = store.saves;
        ClaimResult r = service.onKill(KIM, "Kim", STEVE);
        assertEquals(1, r.claimed().size());
        assertEquals(95, r.payout());
        assertFalse(r.onCooldown());
        assertEquals(1095, eco.balance(KIM));
        Bounty b = service.get(1).orElseThrow();
        assertEquals(BountyStatus.CLAIMED, b.status());
        assertEquals(KIM, b.claimedBy());
        assertEquals(clock.instant(), b.resolvedAt());
        assertEquals(saves + 1, store.saves);
        List<Notice> notices = service.takeNotices(ALEX);
        assertEquals(List.of(new Notice(ALEX, NoticeType.CLAIMED, 1, 100, "Kim")), notices);
    }

    @Test
    void claimsAllBountiesOnTheVictimTogether() {
        alexOnSteve(100);
        post(ZED, "Zed", STEVE, "Steve", 250);
        ClaimResult r = service.onKill(KIM, "Kim", STEVE);
        assertEquals(List.of(1, 2), r.claimed().stream().map(Bounty::id).toList());
        assertEquals(333, r.payout()); // 350 - floor(17.5)
        assertEquals(1333, eco.balance(KIM));
        assertEquals(List.of(new Notice(ALEX, NoticeType.CLAIMED, 1, 100, "Kim")), service.takeNotices(ALEX));
        assertEquals(List.of(new Notice(ZED, NoticeType.CLAIMED, 2, 250, "Kim")), service.takeNotices(ZED));
    }

    @Test
    void taxIsRoundedDownOnTheTotalNotPerBounty() {
        alexOnSteve(10);
        post(ZED, "Zed", STEVE, "Steve", 11);
        ClaimResult r = service.onKill(KIM, "Kim", STEVE);
        assertEquals(20, r.payout()); // 21 total, tax floor(1.05) = 1
    }

    @Test
    void killingYourselfClaimsNothing() {
        alexOnSteve(100);
        ClaimResult r = service.onKill(STEVE, "Steve", STEVE);
        assertTrue(r.claimed().isEmpty());
        assertEquals(0, r.payout());
        assertEquals(BountyStatus.ACTIVE, service.get(1).orElseThrow().status());
    }

    @Test
    void aPosterCannotClaimTheirOwnBounty() {
        alexOnSteve(100);
        post(KIM, "Kim", STEVE, "Steve", 200);
        ClaimResult r = service.onKill(ALEX, "Alex", STEVE);
        assertEquals(List.of(2), r.claimed().stream().map(Bounty::id).toList());
        assertEquals(190, r.payout());
        assertEquals(BountyStatus.ACTIVE, service.get(1).orElseThrow().status());
    }

    @Test
    void aPosterKillingTheirOnlyTargetClaimsNothing() {
        alexOnSteve(100);
        ClaimResult r = service.onKill(ALEX, "Alex", STEVE);
        assertTrue(r.claimed().isEmpty());
        assertFalse(r.onCooldown());
        assertEquals(900, eco.balance(ALEX));
    }

    @Test
    void anExpiredBountyCannotBeClaimed() {
        service.create(ALEX, "Alex", STEVE, "Steve", 100, 1);
        clock.advance(Duration.ofMinutes(59).plusSeconds(59));
        assertEquals(1, service.onKill(KIM, "Kim", STEVE).claimed().size());

        FakeEconomy eco2 = new FakeEconomy().set(ALEX, 1000);
        MutableClock clock2 = new MutableClock(T0);
        BountyService s2 = new BountyService(Settings.defaults(), eco2, new MemoryStore(), clock2);
        s2.create(ALEX, "Alex", STEVE, "Steve", 100, 1);
        clock2.advance(Duration.ofHours(1));
        assertTrue(s2.onKill(KIM, "Kim", STEVE).claimed().isEmpty());
    }

    @Test
    void resolvedBountiesCannotBeClaimed() {
        alexOnSteve(100);
        service.cancel(1, ALEX, false);
        assertTrue(service.onKill(KIM, "Kim", STEVE).claimed().isEmpty());
    }

    @Test
    void bountiesOnOtherPlayersAreUntouched() {
        alexOnSteve(100);
        post(ALEX, "Alex", ZED, "Zed", 100);
        service.onKill(KIM, "Kim", STEVE);
        assertEquals(BountyStatus.ACTIVE, service.get(2).orElseThrow().status());
    }

    @Test
    void theSameKillerWaitsForTheCooldownOnTheSameVictim() {
        alexOnSteve(100);
        clock.advance(Duration.ofHours(1));
        assertEquals(1, service.onKill(KIM, "Kim", STEVE).claimed().size());
        post(ZED, "Zed", STEVE, "Steve", 100);
        clock.advance(Duration.ofMinutes(9).plusSeconds(59));
        ClaimResult cooling = service.onKill(KIM, "Kim", STEVE);
        assertTrue(cooling.claimed().isEmpty());
        assertEquals(0, cooling.payout());
        assertTrue(cooling.onCooldown());
        assertEquals(BountyStatus.ACTIVE, service.get(2).orElseThrow().status());
        clock.advance(Duration.ofSeconds(1)); // exactly 10 minutes after the claim
        ClaimResult ok = service.onKill(KIM, "Kim", STEVE);
        assertEquals(1, ok.claimed().size());
        assertFalse(ok.onCooldown());
    }

    @Test
    void theCooldownIsPerKillerAndPerVictim() {
        alexOnSteve(100);
        service.onKill(KIM, "Kim", STEVE);
        post(ALEX, "Alex", STEVE, "Steve", 100);
        assertEquals(1, service.onKill(ZED, "Zed", STEVE).claimed().size()); // another killer
        post(ALEX, "Alex", ZED, "Zed", 100);
        assertEquals(1, service.onKill(KIM, "Kim", ZED).claimed().size()); // another victim
    }

    @Test
    void theCooldownIsNotPersisted() {
        alexOnSteve(100);
        service.onKill(KIM, "Kim", STEVE);
        post(ZED, "Zed", STEVE, "Steve", 100);
        BountyService restarted = new BountyService(Settings.defaults(), eco, store, clock);
        assertEquals(1, restarted.onKill(KIM, "Kim", STEVE).claimed().size());
    }

    @Test
    void usesTheConfiguredTaxAndCooldown() {
        Settings s = new Settings(10, 1_000_000, 3, 24, 72, 0, 10, 5, 80, 7);
        BountyService noTax = new BountyService(s, eco, store, clock);
        noTax.create(ALEX, "Alex", STEVE, "Steve", 100, null);
        assertEquals(100, noTax.onKill(KIM, "Kim", STEVE).payout());
    }

    // ---- expireDue ----

    @Test
    void expireDueDoesNothingWhenNothingIsDue() {
        service.create(ALEX, "Alex", STEVE, "Steve", 100, 1);
        clock.advance(Duration.ofMinutes(59));
        int saves = store.saves;
        assertEquals(0, service.expireDue());
        assertEquals(saves, store.saves);
        assertEquals(BountyStatus.ACTIVE, service.get(1).orElseThrow().status());
    }

    @Test
    void expireDueRefundsThePosterInFullAndLeavesANotice() {
        service.create(ALEX, "Alex", STEVE, "Steve", 100, 1);
        clock.advance(Duration.ofHours(1));
        int saves = store.saves;
        assertEquals(1, service.expireDue());
        Bounty b = service.get(1).orElseThrow();
        assertEquals(BountyStatus.EXPIRED, b.status());
        assertEquals(clock.instant(), b.resolvedAt());
        assertEquals(1000, eco.balance(ALEX));
        assertEquals(saves + 1, store.saves);
        assertEquals(List.of(new Notice(ALEX, NoticeType.EXPIRED_REFUNDED, 1, 100, "Steve")), service.takeNotices(ALEX));
        assertEquals(0, service.expireDue()); // nothing is refunded twice
        assertEquals(1000, eco.balance(ALEX));
    }

    @Test
    void expireDueHandlesSeveralInIdOrder() {
        service.create(ALEX, "Alex", STEVE, "Steve", 100, 1);
        service.create(ZED, "Zed", KIM, "Kim", 50, 2);
        service.create(ALEX, "Alex", KIM, "Kim", 70, 48);
        clock.advance(Duration.ofHours(3));
        assertEquals(2, service.expireDue());
        assertEquals(List.of(new Notice(ALEX, NoticeType.EXPIRED_REFUNDED, 1, 100, "Steve")), service.takeNotices(ALEX));
        assertEquals(List.of(new Notice(ZED, NoticeType.EXPIRED_REFUNDED, 2, 50, "Kim")), service.takeNotices(ZED));
        assertEquals(BountyStatus.ACTIVE, service.get(3).orElseThrow().status());
    }

    @Test
    void expireDuePurgesOldResolvedBountiesButNotActiveOnes() {
        alexOnSteve(100);
        service.cancel(1, ALEX, false);
        alexOnSteve(100);
        clock.advance(Duration.ofDays(7));
        service.expireDue();
        assertTrue(service.get(1).isPresent()); // exactly 7 days: not "more than"
        clock.advance(Duration.ofSeconds(1));
        int saves = store.saves;
        assertEquals(0, service.expireDue());
        assertTrue(service.get(1).isEmpty());
        assertTrue(service.get(2).isPresent());
        assertEquals(saves + 1, store.saves); // a purge alone saves
        assertEquals(1, store.data.bounties().size());
    }

    @Test
    void anExpiredBountyIsNotListedEvenBeforeExpireDueRuns() {
        service.create(ALEX, "Alex", STEVE, "Steve", 100, 1);
        clock.advance(Duration.ofHours(2));
        assertTrue(service.active().isEmpty());
        assertEquals(0, service.totalOn(STEVE));
    }

    // ---- queries ----

    @Test
    void activeIsSortedByAmountThenAgeThenId() {
        Settings s = new Settings(10, 1_000_000, 10, 24, 72, 5, 10, 5, 80, 7);
        BountyService big = new BountyService(s, eco, store, clock);
        big.create(ALEX, "Alex", STEVE, "Steve", 50, null); // 1
        clock.advance(Duration.ofMinutes(1));
        big.create(ALEX, "Alex", STEVE, "Steve", 300, null); // 2
        big.create(ALEX, "Alex", KIM, "Kim", 300, null); // 3 same time as 2
        clock.advance(Duration.ofMinutes(1));
        big.create(ALEX, "Alex", KIM, "Kim", 300, null); // 4 later
        big.create(ALEX, "Alex", KIM, "Kim", 10, null); // 5
        assertEquals(List.of(2, 3, 4, 1, 5), big.active().stream().map(Bounty::id).toList());
    }

    @Test
    void activeOnActiveByAndTotalOn() {
        alexOnSteve(100);
        post(ZED, "Zed", STEVE, "Steve", 250);
        post(ALEX, "Alex", KIM, "Kim", 40);
        assertEquals(List.of(2, 1), service.activeOn(STEVE).stream().map(Bounty::id).toList());
        assertEquals(List.of(1, 3), service.activeBy(ALEX).stream().map(Bounty::id).toList());
        assertEquals(350, service.totalOn(STEVE));
        assertEquals(0, service.totalOn(ALEX));
        service.cancel(2, ZED, false);
        assertEquals(100, service.totalOn(STEVE));
    }

    @Test
    void pagesTheActiveBounties() {
        Settings s = new Settings(10, 1_000_000, 100, 24, 72, 5, 10, 5, 80, 7);
        BountyService big = new BountyService(s, eco.set(ALEX, 1_000_000), store, clock);
        for (int i = 0; i < 25; i++) {
            big.create(ALEX, "Alex", STEVE, "Steve", 10 + i, null);
        }
        Page<Bounty> p1 = big.page(1, 10);
        assertEquals(10, p1.items().size());
        assertEquals(1, p1.page());
        assertEquals(3, p1.pages());
        assertEquals(25, p1.total());
        assertEquals(34, p1.items().get(0).amount());
        Page<Bounty> p3 = big.page(3, 10);
        assertEquals(5, p3.items().size());
        assertEquals(1, big.page(0, 10).page());
        assertEquals(3, big.page(99, 10).page());
        assertEquals(5, big.page(99, 10).items().size());
        assertThrows(IllegalArgumentException.class, () -> big.page(1, 0));
    }

    @Test
    void anEmptyPageIsPageOneOfOne() {
        Page<Bounty> p = service.page(5, 10);
        assertTrue(p.items().isEmpty());
        assertEquals(1, p.page());
        assertEquals(1, p.pages());
        assertEquals(0, p.total());
    }

    @Test
    void getFindsResolvedBountiesToo() {
        assertTrue(service.get(1).isEmpty());
        alexOnSteve(100);
        service.cancel(1, ALEX, false);
        assertEquals(BountyStatus.CANCELLED, service.get(1).orElseThrow().status());
    }

    @Test
    void takeNoticesReturnsInOrderAndRemoves() {
        alexOnSteve(100);
        post(ALEX, "Alex", KIM, "Kim", 100);
        service.onKill(ZED, "Zed", STEVE);
        service.onKill(ZED, "Zed", KIM);
        int saves = store.saves;
        List<Notice> notices = service.takeNotices(ALEX);
        assertEquals(List.of(1, 2), notices.stream().map(Notice::bountyId).toList());
        assertEquals(saves + 1, store.saves);
        assertTrue(service.takeNotices(ALEX).isEmpty());
        assertEquals(saves + 1, store.saves); // taking nothing does not save
        assertTrue(store.data.notices().isEmpty());
    }

    @Test
    void takeNoticesLeavesOtherPlayersNoticesAlone() {
        alexOnSteve(100);
        post(ZED, "Zed", STEVE, "Steve", 100);
        service.onKill(KIM, "Kim", STEVE);
        service.takeNotices(ALEX);
        assertEquals(1, service.takeNotices(ZED).size());
    }

    // ---- loading and saving ----

    @Test
    void loadsExistingDataAndContinuesTheIds() {
        Bounty existing = new Bounty(4, STEVE, "Steve", ALEX, "Alex", 100, T0, T0.plus(Duration.ofHours(10)), BountyStatus.ACTIVE, null, null);
        Notice notice = new Notice(ALEX, NoticeType.EXPIRED_REFUNDED, 3, 50, "Kim");
        MemoryStore preloaded = new MemoryStore(new StoreData(List.of(existing), List.of(notice), 5));
        BountyService s = new BountyService(Settings.defaults(), eco, preloaded, clock);
        assertEquals(List.of(existing), s.active());
        assertEquals(5, s.create(ZED, "Zed", KIM, "Kim", 100, null).bounty().id());
        assertEquals(List.of(notice), s.takeNotices(ALEX));
    }

    @Test
    void savesEverythingIncludingResolvedBountiesAndNotices() {
        alexOnSteve(100);
        post(ZED, "Zed", KIM, "Kim", 50);
        service.cancel(2, ZED, false);
        service.onKill(KIM, "Kim", STEVE);
        StoreData data = store.data;
        assertEquals(2, data.bounties().size());
        assertEquals(List.of(BountyStatus.CLAIMED, BountyStatus.CANCELLED),
                data.bounties().stream().sorted(java.util.Comparator.comparingInt(Bounty::id)).map(Bounty::status).toList());
        assertEquals(3, data.nextId());
        assertEquals(1, data.notices().size());
    }

    @Test
    void queriesNeverSaveOrMoveMoney() {
        alexOnSteve(100);
        int saves = store.saves;
        int deposits = eco.depositCalls;
        int withdraws = eco.withdrawCalls;
        service.get(1);
        service.active();
        service.activeOn(STEVE);
        service.activeBy(ALEX);
        service.totalOn(STEVE);
        service.page(1, 10);
        service.takeNotices(ALEX);
        assertEquals(saves, store.saves);
        assertEquals(deposits, eco.depositCalls);
        assertEquals(withdraws, eco.withdrawCalls);
    }

    @Test
    void theServiceKeepsItsStateInMemoryAfterLoading() {
        alexOnSteve(100);
        int loads = store.loads;
        service.active();
        service.get(1);
        alexOnSteve(100);
        assertEquals(loads, store.loads);
        assertEquals(new ArrayList<>(store.data.bounties()).size(), service.active().size());
    }
}
