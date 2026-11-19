package io.github.brantunger.gamex.world;

import com.badlogic.gdx.math.Vector2;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.weapon.EnergyMeter;
import io.github.brantunger.gamex.weapon.WeaponTuning;
import java.util.Set;

/**
 * An item hovering above the floor that restores health, armor, energy, keycards or arsenal weapons/ammo when the
 * player walks over it. If the corresponding meter is already full, the pickup is left in place for later.
 */
public final class Pickup {

    /** Collision radius on the XZ plane. */
    public static final float RADIUS = 0.22f;

    /** Callback receiving unlocked weapon and finite ammo pool changes on pickup collection. */
    public interface Arsenal {
        boolean hasScattergun();
        int getShells();
        void grantScattergun(int addedShells);
        void addShells(int addedShells);
        default boolean hasRailLance() {
            return false;
        }
        default int getSlugs() {
            return 0;
        }
        default void grantRailLance(int addedSlugs) {}
        default void addSlugs(int addedSlugs) {}
    }

    /** The kinds of floor pickup. */
    public enum Kind {
        /** Restores player health. */
        MEDKIT,
        /** Adds damage-absorbing armor. */
        ARMOR,
        /** Immediately refills blast/dash energy. */
        ENERGY_CELL,
        /** Grants access to amber-locked bulkheads in the current stage. */
        AMBER_KEYCARD,
        /** Grants access to cyan-locked bulkheads in the current stage. */
        CYAN_KEYCARD,
        /** Grants the Combat Scattergun plus starter shells. */
        SCATTERGUN,
        /** Restores shells for the Combat Scattergun. */
        SHELLS,
        /** Grants the Slot 3 Rail Lance plus starter slugs. */
        RAIL_LANCE,
        /** Restores slugs for the Rail Lance. */
        RAIL_SLUGS;

        /** @return whether this pickup grants a stage security keycard rather than restoring a vital stat. */
        public boolean isKeycard() {
            return this == AMBER_KEYCARD || this == CYAN_KEYCARD;
        }

        /** @return whether this pickup unlocks or supplies a finite-ammunition weapon. */
        public boolean isArsenal() {
            return this == SCATTERGUN || this == SHELLS || this == RAIL_LANCE || this == RAIL_SLUGS;
        }
    }

    /**
     * What collecting a pickup gave, for the HUD's notification. The words are the HUD's: it has a label for each
     * kind. Immutable.
     */
    public static final class Collected {
        /**
         * The kind whose label the notification shows. It is the pickup's own kind, except that a weapon pickup
         * collected with the weapon already held shows the kind of its ammunition.
         */
        public final Kind shown;
        /** How much health, armor, energy or ammunition was gained; 0 for a keycard. */
        public final int amount;
        /** Whether a keycard or a weapon was gained, so the notification names it and gives no amount. */
        public final boolean granted;

        Collected(Kind shown, int amount, boolean granted) {
            this.shown = shown;
            this.amount = amount;
            this.granted = granted;
        }

        @Override
        public String toString() {
            return granted ? "+ " + shown : "+" + amount + " " + shown;
        }
    }

    private final Kind kind;
    private final float x;
    private final float z;
    /** What this pickup gives on Normal difficulty; 0 for a keycard. */
    private final float baseAmount;
    /** The most shells or slugs the player carries, for a pickup that gives them; 0 for the others. */
    private final int ammoCapacity;
    private boolean collected;

    /**
     * @param kind the kind of pickup
     * @param x world x coordinate
     * @param z world z coordinate
     * @param world the level's balance; it holds what a medkit, an armor pickup and an energy cell give
     * @param weapons the weapon balance; it holds what the weapon and ammunition pickups give and the most
     *     ammunition the player carries
     */
    public Pickup(Kind kind, float x, float z, WorldTuning world, WeaponTuning weapons) {
        this.kind = kind;
        this.x = x;
        this.z = z;
        this.baseAmount = baseAmount(kind, world, weapons);
        if (kind == Kind.SCATTERGUN || kind == Kind.SHELLS) {
            this.ammoCapacity = weapons.scattergunMaxShells;
        } else if (kind == Kind.RAIL_LANCE || kind == Kind.RAIL_SLUGS) {
            this.ammoCapacity = weapons.railMaxSlugs;
        } else {
            this.ammoCapacity = 0;
        }
    }

    /**
     * @param kind a kind of pickup
     * @param world the level's balance
     * @param weapons the weapon balance
     * @return what a pickup of that kind gives on Normal difficulty; 0 for a keycard
     */
    public static float baseAmount(Kind kind, WorldTuning world, WeaponTuning weapons) {
        switch (kind) {
            case MEDKIT:
                return world.pickupMedkit;
            case ARMOR:
                return world.pickupArmor;
            case ENERGY_CELL:
                return world.pickupEnergyCell;
            case SCATTERGUN:
                return weapons.scattergunWeaponPickupShells;
            case SHELLS:
                return weapons.scattergunBoxPickupShells;
            case RAIL_LANCE:
                return weapons.railWeaponPickupSlugs;
            case RAIL_SLUGS:
                return weapons.railPackPickupSlugs;
            default:
                return 0f;
        }
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
     * @param pickupScale difficulty multiplier applied to what the pickup gives on Normal
     * @return what was gained when collected, or null if untouched
     */
    public Collected tryCollect(Player player, EnergyMeter energy, float pickupScale) {
        return tryCollect(player, energy, pickupScale, null);
    }

    /**
     * Collects the pickup if the player overlaps it and can benefit from it (or does not yet hold this keycard).
     * @param player the player
     * @param energy the player's energy meter
     * @param pickupScale difficulty multiplier applied to what the pickup gives on Normal
     * @param heldKeycards optional set updated when collecting a keycard
     * @return what was gained (an amount of health, or a keycard), or null if untouched
     */
    public Collected tryCollect(Player player, EnergyMeter energy, float pickupScale, Set<Kind> heldKeycards) {
        return tryCollect(player, energy, pickupScale, heldKeycards, null);
    }

    /**
     * Collects the pickup if the player overlaps it and can benefit from it (vitals/energy, missing keycards,
     * Scattergun unlock, or finite shell pool replenishment).
     * @param player the player
     * @param energy the player's energy meter
     * @param pickupScale difficulty multiplier applied to what the pickup gives on Normal
     * @param heldKeycards optional set updated when collecting a keycard
     * @param arsenal optional mutable weapon/shell holder updated when collecting weapons or ammo
     * @return what was gained (an amount of health, a keycard, a weapon or an amount of shells), or null if
     *     untouched
     */
    public Collected tryCollect(Player player, EnergyMeter energy, float pickupScale, Set<Kind> heldKeycards,
                             Arsenal arsenal) {
        if (collected || player.isDead()) return null;
        float reach = Player.RADIUS + RADIUS;
        if (Vector2.dst2(player.position.x, player.position.y, x, z) >= reach * reach) return null;
        if (kind.isKeycard()) {
            if (heldKeycards == null || !heldKeycards.add(kind)) return null;
            collected = true;
            return new Collected(kind, 0, true);
        }
        if (kind.isArsenal()) {
            return tryCollectArsenal(pickupScale, arsenal);
        }
        float amount = Math.max(1f, Math.round(baseAmount * pickupScale));
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
        return new Collected(kind, Math.max(1, Math.round(applied)), false);
    }

    private Collected tryCollectArsenal(float pickupScale, Arsenal arsenal) {
        if (arsenal == null) return null;
        int scaled = Math.max(1, Math.round(baseAmount * pickupScale));
        if (kind == Kind.SCATTERGUN || kind == Kind.SHELLS) {
            int space = Math.max(0, ammoCapacity - arsenal.getShells());
            int gain = Math.min(space, scaled);
            if (kind == Kind.SCATTERGUN) {
                boolean hadWeapon = arsenal.hasScattergun();
                if (hadWeapon && gain <= 0) return null;
                arsenal.grantScattergun(gain);
                collected = true;
                return hadWeapon ? new Collected(Kind.SHELLS, gain, false) : new Collected(kind, gain, true);
            }
            if (gain <= 0) return null;
            arsenal.addShells(gain);
            collected = true;
            return new Collected(kind, gain, false);
        }
        int space = Math.max(0, ammoCapacity - arsenal.getSlugs());
        int gain = Math.min(space, scaled);
        if (kind == Kind.RAIL_LANCE) {
            boolean hadWeapon = arsenal.hasRailLance();
            if (hadWeapon && gain <= 0) return null;
            arsenal.grantRailLance(gain);
            collected = true;
            return hadWeapon ? new Collected(Kind.RAIL_SLUGS, gain, false) : new Collected(kind, gain, true);
        }
        if (gain <= 0) return null;
        arsenal.addSlugs(gain);
        collected = true;
        return new Collected(kind, gain, false);
    }
}
