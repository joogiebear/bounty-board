package dev.joogiebear.bountyboard.bukkit;

import dev.joogiebear.bountyboard.core.Bounty;
import dev.joogiebear.bountyboard.core.CancelResult;
import dev.joogiebear.bountyboard.core.CancelStatus;
import dev.joogiebear.bountyboard.core.CreateResult;
import dev.joogiebear.bountyboard.core.CreateStatus;
import dev.joogiebear.bountyboard.core.Format;
import dev.joogiebear.bountyboard.core.Page;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class BountyCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("set", "list", "mine", "cancel", "balance", "gui");
    private static final List<String> ALL_SUBCOMMANDS = List.of("set", "list", "mine", "cancel", "balance", "gui", "admin");

    private final BountyBoardPlugin plugin;

    public BountyCommand(BountyBoardPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            send(sender, "usage", Map.of());
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] rest = Arrays.copyOfRange(args, 1, args.length);

        if (isPlayerOnly(sub) && !(sender instanceof Player)) {
            send(sender, "console-only-player", Map.of());
            return true;
        }

        switch (sub) {
            case "set" -> handleSet(sender, rest);
            case "list" -> handleList(sender, rest);
            case "mine" -> handleMine(sender, rest);
            case "cancel" -> handleCancel(sender, rest);
            case "balance" -> handleBalance(sender, rest);
            case "gui" -> handleGui(sender, rest);
            case "admin" -> handleAdmin(sender, rest);
            default -> send(sender, "usage", Map.of());
        }
        return true;
    }

    private boolean isPlayerOnly(String sub) {
        return switch (sub) {
            case "set", "list", "mine", "cancel", "balance", "gui" -> true;
            default -> false;
        };
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 2 || args.length > 3) {
            send(sender, "usage", Map.of());
            return;
        }
        Player player = (Player) sender;
        String targetName = args[0];
        OfflinePlayer target = resolveTarget(targetName);
        if (target == null) {
            send(sender, "unknown-player", Map.of("player", targetName));
            return;
        }
        Long amount = parseNonNegativeLong(args[1]);
        if (amount == null) {
            send(sender, "invalid-number", Map.of());
            return;
        }
        Integer hours = null;
        if (args.length == 3) {
            Long h = parseNonNegativeLong(args[2]);
            if (h == null) {
                send(sender, "invalid-number", Map.of());
                return;
            }
            hours = h > Integer.MAX_VALUE ? Integer.MAX_VALUE : h.intValue();
        }
        CreateResult result = plugin.service().create(
                player.getUniqueId(), player.getName(),
                target.getUniqueId(), target.getName(),
                amount, hours);
        switch (result.status()) {
            case OK -> {
                send(sender, "bounty-set", Map.of(
                        "id", String.valueOf(result.bounty().id()),
                        "target", targetName,
                        "amount", Format.amount(amount)));
                broadcast("bounty-posted", Map.of(
                        "poster", player.getName(),
                        "amount", Format.amount(amount),
                        "target", targetName));
            }
            case SELF_TARGET -> send(sender, "self-target", Map.of());
            case AMOUNT_TOO_LOW -> send(sender, "amount-too-low", Map.of("min", Format.amount(plugin.settings().minAmount())));
            case AMOUNT_TOO_HIGH -> send(sender, "amount-too-high", Map.of("max", Format.amount(plugin.settings().maxAmount())));
            case INVALID_DURATION -> send(sender, "invalid-duration", Map.of("max", String.valueOf(plugin.settings().maxHours())));
            case TOO_MANY_ACTIVE -> send(sender, "too-many-active", Map.of("max", String.valueOf(plugin.settings().maxActivePerPoster())));
            case INSUFFICIENT_FUNDS -> send(sender, "insufficient-funds", Map.of(
                    "amount", Format.amount(amount),
                    "balance", Format.amount(plugin.economy().balance(player.getUniqueId()))));
        }
    }

    private void handleList(CommandSender sender, String[] args) {
        if (args.length > 1) {
            send(sender, "usage", Map.of());
            return;
        }
        int page = 1;
        if (args.length == 1) {
            Long p = parseNonNegativeLong(args[0]);
            if (p == null || p < 1) {
                send(sender, "invalid-number", Map.of());
                return;
            }
            page = p > Integer.MAX_VALUE ? Integer.MAX_VALUE : p.intValue();
        }
        Page<Bounty> pageResult = plugin.service().page(page, 10);
        if (pageResult.items().isEmpty()) {
            send(sender, "list-empty", Map.of());
            return;
        }
        send(sender, "list-header", Map.of(
                "page", String.valueOf(pageResult.page()),
                "pages", String.valueOf(pageResult.pages()),
                "total", String.valueOf(pageResult.total())));
        for (Bounty b : pageResult.items()) {
            Duration remaining = Duration.between(BountyBoardPlugin.clock().instant(), b.expiresAt());
            send(sender, "list-line", Map.of(
                    "id", String.valueOf(b.id()),
                    "target", b.targetName(),
                    "amount", Format.amount(b.amount()),
                    "poster", b.posterName(),
                    "remaining", Format.duration(remaining)));
        }
    }

    private void handleMine(CommandSender sender, String[] args) {
        if (args.length > 0) {
            send(sender, "usage", Map.of());
            return;
        }
        Player player = (Player) sender;
        List<Bounty> bounties = plugin.service().activeBy(player.getUniqueId());
        if (bounties.isEmpty()) {
            send(sender, "mine-empty", Map.of());
            return;
        }
        for (Bounty b : bounties) {
            Duration remaining = Duration.between(BountyBoardPlugin.clock().instant(), b.expiresAt());
            send(sender, "mine-line", Map.of(
                    "id", String.valueOf(b.id()),
                    "target", b.targetName(),
                    "amount", Format.amount(b.amount()),
                    "remaining", Format.duration(remaining)));
        }
    }

    private void handleCancel(CommandSender sender, String[] args) {
        if (args.length != 1) {
            send(sender, "usage", Map.of());
            return;
        }
        Long id = parseNonNegativeLong(args[0]);
        if (id == null) {
            send(sender, "invalid-number", Map.of());
            return;
        }
        boolean admin = sender.hasPermission("bounty.admin");
        Player player = (Player) sender;
        int intId = id > Integer.MAX_VALUE ? Integer.MAX_VALUE : id.intValue();
        CancelResult result = plugin.service().cancel(intId, player.getUniqueId(), admin);
        switch (result.status()) {
            case OK -> send(sender, "cancel-ok", Map.of(
                    "id", String.valueOf(id),
                    "refunded", Format.amount(result.refunded())));
            case NOT_FOUND -> send(sender, "cancel-not-found", Map.of("id", String.valueOf(id)));
            case NOT_ACTIVE -> send(sender, "cancel-not-active", Map.of("id", String.valueOf(id)));
            case NOT_OWNER -> send(sender, "cancel-not-owner", Map.of("id", String.valueOf(id)));
        }
    }

    private void handleBalance(CommandSender sender, String[] args) {
        if (args.length > 0) {
            send(sender, "usage", Map.of());
            return;
        }
        Player player = (Player) sender;
        send(sender, "balance", Map.of("balance", Format.amount(plugin.economy().balance(player.getUniqueId()))));
    }

    private void handleGui(CommandSender sender, String[] args) {
        if (args.length > 0) {
            send(sender, "usage", Map.of());
            return;
        }
        Player player = (Player) sender;
        Page<Bounty> p = plugin.service().page(1, BountyGui.PAGE_SIZE);
        BountyGui gui = new BountyGui(plugin.service(), 1, p.pages());
        player.openInventory(gui.getInventory());
    }

    private void handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bounty.admin")) {
            send(sender, "no-permission", Map.of());
            return;
        }
        if (args.length == 0) {
            send(sender, "usage", Map.of());
            return;
        }
        String subSub = args[0].toLowerCase(Locale.ROOT);
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (subSub) {
            case "give" -> handleAdminGive(sender, rest);
            case "reload" -> handleAdminReload(sender, rest);
            default -> send(sender, "usage", Map.of());
        }
    }

    private void handleAdminGive(CommandSender sender, String[] args) {
        if (args.length != 2) {
            send(sender, "usage", Map.of());
            return;
        }
        String playerName = args[0];
        OfflinePlayer target = resolveTarget(playerName);
        if (target == null) {
            send(sender, "unknown-player", Map.of("player", playerName));
            return;
        }
        Long amount = parseNonNegativeLong(args[1]);
        if (amount == null) {
            send(sender, "invalid-number", Map.of());
            return;
        }
        plugin.economy().deposit(target.getUniqueId(), amount);
        send(sender, "admin-given", Map.of(
                "amount", Format.amount(amount),
                "player", playerName));
    }

    private void handleAdminReload(CommandSender sender, String[] args) {
        if (args.length != 0) {
            send(sender, "usage", Map.of());
            return;
        }
        plugin.reload();
        send(sender, "reloaded", Map.of());
    }

    // ---- helpers ----

    private void send(CommandSender sender, String key, Map<String, String> placeholders) {
        Component msg = plugin.messages().render("prefix", Map.of()).append(plugin.messages().render(key, placeholders));
        sender.sendMessage(msg);
    }

    private void broadcast(String key, Map<String, String> placeholders) {
        Component msg = plugin.messages().render("prefix", Map.of()).append(plugin.messages().render(key, placeholders));
        Bukkit.broadcast(msg);
    }

    private OfflinePlayer resolveTarget(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (name.equals(op.getName()) && op.hasPlayedBefore()) {
                return op;
            }
        }
        return null;
    }

    private Long parseNonNegativeLong(String s) {
        try {
            long v = Long.parseLong(s);
            return v >= 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---- tab completion ----

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = sender.hasPermission("bounty.admin") ? ALL_SUBCOMMANDS : SUBCOMMANDS;
            return filter(subs, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "set" -> {
                if (args.length == 2) {
                    return filter(onlinePlayerNamesExcluding(sender), args[1]);
                }
                if (args.length == 3) {
                    return filter(List.of("100", "500", "1000"), args[2]);
                }
                if (args.length == 4) {
                    return filter(List.of("1", "6", "24", "72"), args[3]);
                }
                return List.of();
            }
            case "cancel" -> {
                if (args.length == 2 && sender instanceof Player player) {
                    List<String> ids = plugin.service().activeBy(player.getUniqueId()).stream()
                            .map(b -> String.valueOf(b.id()))
                            .toList();
                    return filter(ids, args[1]);
                }
                return List.of();
            }
            case "admin" -> {
                if (!sender.hasPermission("bounty.admin")) {
                    return List.of();
                }
                if (args.length == 2) {
                    return filter(List.of("give", "reload"), args[1]);
                }
                if (args.length == 3 && "give".equalsIgnoreCase(args[1])) {
                    return filter(onlinePlayerNamesAll(), args[2]);
                }
                return List.of();
            }
            default -> {
                return List.of();
            }
        }
    }

    private List<String> onlinePlayerNamesExcluding(CommandSender sender) {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (sender instanceof Player sp && p.getUniqueId().equals(sp.getUniqueId())) {
                continue;
            }
            names.add(p.getName());
        }
        return names;
    }

    private List<String> onlinePlayerNamesAll() {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
        }
        return names;
    }

    private List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p))
                .toList();
    }
}
