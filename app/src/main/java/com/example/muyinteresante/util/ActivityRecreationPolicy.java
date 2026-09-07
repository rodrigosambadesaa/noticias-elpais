package com.example.muyinteresante.util;

/** Reglas pequeñas y deterministas para restaurar la pantalla principal. */
public final class ActivityRecreationPolicy {

    private ActivityRecreationPolicy() {
    }

    public static boolean shouldLoadInitialNews(boolean hasSavedInstanceState) {
        return !hasSavedInstanceState;
    }

    public static int visibleNewsCount(int savedCount, int pageSize, int availableCount) {
        if (availableCount <= 0) {
            return 0;
        }
        return Math.min(Math.max(savedCount, pageSize), availableCount);
    }
}
