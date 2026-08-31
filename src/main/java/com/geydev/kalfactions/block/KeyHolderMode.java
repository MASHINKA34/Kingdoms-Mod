package com.geydev.kalfactions.block;

import net.minecraft.util.StringRepresentable;

public enum KeyHolderMode implements StringRepresentable {
    PULSE("pulse"),
    TOGGLE("toggle");

    private final String serializedName;

    KeyHolderMode(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public String displayNameKey() {
        return "screen.kingdoms.key_holder.mode." + serializedName;
    }

    public KeyHolderMode next() {
        KeyHolderMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static KeyHolderMode fromSerializedName(String name) {
        for (KeyHolderMode mode : values()) {
            if (mode.serializedName.equals(name)) {
                return mode;
            }
        }
        return PULSE;
    }

    public static boolean isValidName(String name) {
        for (KeyHolderMode mode : values()) {
            if (mode.serializedName.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
