package dev.joogiebear.bountyboard.bukkit;

import static dev.joogiebear.bountyboard.core.HeldSupport.T0;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.joogiebear.bountyboard.core.Bounty;
import dev.joogiebear.bountyboard.core.BountyStatus;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class HeldCommandTest extends HeldBase {

    private static final String P = "[Bounty] ";

    // ---------------------------------------------------------------- usage / argument counts

    @Test
    void noSubcommandShowsUsage() {
        PlayerMock alice = player("Alice");
        assertEquals(List.of(P + "Usage: /bounty <set|list|mine|cancel|balance|gui>"), run(alice, "bounty"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"bounty set", "bounty set Bob", "bounty set Bob 100 5 6", "bounty list 1 2", "bounty mine extra",
            "bounty balance extra", "bounty gui extra", "bounty cancel", "bounty cancel 1 2"})
    void wrongArgumentCountShowsUsage(String line) {
        PlayerMock alice = player("Alice");
        player("Bob");
        assertEquals(List.of(P + "Usage: /bounty <set|list|mine|cancel|balance|gui>"), run(alice, line), line);
        assertTrue(plugin.service().active().isEmpty());
    }

    @Test
    void adminWithWrongArgumentCountShowsUsage() {
        PlayerMock op = player("Root");
        op.setOp(true);
        player("Bob");
        String usage = P + "Usage: /bounty <set|list|mine|cancel|balance|gui>";
        for (String line : new String[] {"bounty admin", "bounty admin give", "bounty admin give Bob", "bounty admin give Bob 5 6", "bounty admin reload now"}) {
            assertEquals(List.of(usage), run(op, line), line);
        }
    }

    // ---------------------------------------------------------------- set

    @Test
    void setPostsEscrowsAndAnnouncesInOrder() {
        PlayerMock alice = player("Alice");
        PlayerMock bob = player("Bob");
        drain(bob);
        List<String> msgs = run(alice, "bounty set Bob 100");
        assertEquals(List.of(P + "Bounty #1 posted on Bob for 100 coins.", P + "Alice posted a 100 coin bounty on Bob!"), msgs);
        assertEquals(1000 - 100, plugin.economy().balance(alice.getUniqueId()));
        assertEquals(List.of(P + "Alice posted a 100 coin bounty on Bob!"), bounty(bob));
        Bounty b = plugin.service().get(1).orElseThrow();
        assertEquals(bob.getUniqueId(), b.target());
        assertEquals("Bob", b.targetName());
        assertEquals(alice.getUniqueId(), b.poster());
        assertEquals(T0.plus(Duration.ofHours(24)), b.expiresAt());
    }

    @Test
    void setWithExplicitHours() {
        PlayerMock alice = player("Alice");
        player("Bob");
        run(alice, "bounty set Bob 50 2");
        assertEquals(T0.plus(Duration.ofHours(2)), plugin.service().get(1).orElseThrow().expiresAt());
    }

    @Test
    void setFailureMessages() {
        PlayerMock alice = player("Alice");
        player("Bob");
        assertEquals(List.of(P + "You cannot place a bounty on yourself."), run(alice, "bounty set Alice 100"));
        assertEquals(List.of(P + "The minimum bounty is 10 coins."), run(alice, "bounty set Bob 9"));
        assertEquals(List.of(P + "The minimum bounty is 10 coins."), run(alice, "bounty set Bob 0"));
        assertEquals(List.of(P + "Duration must be between 1 and 72 hours."), run(alice, "bounty set Bob 100 0"));
        assertEquals(List.of(P + "Duration must be between 1 and 72 hours."), run(alice, "bounty set Bob 100 73"));
        List<String> low = run(alice, "bounty set Bob 99999999999");
        assertEquals(1, low.size());
        assertTrue(low.get(0).matches("\\[Bounty\\] The maximum bounty is 1,?000,?000 coins\\."), low.get(0));
        List<String> funds = run(alice, "bounty set Bob 2000");
        assertEquals(1, funds.size());
        assertTrue(funds.get(0).matches("\\[Bounty\\] You need 2,?000 coins, you have 1,?000\\."), funds.get(0));
        assertEquals(1000, plugin.economy().balance(alice.getUniqueId()));
        assertTrue(plugin.service().active().isEmpty());
    }

    @Test
    void setRefusesTooManyActive() {
        PlayerMock alice = player("Alice");
        player("Bob");
        for (int i = 0; i < 3; i++) run(alice, "bounty set Bob 10");
        assertEquals(List.of(P + "You already have 3 active bounties."), run(alice, "bounty set Bob 10"));
        assertEquals(3, plugin.service().activeBy(alice.getUniqueId()).size());
    }

    @Test
    void hugeAmountDoesNotOverflowIntoAValidOne() {
        PlayerMock alice = player("Alice");
        player("Bob");
        // 4294967297 is 2^32 + 1: a 32 bit truncation would turn it into 1 (too low), a clamp could turn it valid
        List<String> msgs = run(alice, "bounty set Bob 4294967297");
        assertEquals(1, msgs.size());
        assertTrue(msgs.get(0).startsWith(P + "The maximum bounty is "), msgs.get(0));
        assertTrue(plugin.service().active().isEmpty());
        assertEquals(1000, plugin.economy().balance(alice.getUniqueId()));
    }

    @Test
    void hugeHoursNeverCreateABountyOrCostMoney() {
        PlayerMock alice = player("Alice");
        player("Bob");
        for (String hours : new String[] {"4294967297", "99999999999", "4294967296"}) {
            List<String> msgs = run(alice, "bounty set Bob 100 " + hours);
            assertEquals(1, msgs.size(), hours);
            assertTrue(msgs.get(0).equals(P + "That is not a valid number.") || msgs.get(0).equals(P + "Duration must be between 1 and 72 hours."), msgs.get(0));
        }
        assertTrue(plugin.service().active().isEmpty());
        assertEquals(1000, plugin.economy().balance(alice.getUniqueId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.5", "-5", "1e3", "0x10", "10abc"})
    void nonWholeAmountsAreInvalidNumbers(String amount) {
        PlayerMock alice = player("Alice");
        player("Bob");
        List<String> msgs = run(alice, "bounty set Bob " + amount);
        assertEquals(List.of(P + "That is not a valid number."), msgs, amount);
        assertTrue(plugin.service().active().isEmpty());
    }

    @Test
    void nonNumericHoursIsAnInvalidNumber() {
        PlayerMock alice = player("Alice");
        player("Bob");
        assertEquals(List.of(P + "That is not a valid number."), run(alice, "bounty set Bob 100 soon"));
        assertEquals(List.of(P + "That is not a valid number."), run(alice, "bounty set Bob 100 1.5"));
        assertTrue(plugin.service().active().isEmpty());
    }

    @Test
    void unknownTargetIsRejectedWithoutCharging() {
        PlayerMock alice = player("Alice");
        player("Bob");
        assertEquals(List.of(P + "Unknown player: Nobody"), run(alice, "bounty set Nobody 100"));
        assertTrue(plugin.service().active().isEmpty());
        assertEquals(1000, plugin.economy().balance(alice.getUniqueId()));
    }

    // ---------------------------------------------------------------- list / mine

    @Test
    void emptyListAndMine() {
        PlayerMock alice = player("Alice");
        assertEquals(List.of(P + "There are no active bounties."), run(alice, "bounty list"));
        assertEquals(List.of(P + "You have no active bounties."), run(alice, "bounty mine"));
    }

    @Test
    void listShowsHeaderAndLinesSortedByAmount() {
        PlayerMock alice = player("Alice");
        PlayerMock carol = player("Carol");
        player("Bob");
        run(alice, "bounty set Bob 100");
        run(carol, "bounty set Bob 300");
        assertEquals(List.of(
                P + "Bounties (page 1/1, 2 total)",
                P + "#2 Bob - 300 coins (by Carol, 1d left)",
                P + "#1 Bob - 100 coins (by Alice, 1d left)"), run(alice, "bounty list"));
        clock.advance(Duration.ofMinutes(61));
        assertEquals(P + "#2 Bob - 300 coins (by Carol, 22h 59m left)", run(alice, "bounty list").get(1));
    }

    @Test
    void mineShowsOnlyTheSendersActiveBounties() {
        PlayerMock alice = player("Alice");
        PlayerMock carol = player("Carol");
        player("Bob");
        run(alice, "bounty set Bob 100 3");
        run(alice, "bounty set Bob 40");
        run(carol, "bounty set Bob 300");
        assertEquals(List.of(P + "#1 Bob - 100 coins (3h left)", P + "#2 Bob - 40 coins (1d left)"), run(alice, "bounty mine"));
        run(alice, "bounty cancel 1");
        assertEquals(List.of(P + "#2 Bob - 40 coins (1d left)"), run(alice, "bounty mine"));
    }

    @Test
    void listPagesHoldTenAndClampHighPages() {
        PlayerMock alice = player("Alice");
        player("Bob");
        for (int i = 0; i < 11; i++) {
            UUID poster = new UUID(9, i);
            plugin.economy().deposit(poster, 1000);
            plugin.service().create(poster, "P" + i, UUID.randomUUID(), "T" + i, 10 + i, null);
        }
        List<String> first = run(alice, "bounty list");
        assertEquals(11, first.size());
        assertEquals(P + "Bounties (page 1/2, 11 total)", first.get(0));
        List<String> second = run(alice, "bounty list 2");
        assertEquals(List.of(P + "Bounties (page 2/2, 11 total)", P + "#1 T0 - 10 coins (by P0, 1d left)"), second);
        assertEquals(P + "Bounties (page 2/2, 11 total)", run(alice, "bounty list 5").get(0));
    }

    @Test
    void listPageThatIsNotAPositiveWholeNumberIsInvalid() {
        PlayerMock alice = player("Alice");
        for (String page : new String[] {"0", "-1", "abc", "1.5"}) {
            assertEquals(List.of(P + "That is not a valid number."), run(alice, "bounty list " + page), page);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"4294967297", "99999999999", "2147483648"})
    void listWithHugePageNeverThrows(String page) {
        PlayerMock alice = player("Alice");
        List<String> msgs = run(alice, "bounty list " + page);
        assertEquals(1, msgs.size());
        assertTrue(msgs.get(0).equals(P + "There are no active bounties.") || msgs.get(0).equals(P + "That is not a valid number."), msgs.get(0));
    }

    // ---------------------------------------------------------------- cancel

    @Test
    void cancelRefundsAccordingToTheWindow() {
        PlayerMock alice = player("Alice");
        player("Bob");
        run(alice, "bounty set Bob 100");
        run(alice, "bounty set Bob 99");
        assertEquals(List.of(P + "Bounty #1 cancelled, 100 coins refunded."), run(alice, "bounty cancel 1"));
        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        assertEquals(List.of(P + "Bounty #2 cancelled, 79 coins refunded."), run(alice, "bounty cancel 2"));
        assertEquals(1000 - 99 + 79, plugin.economy().balance(alice.getUniqueId()));
    }

    @Test
    void cancelFailureMessages() {
        PlayerMock alice = player("Alice");
        PlayerMock carol = player("Carol");
        player("Bob");
        run(alice, "bounty set Bob 100");
        assertEquals(List.of(P + "There is no bounty #99."), run(alice, "bounty cancel 99"));
        assertEquals(List.of(P + "Bounty #1 is not yours."), run(carol, "bounty cancel 1"));
        run(alice, "bounty cancel 1");
        assertEquals(List.of(P + "Bounty #1 is no longer active."), run(alice, "bounty cancel 1"));
        assertEquals(List.of(P + "Bounty #1 is no longer active."), run(carol, "bounty cancel 1"));
    }

    @Test
    void cancelIdMustBeAWholeNumber() {
        PlayerMock alice = player("Alice");
        for (String id : new String[] {"abc", "1.5", "#1"}) {
            assertEquals(List.of(P + "That is not a valid number."), run(alice, "bounty cancel " + id), id);
        }
    }

    @Test
    void hugeCancelIdDoesNotWrapAroundToAnExistingBounty() {
        PlayerMock alice = player("Alice");
        player("Bob");
        run(alice, "bounty set Bob 100");
        for (String id : new String[] {"4294967297", "99999999999", "4294967296"}) {
            List<String> msgs = run(alice, "bounty cancel " + id);
            assertEquals(1, msgs.size(), id);
            assertFalse(msgs.get(0).contains("cancelled"), msgs.get(0));
            assertEquals(BountyStatus.ACTIVE, plugin.service().get(1).orElseThrow().status(), "id " + id + " must not hit bounty #1");
        }
        assertEquals(900, plugin.economy().balance(alice.getUniqueId()));
    }

    @Test
    void adminCancelsSomeoneElsesBountyWithFullRefundToThePoster() {
        PlayerMock alice = player("Alice");
        PlayerMock root = player("Root");
        root.setOp(true);
        player("Bob");
        run(alice, "bounty set Bob 99");
        clock.advance(Duration.ofHours(2));
        assertEquals(List.of(P + "Bounty #1 cancelled, 99 coins refunded."), run(root, "bounty cancel 1"));
        assertEquals(1000, plugin.economy().balance(alice.getUniqueId()));
        assertEquals(1000, plugin.economy().balance(root.getUniqueId()));
    }

    // ---------------------------------------------------------------- balance / admin

    @Test
    void balanceShowsStartingBalanceThenChanges() {
        PlayerMock alice = player("Alice");
        player("Bob");
        assertEquals(1000, plugin.economy().balance(alice.getUniqueId()));
        List<String> msgs = run(alice, "bounty balance");
        assertEquals(1, msgs.size());
        assertTrue(msgs.get(0).matches("\\[Bounty\\] Balance: 1,?000 coins"), msgs.get(0));
        run(alice, "bounty set Bob 250");
        assertEquals(List.of(P + "Balance: 750 coins"), run(alice, "bounty balance"));
    }

    @Test
    void adminGiveDepositsCoins() {
        PlayerMock root = player("Root");
        root.setOp(true);
        PlayerMock bob = player("Bob");
        assertEquals(List.of(P + "Gave 50 coins to Bob."), run(root, "bounty admin give Bob 50"));
        assertEquals(1050, plugin.economy().balance(bob.getUniqueId()));
        run(root, "bounty admin give Bob 4294967297");
        assertEquals(1050 + 4_294_967_297L, plugin.economy().balance(bob.getUniqueId()));
        run(root, "bounty admin give Bob 99999999999");
        assertEquals(1050 + 4_294_967_297L + 99_999_999_999L, plugin.economy().balance(bob.getUniqueId()));
    }

    @Test
    void adminGiveRejectsNonNumbers() {
        PlayerMock root = player("Root");
        root.setOp(true);
        PlayerMock bob = player("Bob");
        assertEquals(List.of(P + "That is not a valid number."), run(root, "bounty admin give Bob lots"));
        assertEquals(List.of(P + "That is not a valid number."), run(root, "bounty admin give Bob 1.5"));
        assertEquals(1000, plugin.economy().balance(bob.getUniqueId()));
    }

    @Test
    void adminSubcommandsNeedThePermission() {
        PlayerMock alice = player("Alice");
        PlayerMock bob = player("Bob");
        assertEquals(List.of(P + "You do not have permission."), run(alice, "bounty admin give Bob 50"));
        assertEquals(List.of(P + "You do not have permission."), run(alice, "bounty admin reload"));
        assertEquals(1000, plugin.economy().balance(bob.getUniqueId()));
    }

    // ---------------------------------------------------------------- console

    @Test
    void consoleGetsConsoleOnlyMessageForPlayerOnlySubcommands() {
        var console = server.getConsoleSender();
        for (String line : new String[] {"bounty set Bob 100", "bounty mine", "bounty balance", "bounty gui", "bounty cancel 1"}) {
            server.dispatchCommand(console, line);
            String msg = null;
            net.kyori.adventure.text.Component c;
            StringBuilder all = new StringBuilder();
            while ((c = console.nextComponentMessage()) != null) all.append(plain(c)).append('\n');
            assertTrue(all.toString().contains("This command can only be used by a player."), line + " -> " + all);
        }
        assertTrue(plugin.service().active().isEmpty());
    }
}
