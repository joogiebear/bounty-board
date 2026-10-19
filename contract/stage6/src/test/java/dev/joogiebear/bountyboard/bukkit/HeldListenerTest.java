package dev.joogiebear.bountyboard.bukkit;

import static dev.joogiebear.bountyboard.core.HeldSupport.T0;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.joogiebear.bountyboard.core.BountyStatus;
import dev.joogiebear.bountyboard.core.CreateStatus;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class HeldListenerTest extends HeldBase {

    private static final String P = "[Bounty] ";

    private void post(PlayerMock poster, PlayerMock target, long amount) {
        assertEquals(CreateStatus.OK, plugin.service().create(poster.getUniqueId(), poster.getName(), target.getUniqueId(), target.getName(), amount, null).status());
    }

    @Test
    void killerIsPaidAndMessagedBeforeTheBroadcastAndPostersAreNotifiedAtOnce() {
        PlayerMock alice = player("Alice");
        PlayerMock bob = player("Bob");
        PlayerMock carol = player("Carol");
        post(alice, bob, 100);
        drain(alice);
        drain(bob);
        drain(carol);
        kill(carol, bob);
        assertEquals(List.of(P + "You collected 95 coins (5 tax).", P + "Carol claimed 100 coins of bounties on Bob!"), bounty(carol));
        assertEquals(List.of(P + "Carol claimed 100 coins of bounties on Bob!", P + "Your bounty #1 was claimed by Carol."), bounty(alice));
        assertEquals(1095, plugin.economy().balance(carol.getUniqueId()));
        assertEquals(900, plugin.economy().balance(alice.getUniqueId()));
        assertEquals(BountyStatus.CLAIMED, plugin.service().get(1).orElseThrow().status());
        assertTrue(plugin.service().takeNotices(alice.getUniqueId()).isEmpty(), "notices already delivered must not be delivered again");
    }

    @Test
    void severalBountiesBroadcastTheSumAndNotifyEachPoster() {
        PlayerMock alice = player("Alice");
        PlayerMock bob = player("Bob");
        PlayerMock carol = player("Carol");
        PlayerMock dave = player("Dave");
        post(alice, bob, 100);
        post(dave, bob, 50);
        post(alice, bob, 70);
        drain(alice);
        drain(dave);
        drain(carol);
        kill(carol, bob);
        assertEquals(List.of(P + "You collected 209 coins (11 tax).", P + "Carol claimed 220 coins of bounties on Bob!"), bounty(carol));
        assertEquals(List.of(P + "Carol claimed 220 coins of bounties on Bob!", P + "Your bounty #1 was claimed by Carol.", P + "Your bounty #3 was claimed by Carol."), bounty(alice));
        assertEquals(List.of(P + "Carol claimed 220 coins of bounties on Bob!", P + "Your bounty #2 was claimed by Carol."), bounty(dave));
    }

    @Test
    void deathWithoutAPlayerKillerClaimsNothing() {
        PlayerMock alice = player("Alice");
        PlayerMock bob = player("Bob");
        post(alice, bob, 100);
        drain(alice);
        bob.setHealth(0);
        assertEquals(BountyStatus.ACTIVE, plugin.service().get(1).orElseThrow().status());
        assertTrue(bounty(alice).isEmpty());
        assertEquals(900, plugin.economy().balance(alice.getUniqueId()));
    }

    @Test
    void killingSomeoneWithoutBountiesSendsNothing() {
        PlayerMock bob = player("Bob");
        PlayerMock carol = player("Carol");
        drain(bob);
        drain(carol);
        kill(carol, bob);
        assertTrue(bounty(carol).isEmpty());
        assertTrue(bounty(bob).isEmpty());
        assertEquals(1000, plugin.economy().balance(carol.getUniqueId()));
    }

    @Test
    void posterKillingTheirOwnTargetGetsNothing() {
        PlayerMock alice = player("Alice");
        PlayerMock bob = player("Bob");
        post(alice, bob, 100);
        drain(alice);
        kill(alice, bob);
        assertTrue(bounty(alice).isEmpty());
        assertEquals(BountyStatus.ACTIVE, plugin.service().get(1).orElseThrow().status());
        assertEquals(900, plugin.economy().balance(alice.getUniqueId()));
    }

    @Test
    void severalKillersInARowRespectTheCooldownPerKiller() {
        PlayerMock alice = player("Alice");
        PlayerMock bob = player("Bob");
        PlayerMock carol = player("Carol");
        PlayerMock dave = player("Dave");
        post(alice, bob, 100);
        kill(carol, bob);
        drain(carol);
        drain(dave);
        post(alice, bob, 100);
        clock.advance(Duration.ofMinutes(9));
        kill(carol, bob);
        assertEquals(List.of(P + "You recently claimed a bounty on Bob; try again later."), bounty(carol));
        assertEquals(BountyStatus.ACTIVE, plugin.service().get(2).orElseThrow().status());
        assertTrue(bounty(dave).isEmpty(), "a cooldown hit is not broadcast");
        kill(dave, bob);
        assertEquals(List.of(P + "You collected 95 coins (5 tax).", P + "Dave claimed 100 coins of bounties on Bob!"), bounty(dave));
        post(alice, bob, 100);
        clock.advance(Duration.ofMinutes(1));
        drain(carol);
        kill(carol, bob);
        assertEquals(List.of(P + "You collected 95 coins (5 tax).", P + "Carol claimed 100 coins of bounties on Bob!"), bounty(carol));
    }

    @Test
    void expiredButUnprocessedBountyIsNotPaid() {
        PlayerMock alice = player("Alice");
        PlayerMock bob = player("Bob");
        PlayerMock carol = player("Carol");
        assertEquals(CreateStatus.OK, plugin.service().create(alice.getUniqueId(), "Alice", bob.getUniqueId(), "Bob", 100, 1).status());
        clock.advance(Duration.ofHours(1));
        drain(carol);
        kill(carol, bob);
        assertTrue(bounty(carol).isEmpty());
        assertEquals(1000, plugin.economy().balance(carol.getUniqueId()));
    }

    @Test
    void offlinePosterGetsTheNoticeOnJoinThenTheBountyOnTheirHeadWarning() {
        PlayerMock alice = player("Alice");
        PlayerMock bob = player("Bob");
        PlayerMock carol = player("Carol");
        UUID aliceId = alice.getUniqueId();
        post(alice, bob, 100);
        alice.disconnect();
        kill(carol, bob);
        // a bounty on Alice herself, so that the join also shows the warning
        post(bob, alice, 60);
        post(carol, alice, 40);
        PlayerMock back = new PlayerMock(server, "Alice", aliceId);
        server.addPlayer(back);
        assertEquals(List.of(P + "Your bounty #1 was claimed by Carol.", P + "There is a 100 coin bounty on your head!"), bounty(back));
        back.disconnect();
        PlayerMock again = new PlayerMock(server, "Alice", aliceId);
        server.addPlayer(again);
        assertEquals(List.of(P + "There is a 100 coin bounty on your head!"), bounty(again), "notices are delivered once");
    }

    @Test
    void joiningWithoutBountiesOrNoticesIsSilent() {
        PlayerMock alice = player("Alice");
        assertTrue(bounty(alice).isEmpty());
    }

    @Test
    void expiredNoticesArriveInCreationOrderOnJoin() {
        UUID poster = UUID.randomUUID();
        UUID t1 = UUID.randomUUID();
        UUID t2 = UUID.randomUUID();
        plugin.economy().deposit(poster, 1000);
        plugin.service().create(poster, "Pete", t1, "First", 30, 1);
        plugin.service().create(poster, "Pete", t2, "Second", 40, 1);
        clock.advance(Duration.ofHours(1));
        assertEquals(2, plugin.service().expireDue());
        PlayerMock pete = new PlayerMock(server, "Pete", poster);
        server.addPlayer(pete);
        assertEquals(List.of(P + "Your bounty #1 on First expired; 30 coins refunded.", P + "Your bounty #2 on Second expired; 40 coins refunded."), bounty(pete));
        assertEquals(2000, plugin.economy().balance(poster), "1000 starting balance + 1000 deposited, everything refunded");
    }

    @Test
    void expiryTaskRefundsAndNotifiesOnlinePostersStraightAway() {
        PlayerMock alice = player("Alice");
        PlayerMock bob = player("Bob");
        assertEquals(CreateStatus.OK, plugin.service().create(alice.getUniqueId(), "Alice", bob.getUniqueId(), "Bob", 100, 1).status());
        drain(alice);
        server.getScheduler().performTicks(1200);
        assertTrue(bounty(alice).isEmpty(), "nothing is due yet");
        clock.advance(Duration.ofHours(1)); // exactly at expiresAt
        server.getScheduler().performTicks(1200);
        assertEquals(List.of(P + "Your bounty #1 on Bob expired; 100 coins refunded."), bounty(alice));
        assertEquals(1000, plugin.economy().balance(alice.getUniqueId()));
        assertEquals(BountyStatus.EXPIRED, plugin.service().get(1).orElseThrow().status());
        server.getScheduler().performTicks(1200);
        assertTrue(bounty(alice).isEmpty(), "no duplicate notices on later runs");
    }

    @Test
    void expiryTaskKeepsOfflinePostersNoticesForTheirJoin() {
        PlayerMock alice = player("Alice");
        PlayerMock bob = player("Bob");
        UUID id = alice.getUniqueId();
        assertEquals(CreateStatus.OK, plugin.service().create(id, "Alice", bob.getUniqueId(), "Bob", 100, 1).status());
        alice.disconnect();
        clock.set(T0.plus(Duration.ofHours(2)));
        server.getScheduler().performTicks(1200);
        assertEquals(1000, plugin.economy().balance(id), "refund happens even though the poster is offline");
        PlayerMock back = new PlayerMock(server, "Alice", id);
        server.addPlayer(back);
        assertEquals(List.of(P + "Your bounty #1 on Bob expired; 100 coins refunded."), bounty(back));
    }
}
