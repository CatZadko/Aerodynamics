package ru.miroslav.aerodynamics.logic;

public final class Progression {
    private Progression() {}
    public static int merge(int left, int right) {
        return left == right && left >= 1 && left <= 3 ? left + 1 : 0;
    }
    public static boolean canApply(int harness, int book) {
        return harness >= 0 && harness <= 5 && book > harness && book <= 5;
    }
    public static boolean canSmith(int bookLevel, boolean netherite, boolean emptyTemplate) {
        return bookLevel == 4 && netherite && emptyTemplate;
    }
}
