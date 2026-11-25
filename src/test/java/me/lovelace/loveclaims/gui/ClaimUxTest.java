package me.lovelace.loveclaims.gui;

import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.model.ClaimPermission;
import me.lovelace.loveclaims.model.TrustLevel;
import me.lovelace.loveclaims.util.MergeSeal;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClaimUxTest {
    ServerMock server;
    LoveClaims plugin;
    PlayerMock owner, member;
    World world;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        MockBukkit.createMockPlugin("LoveCore");
        plugin = MockBukkit.load(LoveClaims.class);
        owner = server.addPlayer("Owner");
        member = server.addPlayer("Member");
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    private Claim claim(PlayerMock who, double minX, double maxX) {
        Claim c = new Claim(UUID.randomUUID(), world, new BoundingBox(minX, 0, 0, maxX, 100, 10), who.getUniqueId(),
                new Location(world, (minX + maxX) / 2, 64, 5));
        c.setAnchorTierId("tier-1");
        plugin.getClaimManager().addClaimToCache(c);
        return c;
    }

    private void click(AbstractGUI gui, PlayerMock who, int slot) {
        var view = who.openInventory(gui.getInventory());
        Bukkit_call(new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL));
    }

    private static void Bukkit_call(InventoryClickEvent e) {
        org.bukkit.Bukkit.getPluginManager().callEvent(e);
    }

    @Test
    void standingInsideTheClaimRefusesTheTeleport() {
        Claim c = claim(owner, 0, 10);
        owner.teleport(new Location(world, 5, 64, 5));
        assertTrue(plugin.getClaimTeleport().isInside(owner, c));
        plugin.getClaimTeleport().request(owner, c);
        // refused: no warm-up started and no cooldown set, so a second request still says "already here"
        plugin.getClaimTeleport().request(owner, c);
        assertTrue(owner.nextMessage() != null);
    }

    @Test
    void afterATeleportTheCooldownBlocksTheNextOneAndDoesNotStartAWarmUp() {
        plugin.getConfig().set("teleport.cooldown-seconds", 5);
        Claim far = claim(owner, 200, 210);
        owner.teleport(new Location(world, 0, 64, 0));
        plugin.getClaimTeleport().startCooldown(owner.getUniqueId());
        assertTrue(plugin.getClaimTeleport().cooldownLeft(owner.getUniqueId()) > 0);
        plugin.getClaimTeleport().request(owner, far);
        server.getScheduler().performTicks(400); // would have teleported by now
        assertEquals(0.0, owner.getLocation().getX(), 1e-6);
    }

    @Test
    void memberHeadLeadsToLeaveConfirmAndLeavingRemovesTheMember() {
        Claim c = claim(owner, 0, 10);
        c.setTrust(member.getUniqueId(), TrustLevel.ACCESS);
        MembersGUI gui = new MembersGUI(plugin, member, c);
        // the member's own head (first content slot) shows the leave action and no rights editing
        var lore = gui.getInventory().getItem(19).getItemMeta().lore().stream()
                .map(l -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(l)).toList();
        assertTrue(lore.stream().anyMatch(l -> l.contains("покинуть клейм")));
        assertTrue(lore.stream().noneMatch(l -> l.contains("настроить права")));
        click(gui, member, 19);
        assertInstanceOf(LeaveConfirmGUI.class, member.getOpenInventory().getTopInventory().getHolder());
        var confirm = (LeaveConfirmGUI) member.getOpenInventory().getTopInventory().getHolder();
        click(confirm, member, 1);
        assertFalse(c.getMembers().containsKey(member.getUniqueId()));
    }

    @Test
    void corePermissionOpensGemsForAMemberWithoutManagement() {
        Claim c = claim(owner, 0, 10);
        c.setTrust(member.getUniqueId(), TrustLevel.ACCESS);
        assertFalse(c.canManageCore(member.getUniqueId()));
        c.setPermission(member.getUniqueId(), ClaimPermission.CORE, true);
        assertTrue(c.canManageCore(member.getUniqueId()));
        assertFalse(c.isManager(member.getUniqueId()));
    }

    @Test
    void mergeButtonAppearsOnlyWithANeighbourAndEveryMergeCostsASeal() {
        Claim a = claim(owner, 0, 10);
        assertEquals(org.bukkit.Material.GRAY_STAINED_GLASS_PANE, new MainClaimGUI(plugin, owner, a).getInventory().getItem(4).getType(), "no neighbour, no button");
        Claim b = claim(owner, 12, 22);
        assertNotNull(new MainClaimGUI(plugin, owner, a).getInventory().getItem(4));
        assertNotEquals(org.bukkit.Material.GRAY_STAINED_GLASS_PANE, new MainClaimGUI(plugin, owner, a).getInventory().getItem(4).getType());

        // without a seal nothing merges, even for one owner
        click(new MergeConfirmGUI(plugin, owner, a, b), owner, 1);
        assertTrue(plugin.getClaimManager().getClaimById(b.getId()).isPresent());
        owner.getInventory().addItem(MergeSeal.create(plugin));
        click(new MergeConfirmGUI(plugin, owner, a, b), owner, 1);
        assertTrue(plugin.getClaimManager().getClaimById(b.getId()).isEmpty());
        assertFalse(MergeSeal.has(plugin, owner), "the seal is spent");
    }

    @Test
    void mainMenuOrderIsMembersCoreSettings() {
        Claim a = claim(owner, 0, 10);
        var inv = new MainClaimGUI(plugin, owner, a).getInventory();
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        assertTrue(plain.serialize(inv.getItem(11).getItemMeta().displayName()).contains("Участники"));
        assertTrue(plain.serialize(inv.getItem(13).getItemMeta().displayName()).contains("Ядро"));
        assertTrue(plain.serialize(inv.getItem(15).getItemMeta().displayName()).contains("Настройки"));
    }

    @Test
    void pickerLoreHasNoWorldAndLoreSeparatorsAreAbovedByABlankLine() {
        Claim a = claim(owner, 0, 10);
        Claim b = claim(owner, 50, 60);
        var inv = new ClaimPickerGUI(plugin, owner, java.util.List.of(a, b)).getInventory();
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        for (int slot : new int[]{12, 14}) {
            var lore = inv.getItem(slot).getItemMeta().lore().stream().map(plain::serialize).toList();
            assertTrue(lore.stream().noneMatch(l -> l.contains("Мир")), "world is not shown");
            for (int i = 0; i < lore.size(); i++) {
                if (lore.get(i).startsWith("─")) assertEquals("", lore.get(i - 1), "blank line above the separator");
            }
        }
    }

    @Test
    void craftsmanTurnsAnImprovedGemIntoAModifiedOne() {
        plugin.getConfig().set("gems.modify.cost", 0);
        var improved = plugin.getGemRegistry().createGemItem("improved_domain", 1);
        assertNotNull(improved);
        owner.getInventory().setItem(0, improved);
        var stats = plugin.getGemFactory().readStats(improved).orElseThrow();
        assertEquals(me.lovelace.loveclaims.gems.GemRarity.IMPROVED, stats.rarity());
        assertEquals("modified_domain", plugin.getGemRegistry().modifiedFor("improved_domain").orElseThrow());
        assertTrue(plugin.getGemRegistry().modifiedFor("domain").isEmpty());
        click(new GemModifyConfirmGUI(plugin, owner, stats, 0, improved), owner, 1);
        var gems = java.util.Arrays.stream(owner.getInventory().getContents())
                .filter(i -> i != null && plugin.getGemFactory().isGem(i))
                .map(i -> plugin.getGemFactory().readStats(i).orElseThrow().gemId()).toList();
        assertEquals(java.util.List.of("modified_domain"), gems);
    }

    @Test
    void gemItemsShowTheirTextureEvenWhenItemsAdderDoesNotProvideTheItem() {
        var gem = plugin.getGemRegistry().createGemItem("domain", 1);
        assertEquals("voidcore:gem_domain_base", gem.getItemMeta().getItemModel().asString());
        var improved = plugin.getGemRegistry().createGemItem("improved_vita", 1);
        assertEquals("voidcore:gem_vita_upgrade", improved.getItemMeta().getItemModel().asString());
        assertTrue(plugin.getGemFactory().isGem(gem), "the model does not break gem recognition");
    }

    @Test
    void gemsCarryTheirItemsAdderIds() {
        var gems = plugin.getConfigManager().getGems();
        assertEquals("voidcore:gem_domain_base", gems.getString("gems.domain.itemsadder"));
        assertEquals("voidcore:gem_vita_upgrade", gems.getString("gems.improved_vita.itemsadder"));
        assertEquals("voidcore:gem_ceres_modify", gems.getString("gems.modified_ceres.itemsadder"));
        assertEquals("voidcore:gem_demos_legendary", gems.getString("gems.imperium_demos.itemsadder"));
        assertEquals("voidcore:gem_domain_ceres_hybrid", gems.getString("hybrids.hybrid_domain_ceres.itemsadder"));
    }
}
