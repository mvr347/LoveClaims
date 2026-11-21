package me.lovelace.loveclaims.command;

import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.gems.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import java.util.*;

/**
 * Handles `/loveclaims gem <give|givehybrid|list|inspect>` subcommands under `loveclaims.admin.gems`.
 */
public final class GemCommandHandler {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String PERMISSION = "loveclaims.admin.gems";
    private static final List<String> SUBCOMMANDS = List.of("give", "givehybrid", "list", "inspect");
    private static final List<String> OUTCOMES = Arrays.stream(HybridRollOutcome.values())
            .map(Enum::name)
            .toList();

    private final LoveClaims plugin;

    public GemCommandHandler(LoveClaims plugin) {
        this.plugin = plugin;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (args.length < 2) {
            sendHelp(sender);
            return true;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "give" -> handleGive(sender, args);
            case "givehybrid" -> handleGiveHybrid(sender, args);
            case "list" -> handleList(sender);
            case "inspect" -> handleInspect(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(MM.deserialize("<red>Использование: /loveclaims gem give <игрок> <gem_id> [количество]</red>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(plugin.getConfigManager().getMessage("member-not-found"));
            return;
        }

        String gemId = args[3].toLowerCase(Locale.ROOT);
        GemRegistry registry = plugin.getGemRegistry();
        Optional<GemDescriptor> descOpt = registry.getDescriptor(gemId);
        if (descOpt.isEmpty()) {
            sender.sendMessage(MM.deserialize("<red>Неизвестный gem_id: <yellow>" + gemId + "</yellow></red>"));
            return;
        }

        int amount = 1;
        if (args.length >= 5) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[4])));
            } catch (NumberFormatException e) {
                sender.sendMessage(MM.deserialize("<red>Количество должно быть числом.</red>"));
                return;
            }
        }

        if (descOpt.get() instanceof HybridGemDescriptor hybrid) {
            for (int i = 0; i < amount; i++) {
                ItemStack item = registry.createHybridItem(hybrid, null);
                giveOrDrop(target, item);
            }
        } else {
            ItemStack item = registry.createGemItem(gemId, amount);
            if (item != null) {
                giveOrDrop(target, item);
            }
        }

        sender.sendMessage(MM.deserialize("<green>Выдано <yellow>" + amount + "x " + gemId
                + "</yellow> игроку <white>" + target.getName() + "</white>.</green>"));
    }

    private void handleGiveHybrid(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(MM.deserialize("<red>Использование: /loveclaims gem givehybrid <игрок> <hybrid_id> <POSITIVE|NEUTRAL|DEBUFF></red>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(plugin.getConfigManager().getMessage("member-not-found"));
            return;
        }

        String hybridId = args[3].toLowerCase(Locale.ROOT);
        Optional<GemDescriptor> descOpt = plugin.getGemRegistry().getDescriptor(hybridId);
        if (descOpt.isEmpty() || !(descOpt.get() instanceof HybridGemDescriptor hybrid)) {
            sender.sendMessage(MM.deserialize("<red>Неизвестный hybrid_id: <yellow>" + hybridId + "</yellow></red>"));
            return;
        }

        HybridRollOutcome outcome;
        try {
            outcome = HybridRollOutcome.valueOf(args[4].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(MM.deserialize("<red>Допустимые исходы: POSITIVE, NEUTRAL, DEBUFF</red>"));
            return;
        }

        ItemStack item = plugin.getGemRegistry().createHybridItem(hybrid, outcome);
        giveOrDrop(target, item);
        sender.sendMessage(MM.deserialize("<green>Выдан гибрид <yellow>" + hybridId + "</yellow> (" + outcome.name()
                + ") игроку <white>" + target.getName() + "</white>.</green>"));
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage(MM.deserialize("<dark_gray>========== <aqua>Доступные гемы</aqua> ==========</dark_gray>"));
        String receiverName = sender instanceof Player p ? p.getName() : "<игрок>";
        for (GemDescriptor desc : plugin.getGemRegistry().getAllDescriptors()) {
            String color = switch (desc.rarity()) {
                case BASIC -> "<green>";
                case MODIFIED -> "<light_purple>";
                case LEGENDARY -> "<gold>";
            };
            String cmd = "/loveclaims gem give " + receiverName + " " + desc.id() + " 1";
            Component line = MM.deserialize("<gray>• " + color + desc.id()
                            + " <dark_gray>[" + desc.rarity().name() + ", " + desc.rarity().slotCost() + " сл.] ")
                    .append(MM.deserialize("<aqua>[Выдать]</aqua>")
                            .clickEvent(sender instanceof Player
                                    ? ClickEvent.runCommand(cmd)
                                    : ClickEvent.suggestCommand(cmd))
                            .hoverEvent(HoverEvent.showText(MM.deserialize("<gray>Клик: <yellow>" + cmd))));
            sender.sendMessage(line);
        }
    }

    private void handleInspect(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("player-only"));
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        GemFactory factory = plugin.getGemFactory();
        Optional<GemStats> statsOpt = factory.readStats(held);
        if (statsOpt.isEmpty()) {
            player.sendMessage(MM.deserialize("<red>В основной руке нет гема.</red>"));
            return;
        }
        GemStats stats = statsOpt.get();
        Map<String, String> rawTags = factory.readRawPdcTags(held);

        player.sendMessage(MM.deserialize("<dark_gray>========== <aqua>Инспекция гема</aqua> ==========</dark_gray>"));
        player.sendMessage(MM.deserialize("<gray>ID: <white>" + stats.gemId() + " <gray>| Редкость: <yellow>" + stats.rarity().name()
                + " <gray>| Слоты: <aqua>" + stats.slotCost()));
        player.sendMessage(MM.deserialize("<gray>Статы: <white>radius=" + stats.radiusBonus()
                + ", members=" + stats.membersBonus()
                + ", crop=" + stats.cropGrowthMultiplier()
                + ", furnace=" + stats.furnaceSpeedMultiplier()
                + ", doubleDrop=" + stats.doubleDropChance()
                + ", bypassLimit=" + stats.allowLimitBypass()));
        if (!stats.potionEffects().isEmpty()) {
            player.sendMessage(MM.deserialize("<gray>Эффекты: <aqua>" + GemFactory.serializeEffects(stats.potionEffects())));
        }
        player.sendMessage(MM.deserialize("<gray>Сырые PDC-теги (" + rawTags.size() + "):"));
        for (Map.Entry<String, String> entry : rawTags.entrySet()) {
            player.sendMessage(MM.deserialize("<dark_gray>  " + entry.getKey() + " = <white>" + entry.getValue()));
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MM.deserialize("<dark_gray>========== <aqua>Команды гемов</aqua> ==========</dark_gray>"));
        sender.sendMessage(MM.deserialize("<aqua>/loveclaims gem give <игрок> <gem_id> [кол-во]</aqua> <gray>- выдать гем</gray>"));
        sender.sendMessage(MM.deserialize("<aqua>/loveclaims gem givehybrid <игрок> <hybrid_id> <POSITIVE|NEUTRAL|DEBUFF></aqua> <gray>- выдать гибрид</gray>"));
        sender.sendMessage(MM.deserialize("<aqua>/loveclaims gem list</aqua> <gray>- список всех гемов</gray>"));
        sender.sendMessage(MM.deserialize("<aqua>/loveclaims gem inspect</aqua> <gray>- инспекция гема в руке</gray>"));
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) return Collections.emptyList();
        if (args.length == 2) {
            return StringUtil.copyPartialMatches(args[1], SUBCOMMANDS, new ArrayList<>());
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        if (args.length == 3 && (sub.equals("give") || sub.equals("givehybrid"))) {
            List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            return StringUtil.copyPartialMatches(args[2], names, new ArrayList<>());
        }
        if (args.length == 4 && sub.equals("give")) {
            return StringUtil.copyPartialMatches(args[3], plugin.getGemRegistry().getAllGemIds(), new ArrayList<>());
        }
        if (args.length == 4 && sub.equals("givehybrid")) {
            return StringUtil.copyPartialMatches(args[3], plugin.getGemRegistry().getHybridGemIds(), new ArrayList<>());
        }
        if (args.length == 5 && sub.equals("givehybrid")) {
            return StringUtil.copyPartialMatches(args[4], OUTCOMES, new ArrayList<>());
        }
        if (args.length == 5 && sub.equals("give")) {
            return StringUtil.copyPartialMatches(args[4], List.of("1", "4", "16"), new ArrayList<>());
        }
        return Collections.emptyList();
    }

    private static void giveOrDrop(Player target, ItemStack item) {
        target.getInventory().addItem(item).values()
                .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
    }
}
