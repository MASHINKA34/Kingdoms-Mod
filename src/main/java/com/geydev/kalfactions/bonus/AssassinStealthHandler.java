package com.geydev.kalfactions.bonus;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.faction.FactionBonus;
import com.geydev.kalfactions.protection.FactionAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class AssassinStealthHandler {
    private static final String STEALTH_TEAM = "kingdoms_stealth";
    private static final String PREVIOUS_TEAM_KEY = "kingdoms:stealth_previous_team";
    private static final int CHECK_INTERVAL_TICKS = 10;
    private static final int DARK_LIGHT = 4;
    private static final int LIT_LIGHT = 8;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.tickCount % CHECK_INTERVAL_TICKS != 0
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        boolean stealthed = isStealthed(player);
        if (!FactionAccess.hasLegacyMastery(player, FactionBonus.ASSASSINS)) {
            if (stealthed) {
                stopStealth(player);
            }
            return;
        }
        int light = level.getMaxLocalRawBrightness(player.blockPosition());
        if (!stealthed && light <= DARK_LIGHT && !player.hasGlowingTag() && !player.isOnFire()) {
            startStealth(player);
            return;
        }
        if (stealthed) {
            if (light >= LIT_LIGHT || player.hasGlowingTag() || player.isOnFire()) {
                stopStealth(player);
            } else {
                applyInvisibility(player);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isStealthed(player)) {
            stopStealth(player);
        }
    }

    private static boolean isStealthed(ServerPlayer player) {
        Team team = player.getTeam();
        return team != null && STEALTH_TEAM.equals(team.getName());
    }

    private static void startStealth(ServerPlayer player) {
        Scoreboard scoreboard = player.getServer() == null ? null : player.getServer().getScoreboard();
        if (scoreboard == null) {
            return;
        }
        Team current = player.getTeam();
        player.getPersistentData().putString(
                PREVIOUS_TEAM_KEY,
                current == null ? "" : current.getName()
        );
        scoreboard.addPlayerToTeam(player.getScoreboardName(), stealthTeam(scoreboard));
        applyInvisibility(player);
    }

    private static void stopStealth(ServerPlayer player) {
        player.removeEffect(MobEffects.INVISIBILITY);
        Scoreboard scoreboard = player.getServer() == null ? null : player.getServer().getScoreboard();
        if (scoreboard == null) {
            return;
        }
        scoreboard.removePlayerFromTeam(player.getScoreboardName(), stealthTeam(scoreboard));
        String previous = player.getPersistentData().getString(PREVIOUS_TEAM_KEY);
        player.getPersistentData().remove(PREVIOUS_TEAM_KEY);
        if (previous.isEmpty()) {
            return;
        }
        PlayerTeam team = scoreboard.getPlayerTeam(previous);
        if (team != null) {
            scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
        }
    }

    private static PlayerTeam stealthTeam(Scoreboard scoreboard) {
        PlayerTeam team = scoreboard.getPlayerTeam(STEALTH_TEAM);
        if (team == null) {
            team = scoreboard.addPlayerTeam(STEALTH_TEAM);
            team.setNameTagVisibility(Team.Visibility.NEVER);
            team.setAllowFriendlyFire(true);
            team.setSeeFriendlyInvisibles(false);
        }
        return team;
    }

    private static void applyInvisibility(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(
                MobEffects.INVISIBILITY,
                MobEffectInstance.INFINITE_DURATION,
                0,
                true,
                false,
                false
        ));
    }

    private AssassinStealthHandler() {
    }
}
