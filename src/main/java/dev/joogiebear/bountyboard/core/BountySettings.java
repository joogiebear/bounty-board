package dev.joogiebear.bountyboard.core;

import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

/** Loads Settings from a config section. See DESIGN.md, section "Config, messages and formatting". */
public final class BountySettings {

    public record Loaded(Settings settings, List<String> warnings) {}

    private BountySettings() {}

    public static Loaded fromConfig(ConfigurationSection section) {
        throw new UnsupportedOperationException("TODO");
    }
}
