package com.geydev.kalfactions.news;

import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.FactionMember;
import com.geydev.kalfactions.faction.FactionRole;
import com.geydev.kalfactions.net.FactionManagerService;
import com.geydev.kalfactions.net.FactionServerHooks;
import com.geydev.kalfactions.tax.OfflineNoticeQueue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class NewsService {
    private static final long ACTION_COOLDOWN_TICKS = 2L;
    private static final ConcurrentHashMap<UUID, Long> LAST_ACTION_TICK = new ConcurrentHashMap<>();

    public static void publish(ServerPlayer player, String rawTitle, String rawBody) {
        MinecraftServer server = player.getServer();
        if (server == null || !player.isAlive() || player.isSpectator()) {
            return;
        }
        long now = player.level().getGameTime();
        Long previous = LAST_ACTION_TICK.put(player.getUUID(), now);
        if (previous != null && now - previous < ACTION_COOLDOWN_TICKS) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.error.action_rate_limited"), false);
            return;
        }
        FactionManager factions = FactionManager.get(player.serverLevel());
        Faction faction = factions.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.error.not_in_faction"), false);
            return;
        }
        FactionMember member = faction.member(player.getUUID()).orElse(null);
        if (member == null || !member.role().isAtLeast(FactionRole.OFFICER)) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.error.news_not_officer"), false);
            return;
        }
        String title = sanitize(rawTitle, NewsManager.MAX_TITLE_LENGTH, false);
        String body = sanitize(rawBody, NewsManager.MAX_BODY_LENGTH, true);
        if (title.isBlank()) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.error.news_title_empty"), false);
            return;
        }
        if (body.isBlank()) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.error.news_body_empty"), false);
            return;
        }
        NewsManager news = NewsManager.get(server);
        long nowMillis = System.currentTimeMillis();
        long cooldownMillis = ModConfigSpec.NEWS_PUBLISH_COOLDOWN_MINUTES.getAsInt() * 60_000L;
        long sinceLast = nowMillis - news.lastPublishMillis(faction.id());
        if (cooldownMillis > 0L && sinceLast < cooldownMillis) {
            long remainingMinutes = (cooldownMillis - sinceLast + 59_999L) / 60_000L;
            FactionServerHooks.sendNotice(
                    player,
                    Component.translatable("kingdoms.error.news_rate_limited", remainingMinutes),
                    false
            );
            return;
        }
        news.ensurePlayer(player.getUUID());
        publishAndNotify(server, faction, title, body, player.getGameProfile().getName(), nowMillis);
    }

    public static boolean adminPublish(MinecraftServer server, Faction faction, String rawTitle, String rawBody, String author) {
        String title = sanitize(rawTitle, NewsManager.MAX_TITLE_LENGTH, false);
        String body = sanitize(rawBody, NewsManager.MAX_BODY_LENGTH, true);
        if (title.isBlank() || body.isBlank()) {
            return false;
        }
        publishAndNotify(server, faction, title, body, author, System.currentTimeMillis());
        return true;
    }

    private static void publishAndNotify(
            MinecraftServer server,
            Faction faction,
            String title,
            String body,
            String author,
            long nowMillis
    ) {
        NewsManager news = NewsManager.get(server);
        news.publish(
                faction.id(),
                title,
                body,
                author,
                nowMillis,
                ModConfigSpec.NEWS_MAX_ARTICLES_PER_FACTION.getAsInt()
        );
        Component notice = Component.translatable("kingdoms.news.published", faction.name(), title);
        for (ServerPlayer online : List.copyOf(server.getPlayerList().getPlayers())) {
            FactionServerHooks.sendNotice(online, notice, true);
            pushBadge(online);
        }
        OfflineNoticeQueue offlineQueue = OfflineNoticeQueue.get(server);
        for (UUID knownPlayer : news.knownPlayerIds()) {
            if (server.getPlayerList().getPlayer(knownPlayer) == null) {
                offlineQueue.enqueue(server, knownPlayer, notice, true);
            }
        }
    }

    public static void sendNewsFactions(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || !player.isAlive()) {
            return;
        }
        FactionManager factions = FactionManager.get(player.serverLevel());
        NewsManager news = NewsManager.get(server);
        news.pruneMissing(factions.factions().stream().map(Faction::id).toList());
        List<NewsPayloads.NewsFactionEntry> entries = new ArrayList<>();
        for (UUID factionId : news.factionIdsWithNews()) {
            Faction faction = factions.getFactionById(factionId).orElse(null);
            if (faction == null) {
                continue;
            }
            entries.add(new NewsPayloads.NewsFactionEntry(
                    faction.id(),
                    faction.name(),
                    faction.color(),
                    FactionManagerService.emblemPixels(faction),
                    faction.emblemUrl(),
                    news.articleCount(factionId),
                    news.latestArticleMillis(factionId)
            ));
        }
        entries.sort(Comparator.comparingLong(NewsPayloads.NewsFactionEntry::latestMillis).reversed());
        PacketDistributor.sendToPlayer(player, new NewsPayloads.S2CNewsFactions(entries));
        news.markSeen(player.getUUID(), System.currentTimeMillis());
        pushBadge(player);
    }

    public static void sendFactionArticles(ServerPlayer player, UUID factionId) {
        MinecraftServer server = player.getServer();
        if (server == null || !player.isAlive()) {
            return;
        }
        FactionManager factions = FactionManager.get(player.serverLevel());
        Faction faction = factions.getFactionById(factionId).orElse(null);
        if (faction == null) {
            return;
        }
        List<NewsPayloads.ArticleEntry> articles = NewsManager.get(server).articles(factionId).stream()
                .limit(NewsPayloads.MAX_ARTICLE_ENTRIES)
                .map(article -> new NewsPayloads.ArticleEntry(
                        article.id(),
                        article.title(),
                        article.body(),
                        article.author(),
                        article.publishedAtMillis()
                ))
                .toList();
        PacketDistributor.sendToPlayer(player, new NewsPayloads.S2CNewsArticles(factionId, faction.name(), articles));
    }

    public static void onLogin(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        NewsManager news = NewsManager.get(server);
        news.ensurePlayer(player.getUUID());
        news.pruneMissing(FactionManager.get(server).factions().stream().map(Faction::id).toList());
        pushBadge(player);
    }

    public static void onLogout(UUID playerId) {
        LAST_ACTION_TICK.remove(playerId);
    }

    public static void pushBadge(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new NewsPayloads.S2CNewsBadge(NewsManager.get(server).unreadCount(player.getUUID())));
    }

    private static String sanitize(String value, int maxLength, boolean allowNewlines) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder builder = new StringBuilder(Math.min(normalized.length(), maxLength));
        for (int index = 0; index < normalized.length() && builder.length() < maxLength; index++) {
            char character = normalized.charAt(index);
            if (character == '\n') {
                if (allowNewlines) {
                    builder.append(character);
                } else {
                    builder.append(' ');
                }
            } else if (character >= ' ') {
                builder.append(character);
            }
        }
        return builder.toString().strip();
    }

    private NewsService() {
    }
}
