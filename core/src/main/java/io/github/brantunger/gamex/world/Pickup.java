package io.github.brantunger.gamex.world;

import com.badlogic.gdx.math.Vector2;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.weapon.EnergyMeter;
import java.util.Set;

/**
 * An item hovering above the floor that restores health, armor or energy when the player walks over it. If the
 * corresponding meter is already full, the pickup is left in place for later.
 */
public final class Pickup {

    /** Collision radius on the XZ plane. */
    public static final float RADIUS = 0.22f;

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
        CYAN_KEYCARD(0f, "CYAN KEYCARD");

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
        if (collected || player.isDead()) return null;
        float reach = Player.RADIUS + RADIUS;
        if (Vector2.dst2(player.position.x, player.position.y, x, z) >= reach * reach) return null;
        if (kind.isKeycard()) {
            if (heldKeycards == null || !heldKeycards.add(kind)) return null;
            collected = true;
            return "+ " + kind.label;
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
}
