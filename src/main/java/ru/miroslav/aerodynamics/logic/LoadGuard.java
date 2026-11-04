package ru.miroslav.aerodynamics.logic;

/** Гистерезис не позволяет ограничителю дёргаться около порогового значения TPS. */
public final class LoadGuard {
    private boolean limited;
    private double healthySeconds;
    public boolean update(double tps, double mspt, double seconds, double enterTps, double exitTps,
                          double enterMspt, double exitMspt, double recoverySeconds) {
        if (!Double.isFinite(tps) || !Double.isFinite(mspt) || tps < enterTps || mspt > enterMspt) {
            limited = true; healthySeconds = 0;
        } else if (limited && tps >= exitTps && mspt <= exitMspt) {
            healthySeconds += seconds;
            if (healthySeconds >= recoverySeconds) { limited = false; healthySeconds = 0; }
        } else healthySeconds = 0;
        return limited;
    }
    public boolean limited() { return limited; }
}
