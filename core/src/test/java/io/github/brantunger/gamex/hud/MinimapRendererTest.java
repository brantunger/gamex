package io.github.brantunger.gamex.hud;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.TestTuning;
import io.github.brantunger.gamex.world.Door;
import io.github.brantunger.gamex.world.GameMap;
import io.github.brantunger.gamex.world.Pickup;
import org.junit.jupiter.api.Test;

import java.util.List;

class MinimapRendererTest {

    @Test
    void boundsAndHeaderStripClearTimeBossToastsCrosshairAndVitalsAcrossStandardAndNarrowWindows() {
        int[][] sizes = {
            {1280, 720}, {1920, 1080}, {2560, 1080}, {800, 600}, {1024, 768}, {720, 720}, {960, 1080}, {480, 720}
        };
        for (int[] size : sizes) {
            float w = size[0];
            float h = size[1];
            float u = HudRenderer.hudUnit(w, h);
            String label = size[0] + "x" + size[1];

            MinimapRenderer.Bounds b = MinimapRenderer.bounds(w, h, u);
            float outerRight = b.outerLeft + b.outerWidth;
            float outerTop = b.outerBottom + b.outerHeight;

            // Right edge aligns cleanly with TIME right margin (28u) inside the window.
            assertEquals(w - MinimapRenderer.RIGHT_MARGIN * u, outerRight, 1e-3f, label);
            assertTrue(b.outerLeft > 0f, label + " fits horizontally");

            // Top edge clears the TIME row in the top-right corner.
            float timeBottomY = h - HudRenderer.STATUS_TOP * u - PixelFont.height(HudRenderer.STATUS_PIXEL * u);
            assertTrue(outerTop <= timeBottomY - 10f * u, label + " clears TIME row");

            // Header strip sits cleanly between the divider above the map grid and the top border.
            assertEquals(MinimapRenderer.HEADER_HEIGHT * u, b.headerHeight, 1e-3f, label);
            assertEquals(b.innerTop + MinimapRenderer.HEADER_DIVIDER * u, b.headerBottom, 1e-3f, label);
            assertEquals(b.headerBottom + b.headerHeight + MinimapRenderer.BORDER * u, outerTop, 1e-3f, label);

            // Clears top-centre boss bar & toast reserve either horizontally (wide) or vertically (narrow).
            float centreReserveRight = w * 0.5f + HudRenderer.CENTRE_HALF_WIDTH * u;
            float bossToastBottom = h - HudRenderer.TOAST_BOTTOM_UNDER_BOSS * u;
            assertTrue(b.outerLeft >= centreReserveRight || outerTop <= bossToastBottom - 4f * u,
                label + " clears top-centre boss bar & toasts");

            // Clears objective line on narrow windows too.
            float objectiveRightLimit = HudRenderer.OBJECTIVE_RIGHT_LIMIT * u;
            float objectiveBottom = HudRenderer.objectiveBottomY(w, h, u);
            assertTrue(b.outerLeft >= objectiveRightLimit || outerTop <= objectiveBottom - 4f * u,
                label + " clears objective line");

            // Sits strictly above centre crosshair & Rail Lance zoom brackets.
            float railBracketTop = h * 0.5f + HudRenderer.railZoomBracketSpan(u, 0f);
            assertTrue(b.outerBottom > railBracketTop + 20f * u, label + " clears crosshair & zoom brackets");

            // Inner viewport and centre are consistent.
            assertEquals(MinimapRenderer.MAP_SIZE * u, b.innerSize, 1e-3f, label);
            assertEquals((b.innerLeft + b.innerRight) * 0.5f, b.centerX, 1e-3f, label);
            assertEquals((b.innerBottom + b.innerTop) * 0.5f, b.centerY, 1e-3f, label);
        }
    }

    @Test
    void northUpMappingMapsPlayerToCenterAndCardinalDirectionsToScreenAxes() {
        float cellPx = 10f;
        float centerX = 500f;
        float centerY = 400f;
        float px = 12.5f;
        float pz = 8.5f;

        assertEquals(centerX, MinimapRenderer.worldToScreenX(px, px, centerX, cellPx), 1e-4f);
        assertEquals(centerY, MinimapRenderer.worldToScreenY(pz, pz, centerY, cellPx), 1e-4f);

        // East (+x) is right (+screenX), West (-x) is left (-screenX).
        assertEquals(centerX + 20f, MinimapRenderer.worldToScreenX(px + 2f, px, centerX, cellPx), 1e-4f);
        assertEquals(centerX - 20f, MinimapRenderer.worldToScreenX(px - 2f, px, centerX, cellPx), 1e-4f);

        // North (-z) is up (+screenY), South (+z) is down (-screenY).
        assertEquals(centerY + 20f, MinimapRenderer.worldToScreenY(pz - 2f, pz, centerY, cellPx), 1e-4f);
        assertEquals(centerY - 20f, MinimapRenderer.worldToScreenY(pz + 2f, pz, centerY, cellPx), 1e-4f);
    }

    @Test
    void horizontalHalfFovRespondsToAspectRatioAndRailLanceZoom() {
        float widescreen = MinimapRenderer.horizontalHalfFovRadians(70f, 0f, 1.8f, 1280f, 720f);
        float square = MinimapRenderer.horizontalHalfFovRadians(70f, 0f, 1.8f, 720f, 720f);
        float zoomed = MinimapRenderer.horizontalHalfFovRadians(70f, 1f, 1.8f, 1280f, 720f);
        float fallback = MinimapRenderer.horizontalHalfFovRadians(Float.NaN, Float.NaN, 1.8f, 0f, -1f);

        assertTrue(widescreen > square, "16:9 horizontal FOV is wider than 1:1");
        assertTrue(zoomed < widescreen, "1.8x Rail Lance zoom narrows the FOV cone");
        assertEquals(widescreen, fallback, 1e-5f, "invalid arguments fall back to 70 deg at 16:9");
    }

    @Test
    void coneRaycastStopsAtSolidWallsAndClosedDoorsOnly() {
        GameMap map = GameMap.parse(
            "#########",
            "#>.=....#",
            "#########");
        float ox = map.getSpawnX(); // 1.5
        float oz = map.getSpawnZ(); // 1.5
        Door closedDoor = new Door(Door.LockKind.NONE, Door.Orientation.SPAN_Z, 3, 1);

        // Westward toward wall at x = 1.0 gives distance 0.5.
        assertEquals(0.5f, MinimapRenderer.castConeRay(map, List.of(closedDoor), ox, oz, -1f, 0f, 3.2f), 1e-4f);

        // Eastward stops at near face of closed door slab (3.5 - 0.09 = 3.41 -> distance 1.91).
        float expectedDoorDist = closedDoor.getX() - closedDoor.getHalfExtentX() - ox;
        assertEquals(expectedDoorDist,
            MinimapRenderer.castConeRay(map, List.of(closedDoor), ox, oz, 1f, 0f, 3.2f), 1e-4f);

        // Once the door retracts past clearance, the cone extends up to maxDist.
        closedDoor.open();
        closedDoor.update(Door.OPEN_DURATION);
        assertEquals(3.2f, MinimapRenderer.castConeRay(map, List.of(closedDoor), ox, oz, 1f, 0f, 3.2f), 1e-4f);

        // Invalid rays return 0 safely.
        assertEquals(0f, MinimapRenderer.castConeRay(null, List.of(), ox, oz, 1f, 0f, 3.2f), 0f);
        assertEquals(0f, MinimapRenderer.castConeRay(map, List.of(), ox, oz, 0f, 0f, 3.2f), 0f);
    }

    @Test
    void colourLookupsCoverEveryDoorLockAndPickupKindAsDefensiveCopies() {
        for (Door.LockKind lock : Door.LockKind.values()) {
            float[] first = MinimapRenderer.doorColour(lock);
            float[] second = MinimapRenderer.doorColour(lock);
            assertEquals(4, first.length, lock.name());
            assertNotSame(first, second, lock.name());
            first[0] = -1f;
            assertArrayEquals(second, MinimapRenderer.doorColour(lock), 0f, lock.name());
        }
        for (Pickup.Kind kind : Pickup.Kind.values()) {
            float[] first = MinimapRenderer.pickupColour(kind);
            float[] second = MinimapRenderer.pickupColour(kind);
            assertEquals(4, first.length, kind.name());
            assertNotSame(first, second, kind.name());
            first[1] = -1f;
            assertArrayEquals(second, MinimapRenderer.pickupColour(kind), 0f, kind.name());
        }
        assertEquals("N", TestTuning.STRINGS.get(UiText.HUD_MINIMAP_NORTH));
    }
}
