package dev.joogiebear.bountyboard.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

class TabCompleteTest extends PluginTestBase {

    Set<String> complete(CommandSender sender, String... args) {
        List<String> result = plugin.getCommand("bounty").tabComplete(sender, "bounty", args);
        assertTrue(result != null, "tab completion must return a list");
        return new HashSet<>(result);
    }

    @Test
    void listsTheSubcommandsAPlayerMayUse() {
        assertEquals(Set.of("set", "list", "mine", "cancel", "balance", "gui"), complete(alex, ""));
    }

    @Test
    void adminIsOnlyOfferedToAdmins() {
        kim.setOp(true);
        assertEquals(Set.of("set", "list", "mine", "cancel", "balance", "gui", "admin"), complete(kim, ""));
    }

    @Test
    void filtersSubcommandsByPrefixIgnoringCase() {
        assertEquals(Set.of("cancel"), complete(alex, "c"));
        assertEquals(Set.of("set"), complete(alex, "S"));
        assertEquals(Set.of(), complete(alex, "zzz"));
        assertEquals(Set.of(), complete(alex, "ad"));
        kim.setOp(true);
        assertEquals(Set.of("admin"), complete(kim, "ad"));
    }

    @Test
    void setSuggestsOnlinePlayersExceptTheSender() {
        assertEquals(Set.of("Steve", "Kim"), complete(alex, "set", ""));
        assertEquals(Set.of("Steve"), complete(alex, "set", "ste"));
        assertEquals(Set.of(), complete(alex, "set", "al"));
    }

    @Test
    void setSuggestsAmountsAndHours() {
        assertEquals(Set.of("100", "500", "1000"), complete(alex, "set", "Steve", ""));
        assertEquals(Set.of("100", "1000"), complete(alex, "set", "Steve", "1"));
        assertEquals(Set.of("1", "6", "24", "72"), complete(alex, "set", "Steve", "100", ""));
        assertEquals(Set.of("24"), complete(alex, "set", "Steve", "100", "2"));
    }

    @Test
    void cancelSuggestsMyActiveBountyIds() {
        cmd(alex, "bounty set Steve 100");
        cmd(kim, "bounty set Steve 100");
        cmd(alex, "bounty set Kim 100");
        assertEquals(Set.of("1", "3"), complete(alex, "cancel", ""));
        assertEquals(Set.of("3"), complete(alex, "cancel", "3"));
        assertEquals(Set.of("2"), complete(kim, "cancel", ""));
    }

    @Test
    void adminSuggestsItsSubcommandsAndPlayers() {
        kim.setOp(true);
        assertEquals(Set.of("give", "reload"), complete(kim, "admin", ""));
        assertEquals(Set.of("give"), complete(kim, "admin", "g"));
        assertEquals(Set.of("Alex", "Steve", "Kim"), complete(kim, "admin", "give", ""));
        assertEquals(Set.of(), complete(alex, "admin", ""));
    }

    @Test
    void otherPositionsSuggestNothing() {
        assertEquals(Set.of(), complete(alex, "list", ""));
        assertEquals(Set.of(), complete(alex, "mine", ""));
        assertEquals(Set.of(), complete(alex, "set", "Steve", "100", "24", ""));
    }
}
