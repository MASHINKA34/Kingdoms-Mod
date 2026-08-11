package com.geydev.kalfactions.faith;

import java.util.List;

public record FaithQuest(
        FaithGod god,
        int level,
        List<FaithRequirement> requirements,
        long spurs,
        int kills,
        boolean killsOrTrophy
) {
    public FaithQuest {
        requirements = List.copyOf(requirements);
        spurs = Math.max(0L, spurs);
        kills = Math.max(0, kills);
    }

    public boolean hasTrophy() {
        return requirements.size() > 1 && requirements.getLast().tag() != null;
    }

    public int trophyIndex() {
        return hasTrophy() ? requirements.size() - 1 : -1;
    }

    public boolean isComplete(int[] delivered, long spursDelivered, int killsDone) {
        if (spursDelivered < spurs) {
            return false;
        }
        int trophyIndex = trophyIndex();
        for (int index = 0; index < requirements.size(); index++) {
            if (index == trophyIndex && killsOrTrophy) {
                continue;
            }
            if (deliveredAt(delivered, index) < requirements.get(index).count()) {
                return false;
            }
        }
        if (god != FaithGod.WAR) {
            return true;
        }
        boolean enoughKills = killsDone >= kills;
        if (!killsOrTrophy) {
            return enoughKills;
        }
        return enoughKills || trophyIndex >= 0
                && deliveredAt(delivered, trophyIndex) >= requirements.get(trophyIndex).count();
    }

    public static int deliveredAt(int[] delivered, int index) {
        return delivered == null || index < 0 || index >= delivered.length ? 0 : Math.max(0, delivered[index]);
    }
}
