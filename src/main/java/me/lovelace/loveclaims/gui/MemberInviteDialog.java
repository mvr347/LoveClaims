package me.lovelace.loveclaims.gui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.model.Claim;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Owner-bound, expiring input. A submission only sends an invitation; it never grants membership. */
public final class MemberInviteDialog {
    private static final java.util.concurrent.ConcurrentMap<UUID, UUID> sessions =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder().maximumSize(1024)
                    .expireAfterWrite(Duration.ofMinutes(3)).<UUID, UUID>build().asMap();
    private MemberInviteDialog() {}

    public static void cancel(Player player) { sessions.remove(player.getUniqueId()); }

    public static void open(LoveClaims plugin, Player viewer, Claim claim) {
        // InventoryClickEvent must finish before replacing the inventory with a native dialog.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (viewer.isOnline()) plugin.getClaimManager().getClaimById(claim.getId())
                    .ifPresent(current -> show(plugin, viewer, current));
        });
    }

    private static void show(LoveClaims plugin, Player viewer, Claim claim) {
        if (!claim.isManager(viewer.getUniqueId()) || claim.isClanTerritory() || claim.isTradePoint()) return;
        plugin.getChatListener().cleanupPlayer(viewer);
        var token = UUID.randomUUID();
        var owner = viewer.getUniqueId();
        var claimId = claim.getId();
        sessions.put(owner, token);
        var lang = plugin.getConfigManager();
        var options = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(3)).build();
        var submit = ActionButton.create(lang.getComponent("members.dialog.send"), null, 150,
                DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player actor) || !actor.getUniqueId().equals(owner)) return;
                    String name = response.getText("player_name");
                    if (!plugin.isEnabled()) return;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!actor.isOnline() || !sessions.remove(owner, token)) return;
                        // Resolve the claim and permissions again: both may have changed while the form was open.
                        if (name != null && !name.isBlank() && name.trim().length() <= 32)
                            plugin.getChatListener().inviteMember(actor, claimId, name.trim());
                        else actor.sendMessage(lang.getMessage("member-not-found"));
                        reopen(plugin, actor, claimId);
                    });
                }, options));
        var close = ActionButton.create(lang.getComponent("common.cancel"), null, 150,
                DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player actor) || !actor.getUniqueId().equals(owner) || !plugin.isEnabled()) return;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (actor.isOnline() && sessions.remove(owner, token)) reopen(plugin, actor, claimId);
                    });
                }, options));
        viewer.closeInventory();
        viewer.showDialog(Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(lang.getComponent("members.dialog.title")).pause(false)
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(lang.getComponent("members.dialog.body"))))
                        .inputs(List.of(DialogInput.text("player_name", 300,
                                lang.getComponent("members.dialog.name"), true, "", 32, null))).build())
                .type(DialogType.multiAction(List.of(submit, close), null, 2))));
    }

    private static void reopen(LoveClaims plugin, Player player, UUID id) {
        plugin.getClaimManager().getClaimById(id).filter(c -> c.isManager(player.getUniqueId())).ifPresent(c ->
                player.openInventory(c.isRentalPlot() ? new RentalMembersGUI(plugin, player, c).getInventory()
                        : new MembersGUI(plugin, player, c).getInventory()));
    }
}
