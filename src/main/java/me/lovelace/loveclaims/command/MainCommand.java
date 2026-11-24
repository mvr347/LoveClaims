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
    private final GemCommandHandler gemCommandHandler;

    public MainCommand(LoveClaims plugin) {
        this.plugin = plugin;
        this.gemCommandHandler = new GemCommandHandler(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("gem")) {
            return gemCommandHandler.execute(sender, args);
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("player-only"));
            return true;
        }

        if (args.length == 0) {
            List<Claim> accessibleClaims = plugin.getClaimManager().getAllClaims().stream()
                    .filter(c -> !c.isRentalPlot())
                    .filter(c -> !c.isClanTerritory()) // Игнорируем клановые клеймы для стандартных команд
                    .filter(c -> c.isOwner(player.getUniqueId()) || c.getMembers().containsKey(player.getUniqueId()))
                    .toList();

            if (accessibleClaims.isEmpty()) {
                player.sendMessage(plugin.getConfigManager().getMessage("not-in-claim"));
                return true;
            }

            if (accessibleClaims.size() == 1) {
                player.openInventory(new me.lovelace.loveclaims.gui.MainClaimGUI(plugin, player, accessibleClaims.get(0)).getInventory());
                return true;
            }

            // Several claims: a mini menu to pick one (replaces the old chat list without coordinates)
            player.openInventory(new me.lovelace.loveclaims.gui.ClaimPickerGUI(plugin, player, accessibleClaims).getInventory());
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
                handleHome(player, args);
                return true;
            }

            case "invite" -> {
                if (args.length >= 2) {
                    if (args[0].equalsIgnoreCase("invite")) {
                        // Подтверждение инвайта
                        if (args.length == 2 && args[1].equalsIgnoreCase("confirm")) {
                            java.util.UUID inviteClaimId = plugin.getClaimManager().getInvite(player.getUniqueId());
                            if (inviteClaimId == null) {
                                player.sendMessage(plugin.getConfigManager().getMessage("invite.none"));
                                return true;
                            }
                            java.util.Optional<me.lovelace.loveclaims.model.Claim> claimOpt = plugin.getClaimManager().getClaimById(inviteClaimId);
                            if (claimOpt.isPresent()) {
                                me.lovelace.loveclaims.model.Claim claim = claimOpt.get();
                                if (claim.isOwner(player.getUniqueId()) || claim.getMembers().containsKey(player.getUniqueId())) {
                                    plugin.getClaimManager().removeInvite(player.getUniqueId());
                                    player.sendMessage(plugin.getConfigManager().getMessage("member-already-exists"));
                                    return true;
                                }
                                int memberLimit = plugin.getClaimManager().getMemberLimit(claim);
                                if (claim.getMembers().size() >= memberLimit) {
                                    plugin.getClaimManager().removeInvite(player.getUniqueId());
                                    player.sendMessage(plugin.getConfigManager().getMessage("limit-members"));
                                    return true;
                                }
                                if (claim.isClanTerritory()) {
                                    player.sendMessage(plugin.getConfigManager().getMessage("clan-claim-restricted"));
                                    plugin.getClaimManager().removeInvite(player.getUniqueId()); // Удаляем инвайт, если это клановый клейм
                                    return true;
                                }

                                int memberCount = 0;
                                for (me.lovelace.loveclaims.model.Claim c : plugin.getClaimManager().getClaimsByPlayer(player.getUniqueId())) {
                                    if (c.isRentalPlot() == claim.isRentalPlot() && (c.getOwnerUuid() == null || !c.getOwnerUuid().equals(player.getUniqueId()))) {
                                        memberCount++;
                                    }
                                }

                                if (memberCount >= 5) {
                                    player.sendMessage(plugin.getConfigManager().getMessage("invite.max-member", "kind", plugin.getConfigManager().getString(claim.isRentalPlot() ? "invite.kind-plots" : "invite.kind-claims")));
                                    plugin.getClaimManager().removeInvite(player.getUniqueId());
                                    return true;
                                }

                                claim.setTrust(player.getUniqueId(), me.lovelace.loveclaims.model.TrustLevel.ACCESS);
                                plugin.getClaimManager().syncTrustGranted(claim, player.getUniqueId());
                                plugin.getStorage().saveMemberAsync(claim.getId(), player.getUniqueId(), me.lovelace.loveclaims.model.TrustLevel.ACCESS);
                                player.sendMessage(plugin.getConfigManager().getMessage("invite.joined"));
                                plugin.getClaimManager().removeInvite(player.getUniqueId());
                            } else {
                                player.sendMessage(plugin.getConfigManager().getMessage("invite.gone"));
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
                            if (claim.isClanTerritory()) { // Проверка на клановый клейм
                                player.sendMessage(plugin.getConfigManager().getMessage("clan-claim-restricted"));
                                return true;
                            }

                            if (!claim.isManager(player.getUniqueId()) || claim.isTradePoint()) {
                                player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                                return true;
                            }

                            org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayerIfCached(targetName);
                            if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                                player.sendMessage(plugin.getConfigManager().getMessage("member-not-found"));
                                return true;
                            }

                            if (claim.isOwner(target.getUniqueId()) || claim.getMembers().containsKey(target.getUniqueId())) {
                                player.sendMessage(plugin.getConfigManager().getMessage("invite.already-member"));
                                return true;
                            }

                            if (!me.lovelace.loveclaims.util.AuthGate.canReceive(target)) {
                                player.sendMessage(plugin.getConfigManager().getMessage("invite.target-unauthorized"));
                                return true;
                            }
                            plugin.getClaimManager().addInvite(target.getUniqueId(), claim.getId(), player.getUniqueId());
                            player.sendMessage(plugin.getConfigManager().getMessage("invite-sent", "player", target.getName()));
                            plugin.getConfigManager().playSound(player, "gui-click");

                            if (target.isOnline() && target.getPlayer() != null) {
                                net.kyori.adventure.text.Component msg = plugin.getConfigManager().getMessage("invite-received", "player", player.getName())
                                        .append(plugin.getConfigManager().getComponent("invite-accept-btn")
                                                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/ac invite confirm"))
                                                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(plugin.getConfigManager().getComponent("invite-accept-hover"))));
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
                    // The anchor must still be in hand and of the pending tier; otherwise the claim would be free.
                    java.util.Optional<me.lovelace.loveclaims.model.ClaimTier> heldTier =
                            plugin.getAnchorManager().getTierFromItem(player.getInventory().getItemInMainHand());
                    if (heldTier.isEmpty() || !heldTier.get().id().equals(pending.tier().id())) {
                        pending.previewTask().revert();
                        player.sendMessage(plugin.getConfigManager().getMessage("anchor-cancel"));
                        return true;
                    }
                    // При создании нового клейма, он всегда будет PLAYER типом, поэтому проверка isClanTerritory() здесь не нужна.
                    pending.previewTask().revert();

                    // Повторная проверка пересечения прямо перед созданием: превью висит открытым
                    // произвольное время (пока игрок держит якорь в руке), и за это время другой
                    // игрок мог успеть создать и подтвердить свой клейм поверх этой же зоны -
                    // исходная проверка в AnchorListener к моменту confirm уже устарела. Без
                    // повторной проверки здесь оба клейма регистрируются с пересекающимися
                    // границами.
                    java.util.Optional<Claim> conflictOpt = plugin.getClaimManager().getFirstOverlappingClaim(pending.location().getWorld(), pending.previewTask().getBox(), null);
                    if (conflictOpt.isPresent()) {
                        Claim conflict = conflictOpt.get();
                        player.sendMessage(plugin.getConfigManager().getMessage("claim-overlap"));
                        me.lovelace.loveclaims.task.BorderDisplayTask.showBorder(plugin, player, conflict.getBoundingBox(), 140L, conflict.getId());
                        return true;
                    }

                    long currentClaims = plugin.getClaimManager().getClaimsByOwner(player.getUniqueId()).stream()
                            .filter(c -> !c.isRentalPlot() && !c.isClanTerritory())
                            .count();
                    if (currentClaims >= plugin.getClaimManager().getMaxClaimsForPlayer(player.getUniqueId())) {
                        player.sendMessage(plugin.getConfigManager().getMessage("claim-limit-reached"));
                        return true;
                    }

                    Claim newClaim = new Claim(java.util.UUID.randomUUID(), pending.location().getWorld(), pending.previewTask().getBox(), player.getUniqueId(), pending.location());
                    newClaim.setAnchorTierId(pending.tier().id());
                    newClaim.setBehaviorBonusRadius(pending.behaviorBonusRadius());
                    // A gem fused into the anchor item stays fused in the new claim. If its radius would overlap
                    // a neighbour, the gem is handed back as an ordinary gem item instead of losing it.
                    var fused = plugin.getAnchorManager().readFusedGem(player.getInventory().getItemInMainHand());
                    // A cracked core stays cracked when the anchor is placed again: it is repaired only by a new core.
                    newClaim.setAnchorBroken(plugin.getAnchorManager().readBroken(player.getInventory().getItemInMainHand()));
                    plugin.getClaimManager().addClaimToCache(newClaim);
                    if (fused.isPresent()) {
                        if (plugin.getClaimManager().canApplyCandidateGems(newClaim, java.util.List.of(), fused.get())) {
                            newClaim.setPermanentGem(fused.get());
                            plugin.getClaimManager().recalculateClaimModifiers(newClaim);
                        } else {
                            var gemItem = plugin.getGemRegistry().createItemFromStats(fused.get());
                            if (gemItem != null) {
                                player.getInventory().addItem(gemItem).values()
                                        .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                            }
                            player.sendMessage(plugin.getConfigManager().getMessage("claim-overlap"));
                        }
                    }
                    plugin.getStorage().saveClaimAsync(newClaim);
                    me.lovelace.loveclaims.integration.AchievementsHook.claimCreated(player.getUniqueId());

                    pending.location().getBlock().setType(pending.tier().material());
                    player.getInventory().getItemInMainHand().setAmount(player.getInventory().getItemInMainHand().getAmount() - 1);
                    player.sendMessage(plugin.getConfigManager().getMessage("claim-created"));
                    plugin.getConfigManager().playSound(player, "anchor-place");
                }
                return true;
            }
            case "master" -> {
                // The anchor master's counter (upgrade, fusing). Only through the craftsman hub, which sets
                // craftsman_hub_until for a few seconds; administrators may always open it.
                boolean viaHub = player.getMetadata("craftsman_hub_until").stream()
                        .anyMatch(v -> v.asLong() > System.currentTimeMillis());
                if (!viaHub && !player.hasPermission("loveclaims.admin")) {
                    player.sendMessage(plugin.getConfigManager().getMessage("master.hub-only"));
                    return true;
                }
                player.openInventory(new me.lovelace.loveclaims.gui.AnchorUpgradeGUI(plugin, player).getInventory());
                return true;
            }
            case "merge" -> {
                handleMerge(player, args);
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
                if (currentClaimOpt.get().isClanTerritory()) { // Проверка на клановый клейм
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
                if (claim.isClanTerritory()) { // Проверка на клановый клейм
                    player.sendMessage(plugin.getConfigManager().getMessage("clan-claim-restricted"));
                    return true;
                }
                if (!claim.getOwnerUuid().equals(player.getUniqueId())) {
                    player.sendMessage(plugin.getConfigManager().getMessage("rental-move-deny"));
                    return true;
                }

                plugin.getAnchorManager().giveClaimAnchorAndGems(claim, player);

                plugin.getClaimManager().removeClaimFromCache(claim.getId());
                plugin.getStorage().deleteClaimAsync(claim.getId());
                claim.getAnchorLocation().getBlock().setType(org.bukkit.Material.AIR);

                player.sendMessage(plugin.getConfigManager().getMessage("rental-move-success"));
                plugin.getConfigManager().playSound(player, "anchor-break");
                return true;
            }
            case "admin" -> {
                if (!player.hasPermission("loveclaims.admin")) {
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
                            .filter(c -> !c.isClanTerritory()) // Игнорируем клановые клеймы
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

    /** Claims the player may teleport to: own ones and those where they are a member (never rentals or clan land). */
    private List<Claim> homeClaims(Player player) {
        return plugin.getClaimManager().getAllClaims().stream()
                .filter(c -> !c.isRentalPlot() && !c.isClanTerritory())
                .filter(c -> c.isOwner(player.getUniqueId()) || c.getMembers().containsKey(player.getUniqueId()))
                .sorted(java.util.Comparator.comparing((Claim c) -> !c.isOwner(player.getUniqueId())))
                .toList();
    }

    /**
     * /claim home [name]: a named claim goes straight there; with several claims (own or as a member) a small menu
     * asks which one; with one claim it teleports (after the warm-up). Standing in the target claim refuses.
     */
    private void handleHome(Player player, String[] args) {
        List<Claim> homes = homeClaims(player);
        if (homes.isEmpty()) {
            player.sendMessage(plugin.getConfigManager().getMessage("not-in-claim"));
            plugin.getConfigManager().playSound(player, "anchor-error");
            return;
        }
        if (args.length >= 2) {
            for (Claim c : homes) {
                if (c.getName() != null && c.getName().equalsIgnoreCase(args[1])) {
                    plugin.getClaimTeleport().request(player, c);
                    return;
                }
            }
        }
        if (homes.size() == 1) {
            plugin.getClaimTeleport().request(player, homes.get(0));
            return;
        }
        player.openInventory(new me.lovelace.loveclaims.gui.ClaimPickerGUI(plugin, player, homes, true).getInventory());
    }

    /** /ac merge: open the merge menu for the claim you stand in; /ac merge accept|decline answers a request. */
    private void handleMerge(Player player, String[] args) {
        var merger = plugin.getClaimMerger();
        if (args.length >= 2 && (args[1].equalsIgnoreCase("accept") || args[1].equalsIgnoreCase("decline"))) {
            var request = merger.takeRequest(player.getUniqueId());
            if (request == null) {
                player.sendMessage(plugin.getConfigManager().getMessage("merge.no-request"));
                return;
            }
            Player requester = plugin.getServer().getPlayer(request.requester());
            var mini = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
            if (args[1].equalsIgnoreCase("decline")) {
                player.sendMessage(plugin.getConfigManager().getMessage("merge.declined-self"));
                if (requester != null) requester.sendMessage(plugin.getConfigManager().getMessage("merge.declined-other"));
                return;
            }
            Claim survivor = plugin.getClaimManager().getClaimById(request.survivorId()).orElse(null);
            Claim absorbed = plugin.getClaimManager().getClaimById(request.absorbedId()).orElse(null);
            if (requester == null || survivor == null || absorbed == null
                    || !survivor.isOwner(requester.getUniqueId()) || !absorbed.isOwner(player.getUniqueId())) {
                player.sendMessage(plugin.getConfigManager().getMessage("merge.stale"));
                return;
            }
            var plan = merger.plan(survivor, absorbed);
            if (plan.isEmpty()) {
                player.sendMessage(plugin.getConfigManager().getMessage("merge.impossible"));
                requester.sendMessage(plugin.getConfigManager().getMessage("merge.impossible"));
                return;
            }
            if (!me.lovelace.loveclaims.util.MergeSeal.consume(plugin, requester)) {
                player.sendMessage(plugin.getConfigManager().getMessage("merge.no-seal-author"));
                requester.sendMessage(plugin.getConfigManager().getMessage("merge.need-seal"));
                return;
            }
            var result = merger.execute(plan.get());
            if (result != me.lovelace.loveclaims.manager.ClaimMerger.Reason.OK) {
                // Give the seal back: nothing was merged.
                requester.getInventory().addItem(me.lovelace.loveclaims.util.MergeSeal.create(plugin));
                player.sendMessage(plugin.getConfigManager().getMessage("merge.failed"));
                return;
            }
            player.sendMessage(plugin.getConfigManager().getMessage("merge.done-owner"));
            requester.sendMessage(plugin.getConfigManager().getMessage("merge.done"));
            return;
        }
        Claim here = plugin.getClaimManager().getClaimAt(player.getLocation()).orElse(null);
        if (here == null || !here.isOwner(player.getUniqueId()) || here.isRentalPlot() || here.isClanTerritory()) {
            player.sendMessage(plugin.getConfigManager().getMessage("merge.stand-inside"));
            return;
        }
        player.openInventory(new me.lovelace.loveclaims.gui.MergeGUI(plugin, player, here).getInventory());
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("gem")) {
            return gemCommandHandler.tabComplete(sender, args);
        }
        java.util.List<String> completions = new java.util.ArrayList<>();
        if (args.length == 1) {
            completions.addAll(java.util.List.of("home", "show", "confirm", "invite", "move", "merge", "help"));
            if (sender.hasPermission(GemCommandHandler.PERMISSION)) {
                completions.add("gem");
            }
            if (sender instanceof Player player) {
                plugin.getClaimManager().getAllClaims().stream()
                        .filter(c -> !c.isRentalPlot())
                        .filter(c -> !c.isClanTerritory()) // Игнорируем клановые клеймы
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
