package ru.miroslav.aerodynamics.logic;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProgressionTest {
    @Test void fullChainNeedsFourLevelTwoBooks() {
        int first = Progression.merge(2, 2), second = Progression.merge(2, 2);
        assertEquals(3, first); assertEquals(4, Progression.merge(first, second));
        assertTrue(Progression.canSmith(4, true, true));
    }
    @Test void equalLevelsCannotBypassNetherite() {
        assertEquals(0, Progression.merge(4, 4)); assertEquals(0, Progression.merge(5, 5));
    }
    @Test void mismatchedAndInvalidInputsNeverUpgrade() {
        for (int left = -1; left <= 6; left++) for (int right = -1; right <= 6; right++) {
            if (left != right || left < 1 || left > 3) assertEquals(0, Progression.merge(left, right));
        }
    }
    @Test void smithingRequiresAllThreeConditions() {
        for (int level = 0; level <= 6; level++) for (boolean ingot : new boolean[]{true, false})
            for (boolean empty : new boolean[]{true, false})
                assertEquals(level == 4 && ingot && empty, Progression.canSmith(level, ingot, empty));
    }
    @Test void applyingSameOrLowerBookCannotCreateLevel() {
        assertTrue(Progression.canApply(0, 5)); assertTrue(Progression.canApply(4, 5));
        assertFalse(Progression.canApply(4, 4)); assertFalse(Progression.canApply(5, 4));
        assertFalse(Progression.canApply(0, 6));
    }
}
