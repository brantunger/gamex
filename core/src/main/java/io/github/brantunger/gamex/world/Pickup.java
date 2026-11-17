package io.github.brantunger.gamex.world;

import com.badlogic.gdx.math.Vector2;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.weapon.EnergyMeter;
import io.github.brantunger.gamex.weapon.Scattergun;
import java.util.Set;

/**
 * An item hovering above the floor that restores health, armor, energy, keycards or shotgun arsenal when the player
 * walks over it. If the corresponding meter is already full, the pickup is left in place for later.
 */
public final class Pickup {

    /** Collision radius on the XZ plane. */
    public static final float RADIUS = 0.22f;

    /** Callback receiving unlocked weapon and finite shell pool changes on pickup collection. */
    public interface Arsenal {
        boolean hasScattergun();
        int getShells();
        void grantScattergun(int addedShells);
        void addShells(int addedShells);
    }

    /** The kinds of floor pickup. */
    public enum Kind {
        /** Restores player health. */
        MEDKIT(25f, "HEALTH"),
        /** Adds damage-absorbing armor. */
        ARMOR(25f, "ARMOR"),
        /** Immediately refills blast/dash energy. */
        ENERGY_CELL(35f, "ENERGY"),
        /** Grants access to amber-locked bulkheads in the current stage. */
        AMBER_KEYCARD(0f, "AMBER KEYCARD"),
        /** Grants access to cyan-locked bulkheads in the current stage. */
        CYAN_KEYCARD(0f, "CYAN KEYCARD"),
        /** Grants the Combat Scattergun plus starter shells. */
        SCATTERGUN(Scattergun.PICKUP_WEAPON_SHELLS, "SCATTERGUN"),
        /** Restores shells for the Combat Scattergun. */
        SHELLS(Scattergun.PICKUP_BOX_SHELLS, "SHELLS");

        /** Baseline restoration amount on Normal difficulty. */
        public final float baseAmount;
        /** Short HUD noun shown in pickup notifications. */
        public final String label;

        Kind(float baseAmount, String label) {
            this.baseAmount = baseAmount;
            this.label = label;
        }

        /** @return whether this pickup grants a stage security keycard rather than restoring a vital stat. */
        public boolean isKeycard() {
            return this == AMBER_KEYCARD || this == CYAN_KEYCARD;
        }

        /** @return whether this pickup unlocks or supplies a finite-ammunition weapon. */
        public boolean isArsenal() {
            return this == SCATTERGUN || this == SHELLS;
        }
    }

    private final Kind kind;
    private final float x;
    private final float z;
    private boolean collected;

    /**
     * @param kind the kind of pickup
     * @param x world x coordinate
     * @param z world z coordinate
     */
    public Pickup(Kind kind, float x, float z) {
        this.kind = kind;
        this.x = x;
        this.z = z;
    }

    /** @return the kind of this pickup. */
    public Kind getKind() {
        return kind;
    }

    /** @return world x coordinate. */
    public float getX() {
        return x;
    }

    /** @return world z coordinate. */
    public float getZ() {
        return z;
    }

    /** @return whether this pickup has already been taken. */
    public boolean isCollected() {
        return collected;
    }

    /**
     * Collects the pickup if the player overlaps it and can benefit from it.
     * @param player the player
     * @param energy the player's energy meter
     * @param pickupScale difficulty multiplier applied to {@link Kind#baseAmount}
     * @return a HUD toast string such as {@code "+25 HEALTH"} when collected, or null if untouched
     */
    public String tryCollect(Player player, EnergyMeter energy, float pickupScale) {
        return tryCollect(player, energy, pickupScale, null);
    }

    /**
     * Collects the pickup if the player overlaps it and can benefit from it (or does not yet hold this keycard).
     * @param player the player
     * @param energy the player's energy meter
     * @param pickupScale difficulty multiplier applied to {@link Kind#baseAmount}
     * @param heldKeycards optional set updated when collecting a keycard
     * @return a HUD toast string such as {@code "+25 HEALTH"} or {@code "+ AMBER KEYCARD"}, or null if untouched
     */
    public String tryCollect(Player player, EnergyMeter energy, float pickupScale, Set<Kind> heldKeycards) {
        return tryCollect(player, energy, pickupScale, heldKeycards, null);
    }

    /**
     * Collects the pickup if the player overlaps it and can benefit from it (vitals/energy, missing keycards,
     * Scattergun unlock, or finite shell pool replenishment).
     * @param player the player
     * @param energy the player's energy meter
     * @param pickupScale difficulty multiplier applied to {@link Kind#baseAmount}
     * @param heldKeycards optional set updated when collecting a keycard
     * @param arsenal optional mutable weapon/shell holder updated when collecting weapons or ammo
     * @return a HUD toast string such as {@code "+25 HEALTH"}, {@code "+ AMBER KEYCARD"}, {@code "+ SCATTERGUN"} or
     *     {@code "+6 SHELLS"}, or null if untouched
     */
    public String tryCollect(Player player, EnergyMeter energy, float pickupScale, Set<Kind> heldKeycards,
                             Arsenal arsenal) {
        if (collected || player.isDead()) return null;
        float reach = Player.RADIUS + RADIUS;
        if (Vector2.dst2(player.position.x, player.position.y, x, z) >= reach * reach) return null;
        if (kind.isKeycard()) {
            if (heldKeycards == null || !heldKeycards.add(kind)) return null;
            collected = true;
            return "+ " + kind.label;
        }
        if (kind.isArsenal()) {
            return tryCollectArsenal(pickupScale, arsenal);
        }
        float amount = Math.max(1f, Math.round(kind.baseAmount * pickupScale));
        float applied;
        switch (kind) {
            case MEDKIT:
                applied = player.heal(amount);
                break;
            case ARMOR:
                applied = player.addArmor(amount);
                break;
            case ENERGY_CELL:
            default:
                applied = energy.add(amount);
                break;
        }
        if (applied <= 0f) return null;
        collected = true;
        return "+" + Math.max(1, Math.round(applied)) + " " + kind.label;
    }

    private String tryCollectArsenal(float pickupScale, Arsenal arsenal) {
        if (arsenal == null) return null;
        int space = Math.max(0, Scattergun.MAX_SHELLS - arsenal.getShells());
        int scaled = Math.max(1, Math.round(kind.baseAmount * pickupScale));
        int gain = Math.min(space, scaled);
        if (kind == Kind.SCATTERGUN) {
            boolean hadWeapon = arsenal.hasScattergun();
            if (hadWeapon && gain <= 0) return null;
            arsenal.grantScattergun(gain);
            collected = true;
            return hadWeapon ? "+" + gain + " SHELLS" : "+ SCATTERGUN";
        }
        if (gain <= 0) return null;
        arsenal.addShells(gain);
        collected = true;
        return "+" + gain + " SHELLS";
    }
}
