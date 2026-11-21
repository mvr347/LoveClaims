package me.lovelace.loveclaims.gui;
import me.lovelace.loveclaims.LoveClaims;
import static me.lovelace.loveclaims.textures.HeadTextures.*;
import me.lovelace.loveclaims.model.Claim;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MainClaimGUI extends AbstractGUI {
    private final LoveClaims plugin;
    private final Player viewer;
    private final Claim claim;

    // cooldown для показа границ (оптимизация от лагов)
    private static final Map<UUID, Long> borderCooldown = new ConcurrentHashMap<>();

    public MainClaimGUI(LoveClaims plugin, Player viewer, Claim claim) {
        super(27, plugin.getConfigManager().getComponent("main.title"));
        this.plugin = plugin;
        this.viewer = viewer;
        this.claim = claim;
        setMenuItems();
    }

    @Override
    protected void setMenuItems() {
        int currentSize = (int) Math.round(claim.getBoundingBox().getMaxX() - claim.getBoundingBox().getMinX());
        String ownerName = Bukkit.getOfflinePlayer(claim.getOwnerUuid()).getName();
        if (ownerName == null) ownerName = "???";

        // gui-gen-5 RULE 3: слот 0 — тематическая иконка/профиль меню (инфо привата, ЛКМ=телепорт, ПКМ=границы).
        inventory.setItem(0, createHead(HEAD_INFO,
                plugin.getConfigManager().getComponent("main.info-name"),
                plugin.getConfigManager().getHelpMessage("main.info-lore", "owner", ownerName, "size", String.valueOf(currentSize))));

        // Рабочая зона (9-17): 3 контентные кнопки, центрированы 11, 13, 15.
        inventory.setItem(11, createHead(HEAD_SETTINGS,
                plugin.getConfigManager().getComponent("main.settings-name"),
                plugin.getConfigManager().getHelpMessage("main.settings-lore")));

        var tier = plugin.getAnchorManager().resolveClaimTier(claim);
        int maxSlots = tier != null ? tier.gemSlots() : 3;
        int usedSlots = claim.getModifiers().usedSlots();
        inventory.setItem(13, createHead(HEAD_INFO,
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<aqua>Гемы якоря</aqua>")
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                List.of(
                        net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                                .deserialize("<gray>Занято слотов: <aqua>" + usedSlots + "<gray>/<white>" + maxSlots)
                                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                        net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                                .deserialize("<dark_gray><st>─────────────────────</st></dark_gray>")
                                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                        net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                                .deserialize("<green>ЛКМ <dark_gray>— <gray>управление гемами")
                                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
                )));

        inventory.setItem(15, createHead(HEAD_MEMBERS,
                plugin.getConfigManager().getComponent("main.members-name"),
                plugin.getConfigManager().getHelpMessage("main.members-lore")));

        // Footer: standalone-меню (открывается напрямую с якоря) — Back неактивен, только Close.
        setFooterButtons(null, null, createHead(HEAD_BARRIER, plugin.getConfigManager().getComponent("common.close"), null));
        fillFrameGlass();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        switch (event.getSlot()) {
            case 0 -> {
                if (event.isLeftClick()) {
                    plugin.getConfigManager().playSound(viewer, "gui-click");
                    viewer.closeInventory();
                    org.bukkit.Location loc = claim.getAnchorLocation();
                    if (loc == null) {
                        double x = (claim.getBoundingBox().getMinX() + claim.getBoundingBox().getMaxX()) / 2.0;
                        double z = (claim.getBoundingBox().getMinZ() + claim.getBoundingBox().getMaxZ()) / 2.0;
                        double y = viewer.getWorld().getHighestBlockYAt((int)x, (int)z) + 1;
                        loc = new org.bukkit.Location(viewer.getWorld(), x, y, z);
                    } else {
                        loc = loc.clone().add(0.5, 1, 0.5);
                    }
                    viewer.teleport(loc);
                    viewer.sendMessage(plugin.getConfigManager().getMessage("teleport-home"));
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
            }
            case 11 -> {
                plugin.getConfigManager().playSound(viewer, "gui-click");
                viewer.openInventory(new SettingsGUI(plugin, viewer, claim).getInventory());
            }
            case 13 -> {
                plugin.getConfigManager().playSound(viewer, "gui-click");
                viewer.openInventory(new AnchorGemsGUI(plugin, viewer, claim).getInventory());
            }
            case 15 -> {
                plugin.getConfigManager().playSound(viewer, "gui-click");
                viewer.openInventory(new MembersGUI(plugin, viewer, claim).getInventory());
            }
            case 26 -> {
                plugin.getConfigManager().playSound(viewer, "gui-click");
                viewer.closeInventory();
            }
        }
    }

    // Добавлено для устранения утечек памяти
    public static void removeCooldown(UUID uuid) {
        borderCooldown.remove(uuid);
    }
}
