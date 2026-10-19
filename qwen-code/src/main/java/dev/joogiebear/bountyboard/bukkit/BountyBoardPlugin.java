package dev.joogiebear.bountyboard.bukkit;

import dev.joogiebear.bountyboard.core.BountyService;
import dev.joogiebear.bountyboard.core.BountySettings;
import dev.joogiebear.bountyboard.core.Economy;
import dev.joogiebear.bountyboard.core.FileBountyStore;
import dev.joogiebear.bountyboard.core.Settings;
import java.io.File;
import java.time.Clock;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** The plugin entry point. See DESIGN.md, sections "Listeners and tasks" and "Wiring". */
public class BountyBoardPlugin extends JavaPlugin {

    private static Clock clockForTests;

    private BountyService service;
    private BuiltinEconomy economy;
    private Messages messages;
    private Settings settings;

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
        BountySettings.Loaded loaded = BountySettings.fromConfig(getConfig());
        settings = loaded.settings();
        for (String warning : loaded.warnings()) {
            getLogger().warning(warning);
        }

        messages = new Messages(getConfig().getConfigurationSection("messages"));

        FileBountyStore store = new FileBountyStore(getDataFolder().toPath().resolve("data.yml"));
        long startingBalance = getConfig().getLong("starting-balance", 1000);
        economy = new BuiltinEconomy(new File(getDataFolder(), "balances.yml"), startingBalance, getLogger());
        service = new BountyService(settings, economy, store, clock());

        BountyCommand command = new BountyCommand(this);
        PluginCommand cmd = getCommand("bounty");
        cmd.setExecutor(command);
        cmd.setTabCompleter(command);

        getServer().getPluginManager().registerEvents(new BountyListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getScheduler().runTaskTimer(this, new ExpiryTask(this), 600, 600);
    }

    @Override
    public void onDisable() {
        // Service and economy save after every state change; nothing pending.
    }

    public void reload() {
        reloadConfig();
        BountySettings.Loaded loaded = BountySettings.fromConfig(getConfig());
        settings = loaded.settings();
        for (String warning : loaded.warnings()) {
            getLogger().warning(warning);
        }
        messages = new Messages(getConfig().getConfigurationSection("messages"));
        service.updateSettings(settings);
    }

    public BountyService service() {
        return service;
    }

    public Economy economy() {
        return economy;
    }

    public Messages messages() {
        return messages;
    }

    public Settings settings() {
        return settings;
    }
}
