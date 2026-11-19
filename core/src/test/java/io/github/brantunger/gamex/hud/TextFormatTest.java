package io.github.brantunger.gamex.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.brantunger.gamex.TestTuning;
import io.github.brantunger.gamex.player.Difficulty;
import org.junit.jupiter.api.Test;

class TextFormatTest {

    @Test
    void decimalDropsTheDecimalsANumberDoesNotNeed() {
        assertEquals("1.8", TextFormat.decimal(1.8f));
        assertEquals("2", TextFormat.decimal(2.0f));
        assertEquals("0.25", TextFormat.decimal(0.25f));
        assertEquals("10", TextFormat.decimal(10f));
        assertEquals("100", TextFormat.decimal(100f));
        assertEquals("0", TextFormat.decimal(0f));
        assertEquals("-1.5", TextFormat.decimal(-1.5f));
        assertThrows(IllegalArgumentException.class, () -> TextFormat.decimal(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> TextFormat.decimal(Float.POSITIVE_INFINITY));
    }

    @Test
    void percentAndSignedPercentRoundTheShippedMultipliersToTheNumbersTheMenuShowed() {
        assertEquals("70%", TextFormat.percent(TestTuning.DIFFICULTY.of(Difficulty.EASY).damageTakenScale));
        assertEquals("140%", TextFormat.percent(TestTuning.DIFFICULTY.of(Difficulty.HARD).damageTakenScale));
        assertEquals("+25%", TextFormat.signedPercent(TestTuning.DIFFICULTY.of(Difficulty.EASY).pickupScale));
        assertEquals("-20%", TextFormat.signedPercent(TestTuning.DIFFICULTY.of(Difficulty.HARD).pickupScale));
        assertEquals("+0%", TextFormat.signedPercent(1f));
        assertEquals("100%", TextFormat.percent(1f));
        assertEquals("0%", TextFormat.percent(0f));
        assertEquals("-100%", TextFormat.signedPercent(0f));
        assertEquals("80%", TextFormat.percent(0.8f), "0.8f times 100 is a little over 80");
        assertEquals("33%", TextFormat.percent(0.333f));
        assertEquals("+67%", TextFormat.signedPercent(1.666f));
    }

    @Test
    void clockIsMinutesAndWholeSecondsRoundedDown() {
        assertEquals("0:00", TextFormat.clock(0f));
        assertEquals("0:00", TextFormat.clock(-4f));
        assertEquals("0:59", TextFormat.clock(59.99f));
        assertEquals("1:42", TextFormat.clock(102.9f));
        assertEquals("99:59", TextFormat.clock(100000f), "the display stops at 99:59");
    }
}
