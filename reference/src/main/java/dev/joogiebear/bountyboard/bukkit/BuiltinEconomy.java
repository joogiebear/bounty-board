package dev.joogiebear.bountyboard.bukkit;

import dev.joogiebear.bountyboard.core.Economy;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Balances kept in memory and saved to balances.yml after every change. */
public class BuiltinEconomy implements Economy {

    private final File file;
    private final long startingBalance;
    private final Logger logger;
    private final Map<UUID, Long> balances = new HashMap<>();

    public BuiltinEconomy(File file, long startingBalance, Logger logger) {
        this.file = file;
        this.startingBalance = startingBalance;
        this.logger = logger;
        load();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection s = yaml.getConfigurationSection("balances");
            if (s != null) {
                for (String key : s.getKeys(false)) {
                    balances.put(UUID.fromString(key), s.getLong(key));
                }
            }
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, "Could not read balances.yml", e);
        }
    }

    @Override
    public long balance(UUID player) {
        return balances.getOrDefault(player, startingBalance);
    }

    @Override
    public boolean withdraw(UUID player, long amount) {
        long current = balance(player);
        if (amount < 0 || current < amount) {
            return false;
        }
        balances.put(player, current - amount);
        save();
        return true;
    }

    @Override
    public void deposit(UUID player, long amount) {
        long current = balance(player);
        long updated = current + amount;
        if (amount > 0 && updated < current) {
            updated = Long.MAX_VALUE;
        }
        balances.put(player, updated);
        save();
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Long> e : balances.entrySet()) {
            yaml.set("balances." + e.getKey(), e.getValue());
        }
        try {
            File parent = file.getAbsoluteFile().getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            yaml.save(file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save balances.yml", e);
        }
    }
}
