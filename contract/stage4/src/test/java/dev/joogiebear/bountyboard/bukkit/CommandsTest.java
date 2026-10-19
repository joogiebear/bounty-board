package dev.joogiebear.bountyboard.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.joogiebear.bountyboard.core.Bounty;
import dev.joogiebear.bountyboard.core.BountyStatus;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommandsTest extends PluginTestBase {

    static final String USAGE = "[Bounty] Usage: /bounty <set|list|mine|cancel|balance|gui>";

    // ---- usage ----

    @Test
    void noSubcommandShowsUsage() {
        assertEquals(List.of(USAGE), run(alex, "bounty"));
    }

    @Test
    void wrongArgumentCountsShowUsage() {
        assertEquals(List.of(USAGE), run(alex, "bounty set"));
        assertEquals(List.of(USAGE), run(alex, "bounty set Steve"));
        assertEquals(List.of(USAGE), run(alex, "bounty set Steve 100 2 3"));
        assertEquals(List.of(USAGE), run(alex, "bounty cancel"));
        assertEquals(List.of(USAGE), run(alex, "bounty cancel 1 2"));
        assertEquals(List.of(USAGE), run(alex, "bounty mine extra"));
        assertEquals(List.of(USAGE), run(alex, "bounty balance extra"));
        assertEquals(List.of(USAGE), run(alex, "bounty list 1 2"));
        assertEquals(List.of(USAGE), run(alex, "bounty nonsense"));
    }

    // ---- set ----

    @Test
    void setPostsABountyAndBroadcastsIt() {
        alex.performCommand("bounty set Steve 100");
        assertEquals(List.of("[Bounty] Bounty #1 posted on Steve for 100 coins.", "[Bounty] Alex posted a 100 coin bounty on Steve!"), drain(alex));
        assertEquals(List.of("[Bounty] Alex posted a 100 coin bounty on Steve!"), drain(steve));
        assertEquals(List.of("[Bounty] Alex posted a 100 coin bounty on Steve!"), drain(kim));
        assertEquals(900, plugin.economy().balance(alex.getUniqueId()));
        assertEquals(1, plugin.service().active().size());
        Bounty b = plugin.service().get(1).orElseThrow();
        assertEquals(steve.getUniqueId(), b.target());
        assertEquals("Steve", b.targetName());
        assertEquals(alex.getUniqueId(), b.poster());
        assertEquals("Alex", b.posterName());
        assertEquals(clock.instant().plus(Duration.ofHours(24)), b.expiresAt());
    }

    @Test
    void setFormatsLargeAmountsWithSeparators() {
        plugin.economy().deposit(alex.getUniqueId(), 10_000);
        assertEquals(List.of("[Bounty] Bounty #1 posted on Steve for 5,000 coins.", "[Bounty] Alex posted a 5,000 coin bounty on Steve!"),
                run(alex, "bounty set Steve 5000"));
    }

    @Test
    void setUsesTheGivenHours() {
        cmd(alex, "bounty set Steve 100 2");
        assertEquals(clock.instant().plus(Duration.ofHours(2)), plugin.service().get(1).orElseThrow().expiresAt());
        assertEquals("Steve", plugin.service().get(1).orElseThrow().targetName());
    }

    @Test
    void setReportsEachFailure() {
        assertEquals(List.of("[Bounty] You cannot place a bounty on yourself."), run(alex, "bounty set Alex 100"));
        assertEquals(List.of("[Bounty] The minimum bounty is 10 coins."), run(alex, "bounty set Steve 5"));
        assertEquals(List.of("[Bounty] The minimum bounty is 10 coins."), run(alex, "bounty set Steve 0"));
        assertEquals(List.of("[Bounty] The maximum bounty is 1,000,000 coins."), run(alex, "bounty set Steve 2000000"));
        assertEquals(List.of("[Bounty] You need 2,000 coins, you have 1,000."), run(alex, "bounty set Steve 2000"));
        assertEquals(List.of("[Bounty] Duration must be between 1 and 72 hours."), run(alex, "bounty set Steve 100 100"));
        assertEquals(List.of("[Bounty] Duration must be between 1 and 72 hours."), run(alex, "bounty set Steve 100 0"));
        assertEquals(List.of("[Bounty] Unknown player: Nobody"), run(alex, "bounty set Nobody 100"));
        assertEquals(1000, plugin.economy().balance(alex.getUniqueId()));
        assertTrue(plugin.service().active().isEmpty());
    }

    @Test
    void setRejectsNumbersThatAreNotWholeNonNegativeNumbers() {
        for (String amount : List.of("abc", "-5", "1.5", "1e3", "ten")) {
            assertEquals(List.of("[Bounty] That is not a valid number."), run(alex, "bounty set Steve " + amount), amount);
        }
        assertEquals(List.of("[Bounty] That is not a valid number."), run(alex, "bounty set Steve 100 x"));
        assertEquals(List.of("[Bounty] That is not a valid number."), run(alex, "bounty set Steve 100 1.5"));
    }

    @Test
    void setLimitsTheNumberOfActiveBounties() {
        cmd(alex, "bounty set Steve 10");
        cmd(alex, "bounty set Steve 10");
        cmd(alex, "bounty set Kim 10");
        assertEquals(List.of("[Bounty] You already have 3 active bounties."), run(alex, "bounty set Kim 10"));
    }

    // ---- list and mine ----

    @Test
    void listWithNoBounties() {
        assertEquals(List.of("[Bounty] There are no active bounties."), run(alex, "bounty list"));
    }

    @Test
    void listShowsBountiesBiggestFirst() {
        cmd(alex, "bounty set Steve 100");
        cmd(kim, "bounty set Steve 300 2");
        assertEquals(List.of("[Bounty] Bounties (page 1/1, 2 total)", "[Bounty] #2 Steve - 300 coins (by Kim, 2h left)",
                "[Bounty] #1 Steve - 100 coins (by Alex, 1d left)"), run(steve, "bounty list"));
    }

    @Test
    void listCountsDownTheTimeLeft() {
        cmd(alex, "bounty set Steve 100 1");
        clock.advance(Duration.ofMinutes(25));
        assertEquals(List.of("[Bounty] Bounties (page 1/1, 1 total)", "[Bounty] #1 Steve - 100 coins (by Alex, 35m left)"), run(kim, "bounty list"));
    }

    @Test
    void listPagesTenAtATime() {
        seed(12, 100);
        List<String> page1 = run(alex, "bounty list");
        assertEquals(11, page1.size());
        assertEquals("[Bounty] Bounties (page 1/2, 12 total)", page1.get(0));
        List<String> page2 = run(alex, "bounty list 2");
        assertEquals(3, page2.size());
        assertEquals("[Bounty] Bounties (page 2/2, 12 total)", page2.get(0));
        assertEquals("[Bounty] Bounties (page 2/2, 12 total)", run(alex, "bounty list 99").get(0)); // clamped
        assertEquals(List.of("[Bounty] That is not a valid number."), run(alex, "bounty list abc"));
        assertEquals(List.of("[Bounty] That is not a valid number."), run(alex, "bounty list 0"));
    }

    @Test
    void mineListsOnlyMyActiveBounties() {
        assertEquals(List.of("[Bounty] You have no active bounties."), run(alex, "bounty mine"));
        cmd(alex, "bounty set Steve 100");
        cmd(kim, "bounty set Steve 300");
        cmd(alex, "bounty set Kim 50 3");
        assertEquals(List.of("[Bounty] #1 Steve - 100 coins (1d left)", "[Bounty] #3 Kim - 50 coins (3h left)"), run(alex, "bounty mine"));
    }

    // ---- cancel ----

    @Test
    void cancelWithinFiveMinutesRefundsEverything() {
        cmd(alex, "bounty set Steve 100");
        clock.advance(Duration.ofMinutes(5));
        assertEquals(List.of("[Bounty] Bounty #1 cancelled, 100 coins refunded."), run(alex, "bounty cancel 1"));
        assertEquals(1000, plugin.economy().balance(alex.getUniqueId()));
        assertEquals(BountyStatus.CANCELLED, plugin.service().get(1).orElseThrow().status());
    }

    @Test
    void cancelLaterRefundsEightyPercent() {
        cmd(alex, "bounty set Steve 100");
        clock.advance(Duration.ofMinutes(6));
        assertEquals(List.of("[Bounty] Bounty #1 cancelled, 80 coins refunded."), run(alex, "bounty cancel 1"));
        assertEquals(980, plugin.economy().balance(alex.getUniqueId()));
    }

    @Test
    void cancelReportsEachFailure() {
        cmd(alex, "bounty set Steve 100");
        assertEquals(List.of("[Bounty] There is no bounty #99."), run(alex, "bounty cancel 99"));
        assertEquals(List.of("[Bounty] Bounty #1 is not yours."), run(kim, "bounty cancel 1"));
        cmd(alex, "bounty cancel 1");
        assertEquals(List.of("[Bounty] Bounty #1 is no longer active."), run(alex, "bounty cancel 1"));
        assertEquals(List.of("[Bounty] That is not a valid number."), run(alex, "bounty cancel abc"));
    }

    @Test
    void anAdminCanCancelAnyBountyAndTheOwnerIsRefundedInFull() {
        cmd(alex, "bounty set Steve 100");
        clock.advance(Duration.ofHours(2));
        kim.setOp(true);
        assertEquals(List.of("[Bounty] Bounty #1 cancelled, 100 coins refunded."), run(kim, "bounty cancel 1"));
        assertEquals(1000, plugin.economy().balance(alex.getUniqueId()));
        assertEquals(1000, plugin.economy().balance(kim.getUniqueId()));
    }

    // ---- balance and admin ----

    @Test
    void balanceShowsTheCoins() {
        assertEquals(List.of("[Bounty] Balance: 1,000 coins"), run(alex, "bounty balance"));
        cmd(alex, "bounty set Steve 250");
        assertEquals(List.of("[Bounty] Balance: 750 coins"), run(alex, "bounty balance"));
    }

    @Test
    void adminGiveAddsCoins() {
        kim.setOp(true);
        assertEquals(List.of("[Bounty] Gave 500 coins to Alex."), run(kim, "bounty admin give Alex 500"));
        assertEquals(1500, plugin.economy().balance(alex.getUniqueId()));
        assertEquals(List.of("[Bounty] That is not a valid number."), run(kim, "bounty admin give Alex lots"));
        assertEquals(List.of("[Bounty] Unknown player: Nobody"), run(kim, "bounty admin give Nobody 5"));
        assertEquals(List.of(USAGE), run(kim, "bounty admin give Alex"));
        assertEquals(List.of(USAGE), run(kim, "bounty admin"));
    }

    @Test
    void adminCommandsNeedThePermission() {
        assertEquals(List.of("[Bounty] You do not have permission."), run(alex, "bounty admin give Alex 500"));
        assertEquals(List.of("[Bounty] You do not have permission."), run(alex, "bounty admin reload"));
        assertEquals(1000, plugin.economy().balance(alex.getUniqueId()));
    }

    @Test
    void adminReloadConfirms() {
        kim.setOp(true);
        assertEquals(List.of("[Bounty] Configuration reloaded."), run(kim, "bounty admin reload"));
    }

    @Test
    void theConsoleCanUseAdminGive() {
        server.dispatchCommand(server.getConsoleSender(), "bounty admin give Alex 500");
        assertEquals(1500, plugin.economy().balance(alex.getUniqueId()));
    }

    @Test
    void theConsoleCannotUsePlayerOnlyCommands() {
        server.dispatchCommand(server.getConsoleSender(), "bounty set Steve 100");
        assertTrue(plugin.service().active().isEmpty());
    }
}
