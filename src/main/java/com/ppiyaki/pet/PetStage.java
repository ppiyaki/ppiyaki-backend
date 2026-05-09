package com.ppiyaki.pet;

public enum PetStage {

    EGG(0),
    CRACKED_EGG(3),
    BABY(7),
    HEALTHY(14),
    GUARDIAN(30),
    EMPEROR(100);

    private final int requiredStreak;

    PetStage(final int requiredStreak) {
        this.requiredStreak = requiredStreak;
    }

    public int getRequiredStreak() {
        return requiredStreak;
    }

    public static PetStage fromStreak(final int streak) {
        if (streak < 0) {
            throw new IllegalArgumentException("streak must be >= 0");
        }
        PetStage result = EGG;
        for (final PetStage stage : values()) {
            if (streak >= stage.requiredStreak) {
                result = stage;
            }
        }
        return result;
    }
}
