package io.github.brantunger.gamex.hud;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.render.ProceduralTextures;
import io.github.brantunger.gamex.world.Door;
import io.github.brantunger.gamex.world.Enemy;
import io.github.brantunger.gamex.world.GameMap;
import io.github.brantunger.gamex.world.MapExploration;
import io.github.brantunger.gamex.world.Pickup;
import io.github.brantunger.gamex.world.SecurityConsole;
import io.github.brantunger.gamex.world.Target;

import java.util.List;

/**
 * Draws the 2D top-down tactical minimap panel in the top-right corner of the combat HUD. The view is north-up with a
 * compact integrated compass header strip, scrolling around the player's position with a raycasted facing FOV cone,
 * showing permanently explored tiles and landmarks while leaving unexplored cells hidden inside the panel void.
 */
final class MinimapRenderer {

    /** Inner viewport size (width and height) of the minimap grid, in HUD units. */
    static final float MAP_SIZE = 168f;
    /** Integrated header strip height above the map grid, in HUD units. */
    static final float HEADER_HEIGHT = 13f;
    /** Divider thickness separating the header strip and the map grid, in HUD units. */
    static final float HEADER_DIVIDER = 1f;
    /** Horizontal inset inside the header strip reserved on each side of its label, in HUD units. */
    static final float HEADER_PAD_X = 4f;
    /** Frame border thickness around the outer panel, in HUD units. */
    static final float BORDER = 2f;
    /** Horizontal gap from the right edge of the window, aligning with the HUD timer. */
    static final float RIGHT_MARGIN = 28f;
    /** Vertical gap from the top of the window when the panel clears the top-centre boss bar and toasts. */
    static final float TOP_MARGIN = 54f;
    /** Vertical gap from the top of the window on narrow layouts, dropping clear below boss-bar toasts. */
    static final float LOW_TOP_MARGIN = 110f;
    /** Size of one world map cell on the minimap, in HUD units. */
    static final float CELL_UNITS = 10f;
    /** Font pixel size of the north compass label inside the header strip, in HUD units. */
    static final float NORTH_LABEL_PIXEL = 1.6f;
    /** Maximum reach of the raycasted player FOV cone on the minimap, in world cells. */
    static final float FOV_CONE_RANGE = 3.2f;
    /** Angular subdivisions used to draw the player FOV cone. */
    static final int FOV_CONE_SECTORS = 20;

    private static final float DEFAULT_VERTICAL_FOV = 70f;
    private static final float DEFAULT_ASPECT = 16f / 9f;
    private static final float FOV_CONE_APEX_COLOR = Color.toFloatBits(0.68f, 0.94f, 1.00f, 0.22f);

    private static final float[] DOOR_NONE_COLOUR = {0.62f, 0.88f, 0.95f, 0.98f};
    private static final float[] DOOR_AMBER_COLOUR = {1.00f, 0.74f, 0.18f, 0.98f};
    private static final float[] DOOR_CYAN_COLOUR = {0.22f, 0.94f, 1.00f, 0.98f};
    private static final float[] DOOR_LOCKDOWN_COLOUR = {1.00f, 0.30f, 0.24f, 0.98f};
    private static final float[] DOOR_BOSS_COLOUR = {0.86f, 0.32f, 1.00f, 0.98f};

    private static final float[] PICKUP_MEDKIT_COLOUR = {0.34f, 0.92f, 0.58f, 0.96f};
    private static final float[] PICKUP_ARMOR_COLOUR = {0.34f, 0.78f, 1.00f, 0.96f};
    private static final float[] PICKUP_ENERGY_COLOUR = {0.76f, 0.46f, 1.00f, 0.96f};
    private static final float[] PICKUP_AMBER_COLOUR = {1.00f, 0.78f, 0.20f, 0.98f};
    private static final float[] PICKUP_CYAN_COLOUR = {0.24f, 0.96f, 1.00f, 0.98f};
    private static final float[] PICKUP_SCATTERGUN_COLOUR = {1.00f, 0.58f, 0.22f, 0.98f};
    private static final float[] PICKUP_SHELLS_COLOUR = {0.95f, 0.54f, 0.34f, 0.94f};
    private static final float[] PICKUP_RAIL_COLOUR = {0.30f, 0.96f, 1.00f, 0.98f};
    private static final float[] PICKUP_SLUGS_COLOUR = {0.42f, 0.82f, 0.96f, 0.94f};

    /** Screen-space layout bounds of the minimap panel and header strip in pixels. */
    static final class Bounds {
        final float outerLeft;
        final float outerBottom;
        final float outerWidth;
        final float outerHeight;
        final float innerLeft;
        final float innerBottom;
        final float innerRight;
        final float innerTop;
        final float innerSize;
        final float headerBottom;
        final float headerHeight;
        final float centerX;
        final float centerY;

        Bounds(float outerLeft, float outerBottom, float outerWidth, float outerHeight,
               float innerLeft, float innerBottom, float innerSize, float headerBottom, float headerHeight) {
            this.outerLeft = outerLeft;
            this.outerBottom = outerBottom;
            this.outerWidth = outerWidth;
            this.outerHeight = outerHeight;
            this.innerLeft = innerLeft;
            this.innerBottom = innerBottom;
            this.innerSize = innerSize;
            this.innerRight = innerLeft + innerSize;
            this.innerTop = innerBottom + innerSize;
            this.headerBottom = headerBottom;
            this.headerHeight = headerHeight;
            this.centerX = innerLeft + innerSize * 0.5f;
            this.centerY = innerBottom + innerSize * 0.5f;
        }
    }

    private final ProceduralTextures textures;
    private final PixelFont font;
    private final UiStrings strings;
    private final float[] coneVerts = new float[20];
    private final float[] rayX = new float[FOV_CONE_SECTORS + 1];
    private final float[] rayY = new float[FOV_CONE_SECTORS + 1];
    private final float[] rayDist = new float[FOV_CONE_SECTORS + 1];

    MinimapRenderer(ProceduralTextures textures, PixelFont font, UiStrings strings) {
        this.textures = textures;
        this.font = font;
        this.strings = strings;
    }

    /**
     * Computes the pixel bounds of the minimap panel and its compass header strip for the given window dimensions
     * and HUD unit scale. On narrow windows where the panel would intrude on the top-centre boss-bar/toast reserve,
     * drops it cleanly beneath that reserve.
     */
    static Bounds bounds(float width, float height, float unit) {
        float outerWidth = (MAP_SIZE + 2f * BORDER) * unit;
        float outerHeight = (MAP_SIZE + HEADER_HEIGHT + HEADER_DIVIDER + 2f * BORDER) * unit;
        float outerLeft = width - RIGHT_MARGIN * unit - outerWidth;
        boolean clearsCentre = outerLeft >= width * 0.5f + HudRenderer.CENTRE_HALF_WIDTH * unit;
        float outerTop = height - (clearsCentre ? TOP_MARGIN : LOW_TOP_MARGIN) * unit;
        float outerBottom = outerTop - outerHeight;
        float innerSize = MAP_SIZE * unit;
        float innerLeft = outerLeft + BORDER * unit;
        float innerBottom = outerBottom + BORDER * unit;
        float innerTop = innerBottom + innerSize;
        float headerBottom = innerTop + HEADER_DIVIDER * unit;
        float headerHeight = HEADER_HEIGHT * unit;
        return new Bounds(outerLeft, outerBottom, outerWidth, outerHeight,
            innerLeft, innerBottom, innerSize, headerBottom, headerHeight);
    }

    /** Converts world X to screen X on a north-up minimap centred on {@code playerX}. */
    static float worldToScreenX(float worldX, float playerX, float centerX, float cellPx) {
        return centerX + (worldX - playerX) * cellPx;
    }

    /** Converts world Z to screen Y on a north-up minimap centred on {@code playerZ}. */
    static float worldToScreenY(float worldZ, float playerZ, float centerY, float cellPx) {
        return centerY - (worldZ - playerZ) * cellPx;
    }

    /**
     * Computes the effective horizontal half-FOV angle in radians from vertical FOV, Rail Lance optic zoom state,
     * and window aspect ratio.
     */
    static float horizontalHalfFovRadians(float verticalFovDeg, float zoom, float zoomFactor,
                                          float width, float height) {
        float safeVert = Float.isFinite(verticalFovDeg) && verticalFovDeg > 0f
            ? verticalFovDeg : DEFAULT_VERTICAL_FOV;
        float aspect = Float.isFinite(width) && width > 0f && Float.isFinite(height) && height > 0f
            ? width / height : DEFAULT_ASPECT;
        float clampedZoom = Float.isFinite(zoom) ? MathUtils.clamp(zoom, 0f, 1f) : 0f;
        float safeZoomFactor = Float.isFinite(zoomFactor) && zoomFactor > 1f ? zoomFactor : 1f;
        float zoomScale = clampedZoom > 0f ? 1f / (1f + (safeZoomFactor - 1f) * clampedZoom) : 1f;
        float effVert = MathUtils.clamp(safeVert * zoomScale, 10f, 140f);
        float halfHoriz = (float) Math.atan(Math.tan(Math.toRadians(effVert * 0.5)) * aspect);
        return MathUtils.clamp(halfHoriz, 10f * MathUtils.degreesToRadians, 65f * MathUtils.degreesToRadians);
    }

    /**
     * Casts a 2D minimap ray from {@code (originX, originZ)} along {@code (dirX, dirZ)} up to {@code maxDist},
     * stopping at solid walls and closed bulkhead door slabs.
     */
    static float castConeRay(GameMap map, List<Door> doors, float originX, float originZ,
                             float dirX, float dirZ, float maxDist) {
        if (map == null || !Float.isFinite(originX) || !Float.isFinite(originZ)
            || !Float.isFinite(dirX) || !Float.isFinite(dirZ) || !Float.isFinite(maxDist) || maxDist <= 0f) {
            return 0f;
        }
        double len = Math.hypot(dirX, dirZ);
        if (len < 1e-6) return 0f;
        float dx = (float) (dirX / len);
        float dz = (float) (dirZ / len);

        int col = (int) Math.floor(originX);
        int row = (int) Math.floor(originZ);
        if (map.isSolid(col, row)) return 0f;

        float best = maxDist;
        int stepCol = dx > 0f ? 1 : -1;
        int stepRow = dz > 0f ? 1 : -1;
        float deltaX = Math.abs(dx) < 1e-8f ? Float.POSITIVE_INFINITY : Math.abs(1f / dx);
        float deltaZ = Math.abs(dz) < 1e-8f ? Float.POSITIVE_INFINITY : Math.abs(1f / dz);
        float nextX = Math.abs(dx) < 1e-8f ? Float.POSITIVE_INFINITY
            : ((dx > 0f ? col + 1f : col) - originX) / dx;
        float nextZ = Math.abs(dz) < 1e-8f ? Float.POSITIVE_INFINITY
            : ((dz > 0f ? row + 1f : row) - originZ) / dz;

        while (true) {
            float dist;
            if (nextX < nextZ) {
                dist = nextX;
                nextX += deltaX;
                col += stepCol;
            } else {
                dist = nextZ;
                nextZ += deltaZ;
                row += stepRow;
            }
            if (!Float.isFinite(dist) || dist >= best) break;
            if (map.isSolid(col, row)) {
                best = Math.max(0f, dist);
                break;
            }
        }

        if (doors != null) {
            for (int i = 0; i < doors.size(); i++) {
                Door door = doors.get(i);
                if (door == null || !door.isBlockingPassage()) continue;
                float hit = intersectDoorSlab2D(originX, originZ, dx, dz, door, best);
                if (hit < best) {
                    best = hit;
                }
            }
        }
        return MathUtils.clamp(best, 0f, maxDist);
    }

    private static float intersectDoorSlab2D(float ox, float oz, float dx, float dz, Door door, float maxDist) {
        float minX = door.getX() - door.getHalfExtentX();
        float maxX = door.getX() + door.getHalfExtentX();
        float minZ = door.getZ() - door.getHalfExtentZ();
        float maxZ = door.getZ() + door.getHalfExtentZ();

        float tMin = 0f;
        float tMax = maxDist;
        if (Math.abs(dx) < 1e-8f) {
            if (ox < minX || ox > maxX) return Float.POSITIVE_INFINITY;
        } else {
            float inv = 1f / dx;
            float t1 = (minX - ox) * inv;
            float t2 = (maxX - ox) * inv;
            if (t1 > t2) {
                float tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return Float.POSITIVE_INFINITY;
        }

        if (Math.abs(dz) < 1e-8f) {
            if (oz < minZ || oz > maxZ) return Float.POSITIVE_INFINITY;
        } else {
            float inv = 1f / dz;
            float t1 = (minZ - oz) * inv;
            float t2 = (maxZ - oz) * inv;
            if (t1 > t2) {
                float tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return Float.POSITIVE_INFINITY;
        }
        return tMin;
    }

    /** @return RGBA colour copy for a bulkhead door lock type. */
    static float[] doorColour(Door.LockKind lockKind) {
        return sharedDoorColour(lockKind).clone();
    }

    /** @return RGBA colour copy for a pickup kind. */
    static float[] pickupColour(Pickup.Kind kind) {
        return sharedPickupColour(kind).clone();
    }

    private static float[] sharedDoorColour(Door.LockKind lockKind) {
        switch (lockKind) {
            case AMBER_KEY:
                return DOOR_AMBER_COLOUR;
            case CYAN_KEY:
                return DOOR_CYAN_COLOUR;
            case LOCKDOWN:
                return DOOR_LOCKDOWN_COLOUR;
            case BOSS:
                return DOOR_BOSS_COLOUR;
            case NONE:
            default:
                return DOOR_NONE_COLOUR;
        }
    }

    private static float[] sharedPickupColour(Pickup.Kind kind) {
        switch (kind) {
            case MEDKIT:
                return PICKUP_MEDKIT_COLOUR;
            case ARMOR:
                return PICKUP_ARMOR_COLOUR;
            case ENERGY_CELL:
                return PICKUP_ENERGY_COLOUR;
            case AMBER_KEYCARD:
                return PICKUP_AMBER_COLOUR;
            case CYAN_KEYCARD:
                return PICKUP_CYAN_COLOUR;
            case SCATTERGUN:
                return PICKUP_SCATTERGUN_COLOUR;
            case SHELLS:
                return PICKUP_SHELLS_COLOUR;
            case RAIL_LANCE:
                return PICKUP_RAIL_COLOUR;
            case RAIL_SLUGS:
            default:
                return PICKUP_SLUGS_COLOUR;
        }
    }

    /** Draws the minimap overlay onto the active {@link SpriteBatch}. */
    void draw(SpriteBatch batch, GameSimulation simulation, float width, float height, float unit,
              float verticalFovDegrees) {
        if (batch == null || simulation == null || textures == null || textures.white == null) return;
        Bounds b = bounds(width, height, unit);
        float cellPx = CELL_UNITS * unit;
        float px = simulation.player.position.x;
        float pz = simulation.player.position.y;
        GameMap map = simulation.map;
        MapExploration exploration = simulation.exploration;

        // Frame border, header strip, divider, and unexplored void background.
        batch.setColor(0.38f, 0.30f, 0.62f, 0.78f);
        batch.draw(textures.white, b.outerLeft, b.outerBottom, b.outerWidth, b.outerHeight);
        batch.setColor(0.07f, 0.08f, 0.14f, 0.94f);
        batch.draw(textures.white, b.innerLeft, b.headerBottom, b.innerSize, b.headerHeight);
        batch.setColor(0.38f, 0.30f, 0.62f, 0.72f);
        batch.draw(textures.white, b.innerLeft, b.innerTop, b.innerSize, HEADER_DIVIDER * unit);
        batch.setColor(0.04f, 0.05f, 0.09f, 0.88f);
        batch.draw(textures.white, b.innerLeft, b.innerBottom, b.innerSize, b.innerSize);

        drawNorthLabel(batch, b, unit);
        drawExploredCells(batch, map, exploration, px, pz, b, cellPx, unit);
        drawFovCone(batch, simulation, b, cellPx, width, height, verticalFovDegrees);
        drawExitPad(batch, map, exploration, px, pz, b, cellPx);
        drawDoors(batch, simulation.doors, exploration, px, pz, b, cellPx);
        drawConsoles(batch, simulation.consoles, exploration, px, pz, b, cellPx);
        drawTargets(batch, simulation.targets, exploration, px, pz, b, cellPx);
        drawPickups(batch, simulation.pickups, exploration, px, pz, b, cellPx);
        drawEnemies(batch, simulation.enemies, simulation.doors, exploration, px, pz, b, cellPx);
        drawPlayerMarker(batch, simulation.player.getYaw(), b, unit);
    }

    private void drawExploredCells(SpriteBatch batch, GameMap map, MapExploration exploration,
                                   float px, float pz, Bounds b, float cellPx, float unit) {
        float halfCells = MAP_SIZE / (2f * CELL_UNITS);
        int minCol = Math.max(0, (int) Math.floor(px - halfCells) - 1);
        int maxCol = Math.min(map.getWidth() - 1, (int) Math.floor(px + halfCells) + 1);
        int minRow = Math.max(0, (int) Math.floor(pz - halfCells) - 1);
        int maxRow = Math.min(map.getHeight() - 1, (int) Math.floor(pz + halfCells) + 1);
        float wallInset = 1.4f * unit;

        for (int row = minRow; row <= maxRow; row++) {
            float cellY = worldToScreenY(row + 1f, pz, b.centerY, cellPx);
            for (int col = minCol; col <= maxCol; col++) {
                if (!exploration.isExplored(col, row)) continue;
                float cellX = worldToScreenX(col, px, b.centerX, cellPx);
                if (map.isSolid(col, row)) {
                    batch.setColor(0.44f, 0.50f, 0.64f, 0.96f);
                    clippedRect(batch, cellX, cellY, cellPx, cellPx, b);
                    batch.setColor(0.32f, 0.37f, 0.50f, 0.96f);
                    clippedRect(batch, cellX + wallInset, cellY + wallInset,
                        cellPx - 2f * wallInset, cellPx - 2f * wallInset, b);
                } else {
                    batch.setColor(0.15f, 0.18f, 0.26f, 0.92f);
                    clippedRect(batch, cellX, cellY, cellPx, cellPx, b);
                    if (map.isHazard(col, row)) {
                        float cx = cellX + cellPx * 0.5f;
                        float cy = cellY + cellPx * 0.5f;
                        batch.setColor(0.07f, 0.10f, 0.14f, 0.96f);
                        clippedCenteredRect(batch, cx, cy, cellPx * 0.82f, cellPx * 0.82f, b);
                        batch.setColor(0.18f, 0.88f, 1.00f, 0.92f);
                        float barW = Math.max(1.2f * unit, cellPx * 0.12f);
                        float barH = cellPx * 0.62f;
                        float offset = cellPx * 0.22f;
                        clippedCenteredRect(batch, cx - offset, cy, barW, barH, b);
                        clippedCenteredRect(batch, cx, cy, barW, barH, b);
                        clippedCenteredRect(batch, cx + offset, cy, barW, barH, b);
                    }
                }
            }
        }
    }

    private void drawFovCone(SpriteBatch batch, GameSimulation simulation, Bounds b, float cellPx,
                             float width, float height, float verticalFovDegrees) {
        float px = simulation.player.position.x;
        float pz = simulation.player.position.y;
        double yawRad = Math.toRadians(simulation.player.getYaw());
        float halfFov = horizontalHalfFovRadians(verticalFovDegrees, simulation.getZoom(),
            simulation.getZoomFactor(), width, height);

        for (int i = 0; i <= FOV_CONE_SECTORS; i++) {
            float frac = (float) i / FOV_CONE_SECTORS;
            double angle = yawRad + MathUtils.lerp(-halfFov, halfFov, frac);
            float dx = (float) Math.sin(angle);
            float dz = (float) -Math.cos(angle);
            float dist = castConeRay(simulation.map, simulation.doors, px, pz, dx, dz, FOV_CONE_RANGE);
            rayDist[i] = dist;
            float sx = worldToScreenX(px + dx * dist, px, b.centerX, cellPx);
            float sy = worldToScreenY(pz + dz * dist, pz, b.centerY, cellPx);
            rayX[i] = MathUtils.clamp(sx, b.innerLeft, b.innerRight);
            rayY[i] = MathUtils.clamp(sy, b.innerBottom, b.innerTop);
        }

        for (int i = 0; i < FOV_CONE_SECTORS; i++) {
            float alpha0 = MathUtils.lerp(0.22f, 0.02f, rayDist[i] / FOV_CONE_RANGE);
            float alpha1 = MathUtils.lerp(0.22f, 0.02f, rayDist[i + 1] / FOV_CONE_RANGE);
            float c0 = Color.toFloatBits(0.68f, 0.94f, 1.00f, alpha0);
            float c1 = Color.toFloatBits(0.68f, 0.94f, 1.00f, alpha1);

            coneVerts[0] = b.centerX;
            coneVerts[1] = b.centerY;
            coneVerts[2] = FOV_CONE_APEX_COLOR;
            coneVerts[3] = 0.5f;
            coneVerts[4] = 0.5f;

            coneVerts[5] = rayX[i];
            coneVerts[6] = rayY[i];
            coneVerts[7] = c0;
            coneVerts[8] = 0.5f;
            coneVerts[9] = 0.5f;

            coneVerts[10] = rayX[i + 1];
            coneVerts[11] = rayY[i + 1];
            coneVerts[12] = c1;
            coneVerts[13] = 0.5f;
            coneVerts[14] = 0.5f;

            coneVerts[15] = b.centerX;
            coneVerts[16] = b.centerY;
            coneVerts[17] = FOV_CONE_APEX_COLOR;
            coneVerts[18] = 0.5f;
            coneVerts[19] = 0.5f;

            batch.draw(textures.white, coneVerts, 0, coneVerts.length);
        }
    }

    private void drawExitPad(SpriteBatch batch, GameMap map, MapExploration exploration,
                             float px, float pz, Bounds b, float cellPx) {
        if (!map.hasExit() || !exploration.isExploredAt(map.getExitX(), map.getExitZ())) return;
        float sx = worldToScreenX(map.getExitX(), px, b.centerX, cellPx);
        float sy = worldToScreenY(map.getExitZ(), pz, b.centerY, cellPx);
        batch.setColor(0.18f, 0.92f, 0.64f, 0.96f);
        clippedCenteredRect(batch, sx, sy, cellPx * 0.78f, cellPx * 0.78f, b);
        batch.setColor(0.80f, 1f, 0.92f, 1f);
        clippedCenteredRect(batch, sx, sy, cellPx * 0.36f, cellPx * 0.36f, b);
    }

    private void drawDoors(SpriteBatch batch, List<Door> doors, MapExploration exploration,
                           float px, float pz, Bounds b, float cellPx) {
        for (int i = 0; i < doors.size(); i++) {
            Door door = doors.get(i);
            if (!exploration.isExplored(door.getCol(), door.getRow())) continue;
            float[] colour = sharedDoorColour(door.getLockKind());
            float sx = worldToScreenX(door.getX(), px, b.centerX, cellPx);
            float sy = worldToScreenY(door.getZ(), pz, b.centerY, cellPx);
            float cellLeft = sx - cellPx * 0.5f;
            float cellBottom = sy - cellPx * 0.5f;

            if (door.isBlockingPassage()) {
                float thick = cellPx * 0.36f;
                float backThick = cellPx * 0.52f;
                batch.setColor(0.04f, 0.05f, 0.09f, 0.90f);
                if (door.getOrientation() == Door.Orientation.SPAN_X) {
                    clippedCenteredRect(batch, sx, sy, cellPx, backThick, b);
                    batch.setColor(colour[0], colour[1], colour[2], colour[3]);
                    clippedCenteredRect(batch, sx, sy, cellPx, thick, b);
                } else {
                    clippedCenteredRect(batch, sx, sy, backThick, cellPx, b);
                    batch.setColor(colour[0], colour[1], colour[2], colour[3]);
                    clippedCenteredRect(batch, sx, sy, thick, cellPx, b);
                }
            } else {
                float jambLen = cellPx * 0.22f;
                float jambThick = cellPx * 0.30f;
                batch.setColor(colour[0], colour[1], colour[2], 0.60f);
                if (door.getOrientation() == Door.Orientation.SPAN_X) {
                    clippedRect(batch, cellLeft, sy - jambThick * 0.5f, jambLen, jambThick, b);
                    clippedRect(batch, cellLeft + cellPx - jambLen, sy - jambThick * 0.5f, jambLen, jambThick, b);
                } else {
                    clippedRect(batch, sx - jambThick * 0.5f, cellBottom, jambThick, jambLen, b);
                    clippedRect(batch, sx - jambThick * 0.5f, cellBottom + cellPx - jambLen, jambThick, jambLen, b);
                }
            }
        }
    }

    private void drawConsoles(SpriteBatch batch, List<SecurityConsole> consoles, MapExploration exploration,
                              float px, float pz, Bounds b, float cellPx) {
        for (int i = 0; i < consoles.size(); i++) {
            SecurityConsole console = consoles.get(i);
            if (!exploration.isExploredAt(console.getX(), console.getZ())) continue;
            float sx = worldToScreenX(console.getX(), px, b.centerX, cellPx);
            float sy = worldToScreenY(console.getZ(), pz, b.centerY, cellPx);
            batch.setColor(0.04f, 0.05f, 0.09f, 0.94f);
            clippedCenteredRect(batch, sx, sy, cellPx * 0.58f, cellPx * 0.58f, b);
            if (console.isActivated()) {
                batch.setColor(0.26f, 0.96f, 0.52f, 0.98f);
            } else {
                batch.setColor(1.00f, 0.34f, 0.24f, 0.98f);
            }
            clippedCenteredRect(batch, sx, sy, cellPx * 0.44f, cellPx * 0.44f, b);
            batch.setColor(0.95f, 1f, 0.98f, 0.95f);
            clippedCenteredRect(batch, sx, sy, cellPx * 0.18f, cellPx * 0.18f, b);
        }
    }

    private void drawTargets(SpriteBatch batch, List<Target> targets, MapExploration exploration,
                             float px, float pz, Bounds b, float cellPx) {
        for (int i = 0; i < targets.size(); i++) {
            Target target = targets.get(i);
            if (target.isDestroyed() || !exploration.isExploredAt(target.getX(), target.getZ())) continue;
            float sx = worldToScreenX(target.getX(), px, b.centerX, cellPx);
            float sy = worldToScreenY(target.getZ(), pz, b.centerY, cellPx);
            if (target.getKind() == Target.Kind.CANISTER) {
                batch.setColor(0.05f, 0.06f, 0.10f, 0.90f);
                clippedCenteredRect(batch, sx, sy, cellPx * 0.44f, cellPx * 0.44f, b);
                batch.setColor(0.22f, 0.86f, 0.74f, 0.98f);
                clippedCenteredRect(batch, sx, sy, cellPx * 0.30f, cellPx * 0.30f, b);
            } else {
                batch.setColor(0.05f, 0.06f, 0.10f, 0.90f);
                clippedCenteredRect(batch, sx, sy, cellPx * 0.50f, cellPx * 0.50f, b);
                batch.setColor(0.91f, 0.90f, 0.85f, 0.96f);
                clippedCenteredRect(batch, sx, sy, cellPx * 0.36f, cellPx * 0.36f, b);
                batch.setColor(0.90f, 0.26f, 0.58f, 1f);
                clippedCenteredRect(batch, sx, sy, cellPx * 0.16f, cellPx * 0.16f, b);
            }
        }
    }

    private void drawPickups(SpriteBatch batch, List<Pickup> pickups, MapExploration exploration,
                             float px, float pz, Bounds b, float cellPx) {
        for (int i = 0; i < pickups.size(); i++) {
            Pickup pickup = pickups.get(i);
            if (pickup.isCollected() || !exploration.isExploredAt(pickup.getX(), pickup.getZ())) continue;
            Pickup.Kind kind = pickup.getKind();
            float sx = worldToScreenX(pickup.getX(), px, b.centerX, cellPx);
            float sy = worldToScreenY(pickup.getZ(), pz, b.centerY, cellPx);
            boolean prominent = kind.isKeycard() || kind == Pickup.Kind.SCATTERGUN || kind == Pickup.Kind.RAIL_LANCE;
            float back = cellPx * (prominent ? 0.56f : 0.40f);
            float inner = cellPx * (prominent ? 0.42f : 0.28f);
            float[] colour = sharedPickupColour(kind);
            batch.setColor(0.04f, 0.05f, 0.09f, 0.92f);
            clippedCenteredRect(batch, sx, sy, back, back, b);
            batch.setColor(colour[0], colour[1], colour[2], colour[3]);
            clippedCenteredRect(batch, sx, sy, inner, inner, b);
            if (prominent) {
                batch.setColor(1f, 1f, 1f, 0.90f);
                clippedCenteredRect(batch, sx, sy, cellPx * 0.16f, cellPx * 0.16f, b);
            }
        }
    }

    private void drawEnemies(SpriteBatch batch, List<Enemy> enemies, List<Door> doors, MapExploration exploration,
                             float px, float pz, Bounds b, float cellPx) {
        for (int i = 0; i < enemies.size(); i++) {
            Enemy enemy = enemies.get(i);
            if (enemy.isDestroyed() || !exploration.isEntityExplored(enemy.getX(), enemy.getZ(), doors)) continue;
            float sx = worldToScreenX(enemy.getX(), px, b.centerX, cellPx);
            float sy = worldToScreenY(enemy.getZ(), pz, b.centerY, cellPx);
            if (enemy.getStats().boss) {
                batch.setColor(0.04f, 0.05f, 0.09f, 0.96f);
                clippedCenteredRect(batch, sx, sy, cellPx * 0.80f, cellPx * 0.80f, b);
                if (enemy.isOverdrive()) {
                    batch.setColor(0.38f, 0.72f, 1.00f, 1f);
                } else {
                    batch.setColor(1.00f, 0.36f, 0.18f, 1f);
                }
                clippedCenteredRect(batch, sx, sy, cellPx * 0.62f, cellPx * 0.62f, b);
                batch.setColor(1f, 0.96f, 0.90f, 1f);
                clippedCenteredRect(batch, sx, sy, cellPx * 0.22f, cellPx * 0.22f, b);
            } else {
                batch.setColor(0.04f, 0.05f, 0.09f, 0.94f);
                clippedCenteredRect(batch, sx, sy, cellPx * 0.54f, cellPx * 0.54f, b);
                batch.setColor(1.00f, 0.24f, 0.26f, 0.98f);
                clippedCenteredRect(batch, sx, sy, cellPx * 0.38f, cellPx * 0.38f, b);
            }
        }
    }

    private void drawPlayerMarker(SpriteBatch batch, float yawDegrees, Bounds b, float unit) {
        double yawRad = Math.toRadians(yawDegrees);
        float fx = (float) Math.sin(yawRad);
        float fy = (float) Math.cos(yawRad);
        float rx = fy;
        float ry = -fx;

        float tipX = b.centerX + fx * 5.0f * unit;
        float tipY = b.centerY + fy * 5.0f * unit;
        float leftX = b.centerX - fx * 3.6f * unit - rx * 3.6f * unit;
        float leftY = b.centerY - fy * 3.6f * unit - ry * 3.6f * unit;
        float rightX = b.centerX - fx * 3.6f * unit + rx * 3.6f * unit;
        float rightY = b.centerY - fy * 3.6f * unit + ry * 3.6f * unit;
        float tailX = b.centerX - fx * 1.4f * unit;
        float tailY = b.centerY - fy * 1.4f * unit;

        float outlineThick = 3.2f * unit;
        float innerThick = 1.8f * unit;
        batch.setColor(0.04f, 0.05f, 0.09f, 0.95f);
        drawSegment(batch, leftX, leftY, tipX, tipY, outlineThick);
        drawSegment(batch, rightX, rightY, tipX, tipY, outlineThick);
        drawSegment(batch, tailX, tailY, tipX, tipY, outlineThick);

        batch.setColor(0.95f, 1f, 0.96f, 1f);
        drawSegment(batch, leftX, leftY, tipX, tipY, innerThick);
        drawSegment(batch, rightX, rightY, tipX, tipY, innerThick);
        drawSegment(batch, tailX, tailY, tipX, tipY, innerThick);
    }

    private void drawNorthLabel(SpriteBatch batch, Bounds b, float unit) {
        if (font == null || strings == null) return;
        String north = strings.get(UiText.HUD_MINIMAP_NORTH);
        float pixel = NORTH_LABEL_PIXEL * unit;
        float textY = b.headerBottom + (b.headerHeight - PixelFont.height(pixel)) * 0.5f;
        batch.setColor(0.76f, 0.90f, 1f, 0.96f);
        font.drawCentred(batch, textures.white, north, b.centerX, textY, pixel);
    }

    private void drawSegment(SpriteBatch batch, float x0, float y0, float x1, float y1, float thickness) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-4f) return;
        float angleDeg = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees;
        batch.draw(textures.white, x0, y0 - thickness * 0.5f, 0f, thickness * 0.5f,
            len, thickness, 1f, 1f, angleDeg, 0, 0, 1, 1, false, false);
    }

    private void clippedCenteredRect(SpriteBatch batch, float cx, float cy, float w, float h, Bounds b) {
        clippedRect(batch, cx - w * 0.5f, cy - h * 0.5f, w, h, b);
    }

    private void clippedRect(SpriteBatch batch, float x, float y, float w, float h, Bounds b) {
        float x0 = Math.max(x, b.innerLeft);
        float y0 = Math.max(y, b.innerBottom);
        float x1 = Math.min(x + w, b.innerRight);
        float y1 = Math.min(y + h, b.innerTop);
        if (x1 > x0 && y1 > y0) {
            batch.draw(textures.white, x0, y0, x1 - x0, y1 - y0);
        }
    }
}
