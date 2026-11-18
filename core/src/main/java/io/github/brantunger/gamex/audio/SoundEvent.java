package io.github.brantunger.gamex.audio;

/**
 * A headless audio event emitted by the game simulation or menu state machine. Has no graphics or audio-backend
 * dependencies so callers can drain and unit-test events directly.
 */
public final class SoundEvent {

    /** Semantic sound cues supported by the game. */
    public enum Type {
        PISTOL_SHOT,
        SCATTERGUN_FIRE,
        SCATTERGUN_ALT_FIRE,
        RAIL_FIRE,
        RAIL_ZOOM_IN,
        RAIL_ZOOM_OUT,
        WEAPON_SWITCH,
        WEAPON_DRY_FIRE,
        WALL_IMPACT,
        BLAST_RELEASE,
        EXPLOSION,
        TARGET_HIT,
        TARGET_DESTROYED,
        ENEMY_HIT,
        ENEMY_DESTROYED,
        ENEMY_SHOOT,
        DRONE_LUNGE,
        HAZARD_ZAP,
        BOSS_SHOOT,
        BOSS_SLAM,
        BOSS_OVERDRIVE,
        BOSS_DESTROYED,
        PLAYER_JUMP,
        PLAYER_LAND,
        PLAYER_DASH,
        PLAYER_HURT,
        PLAYER_DEATH,
        PICKUP_HEALTH,
        PICKUP_ARMOR,
        PICKUP_ENERGY,
        PICKUP_KEYCARD,
        PICKUP_WEAPON,
        PICKUP_SHELLS,
        PICKUP_SLUGS,
        LOW_ENERGY,
        DOOR_OPEN,
        DOOR_LOCKED,
        CONSOLE_ACTIVATE,
        MENU_MOVE,
        MENU_CONFIRM,
        MENU_BACK
    }

    public final Type type;
    public final boolean spatial;
    public final float x;
    public final float y;
    public final float z;
    public final float intensity;

    private SoundEvent(Type type, boolean spatial, float x, float y, float z, float intensity) {
        this.type = type;
        this.spatial = spatial;
        this.x = x;
        this.y = y;
        this.z = z;
        this.intensity = Math.max(0.1f, Math.min(1.5f, intensity));
    }

    /** Creates a centered player-local event. */
    public static SoundEvent player(Type type, float intensity) {
        return new SoundEvent(type, false, 0f, 0f, 0f, intensity);
    }

    /** Creates a centered UI event at full intensity. */
    public static SoundEvent ui(Type type) {
        return new SoundEvent(type, false, 0f, 0f, 0f, 1f);
    }

    /** Creates a 3D world-positioned event for distance attenuation and stereo panning. */
    public static SoundEvent world(Type type, float x, float y, float z, float intensity) {
        return new SoundEvent(type, true, x, y, z, intensity);
    }
}
