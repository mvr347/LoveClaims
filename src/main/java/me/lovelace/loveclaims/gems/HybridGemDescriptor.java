package me.lovelace.loveclaims.gems;

import java.util.List;

public record HybridGemDescriptor(
        String id,
        String firstBaseGemId,
        String secondBaseGemId,
        String firstModifiedGemId,
        String secondModifiedGemId,
        List<GemStats> debuffPool
) implements GemDescriptor {

    public HybridGemDescriptor {
        debuffPool = debuffPool == null ? List.of() : List.copyOf(debuffPool);
    }

    @Override
    public GemRarity rarity() {
        return GemRarity.MODIFIED;
    }

    public boolean matchesIngredients(String idA, String idB) {
        if (idA == null || idB == null) return false;
        return (firstBaseGemId.equalsIgnoreCase(idA) && secondBaseGemId.equalsIgnoreCase(idB))
                || (firstBaseGemId.equalsIgnoreCase(idB) && secondBaseGemId.equalsIgnoreCase(idA));
    }
}
