package ru.miroslav.aerodynamics.logic;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AcquisitionAndLoadTest {
    @Test void probabilitiesAreMutuallyExclusiveAndNeverGiveHighBooks() {
        Chance chance = new Chance(0.08, 0.02);
        int one = 0, two = 0, none = 0;
        for (int i = 0; i < 10000; i++) {
            int level = chance.levelAt((i + 0.5) / 10000);
            if (level == 0) none++; else if (level == 1) one++; else if (level == 2) two++; else fail("Высокая книга");
        }
        assertEquals(800, one); assertEquals(200, two); assertEquals(9000, none);
    }
    @Test void brokenProbabilitiesFailFast() {
        assertThrows(IllegalArgumentException.class, () -> new Chance(0.8, 0.8));
        assertThrows(IllegalArgumentException.class, () -> new Chance(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> new Chance(-0.1, 0));
    }
    @Test void guardNeedsSustainedRecoveryAndResetsOnFreshLag() {
        LoadGuard guard = new LoadGuard();
        assertFalse(update(guard, 20, 10, 1));
        assertTrue(update(guard, 17, 60, 1));
        assertTrue(update(guard, 20, 10, 4));
        assertTrue(update(guard, 18.5, 48, 1));
        assertTrue(update(guard, 20, 10, 7));
        assertFalse(update(guard, 20, 10, 1));
    }
    @Test void tickDurationTriggersBeforeMinuteTpsDrops() {
        assertTrue(update(new LoadGuard(), 20, 70, 0.1));
    }
    private boolean update(LoadGuard g, double tps, double mspt, double seconds) {
        return g.update(tps, mspt, seconds, 18, 19, 55, 45, 8);
    }
}
