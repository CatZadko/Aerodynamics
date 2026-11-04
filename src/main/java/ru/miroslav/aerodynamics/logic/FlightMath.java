package ru.miroslav.aerodynamics.logic;

public final class FlightMath {
    private FlightMath() {}
    // HappyGhast#getRiddenInput: ввод * (3.9f * attribute).
    // HappyGhast#travel: moveRelative(attribute * 5 / 3), затем сопротивление 0.91f.
    // Вектор ввода нормализуется только при длине > 1: зависимость кусочная.
    private static final double INPUT_SCALE = (double) 3.9f;
    private static final double DRAG = (double) 0.91f;
    public static double attributeForBps(double bps) {
        if (!Double.isFinite(bps) || bps < 0) throw new IllegalArgumentException("Некорректная скорость");
        double acceleration = bps * (1 - DRAG) / 20;
        double attribute = Math.sqrt(acceleration / (INPUT_SCALE * (5.0 / 3.0)));
        return attribute * INPUT_SCALE <= 1 ? attribute : acceleration / (5.0 / 3.0);
    }
    public static double bpsForAttribute(double attribute) {
        return 20 * (attribute * 5.0 / 3.0) * Math.min(1, INPUT_SCALE * attribute) / (1 - DRAG);
    }
    public static double approach(double current, double target, double acceleration, double deceleration, double seconds) {
        double delta = target - current;
        double step = (delta >= 0 ? acceleration : deceleration) * seconds;
        return current + Math.copySign(Math.min(Math.abs(delta), step), delta);
    }
    /** Ограничение по тормозному пути; запас учитывает ширину гаста и задержку управления. */
    public static double safeSpeed(double distance, double braking, double reserve) {
        return Math.sqrt(2 * braking * Math.max(0, distance - reserve));
    }
    public static long chunkKey(int x, int z) { return (x & 0xffffffffL) | ((z & 0xffffffffL) << 32); }
}
