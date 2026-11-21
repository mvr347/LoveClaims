package me.lovelace.loveclaims.gems;

public sealed interface GemDescriptor permits PureGemDescriptor, HybridGemDescriptor {
    String id();

    GemRarity rarity();
}
