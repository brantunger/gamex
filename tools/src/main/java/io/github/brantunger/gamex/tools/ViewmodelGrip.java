package io.github.brantunger.gamex.tools;

import io.github.brantunger.gamex.weapon.PistolModel;

/**
 * Shared rear grip, trigger, and trigger-guard geometry matching {@link PistolModel}'s canonical grip envelope so
 * {@code viewmodel-hand.obj} wraps every firearm cleanly with zero glove penetration.
 */
final class ViewmodelGrip {

    private ViewmodelGrip() {}

    static void build(MeshBuilder b, String polymer, String polymerDark, String stipple, String darkMetal) {
        b.usePart("body");
        b.prism("grip", polymer, MeshBuilder.AXIS_X, PistolModel.GRIP_OUTLINE,
            -PistolModel.GRIP_BODY_HALF_WIDTH, PistolModel.GRIP_BODY_HALF_WIDTH);
        b.prism("magazine plate", polymerDark, MeshBuilder.AXIS_X, new float[] {
            -0.105f, 0.046f, -0.105f, 0.113f, -0.112f, 0.115f, -0.112f, 0.048f
        }, -PistolModel.PLATE_HALF_WIDTH, PistolModel.PLATE_HALF_WIDTH);
        for (int row = 0; row < 11; row++) {
            float y = -0.030f - row * 0.0066f;
            for (int column = 0; column < 6; column++) {
                float z = PistolModel.frontStrap(y) + 0.008f + column * 0.0068f;
                String material = (row + column) % 2 == 0 ? stipple : polymerDark;
                b.box("stipple left " + row + "/" + column, material,
                    -PistolModel.GRIP_HALF_WIDTH, -PistolModel.GRIP_BODY_HALF_WIDTH, y - 0.0036f, y, z, z + 0.0036f);
                b.box("stipple right " + row + "/" + column, material,
                    PistolModel.GRIP_BODY_HALF_WIDTH, PistolModel.GRIP_HALF_WIDTH, y - 0.0036f, y, z, z + 0.0036f);
            }
        }
        b.prism("beavertail", polymer, MeshBuilder.AXIS_X, new float[] {
            -0.004f, 0.080f, -0.004f, 0.094f, -0.007f, 0.100f, -0.011f, 0.102f, -0.016f, 0.098f, -0.018f, 0.082f
        }, -PistolModel.BEAVERTAIL_HALF_WIDTH, PistolModel.BEAVERTAIL_HALF_WIDTH);

        float[] outer = {-0.018f, -0.043f, -0.036f, -0.044f, -0.046f, -0.037f, -0.050f, -0.025f, -0.050f, 0.010f,
            -0.045f, 0.027f};
        float[] inner = {-0.018f, -0.039f, -0.034f, -0.040f, -0.042f, -0.034f, -0.0455f, -0.024f, -0.0455f, 0.009f,
            -0.041f, 0.024f};
        for (int i = 0; i < 5; i++) {
            b.prism("trigger guard " + i, polymerDark, MeshBuilder.AXIS_X, new float[] {
                inner[i * 2], inner[i * 2 + 1], inner[i * 2 + 2], inner[i * 2 + 3],
                outer[i * 2 + 2], outer[i * 2 + 3], outer[i * 2], outer[i * 2 + 1]
            }, -0.0055f, 0.0055f);
        }
        b.prism("trigger", darkMetal, MeshBuilder.AXIS_X, new float[] {
            -0.018f, -0.013f, -0.018f, -0.007f, -0.028f, -0.004f, -0.028f, -0.009f
        }, -0.003f, 0.003f);
        b.prism("trigger tip", darkMetal, MeshBuilder.AXIS_X, new float[] {
            -0.028f, -0.009f, -0.028f, -0.004f, -0.037f, -0.0055f, -0.037f, -0.0095f
        }, -0.003f, 0.003f);
    }

    static float[] ring(float centreU, float centreV, float radius, int sides) {
        float[] outline = new float[sides * 2];
        for (int i = 0; i < sides; i++) {
            double angle = (i + 0.5) * 2.0 * Math.PI / sides;
            outline[i * 2] = centreU + radius * (float) Math.cos(angle);
            outline[i * 2 + 1] = centreV + radius * (float) Math.sin(angle);
        }
        return outline;
    }
}
