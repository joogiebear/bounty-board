package dev.joogiebear.bountyboard.bukkit;

import dev.joogiebear.bountyboard.core.Bounty;
import dev.joogiebear.bountyboard.core.CancelResult;
import dev.joogiebear.bountyboard.core.CreateResult;
import dev.joogiebear.bountyboard.core.Format;
import dev.joogiebear.bountyboard.core.Page;
import dev.joogiebear.bountyboard.core.Settings;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** The /bounty command and its tab completion. */
public class BountyCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("set", "list", "mine", "cancel", "balance", "gui");

    private final BountyBoardPlugin plugin;

    public BountyCommand(BountyBoardPlugin plugin) {
        this.plugin = plugin;
    }

    private void send(CommandSender to, String key, Map<String, String> placeholders) {
        to.sendMessage(plugin.messages().prefixed(key, placeholders));
    }

    private void send(CommandSender to, String key) {
        send(to, key, Map.of());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            send(sender, "usage");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "set" -> set(sender, args);
            case "list" -> list(sender, args);
            case "mine" -> noArgs(sender, args, () -> mine(sender));
            case "cancel" -> cancel(sender, args);
            case "balance" -> noArgs(sender, args, () -> balance(sender));
            case "gui" -> noArgs(sender, args, () -> gui(sender));
            case "admin" -> admin(sender, args);
            default -> send(sender, "usage");
        }
        return true;
    }

    private void noArgs(CommandSender sender, String[] args, Runnable action) {
        if (!(sender instanceof Player)) {
            send(sender, "console-only-player");
        } else if (args.length != 1) {
            send(sender, "usage");
        } else {
            action.run();
        }
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        send(sender, "console-only-player");
        return null;
    }

    private record Resolved(UUID id, String name) {}

    private Resolved resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return new Resolved(online.getUniqueId(), online.getName());
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore()) {
            String real = offline.getName() != null ? offline.getName() : name;
            return new Resolved(offline.getUniqueId(), real);
        }
        return null;
    }

    private static Long parseWhole(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void set(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 3 || args.length > 4) {
            send(sender, "usage");
            return;
        }
        Resolved target = resolve(args[1]);
        if (target == null) {
            send(sender, "unknown-player", Map.of("player", args[1]));
            return;
        }
        Long amount = parseWhole(args[2]);
        if (amount == null || amount < 0) {
            send(sender, "invalid-number");
            return;
        }
        Integer hours = null;
        if (args.length >= 4) {
            Long h = parseWhole(args[3]);
            if (h == null) {
                send(sender, "invalid-number");
                return;
            }
            hours = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, h));
        }
        Settings settings = plugin.settings();
        CreateResult result = plugin.service().create(player.getUniqueId(), player.getName(),
                target.id(), target.name(), amount, hours);
        switch (result.status()) {
            case OK -> {
                Bounty b = result.bounty();
                send(sender, "bounty-set", Map.of("id", Integer.toString(b.id()), "target", b.targetName(),
                        "amount", Format.amount(b.amount())));
                Bukkit.getServer().broadcast(plugin.messages().prefixed("bounty-posted", Map.of(
                        "poster", b.posterName(), "amount", Format.amount(b.amount()), "target", b.targetName())));
            }
            case SELF_TARGET -> send(sender, "self-target");
            case AMOUNT_TOO_LOW -> send(sender, "amount-too-low", Map.of("min", Format.amount(settings.minAmount())));
            case AMOUNT_TOO_HIGH -> send(sender, "amount-too-high", Map.of("max", Format.amount(settings.maxAmount())));
            case INVALID_DURATION -> send(sender, "invalid-duration", Map.of("max", Integer.toString(settings.maxHours())));
            case TOO_MANY_ACTIVE -> send(sender, "too-many-active", Map.of("max", Integer.toString(settings.maxActivePerPoster())));
            case INSUFFICIENT_FUNDS -> send(sender, "insufficient-funds", Map.of(
                    "amount", Format.amount(amount),
                    "balance", Format.amount(plugin.economy().balance(player.getUniqueId()))));
        }
    }

    private String remaining(Bounty b) {
        return Format.duration(Duration.between(plugin.activeClock().instant(), b.expiresAt()));
    }

    private void list(CommandSender sender, String[] args) {
        if (args.length > 2) {
            send(sender, "usage");
            return;
        }
        int pageNumber = 1;
        if (args.length == 2) {
            Long p = parseWhole(args[1]);
            if (p == null || p < 1) {
                send(sender, "invalid-number");
                return;
            }
            pageNumber = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, p));
        }
        Page<Bounty> page = plugin.service().page(pageNumber, 10);
        if (page.items().isEmpty()) {
            send(sender, "list-empty");
            return;
        }
        send(sender, "list-header", Map.of("page", Integer.toString(page.page()),
                "pages", Integer.toString(page.pages()), "total", Integer.toString(page.total())));
        for (Bounty b : page.items()) {
            Map<String, String> ph = new HashMap<>();
            ph.put("id", Integer.toString(b.id()));
            ph.put("target", b.targetName());
            ph.put("amount", Format.amount(b.amount()));
            ph.put("poster", b.posterName());
            ph.put("remaining", remaining(b));
            send(sender, "list-line", ph);
        }
    }

    private void mine(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        List<Bounty> mine = plugin.service().activeBy(player.getUniqueId());
        if (mine.isEmpty()) {
            send(sender, "mine-empty");
            return;
        }
        for (Bounty b : mine) {
            Map<String, String> ph = new HashMap<>();
            ph.put("id", Integer.toString(b.id()));
            ph.put("target", b.targetName());
            ph.put("amount", Format.amount(b.amount()));
            ph.put("remaining", remaining(b));
            send(sender, "mine-line", ph);
        }
    }

    private void cancel(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length != 2) {
            send(sender, "usage");
            return;
        }
        Long id = parseWhole(args[1]);
        if (id == null || id < Integer.MIN_VALUE || id > Integer.MAX_VALUE) {
            send(sender, "invalid-number");
            return;
        }
        CancelResult result = plugin.service().cancel(id.intValue(), player.getUniqueId(),
                player.hasPermission("bounty.admin"));
        String idText = Long.toString(id);
        switch (result.status()) {
            case OK -> send(sender, "cancel-ok", Map.of("id", idText, "refunded", Format.amount(result.refunded())));
            case NOT_FOUND -> send(sender, "cancel-not-found", Map.of("id", idText));
            case NOT_ACTIVE -> send(sender, "cancel-not-active", Map.of("id", idText));
            case NOT_OWNER -> send(sender, "cancel-not-owner", Map.of("id", idText));
        }
    }

    private void balance(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        send(sender, "balance", Map.of("balance", Format.amount(plugin.economy().balance(player.getUniqueId()))));
    }

    private void gui(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        BountyGui.open(plugin, player, 1);
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bounty.admin")) {
            send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            send(sender, "usage");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                if (args.length != 2) {
                    send(sender, "usage");
                    return;
                }
                plugin.reloadAll();
                send(sender, "reloaded");
            }
            case "give" -> {
                if (args.length != 4) {
                    send(sender, "usage");
                    return;
                }
                Resolved target = resolve(args[2]);
                if (target == null) {
                    send(sender, "unknown-player", Map.of("player", args[2]));
                    return;
                }
                Long amount = parseWhole(args[3]);
                if (amount == null || amount < 0) {
                    send(sender, "invalid-number");
                    return;
                }
                plugin.economy().deposit(target.id(), amount);
                send(sender, "admin-given", Map.of("amount", Format.amount(amount), "player", target.name()));
            }
            default -> send(sender, "usage");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission("bounty.admin");
        if (args.length == 1) {
            List<String> all = new ArrayList<>(SUBCOMMANDS);
            if (admin) {
                all.add("admin");
            }
            addMatching(out, all, args[0]);
            return out;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String current = args[args.length - 1];
        if (sub.equals("set")) {
            if (args.length == 2) {
                addMatching(out, onlineNames(sender), current);
            } else if (args.length == 3) {
                addMatching(out, List.of("100", "500", "1000"), current);
            } else if (args.length == 4) {
                addMatching(out, List.of("1", "6", "24", "72"), current);
            }
        } else if (sub.equals("cancel") && args.length == 2 && sender instanceof Player player) {
            List<String> ids = new ArrayList<>();
            for (Bounty b : plugin.service().activeBy(player.getUniqueId())) {
                ids.add(Integer.toString(b.id()));
            }
            addMatching(out, ids, current);
        } else if (sub.equals("admin") && admin) {
            if (args.length == 2) {
                addMatching(out, List.of("give", "reload"), current);
            } else if (args.length == 3 && args[1].equalsIgnoreCase("give")) {
                addMatching(out, onlineNames(null), current);
            }
        }
        return out;
    }

    private static List<String> onlineNames(CommandSender except) {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (except instanceof Player self && self.getUniqueId().equals(p.getUniqueId())) {
                continue;
            }
            names.add(p.getName());
        }
        return names;
    }

    private static void addMatching(List<String> out, List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
    }
}
