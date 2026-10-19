package dev.joogiebear.bountyboard.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.joogiebear.bountyboard.core.BountyStatus;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class ClaimsTest extends PluginTestBase {

    // ---- kills ----

    @Test
    void killingTheTargetPaysTheKillerAndTellsEveryone() {
        cmd(alex, "bounty set Steve 100");
        kill(steve, kim);
        assertEquals(List.of("[Bounty] You collected 95 coins (5 tax).", "[Bounty] Kim claimed 100 coins of bounties on Steve!"), drain(kim));
        assertEquals(1095, plugin.economy().balance(kim.getUniqueId()));
        assertEquals(BountyStatus.CLAIMED, plugin.service().get(1).orElseThrow().status());
        assertEquals(kim.getUniqueId(), plugin.service().get(1).orElseThrow().claimedBy());
    }

    @Test
    void theBroadcastGoesToEveryoneAndThePosterGetsTheirNoticeAtOnce() {
        cmd(alex, "bounty set Steve 100");
        kill(steve, kim);
        assertEquals(List.of("[Bounty] Kim claimed 100 coins of bounties on Steve!", "[Bounty] Your bounty #1 was claimed by Kim."), drain(alex));
        assertEquals(List.of("[Bounty] Kim claimed 100 coins of bounties on Steve!"), drain(steve));
        assertTrue(plugin.service().takeNotices(alex.getUniqueId()).isEmpty()); // already delivered
    }

    @Test
    void severalBountiesAreClaimedTogether() {
        PlayerMock zed = server.addPlayer("Zed");
        drain(zed);
        cmd(alex, "bounty set Steve 100");
        cmd(zed, "bounty set Steve 250");
        drain(zed); // Zed also saw the posting broadcasts
        kill(steve, kim);
        assertEquals(List.of("[Bounty] You collected 333 coins (17 tax).", "[Bounty] Kim claimed 350 coins of bounties on Steve!"), drain(kim));
        assertEquals(1333, plugin.economy().balance(kim.getUniqueId()));
        assertEquals(List.of("[Bounty] Kim claimed 350 coins of bounties on Steve!", "[Bounty] Your bounty #1 was claimed by Kim."), drain(alex));
        assertEquals(List.of("[Bounty] Kim claimed 350 coins of bounties on Steve!", "[Bounty] Your bounty #2 was claimed by Kim."), drain(zed));
    }

    @Test
    void aDeathWithoutAPlayerKillerClaimsNothing() {
        cmd(alex, "bounty set Steve 100");
        kill(steve, null);
        assertTrue(drain(alex).isEmpty());
        assertTrue(drain(kim).isEmpty());
        assertEquals(BountyStatus.ACTIVE, plugin.service().get(1).orElseThrow().status());
    }

    @Test
    void thePosterCannotClaimTheirOwnBounty() {
        cmd(alex, "bounty set Steve 100");
        kill(steve, alex);
        assertTrue(drain(alex).isEmpty());
        assertEquals(900, plugin.economy().balance(alex.getUniqueId()));
        assertEquals(BountyStatus.ACTIVE, plugin.service().get(1).orElseThrow().status());
    }

    @Test
    void killingSomeoneWithoutABountyDoesNothing() {
        cmd(alex, "bounty set Kim 100");
        kill(steve, alex);
        assertTrue(drain(alex).isEmpty());
        assertTrue(drain(steve).isEmpty());
    }

    @Test
    void theCooldownBlocksAnImmediateSecondClaimOnTheSameVictim() {
        cmd(alex, "bounty set Steve 100");
        kill(steve, kim);
        drain(kim);
        cmd(alex, "bounty set Steve 100");
        kill(steve, kim);
        assertEquals(List.of("[Bounty] You recently claimed a bounty on Steve; try again later."), drain(kim));
        assertEquals(BountyStatus.ACTIVE, plugin.service().get(2).orElseThrow().status());
        assertEquals(1095, plugin.economy().balance(kim.getUniqueId()));
    }

    @Test
    void theCooldownEndsAfterTenMinutes() {
        cmd(alex, "bounty set Steve 100");
        kill(steve, kim);
        cmd(alex, "bounty set Steve 100");
        clock.advance(Duration.ofMinutes(10));
        kill(steve, kim);
        assertEquals(List.of("[Bounty] You collected 95 coins (5 tax).", "[Bounty] Kim claimed 100 coins of bounties on Steve!"), drain(kim));
    }

    // ---- joins ----

    @Test
    void aPlayerWithABountyIsToldWhenTheyJoin() {
        cmd(alex, "bounty set Steve 100");
        cmd(kim, "bounty set Steve 250");
        steve.disconnect();
        steve.reconnect();
        assertEquals(List.of("[Bounty] There is a 350 coin bounty on your head!"), drain(steve));
    }

    @Test
    void aPlayerWithoutABountyGetsNoJoinMessage() {
        alex.disconnect();
        alex.reconnect();
        assertTrue(drain(alex).isEmpty());
    }

    @Test
    void anOfflinePostersNoticeIsDeliveredWhenTheyJoin() {
        cmd(alex, "bounty set Steve 100 1");
        alex.disconnect();
        clock.advance(Duration.ofHours(2));
        tick(1200);
        assertEquals(1000, plugin.economy().balance(alex.getUniqueId())); // refunded while offline
        alex.reconnect();
        assertEquals(List.of("[Bounty] Your bounty #1 on Steve expired; 100 coins refunded."), drain(alex));
        alex.disconnect();
        alex.reconnect();
        assertTrue(drain(alex).isEmpty()); // delivered once
    }

    @Test
    void noticesAreDeliveredBeforeTheBountyOnYourHeadMessage() {
        cmd(alex, "bounty set Steve 100 1");
        cmd(steve, "bounty set Alex 50");
        alex.disconnect();
        clock.advance(Duration.ofHours(2));
        tick(1200);
        alex.reconnect();
        List<String> got = drain(alex);
        assertEquals("[Bounty] Your bounty #1 on Steve expired; 100 coins refunded.", got.get(0));
        assertEquals("[Bounty] There is a 50 coin bounty on your head!", got.get(got.size() - 1));
    }

    // ---- the expiry task ----

    @Test
    void theExpiryTaskRefundsAndTellsAnOnlinePosterAtOnce() {
        cmd(alex, "bounty set Steve 100 1");
        assertEquals(900, plugin.economy().balance(alex.getUniqueId()));
        clock.advance(Duration.ofHours(2));
        tick(1200);
        assertEquals(1000, plugin.economy().balance(alex.getUniqueId()));
        assertEquals(List.of("[Bounty] Your bounty #1 on Steve expired; 100 coins refunded."), drain(alex));
        assertEquals(BountyStatus.EXPIRED, plugin.service().get(1).orElseThrow().status());
    }

    @Test
    void theExpiryTaskLeavesUnexpiredBountiesAlone() {
        cmd(alex, "bounty set Steve 100 3");
        clock.advance(Duration.ofHours(1));
        tick(1200);
        assertEquals(BountyStatus.ACTIVE, plugin.service().get(1).orElseThrow().status());
        assertTrue(drain(alex).isEmpty());
    }

    @Test
    void theExpiryTaskKeepsRunning() {
        cmd(alex, "bounty set Steve 100 1");
        tick(1200);
        clock.advance(Duration.ofHours(2));
        tick(1200);
        assertEquals(BountyStatus.EXPIRED, plugin.service().get(1).orElseThrow().status());
    }
}
