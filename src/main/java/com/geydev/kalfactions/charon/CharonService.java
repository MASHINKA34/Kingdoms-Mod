package com.geydev.kalfactions.charon;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.dimension.DimensionControlEvents;
import com.geydev.kalfactions.registry.ModItems;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CharonService {
    public static final ResourceLocation GHOST_HEALTH_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "charon_ghost_health");
    public static final ResourceLocation CORPSE_ENTITY = ResourceLocation.fromNamespaceAndPath("corpse", "corpse");
    private static final double CORPSE_SEARCH_RADIUS = 8.0D;
    private static final double AGGRO_RESET_RADIUS = 32.0D;
    private static final int PARTICLE_INTERVAL_TICKS = 5;
    private static final int AGGRO_RESET_INTERVAL_TICKS = 20;
    private static final Map<UUID, ServerBossEvent> BOSS_BARS = new HashMap<>();
    private static final Set<UUID> GHOST_DEATHS = new HashSet<>();
    private static final Set<UUID> MECHANIC_TELEPORTS = new HashSet<>();

    public static boolean isGhost(ServerPlayer player) {
        return activeGhost(player).isPresent();
    }

    public static boolean isGhostVictim(ServerPlayer victim) {
        return GHOST_DEATHS.contains(victim.getUUID()) || isGhost(victim);
    }

    public static boolean isMechanicTeleport(UUID playerId) {
        return MECHANIC_TELEPORTS.contains(playerId);
    }

    public static boolean isCorpse(@Nullable Entity entity) {
        return entity != null && CORPSE_ENTITY.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }

    public static Optional<CharonManager.Ghost> activeGhost(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server == null ? Optional.empty() : CharonManager.get(server).ghost(player.getUUID());
    }

    public static void onDeath(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        CharonManager manager = CharonManager.get(server);
        UUID playerId = player.getUUID();
        if (manager.clearGhost(playerId)) {
            GHOST_DEATHS.add(playerId);
            detach(player);
        }
        manager.recordDeath(
                playerId, GlobalPos.of(player.level().dimension(), player.blockPosition()), System.currentTimeMillis()
        );
    }

    public static void onRespawn(ServerPlayer player) {
        GHOST_DEATHS.remove(player.getUUID());
        applyVisualCooldown(player);
    }

    public static void onLogin(ServerPlayer player) {
        GHOST_DEATHS.remove(player.getUUID());
        CharonManager.Ghost ghost = activeGhost(player).orElse(null);
        if (ghost != null) {
            finish(player, ghost);
            return;
        }
        applyVisualCooldown(player);
    }

    public static void onLogout(ServerPlayer player) {
        UUID playerId = player.getUUID();
        GHOST_DEATHS.remove(playerId);
        MECHANIC_TELEPORTS.remove(playerId);
        CharonManager.Ghost ghost = activeGhost(player).orElse(null);
        if (ghost == null) {
            return;
        }
        if (ghost.returnPos().dimension().equals(player.level().dimension())) {
            finish(player, ghost);
        } else {
            detach(player);
        }
    }

    public static void clear() {
        for (ServerBossEvent bar : BOSS_BARS.values()) {
            bar.removeAllPlayers();
        }
        BOSS_BARS.clear();
        GHOST_DEATHS.clear();
        MECHANIC_TELEPORTS.clear();
        CharonGhosts.clear();
    }

    public static InteractionResultHolder<ItemStack> use(ServerPlayer player, ItemStack stack) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return InteractionResultHolder.fail(stack);
        }
        CharonManager manager = CharonManager.get(server);
        UUID playerId = player.getUUID();
        CharonManager.Entry entry = manager.entry(playerId).orElse(null);
        if (entry != null && entry.ghost() != null) {
            finish(player, entry.ghost());
            return InteractionResultHolder.sidedSuccess(stack, false);
        }
        long now = System.currentTimeMillis();
        if (entry == null || entry.death() == null) {
            return refuse(player, stack, Component.translatable("message.kingdoms.charon.no_death"));
        }
        long readyAt = entry.death().atMillis() + ModConfigSpec.CHARON_DEATH_DELAY_SECONDS.getAsInt() * 1_000L;
        if (now < readyAt) {
            return refuse(player, stack, Component.translatable(
                    "message.kingdoms.charon.too_early", CharonText.duration((readyAt - now + 999L) / 1_000L)
            ));
        }
        if (entry.cooldownUntilMillis() > now) {
            applyVisualCooldown(player);
            return refuse(player, stack, Component.translatable(
                    "message.kingdoms.charon.cooldown",
                    CharonText.duration((entry.cooldownUntilMillis() - now + 999L) / 1_000L)
            ));
        }
        if (player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
            return refuse(player, stack, Component.translatable("message.kingdoms.charon.survival_only"));
        }
        ResourceKey<Level> dimension = entry.death().pos().dimension();
        if (DimensionControlEvents.isClosedFor(server, player, dimension)) {
            return refuse(player, stack, Component.translatable("message.kingdoms.charon.dimension_closed"));
        }
        ServerLevel destination = server.getLevel(dimension);
        if (destination == null) {
            return refuse(player, stack, Component.translatable("message.kingdoms.charon.dimension_missing"));
        }
        begin(player, manager, entry.death(), destination, now);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    public static void tick(MinecraftServer server) {
        CharonManager manager = CharonManager.get(server);
        if (!manager.hasGhosts()) {
            return;
        }
        for (UUID playerId : manager.ghostPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                tick(player);
            }
        }
    }

    public static void tick(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        CharonManager.Ghost ghost = CharonManager.get(server).ghost(player.getUUID()).orElse(null);
        if (ghost == null || !player.isAlive()) {
            return;
        }
        long now = server.overworld().getGameTime();
        if (now >= ghost.endsAtGameTime()) {
            finish(player, ghost);
            return;
        }
        if (!player.isInvisible()) {
            player.setInvisible(true);
        }
        if (player.tickCount % PARTICLE_INTERVAL_TICKS == 0) {
            player.serverLevel().sendParticles(
                    ParticleTypes.SOUL,
                    player.getX(),
                    player.getY() + 0.1D,
                    player.getZ(),
                    2,
                    0.25D,
                    0.05D,
                    0.25D,
                    0.005D
            );
            updateBossBar(player, ghost.endsAtGameTime() - now, ghostTicks());
        }
        if (player.tickCount % AGGRO_RESET_INTERVAL_TICKS == 0) {
            resetAggro(player);
        }
    }

    private static void begin(
            ServerPlayer player,
            CharonManager manager,
            CharonManager.Death death,
            ServerLevel destination,
            long now
    ) {
        MinecraftServer server = destination.getServer();
        UUID playerId = player.getUUID();
        int seconds = ModConfigSpec.CHARON_GHOST_SECONDS.getAsInt();
        CharonManager.Ghost ghost = new CharonManager.Ghost(
                GlobalPos.of(player.level().dimension(), player.blockPosition()),
                server.overworld().getGameTime() + ghostTicks(),
                player.getHealth()
        );
        manager.setCooldownUntil(playerId, now + ModConfigSpec.CHARON_COOLDOWN_MINUTES.getAsInt() * 60_000L);
        manager.startGhost(playerId, ghost);
        applyGhostHealth(player);
        player.setInvisible(true);
        CharonGhosts.set(playerId, true);
        CharonNetwork.broadcast(player, true);
        BlockPos deathPos = death.pos().pos();
        ChunkPos chunk = new ChunkPos(deathPos);
        destination.getChunk(chunk.x, chunk.z);
        teleport(player, destination, landingNearDeath(destination, deathPos, playerId));
        CharonNetwork.broadcast(player, true);
        resetAggro(player);
        updateBossBar(player, ghostTicks(), ghostTicks());
        showTitle(player, seconds);
        player.displayClientMessage(
                Component.translatable("message.kingdoms.charon.started", CharonText.duration(seconds)), false
        );
    }

    private static void finish(ServerPlayer player, CharonManager.Ghost ghost) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        CharonManager.get(server).clearGhost(player.getUUID());
        detach(player);
        player.setHealth(Math.max(1.0F, Math.min(ghost.savedHealth(), player.getMaxHealth())));
        ServerLevel level = server.getLevel(ghost.returnPos().dimension());
        if (level != null) {
            BlockPos target = ghost.returnPos().pos();
            ChunkPos chunk = new ChunkPos(target);
            level.getChunk(chunk.x, chunk.z);
            teleport(player, level, CharonLanding.find(level, target));
        }
        player.displayClientMessage(Component.translatable("message.kingdoms.charon.returned"), true);
        applyVisualCooldown(player);
    }

    private static void detach(ServerPlayer player) {
        UUID playerId = player.getUUID();
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null) {
            attribute.removeModifier(GHOST_HEALTH_MODIFIER);
        }
        player.setInvisible(player.hasEffect(MobEffects.INVISIBILITY));
        CharonGhosts.set(playerId, false);
        CharonNetwork.broadcast(player, false);
        ServerBossEvent bar = BOSS_BARS.remove(playerId);
        if (bar != null) {
            bar.removeAllPlayers();
        }
        MECHANIC_TELEPORTS.remove(playerId);
    }

    private static void applyGhostHealth(ServerPlayer player) {
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        double target = ModConfigSpec.CHARON_GHOST_HEALTH.getAsInt();
        if (attribute != null) {
            attribute.removeModifier(GHOST_HEALTH_MODIFIER);
            double before = attribute.getValue();
            double delta = target - before;
            attribute.addTransientModifier(new AttributeModifier(
                    GHOST_HEALTH_MODIFIER, delta, AttributeModifier.Operation.ADD_VALUE
            ));
            double after = attribute.getValue();
            if (Math.abs(after - target) > 0.01D && Math.abs(after - before) > 0.01D) {
                double scale = (after - before) / delta;
                attribute.removeModifier(GHOST_HEALTH_MODIFIER);
                attribute.addTransientModifier(new AttributeModifier(
                        GHOST_HEALTH_MODIFIER, delta / scale, AttributeModifier.Operation.ADD_VALUE
                ));
            }
        }
        player.setHealth((float) target);
    }

    private static void teleport(ServerPlayer player, ServerLevel level, BlockPos feet) {
        UUID playerId = player.getUUID();
        ServerLevel origin = player.serverLevel();
        Vec3 from = player.position();
        MECHANIC_TELEPORTS.add(playerId);
        try {
            player.teleportTo(
                    level,
                    feet.getX() + 0.5D,
                    feet.getY(),
                    feet.getZ() + 0.5D,
                    player.getYRot(),
                    player.getXRot()
            );
        } finally {
            MECHANIC_TELEPORTS.remove(playerId);
        }
        playSoulEffects(origin, from);
        playSoulEffects(level, Vec3.atBottomCenterOf(feet));
    }

    private static void playSoulEffects(ServerLevel level, Vec3 position) {
        level.sendParticles(ParticleTypes.SOUL, position.x, position.y + 1.0D, position.z, 24, 0.3D, 0.6D, 0.3D, 0.02D);
        level.playSound(
                null,
                position.x,
                position.y,
                position.z,
                SoundEvents.SOUL_ESCAPE.value(),
                SoundSource.PLAYERS,
                1.0F,
                0.8F
        );
    }

    private static BlockPos landingNearDeath(ServerLevel level, BlockPos deathPos, UUID owner) {
        Entity corpse = findCorpse(level, deathPos, owner).orElse(null);
        return CharonLanding.find(level, corpse == null ? deathPos : corpse.blockPosition());
    }

    private static Optional<Entity> findCorpse(ServerLevel level, BlockPos deathPos, UUID owner) {
        Vec3 center = Vec3.atCenterOf(deathPos);
        List<Entity> corpses = level.getEntities(
                (Entity) null, new AABB(deathPos).inflate(CORPSE_SEARCH_RADIUS), CharonService::isCorpse
        );
        Entity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        Entity nearestOwned = null;
        double nearestOwnedDistance = Double.MAX_VALUE;
        for (Entity corpse : corpses) {
            double distance = corpse.distanceToSqr(center);
            if (distance < nearestDistance) {
                nearest = corpse;
                nearestDistance = distance;
            }
            if (distance < nearestOwnedDistance && ownsCorpse(corpse, owner)) {
                nearestOwned = corpse;
                nearestOwnedDistance = distance;
            }
        }
        return Optional.ofNullable(nearestOwned == null ? nearest : nearestOwned);
    }

    private static boolean ownsCorpse(Entity corpse, UUID owner) {
        CompoundTag tag = corpse.saveWithoutId(new CompoundTag());
        for (String key : tag.getAllKeys()) {
            if (!"UUID".equals(key) && tag.hasUUID(key) && owner.equals(tag.getUUID(key))) {
                return true;
            }
        }
        return false;
    }

    private static void resetAggro(ServerPlayer player) {
        List<Mob> mobs = player.serverLevel().getEntitiesOfClass(
                Mob.class, player.getBoundingBox().inflate(AGGRO_RESET_RADIUS), mob -> mob.getTarget() == player
        );
        for (Mob mob : mobs) {
            mob.setTarget(null);
        }
    }

    private static void showTitle(ServerPlayer player, int seconds) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
        player.connection.send(new ClientboundSetSubtitleTextPacket(CharonText.duration(seconds)));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("kingdoms.charon.title")));
    }

    private static void updateBossBar(ServerPlayer player, long remainingTicks, long totalTicks) {
        ServerBossEvent bar = BOSS_BARS.computeIfAbsent(player.getUUID(), ignored -> new ServerBossEvent(
                Component.empty(), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS
        ));
        bar.addPlayer(player);
        bar.setProgress(Math.clamp((float) remainingTicks / Math.max(1L, totalTicks), 0.0F, 1.0F));
        bar.setName(Component.translatable(
                "kingdoms.charon.bossbar", CharonText.duration((Math.max(0L, remainingTicks) + 19L) / 20L)
        ));
    }

    private static void applyVisualCooldown(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        long until = CharonManager.get(server).entry(player.getUUID())
                .map(CharonManager.Entry::cooldownUntilMillis)
                .orElse(0L);
        long remaining = until - System.currentTimeMillis();
        if (remaining > 0L) {
            player.getCooldowns().addCooldown(
                    ModItems.CHARON_TOKEN.get(), (int) Math.min(Integer.MAX_VALUE, (remaining + 49L) / 50L)
            );
        }
    }

    private static long ghostTicks() {
        return ModConfigSpec.CHARON_GHOST_SECONDS.getAsInt() * 20L;
    }

    private static InteractionResultHolder<ItemStack> refuse(ServerPlayer player, ItemStack stack, Component message) {
        player.displayClientMessage(message, true);
        return InteractionResultHolder.fail(stack);
    }

    private CharonService() {
    }
}
