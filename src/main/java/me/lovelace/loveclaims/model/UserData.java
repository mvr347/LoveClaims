package me.lovelace.loveclaims.model;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class UserData {
    private final UUID uuid;
    private final AtomicInteger bonusMemberLimit = new AtomicInteger(0);
    private final AtomicInteger bonusClaimSlots = new AtomicInteger(0);
    private volatile boolean showProximityBorder = true;

    public UserData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    public boolean isShowProximityBorder() {
        return showProximityBorder;
    }

    public void setShowProximityBorder(boolean showProximityBorder) {
        this.showProximityBorder = showProximityBorder;
    }

    public int getBonusMemberLimit() {
        return bonusMemberLimit.get();
    }

    public void setBonusMemberLimit(int val) {
        this.bonusMemberLimit.set(val);
    }

    public void addBonusMemberLimit(int amount) {
        this.bonusMemberLimit.addAndGet(amount);
    }

    public void addBonusMembers(int amount) {
        this.bonusMemberLimit.addAndGet(amount);
    }

    public int getBonusClaimSlots() {
        return bonusClaimSlots.get();
    }

    public void setBonusClaimSlots(int val) {
        this.bonusClaimSlots.set(val);
    }

    public void addBonusClaimSlots(int amount) {
        this.bonusClaimSlots.addAndGet(amount);
    }

    public void loadFrom(UserData other) {
        this.bonusMemberLimit.set(other.getBonusMemberLimit());
        this.bonusClaimSlots.set(other.getBonusClaimSlots());
        this.showProximityBorder = other.isShowProximityBorder();
    }
}
