package me.lovelace.loveclaims.gems;

import dev.lovelace.lovecore.api.social.BehaviorLevels;
import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.gui.AnchorGemsGUI;
import me.lovelace.loveclaims.gui.AnchorUpgradeGUI;
import me.lovelace.loveclaims.gui.DeleteConfirmGUI;
import me.lovelace.loveclaims.gui.MainClaimGUI;
import me.lovelace.loveclaims.gui.SettingsGUI;
import me.lovelace.loveclaims.integration.NpcTags;
import me.lovelace.loveclaims.manager.AnchorManager;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.model.ClaimTier;
import me.lovelace.loveclaims.model.TrustLevel;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BrewingStartEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class GemAndAnchorSystemTest {
    ServerMock server;
    LoveClaims plugin;
    World world;
    PlayerMock owner;
    PlayerMock member;
    PlayerMock stranger;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        MockBukkit.createMockPlugin("LoveCore");
        plugin = MockBukkit.load(LoveClaims.class);
        assertTrue(plugin.isEnabled());
        owner = server.addPlayer("AnchorOwner");
        owner.setOp(true);
        member = server.addPlayer("ClaimMember");
        stranger = server.addPlayer("Stranger");
    }

    @AfterEach
    void teardown() {
        MockBukkit.unmock();
    }

    @Test
    void anchorTiersMatchBalancedSizesAndSlotCounts() {
        var am = plugin.getAnchorManager();
        assertEquals(6, am.getAllTiers().size());

        ClaimTier t1 = am.getTier("tier-1");
        ClaimTier t1m = am.getTier("tier-1-mod");
        ClaimTier t2 = am.getTier("tier-2");
        ClaimTier t2m = am.getTier("tier-2-mod");
        ClaimTier t3 = am.getTier("tier-3");
        ClaimTier t3m = am.getTier("tier-3-mod");

        assertNotNull(t1);
        assertNotNull(t1m);
        assertNotNull(t2);
        assertNotNull(t2m);
        assertNotNull(t3);
        assertNotNull(t3m);

        assertEquals(3, t1.gemSlots());
        assertEquals(12, t1.radiusX());
        assertEquals(25, t1.getSizeX());

        assertEquals(4, t1m.gemSlots());
        assertEquals(15, t1m.radiusX());
        assertEquals(31, t1m.getSizeX());

        assertEquals(6, t2.gemSlots());
        assertEquals(24, t2.radiusX());
        assertEquals(49, t2.getSizeX());

        assertEquals(7, t2m.gemSlots());
        assertEquals(30, t2m.radiusX());
        assertEquals(61, t2m.getSizeX());

        assertEquals(8, t3.gemSlots());
        assertEquals(42, t3.radiusX());
        assertEquals(85, t3.getSizeX());

        assertEquals(9, t3m.gemSlots());
        assertEquals(52, t3m.radiusX());
        assertEquals(105, t3m.getSizeX());
    }

    @Test
    void legendaryGemsHaveZeroCraftRecipesWhileBasicAndModifiedAreLoaded() {
        GemRegistry reg = plugin.getGemRegistry();
        assertEquals(20, reg.getAllDescriptors().size());

        // Ensure NO recipe produces a LEGENDARY gem
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe r = it.next();
            var stats = plugin.getGemFactory().readStats(r.getResult());
            if (stats.isPresent()) {
                assertNotEquals(GemRarity.LEGENDARY, stats.get().rarity(),
                        "LEGENDARY gem must never have a craft recipe: " + stats.get().gemId());
            }
        }
    }

    @Test
    void hybridPdcSerializationPreservesExactRollsAndHandlesAllOutcomes() {
        GemRegistry reg = plugin.getGemRegistry();
        GemFactory factory = plugin.getGemFactory();
        HybridGemDescriptor hybrid = (HybridGemDescriptor) reg.getDescriptor("hybrid_domain_demos").orElseThrow();

        ItemStack pos = reg.createHybridItem(hybrid, HybridRollOutcome.POSITIVE);
        GemStats posStats = factory.readStats(pos).orElseThrow();
        assertEquals("hybrid_domain_demos", posStats.gemId());
        assertEquals(GemRarity.MODIFIED, posStats.rarity());
        assertEquals(2, posStats.slotCost());
        assertTrue(posStats.radiusBonus() > 0 && posStats.membersBonus() > 0,
                "POSITIVE hybrid should combine primary modified + weak secondary positive");
        assertEquals(HybridRollOutcome.POSITIVE, factory.readHybridOutcome(pos).orElseThrow());

        ItemStack neu = reg.createHybridItem(hybrid, HybridRollOutcome.NEUTRAL);
        GemStats neuStats = factory.readStats(neu).orElseThrow();
        assertTrue(neuStats.radiusBonus() > 0 || neuStats.membersBonus() > 0);
        assertFalse(neuStats.radiusBonus() < 0 || neuStats.membersBonus() < 0);

        ItemStack deb = reg.createHybridItem(hybrid, HybridRollOutcome.DEBUFF);
        GemStats debStats = factory.readStats(deb).orElseThrow();
        assertEquals(HybridRollOutcome.DEBUFF, factory.readHybridOutcome(deb).orElseThrow());
        assertTrue(debStats.radiusBonus() < 14 || debStats.membersBonus() < 5,
                "DEBUFF hybrid_domain_demos should carry a debuff lowering radius or members");

        HybridGemDescriptor ceresVita = (HybridGemDescriptor) reg.getDescriptor("hybrid_ceres_vita").orElseThrow();
        ItemStack cvDeb = reg.createHybridItem(ceresVita, HybridRollOutcome.DEBUFF);
        GemStats cvStats = factory.readStats(cvDeb).orElseThrow();
        assertTrue(cvStats.cropGrowthMultiplier() < 0.50 || !cvStats.potionEffects().isEmpty());
    }

    @Test
    void gemCraftListenerGeneratesHybridAndBlocksLegendaryAndVanillaMixing() {
        GemRegistry reg = plugin.getGemRegistry();
        GemFactory factory = plugin.getGemFactory();

        ItemStack domainGem = reg.createGemItem("domain", 1);
        ItemStack demosGem = reg.createGemItem("demos", 1);
        ItemStack legendaryGem = reg.createGemItem("imperium_domain", 1);

        CraftingInventory craftInv = (CraftingInventory) Bukkit.createInventory(null, InventoryType.WORKBENCH);
        craftInv.setMatrix(new ItemStack[]{
                domainGem, demosGem, null,
                null, null, null,
                null, null, null
        });

        InventoryView view = mockView(owner, craftInv);
        // Simulate PrepareItemCraftEvent directly on a workbench inventory
        PrepareItemCraftEvent prepHybrid = new PrepareItemCraftEvent(craftInv, view, false);
        Bukkit.getPluginManager().callEvent(prepHybrid);
        assertNotNull(craftInv.getResult());
        GemStats previewStats = factory.readStats(craftInv.getResult()).orElseThrow();
        assertEquals("hybrid_domain_demos", previewStats.gemId());
        assertTrue(factory.isPendingCraftRoll(craftInv.getResult()));

        // Legendary gem must never craft anything
        craftInv.setResult(null);
        craftInv.setMatrix(new ItemStack[]{
                legendaryGem, demosGem, null,
                null, null, null,
                null, null, null
        });
        PrepareItemCraftEvent prepLeg = new PrepareItemCraftEvent(craftInv, view, false);
        Bukkit.getPluginManager().callEvent(prepLeg);
        assertNull(craftInv.getResult());
    }

    @Test
    void anchorGemsGuiValidatesTierSlotsRadiusOverlapAndPreservesExtractedGem() {
        Location center = new Location(world, 100, 64, 100);
        Claim claim = new Claim(UUID.randomUUID(), world,
                BoundingBox.of(center.clone().add(-12, -12, -12), center.clone().add(12, 12, 12)),
                owner.getUniqueId(), center);
        claim.setAnchorTierId("tier-1");
        plugin.getClaimManager().addClaimToCache(claim);

        // Give owner a MODIFIED gem (requires Tier II) and a BASIC gem (Tier I)
        ItemStack modGem = plugin.getGemRegistry().createGemItem("modified_domain", 1);
        ItemStack basicGem = plugin.getGemRegistry().createGemItem("domain", 1);
        owner.getInventory().setItem(0, modGem);
        owner.getInventory().setItem(1, basicGem);

        AnchorGemsGUI gui = new AnchorGemsGUI(plugin, owner, claim);
        InventoryView view = mockView(owner, gui.getInventory());

        // Attempt to insert MODIFIED gem into Tier I anchor -> rejected
        Bukkit.getPluginManager().callEvent(new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, gui.getInventory().getSize(),
                ClickType.LEFT, InventoryAction.PICKUP_ALL));
        assertTrue(claim.getInstalledGems().isEmpty());
        assertNotNull(owner.getInventory().getItem(0));

        // Insert BASIC domain gem (+6 radius) -> accepted!
        int bottomRawSlot = gui.getInventory().getSize() + 1;
        Bukkit.getPluginManager().callEvent(new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, bottomRawSlot,
                ClickType.LEFT, InventoryAction.PICKUP_ALL));

        assertEquals(1, claim.getInstalledGems().size());
        assertEquals(6, claim.getModifiers().radiusBonus());
        assertEquals(36, Math.round(claim.getBoundingBox().getWidthX())); // 24 + 12
        assertTrue(plugin.getClaimManager().getClaimAt(center.clone().add(17, 0, 0)).isPresent());

        // Extraction needs extract mode: toggle button sits in header slot 4 (gui_gen RULE 4, one button)
        Bukkit.getPluginManager().callEvent(new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, 4,
                ClickType.LEFT, InventoryAction.PICKUP_ALL));
        // Extract gem from top slot 10 (first work slot) -> shrinks back to 24 width
        Bukkit.getPluginManager().callEvent(new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, 10,
                ClickType.LEFT, InventoryAction.PICKUP_ALL));
        assertTrue(claim.getInstalledGems().isEmpty());
        assertEquals(24, Math.round(claim.getBoundingBox().getWidthX()));
        assertTrue(plugin.getClaimManager().getClaimAt(center.clone().add(17, 0, 0)).isEmpty());
    }

    @Test
    void npcAnchorUpgradeAndImperiumDomainExpansionWorkWithOverlapChecks() {
        Location center = new Location(world, 300, 64, 300);
        Claim claim = new Claim(UUID.randomUUID(), world,
                BoundingBox.of(center.clone().add(-42, -42, -42), center.clone().add(42, 42, 42)),
                owner.getUniqueId(), center);
        claim.setAnchorTierId("tier-3");
        plugin.getClaimManager().addClaimToCache(claim);

        // NPC upgrade: tier-3 (8 slots, r=42) -> tier-3-mod (9 slots, r=52)
        assertEquals(AnchorManager.UpgradeOutcome.SUCCESS, plugin.getAnchorManager().upgradeClaimAnchor(claim));
        assertEquals("tier-3-mod", claim.getAnchorTierId());
        assertEquals(104, Math.round(claim.getBoundingBox().getWidthX()));

        // Insert imperium_domain (+26 radius, allowLimitBypass = true)
        GemStats impDomain = ((PureGemDescriptor) plugin.getGemRegistry().getDescriptor("imperium_domain").orElseThrow()).baseStats();
        claim.addInstalledGem(impDomain);
        assertTrue(plugin.getClaimManager().recalculateClaimModifiers(claim));
        assertTrue(claim.getModifiers().allowLimitBypass());
        assertEquals(156, Math.round(claim.getBoundingBox().getWidthX())); // 104 + 52

        // Trigger extra expansion via AnchorGemsGUI footer slot 33
        AnchorGemsGUI gui = new AnchorGemsGUI(plugin, owner, claim);
        InventoryView view = mockView(owner, gui.getInventory());
        Bukkit.getPluginManager().callEvent(new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, 33,
                ClickType.LEFT, InventoryAction.PICKUP_ALL));
        assertTrue(claim.isExtraExpansionUnlocked());
        assertEquals(176, Math.round(claim.getBoundingBox().getWidthX())); // 156 + 20
    }

    @Test
    void playstyleDeterminesClaimLimitGoodVsAggressive() {
        UUID goodPlayer = UUID.randomUUID();
        UUID aggressivePlayer = UUID.randomUUID();

        BehaviorLevels levels = (BehaviorLevels) Proxy.newProxyInstance(
                BehaviorLevels.class.getClassLoader(),
                new Class<?>[]{BehaviorLevels.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "playstyleLevel" -> args[0].equals(goodPlayer) ? 8 : 2;
                    case "politenessLevel" -> 0;
                    case "neutralLevel" -> 0;
                    default -> 0;
                });
        Bukkit.getServicesManager().register(BehaviorLevels.class, levels, plugin, org.bukkit.plugin.ServicePriority.Normal);

        assertEquals(3, plugin.getClaimManager().getMaxClaimsForPlayer(goodPlayer));
        assertEquals(1, plugin.getClaimManager().getMaxClaimsForPlayer(aggressivePlayer));
    }

    @Test
    void sqlitePersistsClaimGemsAndAnchorMetadataAcrossReload() {
        Location center = new Location(world, -200, 64, -200);
        Claim claim = new Claim(UUID.randomUUID(), world,
                BoundingBox.of(center.clone().add(-24, -24, -24), center.clone().add(24, 24, 24)),
                owner.getUniqueId(), center);
        claim.setAnchorTierId("tier-2");
        claim.setBehaviorBonusRadius(2);
        claim.setExtraExpansionUnlocked(true);

        HybridGemDescriptor hybrid = (HybridGemDescriptor) plugin.getGemRegistry().getDescriptor("hybrid_ceres_vita").orElseThrow();
        ItemStack rolled = plugin.getGemRegistry().createHybridItem(hybrid, HybridRollOutcome.DEBUFF);
        GemStats expectedStats = plugin.getGemFactory().readStats(rolled).orElseThrow();
        claim.addInstalledGem(expectedStats);
        plugin.getClaimManager().recalculateClaimModifiers(claim);

        plugin.getStorage().saveAllClaimsSync(List.of(claim));
        Claim reloaded = plugin.getStorage().loadClaimAsync(claim.getId()).join();
        assertNotNull(reloaded);
        assertEquals("tier-2", reloaded.getAnchorTierId());
        assertEquals(2, reloaded.getBehaviorBonusRadius());
        assertTrue(reloaded.isExtraExpansionUnlocked());
        assertEquals(1, reloaded.getInstalledGems().size());
        assertEquals(expectedStats.gemId(), reloaded.getInstalledGems().get(0).gemId());
        assertEquals(expectedStats.cropGrowthMultiplier(), reloaded.getInstalledGems().get(0).cropGrowthMultiplier(), 1e-6);
        assertEquals(expectedStats.potionEffects(), reloaded.getInstalledGems().get(0).potionEffects());
    }

    @Test
    void gemEffectTaskAppliesAndRemovesPotionEffectsInsideClaim() {
        Location center = new Location(world, 500, 64, 500);
        Claim claim = new Claim(UUID.randomUUID(), world,
                BoundingBox.of(center.clone().add(-12, -12, -12), center.clone().add(12, 12, 12)),
                owner.getUniqueId(), center);
        claim.setTrust(member.getUniqueId(), TrustLevel.BUILD);

        GemStats vita = ((PureGemDescriptor) plugin.getGemRegistry().getDescriptor("vita").orElseThrow()).baseStats();
        claim.addInstalledGem(vita);
        plugin.getClaimManager().addClaimToCache(claim);

        member.teleport(center);
        stranger.teleport(center);
        plugin.getGemEffectTask().tick();

        PotionEffect memberRegen = member.getPotionEffect(PotionEffectType.REGENERATION);
        assertNotNull(memberRegen, "Claim member inside claim should receive REGENERATION");
        assertNull(stranger.getPotionEffect(PotionEffectType.REGENERATION), "Stranger should NOT receive positive gem buff");

        // Move member outside claim -> effect should be removed cleanly
        member.teleport(new Location(world, 900, 64, 900));
        plugin.getGemEffectTask().tick();
        assertNull(member.getPotionEffect(PotionEffectType.REGENERATION), "Gem effect should be removed upon leaving claim");
    }

    @Test
    void gemAdminCommandsWorkWithPermissionsAndTabCompletion() {
        MainClaimGUI mainGui = new MainClaimGUI(plugin, owner,
                new Claim(UUID.randomUUID(), world, new BoundingBox(0, 0, 0, 20, 20, 20), owner.getUniqueId(), owner.getLocation()));
        assertEquals(Material.PLAYER_HEAD, mainGui.getInventory().getItem(13).getType());

        assertTrue(server.dispatchCommand(owner, "loveclaims gem give " + member.getName() + " domain 2"));
        assertTrue(Arrays.stream(member.getInventory().getContents())
                .filter(Objects::nonNull)
                .anyMatch(item -> item.getAmount() == 2
                        && plugin.getGemFactory().readStats(item).map(s -> s.gemId().equals("domain")).orElse(false)));

        assertTrue(server.dispatchCommand(owner, "loveclaims gem givehybrid " + member.getName() + " hybrid_demos_vita POSITIVE"));
        assertTrue(Arrays.stream(member.getInventory().getContents())
                .filter(Objects::nonNull)
                .anyMatch(item -> plugin.getGemFactory().readHybridOutcome(item).orElse(null) == HybridRollOutcome.POSITIVE));

        assertTrue(server.dispatchCommand(owner, "loveclaims gem list"));
        assertNotNull(owner.nextComponentMessage());
        while (owner.nextComponentMessage() != null) { /* drain list entries */ }

        // Inspect held gem
        owner.getInventory().setItemInMainHand(plugin.getGemRegistry().createGemItem("imperium_vita", 1));
        assertTrue(server.dispatchCommand(owner, "loveclaims gem inspect"));
        String inspectOut = PlainTextComponentSerializer.plainText().serialize(owner.nextComponentMessage());
        assertTrue(inspectOut.contains("Инспекция гема") || inspectOut.contains("imperium_vita"));

        // Non-op stranger cannot use gem admin command
        while (stranger.nextComponentMessage() != null) { /* drain join starter anchor message */ }
        assertTrue(server.dispatchCommand(stranger, "loveclaims gem list"));
        var deniedComp = stranger.nextComponentMessage();
        assertNotNull(deniedComp);
        String denied = PlainTextComponentSerializer.plainText().serialize(deniedComp);
        assertTrue(denied.contains("прав"), "Expected denial message, got: " + denied);

        // Tab completion checks
        var mainCmd = plugin.getCommand("ac").getTabCompleter();
        assertNotNull(mainCmd);
        assertTrue(mainCmd.onTabComplete(owner, plugin.getCommand("ac"), "loveclaims", new String[]{"gem", ""})
                .containsAll(List.of("give", "givehybrid", "list", "inspect")));
        assertTrue(mainCmd.onTabComplete(stranger, plugin.getCommand("ac"), "loveclaims", new String[]{"gem", ""})
                .isEmpty());
    }

    @Test
    void cropGrowthAndNaturalDoubleDropSkipPlayerPlacedMelons() {
        Location center = new Location(world, 700, 64, 700);
        Claim claim = new Claim(UUID.randomUUID(), world,
                BoundingBox.of(center.clone().add(-20, -20, -20), center.clone().add(20, 20, 20)),
                owner.getUniqueId(), center);
        GemStats impCeres = ((PureGemDescriptor) plugin.getGemRegistry().getDescriptor("imperium_ceres").orElseThrow()).baseStats();
        claim.setAnchorTierId("tier-3"); // legendary gem fits only a tier 3 anchor without the overload cut
        claim.addInstalledGem(impCeres); // +100% growth
        plugin.getClaimManager().addClaimToCache(claim);

        var wheatBlock = world.getBlockAt(701, 64, 701);
        wheatBlock.setType(Material.WHEAT);
        var state = wheatBlock.getState();
        Ageable ageable = (Ageable) Material.WHEAT.createBlockData();
        ageable.setAge(1);
        state.setBlockData(ageable);

        Bukkit.getPluginManager().callEvent(new BlockGrowEvent(wheatBlock, state));
        assertEquals(2, ((Ageable) state.getBlockData()).getAge(), "+100% crop multiplier should add +1 growth stage");

        // Player-placed melon must be tracked and removed cleanly on break
        var melonBlock = world.getBlockAt(702, 64, 702);
        melonBlock.setType(Material.MELON);
        Bukkit.getPluginManager().callEvent(new BlockPlaceEvent(
                melonBlock, melonBlock.getState(), world.getBlockAt(702, 63, 702),
                new ItemStack(Material.MELON), owner, true, EquipmentSlot.HAND));
        Bukkit.getPluginManager().callEvent(new BlockBreakEvent(melonBlock, owner));
        assertTrue(world.getEntitiesByClass(org.bukkit.entity.Item.class).isEmpty(),
                "Player-placed melon must never drop bonus double drops");
    }

    @Test
    void anchorUpgraderNpcTagRecognised() {
        var npc = (dev.lovelace.lovecore.api.npc.NpcHandle) Proxy.newProxyInstance(
                dev.lovelace.lovecore.api.npc.NpcHandle.class.getClassLoader(),
                new Class<?>[]{dev.lovelace.lovecore.api.npc.NpcHandle.class},
                (p, m, args) -> m.getName().equals("tag") && args.length == 1
                        ? Optional.of(NpcTags.ROLE_ANCHOR_UPGRADER)
                        : null);
        assertTrue(NpcTags.isAnchorUpgrader(npc));
        assertNotNull(new AnchorUpgradeGUI(plugin, owner).getInventory());
    }

    @Test
    void craftingValidationBlocksVanillaExploitsCrafterAndShiftClickRolls() {
        GemRegistry reg = plugin.getGemRegistry();
        GemFactory factory = plugin.getGemFactory();

        // 1. Raw vanilla AMETHYST_SHARD must NOT craft modified_domain
        Recipe modDomainRecipe = Bukkit.getRecipe(new NamespacedKey(plugin, "gem_craft_modified_domain"));
        assertNotNull(modDomainRecipe);
        CraftingInventory modInv = (CraftingInventory) Bukkit.createInventory(null, InventoryType.WORKBENCH);
        modInv.setMatrix(new ItemStack[]{
                new ItemStack(Material.AMETHYST_BLOCK), new ItemStack(Material.ENDER_EYE), new ItemStack(Material.AMETHYST_BLOCK),
                new ItemStack(Material.AMETHYST_SHARD), new ItemStack(Material.DIAMOND_BLOCK), new ItemStack(Material.AMETHYST_SHARD),
                new ItemStack(Material.AMETHYST_BLOCK), new ItemStack(Material.ENDER_EYE), new ItemStack(Material.AMETHYST_BLOCK)
        });
        modInv.setResult(modDomainRecipe.getResult().clone());
        InventoryView modView = mockView(owner, modInv);
        Bukkit.getPluginManager().callEvent(new PrepareItemCraftEvent(modInv, modView, false));
        assertNull(modInv.getResult(), "Raw vanilla AMETHYST_SHARD must not craft modified_domain");

        // Replacing both shards with real domain gems must allow it
        ItemStack[] validModMatrix = modInv.getMatrix().clone();
        validModMatrix[3] = reg.createGemItem("domain", 1);
        validModMatrix[5] = reg.createGemItem("domain", 1);
        modInv.setMatrix(validModMatrix);
        modInv.setResult(modDomainRecipe.getResult().clone());
        Bukkit.getPluginManager().callEvent(new PrepareItemCraftEvent(modInv, modView, false));
        assertNotNull(modInv.getResult(), "Two real domain gems should craft modified_domain");

        // 2. Vanilla CAMPFIRE must NOT craft tier-2 anchor
        Recipe tier2Recipe = Bukkit.getRecipe(new NamespacedKey(plugin, "anchor_upgrade_tier_2_0"));
        assertNotNull(tier2Recipe);
        CraftingInventory anchorInv = (CraftingInventory) Bukkit.createInventory(null, InventoryType.WORKBENCH);
        anchorInv.setMatrix(new ItemStack[]{
                new ItemStack(Material.GOLD_INGOT), new ItemStack(Material.IRON_BLOCK), new ItemStack(Material.GOLD_INGOT),
                new ItemStack(Material.IRON_BLOCK), new ItemStack(Material.CAMPFIRE), new ItemStack(Material.IRON_BLOCK),
                new ItemStack(Material.GOLD_INGOT), new ItemStack(Material.IRON_BLOCK), new ItemStack(Material.GOLD_INGOT)
        });
        anchorInv.setResult(tier2Recipe.getResult().clone());
        Bukkit.getPluginManager().callEvent(new PrepareItemCraftEvent(anchorInv, mockView(owner, anchorInv), false));
        assertNull(anchorInv.getResult(), "Vanilla CAMPFIRE must not craft tier-2 anchor");

        // 3. Unmatched 2-gem pair (domain + vita) must not bypass custom item guard
        CraftingInventory pairInv = (CraftingInventory) Bukkit.createInventory(null, InventoryType.WORKBENCH);
        pairInv.setMatrix(new ItemStack[]{
                reg.createGemItem("domain", 1), reg.createGemItem("vita", 1), null,
                null, null, null, null, null, null
        });
        pairInv.setResult(new ItemStack(Material.STONE));
        Bukkit.getPluginManager().callEvent(new PrepareItemCraftEvent(pairInv, mockView(owner, pairInv), false));
        assertNull(pairInv.getResult(), "Unmatched 2-gem combo must clear any external recipe result");

        // 4. CrafterCraftEvent must cancel plugin recipes
        if (modDomainRecipe instanceof CraftingRecipe cr) {
            var crafterBlock = world.getBlockAt(10, 64, 10);
            crafterBlock.setType(Material.CRAFTER);
            CrafterCraftEvent crafterEvent = new CrafterCraftEvent(crafterBlock, cr, cr.getResult().clone());
            Bukkit.getPluginManager().callEvent(crafterEvent);
            assertTrue(crafterEvent.isCancelled(), "1.21 Crafter must not bypass custom gem ingredient checks");
        }

        // 5. CraftItemEvent shift-click rolls individual hybrids and consumes ingredients
        owner.getInventory().clear();
        CraftingInventory hybridInv = (CraftingInventory) Bukkit.createInventory(null, InventoryType.WORKBENCH);
        hybridInv.setMatrix(new ItemStack[]{
                reg.createGemItem("domain", 2), reg.createGemItem("demos", 2), null,
                null, null, null, null, null, null
        });
        InventoryView hybridView = mockView(owner, hybridInv);
        PrepareItemCraftEvent prep = new PrepareItemCraftEvent(hybridInv, hybridView, false);
        Bukkit.getPluginManager().callEvent(prep);
        ItemStack preview = hybridInv.getResult();
        assertNotNull(preview);
        assertTrue(factory.isPendingCraftRoll(preview));

        // Block NUMBER_KEY on hybrid preview
        CraftItemEvent numKey = new CraftItemEvent(
                (Recipe) Bukkit.getRecipe(new NamespacedKey(plugin, "gem_hybrid_hybrid_domain_demos")),
                hybridView, InventoryType.SlotType.RESULT, 0, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 0);
        Bukkit.getPluginManager().callEvent(numKey);
        assertTrue(numKey.isCancelled(), "NUMBER_KEY on pending hybrid must be cancelled");

        // Shift-click crafts 2 rolled hybrids and clears matrix
        CraftItemEvent shiftCraft = new CraftItemEvent(
                (Recipe) Bukkit.getRecipe(new NamespacedKey(plugin, "gem_hybrid_hybrid_domain_demos")),
                hybridView, InventoryType.SlotType.RESULT, 0, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        Bukkit.getPluginManager().callEvent(shiftCraft);
        long craftedHybrids = Arrays.stream(owner.getInventory().getContents())
                .filter(Objects::nonNull)
                .filter(item -> factory.readStats(item).map(s -> s.gemId().equals("hybrid_domain_demos")).orElse(false))
                .peek(item -> assertFalse(factory.isPendingCraftRoll(item), "Crafted hybrid must be finalized"))
                .count();
        assertEquals(2, craftedHybrids);
        assertNull(hybridInv.getMatrix()[0]);
        assertNull(hybridInv.getMatrix()[1]);
    }

    @Test
    void guiGen6LayoutAndOverlapGuardsWorkForDebuffRemovalUpgradeAndMove() {
        Location c1 = new Location(world, 1000, 64, 1000);
        Claim claim1 = new Claim(UUID.randomUUID(), world,
                BoundingBox.of(c1.clone().add(-24, -24, -24), c1.clone().add(24, 24, 24)),
                owner.getUniqueId(), c1);
        claim1.setAnchorTierId("tier-2");
        claim1.setName("BaseAlpha");
        plugin.getClaimManager().addClaimToCache(claim1);

        // Insert negative-radius debuff hybrid (-4 radius -> width becomes 40)
        GemStats negHybrid = new GemStats("hybrid_domain_demos", GemRarity.MODIFIED, 2, -4, 5, 0, 0, 0, false,
                Map.of(PotionEffectType.SPEED, 0), HybridRollOutcome.DEBUFF);
        claim1.addInstalledGem(negHybrid);
        assertTrue(plugin.getClaimManager().recalculateClaimModifiers(claim1));
        assertEquals(40, Math.round(claim1.getBoundingBox().getWidthX()));

        // Place neighbor right next to the shrunk boundary (at x = 1020..1044)
        Location c2 = new Location(world, 1033, 64, 1000);
        Claim neighbor = new Claim(UUID.randomUUID(), world,
                BoundingBox.of(c2.clone().add(-12, -12, -12), c2.clone().add(12, 12, 12)),
                stranger.getUniqueId(), c2);
        neighbor.setAnchorTierId("tier-1");
        plugin.getClaimManager().addClaimToCache(neighbor);

        // Verify AnchorGemsGUI gui-gen-6 layout: side walls empty, no glass in work zone, footer populated
        AnchorGemsGUI gemsGui = new AnchorGemsGUI(plugin, owner, claim1);
        Inventory top = gemsGui.getInventory();
        assertEquals(36, top.getSize());
        assertEquals(Material.PLAYER_HEAD, top.getItem(0).getType());
        for (int wall : new int[]{9, 17, 18, 26}) {
            assertNull(top.getItem(wall), "Work zone side wall slot " + wall + " must be empty");
        }
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, top.getItem(27).getType());
        assertEquals(Material.PLAYER_HEAD, top.getItem(34).getType());
        assertEquals(Material.PLAYER_HEAD, top.getItem(35).getType());

        // Attempting to remove the negative-radius debuff must be blocked due to neighbor overlap
        owner.getInventory().clear();
        Bukkit.getPluginManager().callEvent(new InventoryClickEvent(
                mockView(owner, top), InventoryType.SlotType.CONTAINER, 4,
                ClickType.LEFT, InventoryAction.PICKUP_ALL));
        Bukkit.getPluginManager().callEvent(new InventoryClickEvent(
                mockView(owner, top), InventoryType.SlotType.CONTAINER, 10,
                ClickType.LEFT, InventoryAction.PICKUP_ALL));
        assertEquals(1, claim1.getInstalledGems().size(), "Debuff removal must be blocked on neighbor overlap");
        assertEquals(40, Math.round(claim1.getBoundingBox().getWidthX()));

        // NPC upgrade of claim1 (24 -> 30) must also fail with OVERLAP
        assertEquals(AnchorManager.UpgradeOutcome.OVERLAP, plugin.getAnchorManager().upgradeClaimAnchor(claim1));

        // Remove neighbor -> debuff removal succeeds and preserves HybridRollOutcome!
        plugin.getClaimManager().removeClaimFromCache(neighbor.getId());
        Bukkit.getPluginManager().callEvent(new InventoryClickEvent(
                mockView(owner, top), InventoryType.SlotType.CONTAINER, 10,
                ClickType.LEFT, InventoryAction.PICKUP_ALL));
        assertTrue(claim1.getInstalledGems().isEmpty());
        assertEquals(48, Math.round(claim1.getBoundingBox().getWidthX()));
        ItemStack extracted = Arrays.stream(owner.getInventory().getContents())
                .filter(Objects::nonNull)
                .filter(plugin.getGemFactory()::isGem)
                .findFirst().orElseThrow();
        assertEquals(HybridRollOutcome.DEBUFF, plugin.getGemFactory().readHybridOutcome(extracted).orElse(null));

        // Legacy tier fallback with behavior + admin expand bonuses
        Claim legacy = new Claim(UUID.randomUUID(), world,
                BoundingBox.of(c1.clone().add(-20, -12, -20), c1.clone().add(20, 12, 20)),
                owner.getUniqueId(), c1);
        legacy.setBehaviorBonusRadius(2);
        legacy.setAdminExpandBonus(6); // base = 12 (tier-1)
        assertEquals("tier-1", plugin.getAnchorManager().resolveClaimTier(legacy).id());

        // SettingsGUI move returns anchor & gems, and clicking stale SettingsGUI does not dupe
        claim1.addInstalledGem(negHybrid);
        SettingsGUI settingsGui = new SettingsGUI(plugin, owner, claim1);
        owner.getInventory().clear();
        Bukkit.getPluginManager().callEvent(new InventoryClickEvent(
                mockView(owner, settingsGui.getInventory()), InventoryType.SlotType.CONTAINER, 16,
                ClickType.LEFT, InventoryAction.PICKUP_ALL));
        assertTrue(plugin.getClaimManager().getClaimById(claim1.getId()).isEmpty());
        long returnedAnchors = Arrays.stream(owner.getInventory().getContents())
                .filter(Objects::nonNull)
                .filter(plugin.getAnchorManager()::isAnchor)
                .count();
        assertEquals(1, returnedAnchors);

        // Stale click must NOT dupe
        owner.getInventory().clear();
        Bukkit.getPluginManager().callEvent(new InventoryClickEvent(
                mockView(owner, settingsGui.getInventory()), InventoryType.SlotType.CONTAINER, 16,
                ClickType.LEFT, InventoryAction.PICKUP_ALL));
        assertTrue(Arrays.stream(owner.getInventory().getContents()).allMatch(Objects::isNull),
                "Clicking move on already-deleted claim must not dupe anchor or gems");
    }

    @Test
    void furnaceBrewingHarmfulDebuffsPistonTrackingAndClaimHomeWork() {
        Location center = new Location(world, 1500, 64, 1500);
        Claim claim = new Claim(UUID.randomUUID(), world,
                BoundingBox.of(center.clone().add(-20, -20, -20), center.clone().add(20, 20, 20)),
                owner.getUniqueId(), center);
        claim.setName("MyFarm");
        claim.setAnchorTierId("tier-3"); // legendary gem fits only a tier 3 anchor without the overload cut
        claim.addInstalledGem(new GemStats("imperium_ceres", GemRarity.LEGENDARY, 3, 0, 0, 1.0, 1.0, 1.0, false, Map.of()));
        claim.addInstalledGem(new GemStats("hybrid_demos_vita", GemRarity.MODIFIED, 2, 0, 0, 0, 0, 0, false,
                Map.of(PotionEffectType.SLOWNESS, 0), HybridRollOutcome.DEBUFF));
        plugin.getClaimManager().addClaimToCache(claim);

        // Furnace + Brewing speedup (+100% -> halved cook/brew time)
        var furnaceBlock = world.getBlockAt(1501, 64, 1501);
        FurnaceStartSmeltEvent smelt = new FurnaceStartSmeltEvent(furnaceBlock, new ItemStack(Material.IRON_ORE), null, 200);
        Bukkit.getPluginManager().callEvent(smelt);
        assertEquals(100, smelt.getTotalCookTime());

        var brewBlock = world.getBlockAt(1502, 64, 1502);
        BrewingStartEvent brew = new BrewingStartEvent(brewBlock, new ItemStack(Material.BLAZE_POWDER), 400);
        Bukkit.getPluginManager().callEvent(brew);
        assertEquals(200, brew.getBrewingTime());

        // Harmful debuff applies to regular player, skipped for loveclaims.bypass
        stranger.teleport(center);
        member.addAttachment(plugin, "loveclaims.bypass", true);
        member.teleport(center);
        plugin.getGemEffectTask().tick();
        assertNotNull(stranger.getPotionEffect(PotionEffectType.SLOWNESS));
        assertNull(member.getPotionEffect(PotionEffectType.SLOWNESS));

        // Piston-pushed player-placed melon must remain tracked and never double-drop
        var melonStart = world.getBlockAt(1505, 64, 1505);
        melonStart.setType(Material.MELON);
        Bukkit.getPluginManager().callEvent(new BlockPlaceEvent(
                melonStart, melonStart.getState(), world.getBlockAt(1505, 63, 1505),
                new ItemStack(Material.MELON), owner, true, EquipmentSlot.HAND));
        var piston = world.getBlockAt(1504, 64, 1505);
        piston.setType(Material.PISTON);
        Bukkit.getPluginManager().callEvent(new BlockPistonExtendEvent(piston, List.of(melonStart), BlockFace.EAST));
        var melonMoved = melonStart.getRelative(BlockFace.EAST);
        melonMoved.setType(Material.MELON);
        melonStart.setType(Material.AIR);
        Bukkit.getPluginManager().callEvent(new BlockBreakEvent(melonMoved, owner));
        assertTrue(world.getEntitiesByClass(org.bukkit.entity.Item.class).isEmpty(),
                "Piston-pushed player-placed melon must still be tracked and skip double-drop");

        // /ac home <name> resolves named claim (MockBukkit's EntityMock.teleportAsync is unimplemented,
        // so verify either successful teleport or that teleportAsync was invoked on the claim's home)
        owner.teleport(new Location(world, 0, 70, 0));
        assertEquals(1500, claim.getHomeLocation().getBlockX());
        assertEquals(1500, claim.getHomeLocation().getBlockZ());
        try {
            assertTrue(server.dispatchCommand(owner, "ac home MyFarm"));
            assertEquals(1500, owner.getLocation().getBlockX());
            assertEquals(1500, owner.getLocation().getBlockZ());
        } catch (org.bukkit.command.CommandException ex) {
            assertInstanceOf(org.mockbukkit.mockbukkit.exception.UnimplementedOperationException.class, ex.getCause());
        }
    }

    private static InventoryView mockView(Player player, Inventory top) {
        Inventory bottom = player.getInventory();
        return (InventoryView) Proxy.newProxyInstance(
                InventoryView.class.getClassLoader(),
                new Class<?>[]{InventoryView.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getTopInventory" -> top;
                    case "getBottomInventory" -> bottom;
                    case "getPlayer" -> player;
                    case "getType" -> top.getType();
                    case "getInventory" -> {
                        int raw = (int) args[0];
                        yield raw < top.getSize() ? top : bottom;
                    }
                    case "convertSlot" -> {
                        int raw = (int) args[0];
                        yield raw < top.getSize() ? raw : raw - top.getSize();
                    }
                    case "getSlotType" -> InventoryType.SlotType.CONTAINER;
                    case "countSlots" -> top.getSize() + bottom.getSize();
                    case "getItem" -> {
                        int raw = (int) args[0];
                        yield raw < top.getSize() ? top.getItem(raw) : bottom.getItem(raw - top.getSize());
                    }
                    case "setItem" -> {
                        int raw = (int) args[0];
                        ItemStack item = (ItemStack) args[1];
                        if (raw < top.getSize()) top.setItem(raw, item);
                        else bottom.setItem(raw - top.getSize(), item);
                        yield null;
                    }
                    case "getTitle", "getOriginalTitle" -> "Menu";
                    case "title" -> net.kyori.adventure.text.Component.text("Menu");
                    case "getCursor" -> new ItemStack(Material.AIR);
                    case "close" -> null;
                    default -> null;
                });
    }
}
