package dev.joogiebear.bountyboard.bukkit;

import dev.joogiebear.bountyboard.core.Economy;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.logging.Logger;

public class BuiltinEconomy implements Economy {

    private final File file;
    private final long startingBalance;
    private final Logger logger;
    private final Map<UUID, Long> balances = new HashMap<>();

    public BuiltinEconomy(File file, long startingBalance, Logger logger) {
        this.file = file;
        this.startingBalance = startingBalance;
        this.logger = logger;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                balances.put(id, yaml.getLong(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @Override
    public long balance(UUID player) {
        return balances.getOrDefault(player, startingBalance);
    }

    @Override
    public boolean withdraw(UUID player, long amount) {
        long current = balance(player);
        if (current < amount) {
            return false;
        }
        balances.put(player, current - amount);
        save();
        return true;
    }

    @Override
    public void deposit(UUID player, long amount) {
        balances.put(player, balance(player) + amount);
        save();
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Long> e : balances.entrySet()) {
            yaml.set(e.getKey().toString(), e.getValue());
        }
        try {
            yaml.save(file);
        } catch (Exception ex) {
            logger.warning("Failed to save balances: " + ex.getMessage());
        }
    }
}
