package me.lovelace.loveclaims.command;

import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.model.UserData;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Единая административная команда плагина: {@code /loveclaimsadmin <subcommand>}.
 * <p>
 * Раньше все admin-возможности LoveClaims (reload, выдача якорей, расширение приватов,
 * бонусные слоты участников, очки расширения) были зарыты внутри {@code /ac admin ...}, что не
 * соответствует принятому в экосистеме Love* стилю единой родительской admin-команды с
 * подкомандами. Старый путь оставлен в {@link MainCommand} как редирект, чтобы команда не
 * «молчала» для тех, кто наберёт её по привычке. Admin-подкоманды {@code /rental admin ...} сюда
 * не переносились — это отдельный домен (администрирование аренды) со своим собственным
 * разрешением и уже единой подкомандой внутри {@code /rental}.
 */
public class LoveClaimsAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("reload", "give", "expand", "addmembers", "claim", "help");
    private static final List<String> ANCHOR_TIERS = List.of("tier-1", "tier-2", "tier-3");
    private static final List<String> LIMIT_ACTIONS = List.of("add", "remove");

    private final LoveClaims plugin;

    public LoveClaimsAdminCommand(@NotNull LoveClaims plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("loveclaims.admin")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("player-only"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> handleReload(player);
            case "give" -> handleGive(player, args);
            case "expand" -> handleExpand(player, args);
            case "addmembers" -> handleAddMembers(player, args);
            case "claim" -> handleClaimLimit(player, args);
            default -> sendHelp(player);
        }
        return true;
    }

    private void handleReload(@NotNull Player player) {
        plugin.getConfigManager().loadAll();
        plugin.getAnchorManager().loadTiers();
        plugin.getQuestManager().loadQuests();
        player.sendMessage(plugin.getConfigManager().getMessage("admin-reloaded"));
    }

    private void handleGive(@NotNull Player player, @NotNull String[] args) {
        if (args.length < 3) {
            sendHelp(player);
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        ItemStack anchor = plugin.getAnchorManager().createAnchorItem(args[2]);
        if (target != null && anchor != null) {
            target.getInventory().addItem(anchor);
            player.sendMessage(plugin.getConfigManager().getMessage("admin-give-success", "player", target.getName()));
        } else {
            player.sendMessage(plugin.getConfigManager().getMessage("admin-give-fail"));
        }
    }

    private void handleExpand(@NotNull Player player, @NotNull String[] args) {
        if (args.length < 3) {
            sendHelp(player);
            return;
        }
        Player owner = Bukkit.getPlayer(args[1]);
        if (owner == null) return;

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sendHelp(player);
            return;
        }

        Optional<Claim> opt = plugin.getClaimManager().getClaimAt(player.getLocation());
        if (opt.isEmpty()) {
            player.sendMessage(plugin.getConfigManager().getMessage("rental-admin-expand-fail"));
            return;
        }
        Claim claim = opt.get();
        // Админ-расширение работает над любыми приватами, включая клановые, при необходимости.
        if (!claim.getOwnerUuid().equals(owner.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage("admin-expand-not-owner", "player", owner.getName()));
            return;
        }
        // Клонируем ДО expand(): BoundingBox#expand мутирует объект на месте и возвращает this,
        // а getBoundingBox() отдаёт internal-ссылку без копии - без clone() тут же затёрлись бы
        // и старые границы (нечем будет обновить чанковый кэш), и claim.boundingBox оказался бы
        // изменён ещё до setBoundingBox().
        org.bukkit.util.BoundingBox oldBox = claim.getBoundingBox().clone();
        claim.setBoundingBox(oldBox.clone().expand(amount, amount, amount));
        plugin.getClaimManager().resizeClaimInCache(claim, oldBox);
        plugin.getStorage().saveClaimAsync(claim);
        player.sendMessage(plugin.getConfigManager().getMessage("rental-admin-expand-success", "amount", String.valueOf(amount)));
        me.lovelace.loveclaims.task.BorderDisplayTask.showBorder(plugin, player, claim.getBoundingBox(), 100);
    }

    private void handleAddMembers(@NotNull Player player, @NotNull String[] args) {
        if (args.length < 3) {
            sendHelp(player);
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) return;

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sendHelp(player);
            return;
        }

        UserData data = plugin.getQuestManager().getUserData(target.getUniqueId());
        data.addBonusMemberLimit(amount);
        plugin.getStorage().saveUserDataAsync(data);
        player.sendMessage(plugin.getConfigManager().getMessage("admin-limit-success", "player", target.getName(), "amount", String.valueOf(amount)));
    }

    private void handleClaimLimit(@NotNull Player player, @NotNull String[] args) {
        if (args.length < 4 || !args[1].equalsIgnoreCase("limit")) {
            sendHelp(player);
            return;
        }
        String action = args[2];
        Player target = Bukkit.getPlayer(args[3]);
        if (target == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("rental-admin-player-not-found"));
            return;
        }

        int count = 1;
        if (args.length >= 5) {
            try {
                count = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) {
                sendHelp(player);
                return;
            }
        }

        UserData data = plugin.getQuestManager().getUserData(target.getUniqueId());
        if (action.equalsIgnoreCase("add")) {
            data.addExpansionBlocks(count);
            player.sendMessage(plugin.getConfigManager().getMessage("rental-admin-limit-add", "count", String.valueOf(count), "player", target.getName()));
        } else if (action.equalsIgnoreCase("remove")) {
            if (data.getExpansionBlocks() >= count) {
                data.removeExpansionBlocks(count);
                player.sendMessage(plugin.getConfigManager().getMessage("rental-admin-limit-remove", "count", String.valueOf(count), "player", target.getName()));
            } else {
                player.sendMessage(plugin.getConfigManager().getMessage("rental-admin-limit-insufficient"));
            }
        }
        plugin.getStorage().saveUserDataAsync(data);
    }

    private void sendHelp(@NotNull CommandSender sender) {
        sender.sendMessage(plugin.getConfigManager().getComponent("admin-help-header"));
        for (Component line : plugin.getConfigManager().getHelpMessage("admin-help-body")) {
            sender.sendMessage(line);
        }
        sender.sendMessage(plugin.getConfigManager().getComponent("admin-help-footer"));
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("loveclaims.admin")) return Collections.emptyList();

        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], SUBCOMMANDS, new ArrayList<>());
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        List<String> onlineNames = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) onlineNames.add(p.getName());

        if (args.length == 2 && (sub.equals("give") || sub.equals("expand") || sub.equals("addmembers"))) {
            return StringUtil.copyPartialMatches(args[1], onlineNames, new ArrayList<>());
        }
        if (args.length == 3 && sub.equals("give")) {
            return StringUtil.copyPartialMatches(args[2], ANCHOR_TIERS, new ArrayList<>());
        }
        if (args.length == 2 && sub.equals("claim")) {
            return StringUtil.copyPartialMatches(args[1], List.of("limit"), new ArrayList<>());
        }
        if (args.length == 3 && sub.equals("claim") && args[1].equalsIgnoreCase("limit")) {
            return StringUtil.copyPartialMatches(args[2], LIMIT_ACTIONS, new ArrayList<>());
        }
        if (args.length == 4 && sub.equals("claim") && args[1].equalsIgnoreCase("limit")) {
            return StringUtil.copyPartialMatches(args[3], onlineNames, new ArrayList<>());
        }
        return Collections.emptyList();
    }
}
