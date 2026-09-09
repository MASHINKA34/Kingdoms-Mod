package com.geydev.kalfactions.charon;

import net.minecraft.network.chat.Component;

public final class CharonText {
    public static Component duration(long seconds) {
        long safe = Math.max(0L, seconds);
        long minutes = safe / 60L;
        long rest = safe % 60L;
        if (minutes == 0L) {
            return Component.translatable("kingdoms.charon.duration.seconds", rest);
        }
        if (rest == 0L) {
            return Component.translatable("kingdoms.charon.duration.minutes", minutes);
        }
        return Component.translatable("kingdoms.charon.duration.mixed", minutes, rest);
    }

    private CharonText() {
    }
}
