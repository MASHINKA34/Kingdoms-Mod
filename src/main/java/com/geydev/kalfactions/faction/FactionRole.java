package com.geydev.kalfactions.faction;

public enum FactionRole {
    MEMBER(0),
    OFFICER(1),
    LEADER(2);

    private final int authority;

    FactionRole(int authority) {
        this.authority = authority;
    }

    public boolean isAtLeast(FactionRole required) {
        return authority >= required.authority;
    }

    public boolean canManageClaims() {
        return isAtLeast(OFFICER);
    }

    public boolean canManageTreasury() {
        return isAtLeast(OFFICER);
    }

    public boolean canManageMembers() {
        return isAtLeast(OFFICER);
    }
}
