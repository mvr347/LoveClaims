package me.lovelace.loveclaims.gems;

public enum GemRarity {
    BASIC(1, 1, true),
    MODIFIED(2, 2, true),
    LEGENDARY(3, 3, false); // Крафт строго запрещён

    private final int slotCost;
    private final int minAnchorLevel;
    private final boolean craftable;

    GemRarity(int slotCost, int minAnchorLevel, boolean craftable) {
        this.slotCost = slotCost;
        this.minAnchorLevel = minAnchorLevel;
        this.craftable = craftable;
    }

    public int slotCost() {
        return slotCost;
    }

    public int minAnchorLevel() {
        return minAnchorLevel;
    }

    public boolean isCraftable() {
        return craftable;
    }
}
