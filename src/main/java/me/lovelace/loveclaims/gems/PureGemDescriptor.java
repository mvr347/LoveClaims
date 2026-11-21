package me.lovelace.loveclaims.gems;

public record PureGemDescriptor(
        String id,
        GemRarity rarity,
        GemStats baseStats
) implements GemDescriptor {
}
