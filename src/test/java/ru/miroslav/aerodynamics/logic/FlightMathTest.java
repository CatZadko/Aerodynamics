package ru.miroslav.aerodynamics.logic;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlightMathTest {
    @Test void calibratedSpeedsAgreeWithDiscreteVanillaPhysics() {
        for (double target : new double[]{0, 5, 10, 14, 19, 24, 30, 72.7, 100}) {
            double a = FlightMath.attributeForBps(target);
            // Независимо воспроизводим 1000 тиков: ввод -> moveRelative -> движение -> drag.
            double input = Math.min(1, (double) 3.9f * a);
            double push = (float) ((float) a * 5.0f / 3.0f) * input;
            double velocity = 0, distance = 0;
            for (int tick = 0; tick < 1000; tick++) {
                velocity += push;
                if (tick >= 980) distance += velocity;
                velocity *= (double) 0.91f;
            }
            assertEquals(target, distance, 0.0001, "Скорость " + target);
        }
    }
    @Test void vanillaAttributeIsNotFiveBlocksPerSecond() {
        assertEquals(3.611112, FlightMath.bpsForAttribute(0.05), 0.00001);
    }
    @Test void smoothRampHasNoOvershootAndBrakesFaster() {
        assertEquals(5.6, FlightMath.approach(5, 72.7, 6, 16, 0.1), 0.00001);
        assertEquals(71.1, FlightMath.approach(72.7, 10, 6, 16, 0.1), 0.00001);
        assertEquals(10, FlightMath.approach(9.9, 10, 6, 16, 0.1), 0.00001);
        assertEquals(0, FlightMath.approach(0.1, 0, 6, 16, 0.1), 0.00001);
    }
    @Test void stoppingDistanceAndNegativeChunkCoordinates() {
        assertEquals(0, FlightMath.safeSpeed(10, 16, 12));
        double speed = FlightMath.safeSpeed(100, 16, 12);
        assertEquals(88, speed * speed / 32, 0.00001);
        long key = FlightMath.chunkKey(-7, -19);
        assertEquals(-7, (int) key); assertEquals(-19, (int) (key >> 32));
    }
}
