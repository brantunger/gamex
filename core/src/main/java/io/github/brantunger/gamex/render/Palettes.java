package io.github.brantunger.gamex.render;

import io.github.brantunger.gamex.model.ModelPalette;
import io.github.brantunger.gamex.weapon.HandModel;
import io.github.brantunger.gamex.weapon.PistolModel;
import io.github.brantunger.gamex.weapon.RailLanceModel;
import io.github.brantunger.gamex.weapon.ScattergunModel;
import io.github.brantunger.gamex.world.WorldModels;

/**
 * The colours of every OBJ model the game draws: one {@link ModelPalette} per model family, each from its own file
 * in {@code assets/data/}. Loaded once at start-up and passed to {@link WorldRenderer} and
 * {@link ViewmodelRenderer}; the {@code tools} model previewer loads the same files.
 *
 * <p>Instances are immutable and safe to share.
 */
public final class Palettes {

    /** The colours of the world models, from {@link WorldModels#PALETTE_PATH}. */
    public final ModelPalette world;
    /** The colours of the pistol in hand, from {@link PistolModel#PALETTE_PATH}. */
    public final ModelPalette pistol;
    /** The colours of the Combat Scattergun in hand, from {@link ScattergunModel#PALETTE_PATH}. */
    public final ModelPalette scattergun;
    /** The colours of the Rail Lance in hand, from {@link RailLanceModel#PALETTE_PATH}. */
    public final ModelPalette railLance;
    /** The colours of the hand and sleeve, from {@link HandModel#PALETTE_PATH}. */
    public final ModelPalette hand;

    /**
     * @param world the colours of the world models
     * @param pistol the colours of the pistol in hand
     * @param scattergun the colours of the Combat Scattergun in hand
     * @param railLance the colours of the Rail Lance in hand
     * @param hand the colours of the hand and sleeve
     */
    public Palettes(ModelPalette world, ModelPalette pistol, ModelPalette scattergun, ModelPalette railLance,
                    ModelPalette hand) {
        this.world = world;
        this.pistol = pistol;
        this.scattergun = scattergun;
        this.railLance = railLance;
        this.hand = hand;
    }

    /**
     * Reads the five palette files.
     * @return the loaded palettes
     * @throws IllegalArgumentException if a file breaks the format, lacks a material or accent, holds a name its
     *     model family does not know, or holds a value that is not four numbers from 0 to 1
     */
    public static Palettes load() {
        return new Palettes(WorldModels.loadPalette(), PistolModel.loadPalette(), ScattergunModel.loadPalette(),
            RailLanceModel.loadPalette(), HandModel.loadPalette());
    }
}
