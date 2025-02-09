package net.casim.jbd.entity.enums;

import java.util.Arrays;

public enum InstallmentCount {
    SIX(6),
    NINE(9),
    TWELVE(12),
    TWENTY_FOUR(24);

    private final int count;

    InstallmentCount(int count) {
        this.count = count;
    }

    public int getCount() {
        return count;
    }

    public static boolean isAllowed(int value) {
        return Arrays.stream(InstallmentCount.values())
                .anyMatch(e -> e.getCount() == value);
    }
}

