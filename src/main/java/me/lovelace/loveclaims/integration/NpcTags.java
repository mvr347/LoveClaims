package me.lovelace.loveclaims.integration;

import dev.lovelace.lovecore.api.npc.NpcHandle;
import org.bukkit.ChatColor;

import java.util.Optional;

/** Tags LoveClaims puts on the NPCs it owns, and the recognition rules built on them. */
public final class NpcTags {
    public static final String ROLE = "loveclaims:role";
    public static final String ROLE_TAXER = "taxer";
    public static final String ROLE_LANDLORD = "landlord";
    public static final String CLAIM = "loveclaims:claim";

    private NpcTags() {
    }

    /**
     * Whether {@code npc} is a tax collector. The tag is authoritative; an NPC without any role tag
     * is matched by the legacy name rule (name contains the configured taxer name) and, when it
     * matches, gets tagged so the name is never needed again.
     */
    public static boolean isTaxer(NpcHandle npc, String taxerName) {
        Optional<String> role = npc.tag(ROLE);
        if (role.isPresent()) return ROLE_TAXER.equals(role.get());
        if (!legacyNameMatches(npc.name(), taxerName)) return false;
        npc.tag(ROLE, ROLE_TAXER);
        return true;
    }

    /** Whether {@code npc} is the landlord NPC of the claim with id {@code claimId}. */
    public static boolean isLandlordOf(NpcHandle npc, String claimId) {
        return ROLE_LANDLORD.equals(npc.tag(ROLE).orElse(null)) && claimId.equals(npc.tag(CLAIM).orElse(null));
    }

    /** Legacy rule: colour-stripped NPC name contains the colour-stripped configured name. */
    public static boolean legacyNameMatches(String npcName, String wanted) {
        String wantedPlain = ChatColor.stripColor(wanted);
        if (npcName == null || wantedPlain == null || wantedPlain.isBlank()) return false;
        String plain = ChatColor.stripColor(npcName);
        return plain != null && plain.contains(wantedPlain);
    }
}
