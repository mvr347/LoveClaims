package me.lovelace.loveclaims.gui;

import me.lovelace.loveclaims.LoveClaims;
import static me.lovelace.loveclaims.textures.HeadTextures.*;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.model.Quest;
import me.lovelace.loveclaims.model.UserData;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;

public class QuestsGUI extends AbstractGUI {
    private static final int PAGE_SIZE = CONTENT_SLOTS_54.length;

    private final LoveClaims plugin;
    private final Player viewer;
    private final Claim claim;
    private int page = 0;
    private int visibleQuestCount = 0;

    public QuestsGUI(LoveClaims plugin, Player viewer, Claim claim) {
        super(54, plugin.getConfigManager().getComponent("quests.title"));
        this.plugin = plugin;
        this.viewer = viewer;
        this.claim = claim;
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        inventory.clear();

        UserData data = plugin.getQuestManager().getUserData(viewer.getUniqueId());

        int currentSize = (int) Math.round(claim.getBoundingBox().getMaxX() - claim.getBoundingBox().getMinX());
        me.lovelace.loveclaims.model.ClaimTier currentTier = plugin.getAnchorManager().getTierBySize(currentSize);
        String currentTierId = currentTier != null ? currentTier.id() : "all";

        List<Quest> visibleQuests = new ArrayList<>();
        for (Quest quest : plugin.getQuestManager().getAllQuests()) {
            if (!quest.tier().equals("all") && !quest.tier().equals(currentTierId)) {
                continue; // Пропускаем квесты, которые не предназначены для текущего тира привата
            }
            visibleQuests.add(quest);
        }

        // gui-gen-5 RULE 3: слот 0 — тематическая иконка (список квестов привата).
        inventory.setItem(0, createHead(HEAD_QUEST, plugin.getConfigManager().getComponent("quests.title"), null));

        visibleQuestCount = visibleQuests.size();
        int totalPages = Math.max(1, (int) Math.ceil(visibleQuests.size() / (double) PAGE_SIZE));
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, visibleQuests.size());

        for (int i = start; i < end; i++) {
            Quest quest = visibleQuests.get(i);
            int contentSlot = CONTENT_SLOTS_54[i - start];

            boolean completed = data.isQuestCompleted(quest.id());
            int progress = Math.min(data.getQuestProgress(quest.id()), quest.targetAmount());

            List<Component> lore = new ArrayList<>();
            if (completed) {
                lore.add(Component.empty());
                lore.add(plugin.getConfigManager().getComponent("quests.completed"));
                if (quest.repeatable()) {
                    lore.add(Component.empty());
                    lore.add(Component.text("§7Повторяемый: §aДа"));
                }
            } else {
                lore.add(Component.text(quest.description().replace("&", "§")));
                lore.add(plugin.getConfigManager().getComponent("quests.target", "amount", String.valueOf(quest.targetAmount()), "target", quest.targetName()));
                lore.add(plugin.getConfigManager().getComponent("quests.progress", "progress", String.valueOf(progress), "amount", String.valueOf(quest.targetAmount())));
                lore.add(Component.empty());

                // Показываем тип квеста
                if (quest.daily()) {
                    lore.add(Component.text("§b§lЕжедневный квест"));
                } else if (quest.repeatable()) {
                    lore.add(Component.text("§7Повторяемый: §aДа"));
                }

                lore.add(Component.empty());
                lore.add(plugin.getConfigManager().getComponent("quests.reward"));

                boolean hasRewards = false;
                if (quest.rewardSlots() > 0) { lore.add(plugin.getConfigManager().getComponent("quests.reward-slots", "slots", String.valueOf(quest.rewardSlots()))); hasRewards = true; }
                if (quest.rewardBlocks() > 0) { lore.add(plugin.getConfigManager().getComponent("quests.reward-blocks", "blocks", String.valueOf(quest.rewardBlocks()))); hasRewards = true; }
                if (quest.rewardExpansionBlocks() > 0) { lore.add(plugin.getConfigManager().getComponent("quests.reward-points", "points", String.valueOf(quest.rewardExpansionBlocks()))); hasRewards = true; }
                if (quest.rewardMembers() > 0) { lore.add(plugin.getConfigManager().getComponent("quests.reward-members", "members", String.valueOf(quest.rewardMembers()))); hasRewards = true; }

                // ВЫВОД БАФФА
                String rBuff = quest.rewardBuff();
                if (rBuff != null && !rBuff.trim().isEmpty()) {
                    String flagKey = rBuff.replace("PERK_", "").toLowerCase(); // haste
                    String buffTranslate = plugin.getConfigManager().getString("settings.flags." + flagKey);

                    if (buffTranslate.contains("Error")) {
                        buffTranslate = rBuff;
                    }

                    lore.add(plugin.getConfigManager().getComponent("quests.reward-buff", "buff", buffTranslate));
                    hasRewards = true;
                }

                if (!hasRewards) lore.add(plugin.getConfigManager().getComponent("quests.no-reward"));
            }

            inventory.setItem(contentSlot, createHead(HEAD_QUEST, plugin.getConfigManager().getComponent("quests.quest-name", "name", quest.name()), lore));
        }

        // Пагинация (Исключение 2, только 54-слотовое): активна только если есть больше одной страницы.
        if (page > 0) {
            inventory.setItem(PAGINATION_PREV_SLOT_54, createHead(HEAD_ARROW_LEFT, Component.text("§6← Назад"), null));
        }
        if (end < visibleQuests.size()) {
            inventory.setItem(PAGINATION_NEXT_SLOT_54, createHead(HEAD_ARROW_RIGHT, Component.text("§6Вперёд →"), null));
        }

        setFooterButtons(
                null,
                createHead(HEAD_BACK, plugin.getConfigManager().getComponent("common.back"), null),
                createHead(HEAD_BARRIER, plugin.getConfigManager().getComponent("common.close"), null)
        );
        fillFrameGlass();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        int size = inventory.getSize();

        if (slot == size - 1) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.closeInventory();
            return;
        }
        if (slot == size - 2) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.openInventory(new MainClaimGUI(plugin, viewer, claim).getInventory());
            return;
        }
        if (slot == PAGINATION_PREV_SLOT_54 && page > 0) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            page--;
            setMenuItems();
            return;
        }
        if (slot == PAGINATION_NEXT_SLOT_54) {
            int totalPages = Math.max(1, (int) Math.ceil(visibleQuestCount / (double) PAGE_SIZE));
            if (page < totalPages - 1) {
                plugin.getConfigManager().playSound(viewer, "gui-click");
                page++;
                setMenuItems();
            }
        }
    }
}
