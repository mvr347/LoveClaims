package me.lovelace.loveclaims.gui;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.model.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class AccessMenusTest {
    ServerMock server; LoveClaims plugin; PlayerMock owner, target; Claim claim;
    @BeforeEach void setup() {
        server=MockBukkit.mock();var world=server.addSimpleWorld("world");
        MockBukkit.createMockPlugin("LoveCore");
        plugin=MockBukkit.load(LoveClaims.class);
        assertTrue(plugin.isEnabled());
        owner=server.addPlayer("Owner");target=server.addPlayer("Target");
        claim=new Claim(UUID.randomUUID(),world,new BoundingBox(0,0,0,10,100,10),owner.getUniqueId(),owner.getLocation());
        claim.setName("Дом");plugin.getClaimManager().addClaimToCache(claim);
    }
    @AfterEach void stop(){MockBukkit.unmock();}
    @Test void permissionsAreTexturedHeadsWithSeparateFormattedHintLines(){
        var member=new MemberActionGUI(plugin,owner,claim,target.getUniqueId()).getInventory();
        // five toggles centred: build, containers, interact, core, manage
        for(int slot:new int[]{11,12,13,14,15})assertEquals(Material.PLAYER_HEAD,member.getItem(slot).getType());
        var plain=PlainTextComponentSerializer.plainText();
        var lore=member.getItem(14).getItemMeta().lore();
        assertTrue(plain.serialize(member.getItem(14).getItemMeta().displayName()).contains("Ядро"));
        assertTrue(lore.stream().anyMatch(line->plain.serialize(line).contains("ЛКМ")));
        for(var line:lore)assertFalse(plain.serialize(line).matches("(?s).*(§|\\n|Error:).*"));
        var settings=new SettingsGUI(plugin,owner,claim).getInventory();
        for(int slot:new int[]{12,14})for(var line:settings.getItem(slot).getItemMeta().lore())
            assertFalse(plain.serialize(line).matches("(?s).*(§|\\n|Error:).*"));
    }
    @Test void addMovesBeforeBackForBothClaimsAndRentals(){
        var empty=new MembersGUI(plugin,owner,claim).getInventory();
        // no members: the "no members" head sits in the centre, the add button before Back
        assertEquals(Material.PLAYER_HEAD,empty.getItem(31).getType());
        assertTrue(PlainTextComponentSerializer.plainText().serialize(empty.getItem(31).getItemMeta().displayName()).contains("Нет участников"));
        assertTrue(PlainTextComponentSerializer.plainText().serialize(empty.getItem(51).getItemMeta().displayName()).contains("Добавить"));
        claim.setTrust(target.getUniqueId(),TrustLevel.ACCESS);
        var populated=new MembersGUI(plugin,owner,claim).getInventory();
        assertTrue(PlainTextComponentSerializer.plainText().serialize(populated.getItem(51).getItemMeta().displayName()).contains("Добавить"));
        assertTrue(PlainTextComponentSerializer.plainText().serialize(populated.getItem(19).getItemMeta().displayName()).contains("Target"));
        var rental=new RentalMembersGUI(plugin,owner,claim).getInventory();
        assertTrue(PlainTextComponentSerializer.plainText().serialize(rental.getItem(33).getItemMeta().displayName()).contains("Добавить"));
    }
    @Test void inputOnlyInvitesAndRechecksTheEditorsCurrentPermission(){
        plugin.getChatListener().inviteMember(owner,claim.getId(),target.getName());
        assertEquals(claim.getId(),plugin.getClaimManager().getInvite(target.getUniqueId()));
        assertFalse(claim.getMembers().containsKey(target.getUniqueId()));
        var stranger=server.addPlayer("Stranger");
        plugin.getChatListener().inviteMember(stranger,claim.getId(),owner.getName());
        assertNull(plugin.getClaimManager().getInvite(owner.getUniqueId()));
    }
    @Test void artworkConfirmationsWorkThroughItemsAdderWithoutItsPlaceholderExpansion(){
        MockBukkit.createMockPlugin("ItemsAdder");
        assertTrue(ConfirmMenuLayout.available(owner));
        assertFalse(PlainTextComponentSerializer.plainText().serialize(ConfirmMenuLayout.title(owner)).contains("§"));
        var deletion=new DeleteConfirmGUI(plugin,owner,claim).getInventory();
        var payment=new RentalPaymentGUI(plugin,claim,owner).getInventory();
        var abandon=new RentalAbandonConfirmGUI(plugin,owner,claim).getInventory();
        for(var inventory:java.util.List.of(deletion,payment,abandon)){
            assertEquals(27,inventory.getSize());
            for(int slot:ConfirmMenuLayout.CONFIRM_ZONE)assertNotNull(inventory.getItem(slot));
            for(int slot:ConfirmMenuLayout.CANCEL_ZONE)assertNotNull(inventory.getItem(slot));
        }
    }
    @Test void confirmationsStayVisibleWhenItemsAdderIsUnavailable(){
        assertEquals(9,new DeleteConfirmGUI(plugin,owner,claim).getInventory().getSize());
        assertEquals(9,new RentalPaymentGUI(plugin,claim,owner).getInventory().getSize());
    }
}
