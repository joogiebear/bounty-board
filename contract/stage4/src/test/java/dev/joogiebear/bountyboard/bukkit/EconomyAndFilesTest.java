package dev.joogiebear.bountyboard.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.joogiebear.bountyboard.core.Economy;
import java.io.File;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class EconomyAndFilesTest extends PluginTestBase {

    final UUID stranger = UUID.fromString("99999999-9999-9999-9999-999999999999");

    @Test
    void aNewPlayerStartsWithTheConfiguredBalance() {
        assertEquals(1000, plugin.economy().balance(stranger));
        assertEquals(1000, plugin.economy().balance(alex.getUniqueId()));
        assertEquals(1000, plugin.getConfig().getLong("starting-balance"));
    }

    @Test
    void withdrawAndDepositMoveTheBalance() {
        Economy eco = plugin.economy();
        assertTrue(eco.withdraw(stranger, 300));
        assertEquals(700, eco.balance(stranger));
        eco.deposit(stranger, 50);
        assertEquals(750, eco.balance(stranger));
    }

    @Test
    void aTooBigWithdrawalFailsAndChangesNothing() {
        Economy eco = plugin.economy();
        assertFalse(eco.withdraw(stranger, 1001));
        assertEquals(1000, eco.balance(stranger));
        assertTrue(eco.withdraw(stranger, 1000));
        assertEquals(0, eco.balance(stranger));
        assertFalse(eco.withdraw(stranger, 1));
    }

    @Test
    void balancesAreSavedToBalancesYmlAfterEveryChange() {
        plugin.economy().withdraw(stranger, 250);
        File file = new File(plugin.getDataFolder(), "balances.yml");
        assertTrue(file.exists());
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        assertTrue(yaml.saveToString().contains("750"), yaml.saveToString());
        plugin.economy().deposit(stranger, 5);
        assertTrue(YamlConfiguration.loadConfiguration(file).saveToString().contains("755"));
    }

    @Test
    void bountiesAreSavedToDataYmlAfterEveryChange() {
        cmd(alex, "bounty set Steve 100");
        File file = new File(plugin.getDataFolder(), "data.yml");
        assertTrue(file.exists());
        long size = file.length();
        assertTrue(size > 0);
        cmd(kim, "bounty set Steve 200");
        assertTrue(file.length() > size);
    }

    @Test
    void theDefaultConfigIsLoaded() {
        assertEquals(5, plugin.getConfig().getInt("tax-percent"));
        assertEquals(10, plugin.getConfig().getInt("min-amount"));
        assertEquals(24, plugin.getConfig().getInt("default-hours"));
    }

    @Test
    void theCommandIsRegisteredWithItsPermission() {
        assertEquals("bounty.use", plugin.getCommand("bounty").getPermission());
    }
}
