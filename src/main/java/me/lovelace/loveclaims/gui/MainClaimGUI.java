package me.lovelace.loveclaims.gui;

import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.util.GuiText;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static me.lovelace.loveclaims.textures.HeadTextures.*;

/**
 * Root menu of a claim (27 slots): members, core (gems), settings. The merge button sits in the Header and appears
 * only for the owner of a claim that has a neighbour to merge with. Root menu: no Back button.
 */
public class MainClaimGUI extends AbstractGUI {
    private static final int SLOT_MEMBERS = 11;
    private static final int SLOT_CORE = 13;
    private static final int SLOT_SETTINGS = 15;

    // cooldown для показа границ (оптимизация от лагов)
    private static final Map<UUID, Long> borderCooldown = new ConcurrentHashMap<>();

    private final LoveClaims plugin;
    private final Player viewer;
    private final Claim claim;
    private final boolean showMerge;

    public MainClaimGUI(LoveClaims plugin, Player viewer, Claim claim) {
        super(27, plugin.getConfigManager().getComponent("main.title"));
        this.plugin = plugin;
        this.viewer = viewer;
        this.claim = claim;
        this.showMerge = claim.isOwner(viewer.getUniqueId()) && !claim.isRentalPlot() && !claim.isClanTerritory()
                && plugin.getClaimMerger() != null && MergeGUI.hasNeighbour(plugin, claim);
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        inventory.clear();
        int currentSize = (int) Math.round(claim.getBoundingBox().getMaxX() - claim.getBoundingBox().getMinX());
        String ownerName = Bukkit.getOfflinePlayer(claim.getOwnerUuid()).getName();
        if (ownerName == null) ownerName = "???";

        // gui_gen RULE 3: slot 0 is the menu's own icon (claim info: LMB teleport, RMB borders)
        inventory.setItem(0, createHead(HEAD_INFO, GuiText.name(plugin, "main.info-name"),
                GuiText.lore(plugin, "main.info-lore", "owner", GuiText.esc(ownerName), "size", String.valueOf(currentSize),
                        "delay", String.valueOf(plugin.getClaimTeleport().delaySeconds()))));

        var tier = plugin.getAnchorManager().resolveClaimTier(claim);
        int maxSlots = tier != null ? tier.gemSlots() : 3;
        int usedSlots = claim.getModifiers().usedSlots();
        int maxMembers = plugin.getClaimManager().getMemberLimit(claim);

        // Work zone: members, core, settings (centred 11, 13, 15)
        inventory.setItem(SLOT_MEMBERS, createHead(HEAD_MEMBERS, GuiText.name(plugin, "main.members-name"),
                GuiText.lore(plugin, "main.members-lore", "count", String.valueOf(claim.getMembers().size()),
                        "max", String.valueOf(maxMembers))));
        if (claim.canManageCore(viewer.getUniqueId())) {
            inventory.setItem(SLOT_CORE, createHead(HEAD_INFO, GuiText.name(plugin, "main.gems-name"),
                    GuiText.lore(plugin, "main.gems-lore", "used", String.valueOf(usedSlots), "max", String.valueOf(maxSlots))));
        } else {
            inventory.setItem(SLOT_CORE, createHead(HEAD_BARRIER, GuiText.name(plugin, "main.gems-blocked-name"),
                    GuiText.lore(plugin, "main.gems-blocked-lore")));
        }
        inventory.setItem(SLOT_SETTINGS, createHead(HEAD_SETTINGS, GuiText.name(plugin, "main.settings-name"),
                GuiText.lore(plugin, "main.settings-lore")));

        // Header control button (gui_gen RULE 4: one button -> slot 4)
        if (showMerge) {
            inventory.setItem(headerControlSlots(1)[0], createHead(HEAD_PROMOTE, GuiText.name(plugin, "main.merge-name"),
                    GuiText.lore(plugin, "main.merge-lore")));
        }

        // Footer: standalone menu (opened straight from the anchor) — no Back, only Close.
        setFooterButtons(null, null, createHead(HEAD_BARRIER, plugin.getConfigManager().getComponent("common.close"), null));
        fillFrameGlass();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        if (slot == 0) {
            if (event.isLeftClick()) {
                plugin.getConfigManager().playSound(viewer, "gui-click");
                viewer.closeInventory();
                plugin.getClaimTeleport().request(viewer, claim);
            } else if (event.isRightClick()) {
                long lastTime = borderCooldown.getOrDefault(viewer.getUniqueId(), 0L);
                if (System.currentTimeMillis() - lastTime > 15000L) {
                    plugin.getConfigManager().playSound(viewer, "gui-click");
                    viewer.closeInventory();
                    me.lovelace.loveclaims.task.BorderDisplayTask.showBorder(plugin, viewer, claim.getBoundingBox(), 200L, claim.getId());
                    borderCooldown.put(viewer.getUniqueId(), System.currentTimeMillis());
                } else {
                    plugin.getConfigManager().playSound(viewer, "gui-error");
                }
            }
            return;
        }
        if (showMerge && slot == headerControlSlots(1)[0]) {
            plugin.getConfigManager().playSound(viewer, "gui-click");
            viewer.openInventory(new MergeGUI(plugin, viewer, claim).getInventory());
            return;
        }
        switch (slot) {
            case SLOT_MEMBERS -> {
                plugin.getConfigManager().playSound(viewer, "gui-click");
                viewer.openInventory(new MembersGUI(plugin, viewer, claim).getInventory());
            }
            case SLOT_CORE -> {
                if (!claim.canManageCore(viewer.getUniqueId())) {
                    plugin.getConfigManager().playSound(viewer, "gui-error");
                    return;
                }
                plugin.getConfigManager().playSound(viewer, "gui-click");
                viewer.openInventory(new AnchorGemsGUI(plugin, viewer, claim).getInventory());
            }
            case SLOT_SETTINGS -> {
                plugin.getConfigManager().playSound(viewer, "gui-click");
                viewer.openInventory(new SettingsGUI(plugin, viewer, claim).getInventory());
            }
            case 26 -> {
                plugin.getConfigManager().playSound(viewer, "gui-click");
                viewer.closeInventory();
            }
            default -> {
            }
        }
    }

    // Добавлено для устранения утечек памяти
    public static void removeCooldown(UUID uuid) {
        borderCooldown.remove(uuid);
    }
}
