package dev.joogiebear.bountyboard.bukkit;

import dev.joogiebear.bountyboard.core.BountyService;
import dev.joogiebear.bountyboard.core.BountySettings;
import dev.joogiebear.bountyboard.core.Economy;
import dev.joogiebear.bountyboard.core.FileBountyStore;
import dev.joogiebear.bountyboard.core.Format;
import dev.joogiebear.bountyboard.core.Notice;
import dev.joogiebear.bountyboard.core.NoticeType;
import dev.joogiebear.bountyboard.core.Settings;
import java.io.File;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** The plugin entry point. See DESIGN.md, sections "Listeners and tasks" and "Wiring". */
public class BountyBoardPlugin extends JavaPlugin {

    private static Clock clockForTests;

    private BountyService service;
    private BuiltinEconomy economy;
    private Messages messages;
    private Settings settings;
    private Clock activeClock;

    /** Test seam: set before the plugin loads; null restores the system clock. */
    public static void setClockForTests(Clock clock) {
        clockForTests = clock;
    }

    static Clock clock() {
        return clockForTests != null ? clockForTests : Clock.systemUTC();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        BountySettings.Loaded loaded = BountySettings.fromConfig(getConfig());
        loaded.warnings().forEach(w -> getLogger().warning(w));
        settings = loaded.settings();
        messages = new Messages(getConfig().getConfigurationSection("messages"));
        activeClock = clock();

        long starting = readStartingBalance();
        economy = new BuiltinEconomy(new File(getDataFolder(), "balances.yml"), starting, getLogger());
        service = new BountyService(settings, economy,
                new FileBountyStore(getDataFolder().toPath().resolve("data.yml")), activeClock);

        PluginCommand command = getCommand("bounty");
        if (command != null) {
            BountyCommand handler = new BountyCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }
        Bukkit.getPluginManager().registerEvents(new BountyListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BountyGuiListener(this), this);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            service.expireDue();
            for (Player p : Bukkit.getOnlinePlayers()) {
                deliverNotices(p);
            }
        }, 600L, 600L);
    }

    @Override
    public void onDisable() {
        if (service != null) {
            service.save();
        }
        if (economy != null) {
            economy.save();
        }
    }

    private long readStartingBalance() {
        Object raw = getConfig().get("starting-balance");
        if (raw == null) {
            return 1000L;
        }
        if ((raw instanceof Integer || raw instanceof Long) && ((Number) raw).longValue() >= 0) {
            return ((Number) raw).longValue();
        }
        getLogger().warning("starting-balance: invalid value, using 1000");
        return 1000L;
    }

    /** Reloads config.yml, the settings and the messages. */
    void reloadAll() {
        reloadConfig();
        BountySettings.Loaded loaded = BountySettings.fromConfig(getConfig());
        loaded.warnings().forEach(w -> getLogger().warning(w));
        settings = loaded.settings();
        service.updateSettings(settings);
        messages = new Messages(getConfig().getConfigurationSection("messages"));
    }

    /** Sends and removes the player's pending notices. */
    void deliverNotices(Player player) {
        List<Notice> notices = service.takeNotices(player.getUniqueId());
        for (Notice n : notices) {
            String key = n.type() == NoticeType.EXPIRED_REFUNDED ? "notice-expired" : "notice-claimed";
            player.sendMessage(messages.prefixed(key, Map.of(
                    "id", Integer.toString(n.bountyId()),
                    "amount", Format.amount(n.amount()),
                    "other", n.other())));
        }
    }

    Messages messages() {
        return messages;
    }

    Settings settings() {
        return settings;
    }

    Clock activeClock() {
        return activeClock;
    }

    public BountyService service() {
        return service;
    }

    public Economy economy() {
        return economy;
    }
}
