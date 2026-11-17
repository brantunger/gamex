package io.github.brantunger.gamex.weapon;

/** Selectable player weapons held in the arsenal. */
public enum WeaponSlot {
    PISTOL(1, "PISTOL"),
    SCATTERGUN(2, "SCATTERGUN");

    /** Hotkey / HUD slot number (1-based). */
    public final int slotNumber;
    /** Short uppercase HUD name. */
    public final String label;

    WeaponSlot(int slotNumber, String label) {
        this.slotNumber = slotNumber;
        this.label = label;
    }

    /** @return the other weapon slot in the two-weapon arsenal. */
    public WeaponSlot other() {
        return this == PISTOL ? SCATTERGUN : PISTOL;
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
