package com.geydev.kalfactions.client;

public final class ClientDungeonSelection {
    private static volatile int dungeonId;
    private static volatile String dungeonName = "";

    public static void begin(int id, String name) {
        dungeonId = id;
        dungeonName = name == null ? "" : name;
    }

    public static void clear() {
        dungeonId = 0;
        dungeonName = "";
    }

    public static boolean isActive() {
        return dungeonId > 0;
    }

    public static int dungeonId() {
        return dungeonId;
    }

    public static String dungeonName() {
        return dungeonName;
    }

    private ClientDungeonSelection() {
    }
}
