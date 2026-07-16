package com.geydev.kalfactions.news;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class NewsManager extends SavedData {
    public static final String DATA_NAME = "kingdoms_news";
    public static final Factory<NewsManager> FACTORY = new Factory<>(NewsManager::new, NewsManager::load);

    public static final int MAX_TITLE_LENGTH = 48;
    public static final int MAX_BODY_LENGTH = 1000;
    public static final int MAX_AUTHOR_LENGTH = 32;

    private static final String TAG_FACTIONS = "factions";
    private static final String TAG_FACTION_ID = "id";
    private static final String TAG_LAST_PUBLISH = "lastPublish";
    private static final String TAG_ARTICLES = "articles";
    private static final String TAG_ARTICLE_ID = "id";
    private static final String TAG_TITLE = "title";
    private static final String TAG_BODY = "body";
    private static final String TAG_AUTHOR = "author";
    private static final String TAG_PUBLISHED = "publishedAt";
    private static final String TAG_READERS = "readers";
    private static final String TAG_READER_ID = "id";
    private static final String TAG_LAST_SEEN = "lastSeen";

    public record Article(UUID id, String title, String body, String author, long publishedAtMillis) {
        public Article {
            Objects.requireNonNull(id, "id");
            title = limit(title, MAX_TITLE_LENGTH);
            body = limit(body, MAX_BODY_LENGTH);
            author = limit(author, MAX_AUTHOR_LENGTH);
            publishedAtMillis = Math.max(0L, publishedAtMillis);
        }
    }

    private static final class FactionNews {
        private final List<Article> articles = new ArrayList<>();
        private long lastPublishMillis;
    }

    private final Map<UUID, FactionNews> factions = new LinkedHashMap<>();
    private final Map<UUID, Long> lastSeenByPlayer = new LinkedHashMap<>();

    public static NewsManager get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public synchronized Article publish(UUID factionId, String title, String body, String author, long nowMillis, int maxArticles) {
        FactionNews news = factions.computeIfAbsent(factionId, ignored -> new FactionNews());
        Article article = new Article(UUID.randomUUID(), title, body, author, nowMillis);
        news.articles.add(article);
        while (news.articles.size() > Math.max(1, maxArticles)) {
            news.articles.removeFirst();
        }
        news.lastPublishMillis = nowMillis;
        setDirty();
        return article;
    }

    public synchronized long lastPublishMillis(UUID factionId) {
        FactionNews news = factions.get(factionId);
        return news == null ? 0L : news.lastPublishMillis;
    }

    public synchronized long latestArticleMillis(UUID factionId) {
        FactionNews news = factions.get(factionId);
        if (news == null || news.articles.isEmpty()) {
            return 0L;
        }
        return news.articles.getLast().publishedAtMillis();
    }

    public synchronized List<Article> articles(UUID factionId) {
        FactionNews news = factions.get(factionId);
        if (news == null || news.articles.isEmpty()) {
            return List.of();
        }
        return news.articles.stream()
            .sorted(Comparator.comparingLong(Article::publishedAtMillis).reversed())
            .toList();
    }

    public synchronized Set<UUID> factionIdsWithNews() {
        Set<UUID> ids = new java.util.LinkedHashSet<>();
        for (Map.Entry<UUID, FactionNews> entry : factions.entrySet()) {
            if (!entry.getValue().articles.isEmpty()) {
                ids.add(entry.getKey());
            }
        }
        return ids;
    }

    public synchronized int articleCount(UUID factionId) {
        FactionNews news = factions.get(factionId);
        return news == null ? 0 : news.articles.size();
    }

    public synchronized void pruneMissing(Collection<UUID> existingFactionIds) {
        if (factions.keySet().retainAll(Set.copyOf(existingFactionIds))) {
            setDirty();
        }
    }

    public synchronized void removeFaction(UUID factionId) {
        if (factions.remove(factionId) != null) {
            setDirty();
        }
    }

    public synchronized void ensurePlayer(UUID playerId) {
        if (lastSeenByPlayer.putIfAbsent(playerId, 0L) == null) {
            setDirty();
        }
    }

    public synchronized void markSeen(UUID playerId, long millis) {
        Long previous = lastSeenByPlayer.put(playerId, millis);
        if (previous == null || previous != millis) {
            setDirty();
        }
    }

    public synchronized long lastSeenMillis(UUID playerId) {
        return lastSeenByPlayer.getOrDefault(playerId, 0L);
    }

    public synchronized int unreadCount(UUID playerId) {
        long lastSeen = lastSeenMillis(playerId);
        int unread = 0;
        for (FactionNews news : factions.values()) {
            for (Article article : news.articles) {
                if (article.publishedAtMillis() > lastSeen) {
                    unread++;
                }
            }
        }
        return unread;
    }

    public synchronized Set<UUID> knownPlayerIds() {
        return Set.copyOf(lastSeenByPlayer.keySet());
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag factionsTag = new ListTag();
        factions.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
            .forEach(entry -> {
                FactionNews news = entry.getValue();
                CompoundTag factionTag = new CompoundTag();
                factionTag.putUUID(TAG_FACTION_ID, entry.getKey());
                factionTag.putLong(TAG_LAST_PUBLISH, news.lastPublishMillis);
                ListTag articlesTag = new ListTag();
                for (Article article : news.articles) {
                    CompoundTag articleTag = new CompoundTag();
                    articleTag.putUUID(TAG_ARTICLE_ID, article.id());
                    articleTag.putString(TAG_TITLE, article.title());
                    articleTag.putString(TAG_BODY, article.body());
                    articleTag.putString(TAG_AUTHOR, article.author());
                    articleTag.putLong(TAG_PUBLISHED, article.publishedAtMillis());
                    articlesTag.add(articleTag);
                }
                factionTag.put(TAG_ARTICLES, articlesTag);
                factionsTag.add(factionTag);
            });
        tag.put(TAG_FACTIONS, factionsTag);

        ListTag readersTag = new ListTag();
        lastSeenByPlayer.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
            .forEach(entry -> {
                CompoundTag readerTag = new CompoundTag();
                readerTag.putUUID(TAG_READER_ID, entry.getKey());
                readerTag.putLong(TAG_LAST_SEEN, entry.getValue());
                readersTag.add(readerTag);
            });
        tag.put(TAG_READERS, readersTag);
        return tag;
    }

    private static NewsManager load(CompoundTag tag, HolderLookup.Provider registries) {
        NewsManager manager = new NewsManager();
        ListTag factionsTag = tag.getList(TAG_FACTIONS, Tag.TAG_COMPOUND);
        for (int index = 0; index < factionsTag.size(); index++) {
            CompoundTag factionTag = factionsTag.getCompound(index);
            if (!factionTag.hasUUID(TAG_FACTION_ID)) {
                continue;
            }
            FactionNews news = new FactionNews();
            news.lastPublishMillis = Math.max(0L, factionTag.getLong(TAG_LAST_PUBLISH));
            ListTag articlesTag = factionTag.getList(TAG_ARTICLES, Tag.TAG_COMPOUND);
            for (int articleIndex = 0; articleIndex < articlesTag.size(); articleIndex++) {
                CompoundTag articleTag = articlesTag.getCompound(articleIndex);
                if (!articleTag.hasUUID(TAG_ARTICLE_ID)) {
                    continue;
                }
                news.articles.add(new Article(
                    articleTag.getUUID(TAG_ARTICLE_ID),
                    articleTag.getString(TAG_TITLE),
                    articleTag.getString(TAG_BODY),
                    articleTag.getString(TAG_AUTHOR),
                    articleTag.getLong(TAG_PUBLISHED)
                ));
            }
            manager.factions.put(factionTag.getUUID(TAG_FACTION_ID), news);
        }
        ListTag readersTag = tag.getList(TAG_READERS, Tag.TAG_COMPOUND);
        for (int index = 0; index < readersTag.size(); index++) {
            CompoundTag readerTag = readersTag.getCompound(index);
            if (readerTag.hasUUID(TAG_READER_ID)) {
                manager.lastSeenByPlayer.put(readerTag.getUUID(TAG_READER_ID), Math.max(0L, readerTag.getLong(TAG_LAST_SEEN)));
            }
        }
        return manager;
    }

    private static String limit(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
