package ru.miroslav.aerodynamics.logic;

import java.util.concurrent.ThreadLocalRandom;

/** Одна попытка, максимум один результат. Сначала проверяем более редкий II. */
public record Chance(double levelOne, double levelTwo) {
    public Chance {
        if (!Double.isFinite(levelOne) || !Double.isFinite(levelTwo) || levelOne < 0 || levelTwo < 0
                || levelOne + levelTwo > 1) throw new IllegalArgumentException("Сумма шансов должна быть от 0 до 1");
    }
    public int roll() { return levelAt(ThreadLocalRandom.current().nextDouble()); }
    public int levelAt(double value) {
        if (!Double.isFinite(value) || value < 0 || value >= 1) throw new IllegalArgumentException("Ожидается [0, 1)");
        if (value < levelTwo) return 2;
        return value < levelTwo + levelOne ? 1 : 0;
    }
}
