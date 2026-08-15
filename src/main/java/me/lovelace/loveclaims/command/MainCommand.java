package me.lovelace.loveclaims.command;
import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.model.Claim;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.Optional;
import java.util.List;
import java.util.stream.Collectors;

public class MainCommand implements CommandExecutor, org.bukkit.command.TabCompleter {
    private final LoveClaims plugin;

    public MainCommand(LoveClaims plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (args.length == 0) {
            List<Claim> accessibleClaims = plugin.getClaimManager().getAllClaims().stream()
                    .filter(c -> !c.isRentalPlot())
                    .filter(c -> !c.isClanTerritory()) // Игнорируем клановые приваты для стандартных команд
                    .filter(c -> c.getTrust(player.getUniqueId()) != me.lovelace.loveclaims.model.TrustLevel.NONE)
                    .toList();

            if (accessibleClaims.isEmpty()) {
                player.sendMessage(plugin.getConfigManager().getMessage("not-in-claim"));
                return true;
            }

            if (accessibleClaims.size() == 1) {
                player.openInventory(new me.lovelace.loveclaims.gui.MainClaimGUI(plugin, player, accessibleClaims.get(0)).getInventory());
                return true;
            }

            player.sendMessage(plugin.getConfigManager().getMessage("rental-multiple-claims"));
            for (Claim c : accessibleClaims) {
                String ownerName = Bukkit.getOfflinePlayer(c.getOwnerUuid()).getName();
                if (ownerName == null) ownerName = "Неизвестно";
                player.sendMessage(plugin.getConfigManager().getComponent("msg-invite-list-entry", "player", ownerName));
            }
            player.sendMessage(plugin.getConfigManager().getMessage("rental-multiple-claims-hint"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help" -> {
                player.sendMessage(plugin.getConfigManager().getComponent("command-help-header"));
                for (Component line : plugin.getConfigManager().getHelpMessage("command-help-body")) {
                    player.sendMessage(line);
                }
                if (player.hasPermission("loveclaims.admin")) {
                    player.sendMessage(plugin.getConfigManager().getComponent("command-help-admin"));
                }
                player.sendMessage(plugin.getConfigManager().getComponent("command-help-footer"));
                return true;
            }
            case "home" -> {
                Claim targetClaim = null;
                // Ищем свой приват
                for (Claim c : plugin.getClaimManager().getAllClaims()) {
                    if (!c.isRentalPlot() && !c.isClanTerritory() && c.getOwnerUuid() != null && c.getOwnerUuid().equals(player.getUniqueId())) {
                        targetClaim = c;
                        break;
                    }
                }
                // Если нет своего, ищем приват, где есть доступ
                if (targetClaim == null) {
                    for (Claim c : plugin.getClaimManager().getAllClaims()) {
                        if (!c.isRentalPlot() && !c.isClanTerritory() && c.getTrust(player.getUniqueId()) != me.lovelace.loveclaims.model.TrustLevel.NONE) {
                            targetClaim = c;
                            break;
                        }
                    }
                }

                if (targetClaim != null) {
                    player.teleportAsync(targetClaim.getHomeLocation());
                    player.sendMessage(plugin.getConfigManager().getMessage("teleport-home"));
                    plugin.getConfigManager().playSound(player, "anchor-place");
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessage("not-in-claim"));
                    plugin.getConfigManager().playSound(player, "anchor-error");
                }
                return true;
            }

            case "invite" -> {
                if (args.length >= 2) {
                    if (args[0].equalsIgnoreCase("invite")) {
                        // Подтверждение инвайта
                        if (args.length == 2 && args[1].equalsIgnoreCase("confirm")) {
                            java.util.UUID inviteClaimId = plugin.getClaimManager().getInvite(player.getUniqueId());
                            if (inviteClaimId == null) {
                                player.sendMessage(net.kyori.adventure.text.Component.text("§cУ вас нет активных приглашений."));
                                return true;
                            }
                            java.util.Optional<me.lovelace.loveclaims.model.Claim> claimOpt = plugin.getClaimManager().getClaimById(inviteClaimId);
                            if (claimOpt.isPresent()) {
                                me.lovelace.loveclaims.model.Claim claim = claimOpt.get();
                                if (claim.isClanTerritory()) {
                                    player.sendMessage(plugin.getConfigManager().getMessage("clan-claim-restricted"));
                                    plugin.getClaimManager().removeInvite(player.getUniqueId()); // Удаляем инвайт, если это клановый приват
                                    return true;
                                }

                                int memberCount = 0;
                                for (me.lovelace.loveclaims.model.Claim c : plugin.getClaimManager().getClaimsByPlayer(player.getUniqueId())) {
                                    if (c.isRentalPlot() == claim.isRentalPlot() && (c.getOwnerUuid() == null || !c.getOwnerUuid().equals(player.getUniqueId()))) {
                                        memberCount++;
                                    }
                                }

                                if (memberCount >= 5) {
                                    player.sendMessage(net.kyori.adventure.text.Component.text("§cВы уже состоите в максимальном количестве " + (claim.isRentalPlot() ? "плотов!" : "приватов!")));
                                    plugin.getClaimManager().removeInvite(player.getUniqueId());
                                    return true;
                                }

                                claim.setTrust(player.getUniqueId(), me.lovelace.loveclaims.model.TrustLevel.ACCESS);
                                plugin.getClaimManager().syncTrustGranted(claim, player.getUniqueId());
                                plugin.getStorage().saveMemberAsync(claim.getId(), player.getUniqueId(), me.lovelace.loveclaims.model.TrustLevel.ACCESS);
                                player.sendMessage(net.kyori.adventure.text.Component.text("§aВы успешно присоединились к привату!"));
                                plugin.getClaimManager().removeInvite(player.getUniqueId());
                                player.openInventory(new me.lovelace.loveclaims.gui.MembersGUI(plugin, player, claim).getInventory());
                            } else {
                                player.sendMessage(net.kyori.adventure.text.Component.text("§cЭтот приват больше не существует."));
                                plugin.getClaimManager().removeInvite(player.getUniqueId());
                            }
                            return true;
                        }
                        if (args.length == 2) {
                            String targetName = args[1];
                            java.util.Optional<me.lovelace.loveclaims.model.Claim> currentOpt = plugin.getClaimManager().getClaimAt(player.getLocation());

                            if (currentOpt.isEmpty()) {
                                player.sendMessage(plugin.getConfigManager().getMessage("not-in-your-claim"));
                                return true;
                            }

                            me.lovelace.loveclaims.model.Claim claim = currentOpt.get();
                            if (claim.isClanTerritory()) { // Проверка на клановый приват
                                player.sendMessage(plugin.getConfigManager().getMessage("clan-claim-restricted"));
                                return true;
                            }

                            if (claim.getTrust(player.getUniqueId()).ordinal() < me.lovelace.loveclaims.model.TrustLevel.MANAGER.ordinal()) {
                                player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                                return true;
                            }

                            org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(targetName);
                            if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                                player.sendMessage(plugin.getConfigManager().getMessage("member-not-found"));
                                return true;
                            }

                            if (claim.getTrust(target.getUniqueId()) != me.lovelace.loveclaims.model.TrustLevel.NONE) {
                                player.sendMessage(net.kyori.adventure.text.Component.text("§cИгрок уже является участником привата!"));
                                return true;
                            }

                            plugin.getClaimManager().addInvite(target.getUniqueId(), claim.getId());
                            player.sendMessage(plugin.getConfigManager().getMessage("invite-sent", "player", target.getName()));
                            plugin.getConfigManager().playSound(player, "gui-click");

                            if (target.isOnline() && target.getPlayer() != null) {
                                net.kyori.adventure.text.Component msg = plugin.getConfigManager().getMessage("invite-received", "player", player.getName())
                                        .append(plugin.getConfigManager().getMessage("invite-accept-btn")
                                                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/ac invite confirm"))
                                                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(plugin.getConfigManager().getMessage("invite-accept-hover"))));
                                target.getPlayer().sendMessage(msg);
                                plugin.getConfigManager().playSound(target.getPlayer(), "anchor-place");
                            }
                        } else {
                            player.sendMessage(plugin.getConfigManager().getComponent("msg-invite-usage"));
                        }
                        return true;
                    }
                }
                player.sendMessage(plugin.getConfigManager().getComponent("msg-invite-usage"));
                return true;
            }
            case "confirm" -> {
                me.lovelace.loveclaims.listener.AnchorListener.PendingClaim pending = plugin.getAnchorListener().getPendingClaims().remove(player.getUniqueId());
                if (pending != null) {
                    // При создании нового привата, он всегда будет PLAYER типом, поэтому проверка isClanTerritory() здесь не нужна.
                    pending.previewTask().revert();

                    // Повторная проверка пересечения прямо перед созданием: превью висит открытым
                    // произвольное время (пока игрок держит якорь в руке), и за это время другой
                    // игрок мог успеть создать и подтвердить свой приват поверх этой же зоны -
                    // исходная проверка в AnchorListener к моменту confirm уже устарела. Без
                    // повторной проверки здесь оба привата регистрируются с пересекающимися
                    // границами.
                    if (plugin.getClaimManager().checkOverlap(pending.location().getWorld(), pending.previewTask().getBox())) {
                        player.sendMessage(plugin.getConfigManager().getMessage("claim-overlap"));
                        return true;
                    }

                    Claim newClaim = new Claim(java.util.UUID.randomUUID(), pending.location().getWorld(), pending.previewTask().getBox(), player.getUniqueId(), pending.location());
                    plugin.getClaimManager().addClaimToCache(newClaim);
                    plugin.getStorage().saveClaimAsync(newClaim);

                    pending.location().getBlock().setType(pending.tier().material());
                    player.getInventory().getItemInMainHand().setAmount(player.getInventory().getItemInMainHand().getAmount() - 1);
                    player.sendMessage(plugin.getConfigManager().getMessage("claim-created"));
                    plugin.getConfigManager().playSound(player, "anchor-place");
                }
                return true;
            }
            case "show" -> {
                Optional<Claim> currentClaimOpt = plugin.getClaimManager().getClaimAt(player.getLocation());
                if (currentClaimOpt.isEmpty()) {
                    boolean hasAnyClaim = plugin.getClaimManager().getAllClaims().stream()
                            .anyMatch(c -> !c.isClanTerritory() && c.getTrust(player.getUniqueId()) != me.lovelace.loveclaims.model.TrustLevel.NONE);
                    if (hasAnyClaim) {
                        player.sendMessage(plugin.getConfigManager().getMessage("not-in-your-claim"));
                    } else {
                        player.sendMessage(plugin.getConfigManager().getMessage("not-in-claim"));
                    }
                    return true;
                }
                if (currentClaimOpt.get().isClanTerritory()) { // Проверка на клановый приват
                    player.sendMessage(plugin.getConfigManager().getMessage("clan-claim-restricted"));
                    return true;
                }
                me.lovelace.loveclaims.task.BorderDisplayTask.hideBorder(player);
                me.lovelace.loveclaims.task.BorderDisplayTask.showBorder(plugin, player, currentClaimOpt.get().getBoundingBox(), 200L);
                return true;
            }
            case "move" -> {
                Optional<Claim> opt = plugin.getClaimManager().getClaimAt(player.getLocation());
                if (opt.isEmpty() || opt.get().isRentalPlot()) {
                    player.sendMessage(plugin.getConfigManager().getMessage("not-in-your-claim"));
                    return true;
                }
                Claim claim = opt.get();
                if (claim.isClanTerritory()) { // Проверка на клановый приват
                    player.sendMessage(plugin.getConfigManager().getMessage("clan-claim-restricted"));
                    return true;
                }
                if (!claim.getOwnerUuid().equals(player.getUniqueId())) {
                    player.sendMessage(plugin.getConfigManager().getMessage("rental-move-deny"));
                    return true;
                }

                int currentSize = (int) Math.round(claim.getBoundingBox().getMaxX() - claim.getBoundingBox().getMinX());
                me.lovelace.loveclaims.model.ClaimTier currentTier = plugin.getAnchorManager().getTierBySize(currentSize);

                int baseSize = currentTier != null ? currentTier.radiusX() * 2 : 16;
                int expansions = (currentSize - baseSize);
                if (expansions > 0) {
                    me.lovelace.loveclaims.model.UserData data = plugin.getQuestManager().getUserData(player.getUniqueId());
                    data.addExpansionBlocks(expansions);
                    plugin.getStorage().saveUserDataAsync(data);
                }

                org.bukkit.inventory.ItemStack anchor = plugin.getAnchorManager().createAnchorItem(currentTier != null ? currentTier.id() : "tier-1");
                if (anchor != null) {
                    player.getInventory().addItem(anchor).values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                }

                plugin.getClaimManager().removeClaimFromCache(claim.getId());
                plugin.getStorage().deleteClaimAsync(claim.getId());
                claim.getAnchorLocation().getBlock().setType(org.bukkit.Material.AIR);

                player.sendMessage(plugin.getConfigManager().getMessage("rental-move-success"));
                plugin.getConfigManager().playSound(player, "anchor-break");
                return true;
            }
            case "admin" -> {
                if (!player.hasPermission("loveclaims.admin")) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                // Админ-подкоманды переехали под единую /loveclaimsadmin — здесь остаётся только
                // понятная подсказка, чтобы команда не «молчала» для тех, кто по привычке
                // набирает /ac admin ...
                player.sendMessage(plugin.getConfigManager().getMessage("admin-moved"));
                return true;
            }
            default -> {
                if (args.length == 1) {
                    String targetName = args[0];
                    plugin.getClaimManager().getAllClaims().stream()
                            .filter(c -> !c.isRentalPlot())
                            .filter(c -> !c.isClanTerritory()) // Игнорируем клановые приваты
                            .filter(c -> {
                                org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(c.getOwnerUuid());
                                return op.getName() != null && op.getName().equalsIgnoreCase(targetName);
                            })
                            .findFirst()
                            .ifPresentOrElse(
                                    c -> player.openInventory(new me.lovelace.loveclaims.gui.MainClaimGUI(plugin, player, c).getInventory()),
                                    () -> player.sendMessage(plugin.getConfigManager().getMessage("rental-claim-not-found", "player", targetName))
                            );
                    return true;
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        java.util.List<String> completions = new java.util.ArrayList<>();
        if (args.length == 1) {
            completions.addAll(java.util.List.of("home", "show", "confirm", "invite", "move", "help"));
            if (sender instanceof Player player) {
                plugin.getClaimManager().getAllClaims().stream()
                        .filter(c -> !c.isRentalPlot())
                        .filter(c -> !c.isClanTerritory()) // Игнорируем клановые приваты
                        .filter(c -> c.getTrust(player.getUniqueId()) != me.lovelace.loveclaims.model.TrustLevel.NONE)
                        .forEach(c -> {
                            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(c.getOwnerUuid());
                            if (op.getName() != null) {
                                completions.add(op.getName());
                            }
                        });
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("invite")) {
            for (Player p : Bukkit.getOnlinePlayers()) completions.add(p.getName());
            completions.add("confirm");
        }
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(java.util.stream.Collectors.toList());
    }
}
