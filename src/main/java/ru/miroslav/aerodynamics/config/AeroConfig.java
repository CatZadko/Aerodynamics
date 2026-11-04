package ru.miroslav.aerodynamics.config;

import org.bukkit.configuration.file.FileConfiguration;
import ru.miroslav.aerodynamics.logic.Chance;

/** Неизменяемый снимок настроек; неправильный reload не заменяет рабочие настройки. */
public record AeroConfig(double[] speeds, double speedCalibration, int periodTicks,
                         double acceleration, double deceleration,
                         boolean lagEnabled, double safeSpeed, double enterTps, double exitTps,
                         double enterMspt, double exitMspt, double recoverySeconds,
                         boolean chunkGuard, double lookaheadSeconds, double maxLookahead,
                         boolean requireSent, boolean tableEnabled,
                         Chance table, Chance ghast, Chance phantom, Chance barter,
                         boolean villagers, int emeralds, int tears, int tradeUses,
                         int[] mergeCosts, int[] applyCosts, int renameCost, float modelData) {
    public AeroConfig {
        speeds = speeds.clone(); mergeCosts = mergeCosts.clone(); applyCosts = applyCosts.clone();
    }
    @Override public double[] speeds() { return speeds.clone(); }
    @Override public int[] mergeCosts() { return mergeCosts.clone(); }
    @Override public int[] applyCosts() { return applyCosts.clone(); }
    public double speed(int level) { return speeds[level]; }
    public int mergeCost(int outputLevel) { return mergeCosts[outputLevel]; }
    public int applyCost(int level) { return applyCosts[level]; }

    public static AeroConfig load(FileConfiguration c) {
        double[] speeds = new double[6]; int[] merge = new int[6]; int[] apply = new int[6];
        for (int i = 0; i <= 5; i++) {
            speeds[i] = number(c, "flight.speeds." + i, 0.1, 100);
            if (i > 0 && speeds[i] <= speeds[i - 1]) throw new IllegalArgumentException("Скорости должны возрастать");
            if (i > 0) apply[i] = integer(c, "anvil.apply-costs." + i, 1, 39);
            if (i >= 2 && i <= 4) merge[i] = integer(c, "anvil.merge-costs." + i, 1, 39);
        }
        double enterTps = number(c, "flight.lag-guard.enter-tps", 1, 20);
        double exitTps = number(c, "flight.lag-guard.exit-tps", enterTps, 20);
        double enterMspt = number(c, "flight.lag-guard.enter-mspt", 1, 1000);
        double exitMspt = number(c, "flight.lag-guard.exit-mspt", 1, enterMspt);
        return new AeroConfig(speeds, number(c, "flight.speed-calibration", 0.1, 3),
                integer(c, "flight.update-period-ticks", 1, 10),
                number(c, "flight.acceleration-bps2", 0.1, 100), number(c, "flight.deceleration-bps2", 1, 200),
                c.getBoolean("flight.lag-guard.enabled"), number(c, "flight.lag-guard.safe-speed-bps", 1, speeds[4]),
                enterTps, exitTps, enterMspt, exitMspt, number(c, "flight.lag-guard.recovery-seconds", 1, 60),
                c.getBoolean("flight.chunk-guard.enabled"), number(c, "flight.chunk-guard.lookahead-seconds", 0.5, 10),
                number(c, "flight.chunk-guard.max-lookahead-blocks", 32, 512),
                c.getBoolean("flight.chunk-guard.require-sent-to-passengers"), c.getBoolean("acquisition.table.enabled"),
                chance(c, "table"), chance(c, "ghast"), chance(c, "phantom"), chance(c, "barter"),
                c.getBoolean("acquisition.villagers.enabled"), integer(c, "acquisition.villagers.emeralds", 1, 64),
                integer(c, "acquisition.villagers.ghast-tears", 1, 64), integer(c, "acquisition.villagers.max-uses", 1, 64),
                merge, apply, integer(c, "anvil.rename-cost", 1, 39),
                (float) number(c, "items.custom-model-data", 0, 16_777_216));
    }
    private static Chance chance(FileConfiguration c, String name) {
        String path = "acquisition." + name;
        return new Chance(number(c, path + ".level-i", 0, 1), number(c, path + ".level-ii", 0, 1));
    }
    private static double number(FileConfiguration c, String path, double min, double max) {
        if (!c.isSet(path)) throw new IllegalArgumentException("Нет настройки: " + path);
        double n = c.getDouble(path);
        if (!Double.isFinite(n) || n < min || n > max) throw new IllegalArgumentException("Недопустимая настройка: " + path);
        return n;
    }
    private static int integer(FileConfiguration c, String path, int min, int max) {
        double n = number(c, path, min, max);
        if (n != Math.rint(n)) throw new IllegalArgumentException("Требуется целое: " + path);
        return (int) n;
    }
}
