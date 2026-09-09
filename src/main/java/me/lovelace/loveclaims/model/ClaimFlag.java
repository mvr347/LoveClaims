package me.lovelace.loveclaims.model;

public enum ClaimFlag {
    HIDE_ANCHOR(false);

    private final boolean defaultState;

    ClaimFlag(boolean defaultState) {
        this.defaultState = defaultState;
    }

    public boolean getDefaultState() {
        return defaultState;
    }
}
