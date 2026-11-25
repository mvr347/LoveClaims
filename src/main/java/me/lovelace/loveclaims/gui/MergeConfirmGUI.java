package me.lovelace.loveclaims.gui;

import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.util.GuiText;
import me.lovelace.loveclaims.manager.ClaimMerger;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.util.MergeSeal;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;

import static me.lovelace.loveclaims.textures.HeadTextures.*;

/**
 * Confirmation of a merge (gui_gen exception 1, 9 slots). Your own claims merge right away; with another owner a
 * request goes to them first and the seal is spent only once they accept.
 */
public class MergeConfirmGUI extends AbstractGUI {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final LoveClaims plugin;
    private final Player viewer;
    private final Claim survivor;
    private final Claim absorbed;

    public MergeConfirmGUI(LoveClaims plugin, Player viewer, Claim survivor, Claim absorbed) {
        super(confirmSize(viewer), confirmTitle(viewer, GuiText.name(plugin, "mergeConfirmGUI.t1")));
        this.plugin = plugin;
        this.viewer = viewer;
        this.survivor = survivor;
        this.absorbed = absorbed;
        setMenuItems();
        plugin.getEffects().playFor(viewer, "gui-click");
    }

    private boolean sameOwner() {
        return survivor.getOwnerUuid() != null && survivor.getOwnerUuid().equals(absorbed.getOwnerUuid());
    }

    @Override
    protected void setMenuItems() {
        var plan = plugin.getClaimMerger().plan(survivor, absorbed);
        List<Component> lore = new ArrayList<>();
        lore.add(GuiText.name(plugin, "mergeConfirmGUI.t2"));
        lore.add(Component.empty());
        lore.add(GuiText.name(plugin, "mergeConfirmGUI.t3"));
        lore.add(GuiText.name(plugin, "mergeConfirmGUI.t4"));
        lore.add(GuiText.name(plugin, "mergeConfirmGUI.t5"));
        lore.add(GuiText.name(plugin, "mergeConfirmGUI.t6"));
        if (plan.isPresent() && plan.get().bothHavePermanentGem()) {
            lore.add(GuiText.name(plugin, "mergeConfirmGUI.t7"));
            lore.add(GuiText.name(plugin, "mergeConfirmGUI.t8"));
        }
        boolean seal = MergeSeal.has(plugin, viewer);
        lore.add(Component.empty());
        lore.add(GuiText.name(plugin, "mergeConfirmGUI.t9"));
        lore.add(noItalic(MM.deserialize((seal ? "  <green>✓ " : "  <red>✕ ") + "<gray>Печать слияния: " + (seal ? "<green>есть" : "<red>нет"))));
        if (!sameOwner()) {
            lore.add(GuiText.name(plugin, "mergeConfirmGUI.t10"));
        }
        lore.add(Component.empty());
        lore.add(noItalic(MM.deserialize("<dark_gray><st>─────────────────────</st></dark_gray>")));
        lore.add(noItalic(MM.deserialize(sameOwner() ? "<green>ЛКМ <dark_gray>— <gray>объединить" : "<green>ЛКМ <dark_gray>— <gray>отправить запрос владельцу")));
        placeConfirm(GuiText.name(plugin, "mergeConfirmGUI.t11"), lore,
                GuiText.name(plugin, "mergeConfirmGUI.t12"),
                List.of(Component.empty(), noItalic(MM.deserialize("<dark_gray><st>─────────────────────</st></dark_gray>")),
                        GuiText.name(plugin, "mergeConfirmGUI.t13")));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (isCancelSlot(event.getSlot())) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.openInventory(new MergeGUI(plugin, viewer, survivor).getInventory());
            return;
        }
        if (!isConfirmSlot(event.getSlot())) return;

        if (!survivor.isOwner(viewer.getUniqueId())) {
            deny("<red>Объединять клейм может только его владелец.</red>");
            return;
        }
        var plan = plugin.getClaimMerger().plan(survivor, absorbed);
        if (plan.isEmpty()) {
            deny("<red>" + MergeGUI.reasonText(plugin.getClaimMerger().check(survivor, absorbed)) + ".</red>");
            return;
        }
        // Every merge costs a seal, also between two claims of one owner.
        if (!MergeSeal.has(plugin, viewer)) {
            deny("<red>Нужна Печать слияния.</red>");
            return;
        }
        viewer.closeInventory();
        if (sameOwner()) {
            MergeSeal.consume(plugin, viewer);
            ClaimMerger.Reason result = plugin.getClaimMerger().execute(plan.get());
            if (result != ClaimMerger.Reason.OK) viewer.getInventory().addItem(MergeSeal.create(plugin)); // nothing merged: give it back
            viewer.sendMessage(noItalic(MM.deserialize(result == ClaimMerger.Reason.OK
                    ? "<green>Клеймы объединены.</green>" : "<red>" + MergeGUI.reasonText(result) + ".</red>")));
            return;
        }
        Player target = plugin.getServer().getPlayer(absorbed.getOwnerUuid());
        if (target == null) {
            deny("<red>Владелец второго клейма сейчас не в сети.</red>");
            return;
        }
        plugin.getClaimMerger().request(survivor, absorbed, viewer);
        viewer.sendMessage(noItalic(MM.deserialize("<green>Запрос на слияние отправлен игроку <white>" + MM.escapeTags(target.getName())
                + "</white>. Он действует 60 секунд.</green>")));
        target.sendMessage(noItalic(MM.deserialize("<gold>" + MM.escapeTags(viewer.getName())
                + "</gold> <gray>предлагает объединить ваш клейм со своим. Вы станете менеджером объединённого клейма; "
                + "ваш якорь и гемы вернутся вам.")));
        target.sendMessage(noItalic(MM.deserialize("<green>[Принять]</green>")
                .clickEvent(ClickEvent.runCommand("/ac merge accept"))
                .append(Component.text("  "))
                .append(MM.deserialize("<red>[Отклонить]</red>").clickEvent(ClickEvent.runCommand("/ac merge decline")))));
    }

    private void deny(String message) {
        plugin.getEffects().playFor(viewer, "claim-merge-denied");
        viewer.sendMessage(noItalic(MM.deserialize(message)));
    }

    private static Component noItalic(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }
}
