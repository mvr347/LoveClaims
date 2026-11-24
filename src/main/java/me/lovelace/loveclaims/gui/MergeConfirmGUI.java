package me.lovelace.loveclaims.gui;

import me.lovelace.loveclaims.LoveClaims;
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
    private static final int CONFIRM_SLOT = 1;
    private static final int CANCEL_SLOT = 7;

    private final LoveClaims plugin;
    private final Player viewer;
    private final Claim survivor;
    private final Claim absorbed;

    public MergeConfirmGUI(LoveClaims plugin, Player viewer, Claim survivor, Claim absorbed) {
        super(9, noItalic(MM.deserialize("<gold>Подтвердите слияние</gold>")));
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
        lore.add(noItalic(MM.deserialize("<dark_gray>Слияние двух приватов")));
        lore.add(Component.empty());
        lore.add(noItalic(MM.deserialize("<dark_gray>▪ <gray>Что произойдёт:")));
        lore.add(noItalic(MM.deserialize("  <green>✓ <gray>Остаётся этот приват, область объединяется")));
        lore.add(noItalic(MM.deserialize("  <gray>Якорь и гемы второго привата вернутся владельцу")));
        lore.add(noItalic(MM.deserialize("  <gray>Участники объединяются, права строже побеждают")));
        if (plan.isPresent() && plan.get().bothHavePermanentGem()) {
            lore.add(noItalic(MM.deserialize("  <red>✕ <gray>У обоих вшит гем: останется <white>случайный</white>,")));
            lore.add(noItalic(MM.deserialize("    <gray>второй пропадёт")));
        }
        if (!sameOwner()) {
            boolean seal = MergeSeal.has(plugin, viewer);
            lore.add(Component.empty());
            lore.add(noItalic(MM.deserialize("<dark_gray>▪ <gray>Требования:")));
            lore.add(noItalic(MM.deserialize((seal ? "  <green>✓ " : "  <red>✕ ") + "<gray>Печать слияния: " + (seal ? "<green>есть" : "<red>нет"))));
            lore.add(noItalic(MM.deserialize("  <gray>Согласие владельца второго привата")));
        }
        lore.add(noItalic(MM.deserialize("<dark_gray><st>─────────────────────</st></dark_gray>")));
        lore.add(noItalic(MM.deserialize(sameOwner() ? "<green>ЛКМ <dark_gray>— <gray>объединить" : "<green>ЛКМ <dark_gray>— <gray>отправить запрос владельцу")));
        inventory.setItem(CONFIRM_SLOT, createHead(HEAD_DELETE_YES, noItalic(MM.deserialize("<green>Подтвердить")), lore));
        inventory.setItem(CANCEL_SLOT, createHead(HEAD_DELETE_NO, noItalic(MM.deserialize("<red>Отмена")),
                List.of(noItalic(MM.deserialize("<dark_gray><st>─────────────────────</st></dark_gray>")),
                        noItalic(MM.deserialize("<green>ЛКМ <dark_gray>— <gray>вернуться")))));
        setGlass(0, 2, 6, 8);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getSlot() == CANCEL_SLOT) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.openInventory(new MergeGUI(plugin, viewer, survivor).getInventory());
            return;
        }
        if (event.getSlot() != CONFIRM_SLOT) return;

        if (!survivor.isOwner(viewer.getUniqueId())) {
            deny("<red>Объединять приват может только его владелец.</red>");
            return;
        }
        var plan = plugin.getClaimMerger().plan(survivor, absorbed);
        if (plan.isEmpty()) {
            deny("<red>" + MergeGUI.reasonText(plugin.getClaimMerger().check(survivor, absorbed)) + ".</red>");
            return;
        }
        viewer.closeInventory();
        if (sameOwner()) {
            ClaimMerger.Reason result = plugin.getClaimMerger().execute(plan.get());
            viewer.sendMessage(noItalic(MM.deserialize(result == ClaimMerger.Reason.OK
                    ? "<green>Приваты объединены.</green>" : "<red>" + MergeGUI.reasonText(result) + ".</red>")));
            return;
        }
        if (!MergeSeal.has(plugin, viewer)) {
            deny("<red>Нужна Печать слияния.</red>");
            return;
        }
        Player target = plugin.getServer().getPlayer(absorbed.getOwnerUuid());
        if (target == null) {
            deny("<red>Владелец второго привата сейчас не в сети.</red>");
            return;
        }
        plugin.getClaimMerger().request(survivor, absorbed, viewer);
        viewer.sendMessage(noItalic(MM.deserialize("<green>Запрос на слияние отправлен игроку <white>" + MM.escapeTags(target.getName())
                + "</white>. Он действует 60 секунд.</green>")));
        target.sendMessage(noItalic(MM.deserialize("<gold>" + MM.escapeTags(viewer.getName())
                + "</gold> <gray>предлагает объединить ваш приват со своим. Вы станете менеджером объединённого привата; "
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
