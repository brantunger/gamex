package io.github.brantunger.gamex.weapon;

/** Selectable player weapons held in the arsenal. */
public enum WeaponSlot {
    PISTOL(1, "PISTOL"),
    SCATTERGUN(2, "SCATTERGUN"),
    RAIL_LANCE(3, "RAIL LANCE");

    /** Hotkey / HUD slot number (1-based). */
    public final int slotNumber;
    /** Short uppercase HUD name. */
    public final String label;

    WeaponSlot(int slotNumber, String label) {
        this.slotNumber = slotNumber;
        this.label = label;
    }

    /** @return whether this slot is currently owned by the player. */
    public boolean isOwned(boolean hasScattergun, boolean hasRailLance) {
        switch (this) {
            case SCATTERGUN:
                return hasScattergun;
            case RAIL_LANCE:
                return hasRailLance;
            case PISTOL:
            default:
                return true;
        }
    }

    /** @return the other weapon slot in the original two-weapon arsenal. */
    public WeaponSlot other() {
        return this == PISTOL ? SCATTERGUN : PISTOL;
    }

    /**
     * Cycles forward ({@code step > 0}) or backward ({@code step < 0}) through owned weapons.
     * @param step direction (+1 next, -1 previous)
     * @param hasScattergun whether Slot 2 is owned
     * @param hasRailLance whether Slot 3 is owned
     * @return next owned weapon slot (or {@code this} if only Pistol is owned)
     */
    public WeaponSlot cycle(int step, boolean hasScattergun, boolean hasRailLance) {
        if (step == 0) return this;
        WeaponSlot[] slots = values();
        int dir = step > 0 ? 1 : -1;
        int idx = ordinal();
        for (int i = 1; i <= slots.length; i++) {
            WeaponSlot candidate = slots[Math.floorMod(idx + dir * i, slots.length)];
            if (candidate.isOwned(hasScattergun, hasRailLance)) {
                return candidate;
            }
        }
        return this;
    }

    /**
     * Parses a persisted weapon slot name safely.
     * @param name serialized enum name or null
     * @return parsed slot, or {@link #PISTOL} if missing or unknown
     */
    public static WeaponSlot fromName(String name) {
        if (name == null || name.isEmpty()) return PISTOL;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return PISTOL;
        }
    }
}
