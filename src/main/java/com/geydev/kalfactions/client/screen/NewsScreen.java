package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.EmblemTextures;
import com.geydev.kalfactions.news.NewsPayloads;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

public final class NewsScreen extends Screen {
    private static final ResourceLocation PANEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/faction/panel.png");
    private static final int PANEL_WIDTH = 330;
    private static final int PANEL_HEIGHT = 220;
    private static final int CONTENT_LEFT = 28;
    private static final int CONTENT_RIGHT = 298;
    private static final int TEXT_DARK = 0xFF3F2A19;
    private static final int TEXT_MUTED = 0xFF5B452E;
    private static final int FACTION_ROWS = 4;
    private static final int FACTION_ROW_HEIGHT = 28;
    private static final int FACTION_LIST_TOP = 64;
    private static final int ARTICLE_ROWS = 5;
    private static final int ARTICLE_ROW_HEIGHT = 22;
    private static final int ARTICLE_LIST_TOP = 74;
    private static final int BODY_TOP = 92;
    private static final int BODY_BOTTOM = 182;
    private static final int LINE_HEIGHT = 10;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private List<NewsPayloads.NewsFactionEntry> factions;
    private UUID openedFactionId;
    private String openedFactionName = "";
    private List<NewsPayloads.ArticleEntry> articles;
    private NewsPayloads.ArticleEntry openedArticle;
    private List<FormattedCharSequence> bodyLines = List.of();
    private int factionScroll;
    private int articleScroll;
    private int bodyScroll;
    private int left;
    private int top;

    public NewsScreen() {
        super(Component.translatable("screen.kingdoms.news.title"));
    }

    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) {
            return;
        }
        minecraft.setScreen(new NewsScreen());
        PacketDistributor.sendToServer(NewsPayloads.C2SRequestNews.INSTANCE);
    }

    public static void handleFactions(NewsPayloads.S2CNewsFactions payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof NewsScreen screen) {
                screen.acceptFactions(payload);
            }
        });
    }

    public static void handleArticles(NewsPayloads.S2CNewsArticles payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof NewsScreen screen) {
                screen.acceptArticles(payload);
            }
        });
    }

    private void acceptFactions(NewsPayloads.S2CNewsFactions payload) {
        factions = payload.factions();
        factionScroll = Math.clamp(factionScroll, 0, maxFactionScroll());
        rebuildWidgets();
    }

    private void acceptArticles(NewsPayloads.S2CNewsArticles payload) {
        if (openedFactionId == null || !openedFactionId.equals(payload.factionId())) {
            return;
        }
        openedFactionName = payload.factionName();
        articles = payload.articles();
        articleScroll = Math.clamp(articleScroll, 0, maxArticleScroll());
        if (openedArticle != null) {
            openedArticle = articles.stream()
                    .filter(article -> article.id().equals(openedArticle.id()))
                    .findFirst()
                    .orElse(null);
            rebuildBodyLines();
        }
        rebuildWidgets();
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        if (openedFactionId != null) {
            addRenderableWidget(KingdomsButton.create(
                    Component.translatable("screen.kingdoms.back"),
                    button -> goBack(),
                    left + CONTENT_LEFT,
                    top + PANEL_HEIGHT - 25,
                    66,
                    20
            ));
        }
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("gui.done"),
                button -> onClose(),
                left + PANEL_WIDTH - 74,
                top + PANEL_HEIGHT - 25,
                66,
                20
        ));
        rebuildBodyLines();
    }

    private void goBack() {
        if (openedArticle != null) {
            openedArticle = null;
            bodyScroll = 0;
        } else {
            openedFactionId = null;
            openedFactionName = "";
            articles = null;
            articleScroll = 0;
        }
        rebuildWidgets();
    }

    private void rebuildBodyLines() {
        if (openedArticle == null || font == null) {
            bodyLines = List.of();
            return;
        }
        bodyLines = font.split(Component.literal(openedArticle.body()), CONTENT_RIGHT - CONTENT_LEFT);
        bodyScroll = Math.clamp(bodyScroll, 0, maxBodyScroll());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(PANEL_TEXTURE, left, top, 0.0F, 0.0F, PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, left + (PANEL_WIDTH - font.width(title)) / 2, top + 48, TEXT_DARK, false);
        if (openedArticle != null) {
            renderArticleView(graphics);
        } else if (openedFactionId != null) {
            renderArticleList(graphics, mouseX, mouseY);
        } else {
            renderFactionList(graphics, mouseX, mouseY);
        }
    }

    private void renderFactionList(GuiGraphics graphics, int mouseX, int mouseY) {
        if (factions == null) {
            Component loading = Component.translatable("screen.kingdoms.flist.loading");
            graphics.drawString(font, loading, left + (PANEL_WIDTH - font.width(loading)) / 2, top + 110, TEXT_MUTED, false);
            return;
        }
        if (factions.isEmpty()) {
            Component empty = Component.translatable("screen.kingdoms.news.empty");
            graphics.drawString(font, empty, left + (PANEL_WIDTH - font.width(empty)) / 2, top + 110, TEXT_MUTED, false);
            return;
        }
        int shown = Math.min(FACTION_ROWS, factions.size() - factionScroll);
        for (int index = 0; index < shown; index++) {
            NewsPayloads.NewsFactionEntry entry = factions.get(factionScroll + index);
            int rowLeft = left + CONTENT_LEFT;
            int rowRight = left + CONTENT_RIGHT;
            int rowTop = top + FACTION_LIST_TOP + index * FACTION_ROW_HEIGHT;
            boolean hovered = mouseX >= rowLeft && mouseX < rowRight
                    && mouseY >= rowTop && mouseY < rowTop + FACTION_ROW_HEIGHT - 2;
            graphics.fill(rowLeft, rowTop, rowRight, rowTop + FACTION_ROW_HEIGHT - 2, hovered ? 0x55C9A24C : 0x24A8783D);

            graphics.fill(rowLeft + 2, rowTop + 2, rowLeft + 24, rowTop + 24, 0xFF1A140C);
            EmblemTextures.Emblem emblem =
                    EmblemTextures.resolve(entry.factionId(), entry.emblem(), entry.emblemUrl(), entry.color());
            graphics.blit(emblem.texture(), rowLeft + 3, rowTop + 3, 20, 20,
                    0.0F, 0.0F, emblem.width(), emblem.height(), emblem.width(), emblem.height());

            Component count = Component.translatable("screen.kingdoms.news.article_count", entry.articleCount());
            int countX = rowRight - 6 - font.width(count);
            String name = font.plainSubstrByWidth(entry.name(), Math.max(20, countX - rowLeft - 34));
            graphics.drawString(font, name, rowLeft + 28, rowTop + 4, TEXT_DARK, false);
            graphics.drawString(font, count, countX, rowTop + 4, TEXT_MUTED, false);
            graphics.drawString(font, formatDate(entry.latestMillis()), rowLeft + 28, rowTop + 15, TEXT_MUTED, false);
        }
        if (factions.size() > FACTION_ROWS) {
            String pager = (factionScroll + 1) + "-" + (factionScroll + shown) + " / " + factions.size();
            graphics.drawString(font, pager, left + CONTENT_RIGHT - font.width(pager), top + 48, TEXT_MUTED, false);
        }
    }

    private void renderArticleList(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, openedFactionName, left + CONTENT_LEFT, top + 62, TEXT_DARK, false);
        if (articles == null) {
            Component loading = Component.translatable("screen.kingdoms.flist.loading");
            graphics.drawString(font, loading, left + (PANEL_WIDTH - font.width(loading)) / 2, top + 110, TEXT_MUTED, false);
            return;
        }
        if (articles.isEmpty()) {
            Component empty = Component.translatable("screen.kingdoms.news.no_articles");
            graphics.drawString(font, empty, left + (PANEL_WIDTH - font.width(empty)) / 2, top + 110, TEXT_MUTED, false);
            return;
        }
        int shown = Math.min(ARTICLE_ROWS, articles.size() - articleScroll);
        for (int index = 0; index < shown; index++) {
            NewsPayloads.ArticleEntry entry = articles.get(articleScroll + index);
            int rowLeft = left + CONTENT_LEFT;
            int rowRight = left + CONTENT_RIGHT;
            int rowTop = top + ARTICLE_LIST_TOP + index * ARTICLE_ROW_HEIGHT;
            boolean hovered = mouseX >= rowLeft && mouseX < rowRight
                    && mouseY >= rowTop && mouseY < rowTop + ARTICLE_ROW_HEIGHT - 2;
            graphics.fill(rowLeft, rowTop, rowRight, rowTop + ARTICLE_ROW_HEIGHT - 2, hovered ? 0x55C9A24C : 0x24A8783D);
            String date = formatDate(entry.publishedAtMillis());
            int dateX = rowRight - 6 - font.width(date);
            String clippedTitle = font.plainSubstrByWidth(entry.title(), Math.max(20, dateX - rowLeft - 12));
            graphics.drawString(font, clippedTitle, rowLeft + 6, rowTop + 6, TEXT_DARK, false);
            graphics.drawString(font, date, dateX, rowTop + 6, TEXT_MUTED, false);
        }
        if (articles.size() > ARTICLE_ROWS) {
            String pager = (articleScroll + 1) + "-" + (articleScroll + shown) + " / " + articles.size();
            graphics.drawString(font, pager, left + CONTENT_RIGHT - font.width(pager), top + 62, TEXT_MUTED, false);
        }
    }

    private void renderArticleView(GuiGraphics graphics) {
        String clippedTitle = font.plainSubstrByWidth(openedArticle.title(), CONTENT_RIGHT - CONTENT_LEFT);
        graphics.drawString(font, clippedTitle, left + CONTENT_LEFT, top + 62, TEXT_DARK, false);
        Component meta = Component.translatable(
                "screen.kingdoms.news.meta",
                openedFactionName,
                openedArticle.author(),
                formatDate(openedArticle.publishedAtMillis())
        );
        graphics.drawString(font, font.plainSubstrByWidth(meta.getString(), CONTENT_RIGHT - CONTENT_LEFT),
                left + CONTENT_LEFT, top + 76, TEXT_MUTED, false);

        int bodyTop = top + BODY_TOP;
        int bodyBottom = top + BODY_BOTTOM;
        graphics.enableScissor(left + CONTENT_LEFT, bodyTop, left + CONTENT_RIGHT, bodyBottom);
        int visible = visibleBodyLines();
        for (int index = 0; index < visible && bodyScroll + index < bodyLines.size(); index++) {
            graphics.drawString(
                    font,
                    bodyLines.get(bodyScroll + index),
                    left + CONTENT_LEFT,
                    bodyTop + index * LINE_HEIGHT,
                    TEXT_DARK,
                    false
            );
        }
        graphics.disableScissor();
        if (maxBodyScroll() > 0) {
            int trackLeft = left + CONTENT_RIGHT + 4;
            graphics.fill(trackLeft, bodyTop, trackLeft + 3, bodyBottom, 0x33000000);
            int trackHeight = bodyBottom - bodyTop;
            int thumbHeight = Math.max(12, trackHeight * visible / Math.max(1, bodyLines.size()));
            int travel = Math.max(1, trackHeight - thumbHeight);
            int thumbTop = bodyTop + Math.round(travel * (bodyScroll / (float) maxBodyScroll()));
            graphics.fill(trackLeft, thumbTop, trackLeft + 3, thumbTop + thumbHeight, 0xAA6B4A2B);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && openedArticle == null) {
            if (openedFactionId == null && factions != null && !factions.isEmpty()) {
                int shown = Math.min(FACTION_ROWS, factions.size() - factionScroll);
                for (int index = 0; index < shown; index++) {
                    int rowTop = top + FACTION_LIST_TOP + index * FACTION_ROW_HEIGHT;
                    if (mouseX >= left + CONTENT_LEFT && mouseX < left + CONTENT_RIGHT
                            && mouseY >= rowTop && mouseY < rowTop + FACTION_ROW_HEIGHT - 2) {
                        openFaction(factions.get(factionScroll + index));
                        return true;
                    }
                }
            } else if (openedFactionId != null && articles != null && !articles.isEmpty()) {
                int shown = Math.min(ARTICLE_ROWS, articles.size() - articleScroll);
                for (int index = 0; index < shown; index++) {
                    int rowTop = top + ARTICLE_LIST_TOP + index * ARTICLE_ROW_HEIGHT;
                    if (mouseX >= left + CONTENT_LEFT && mouseX < left + CONTENT_RIGHT
                            && mouseY >= rowTop && mouseY < rowTop + ARTICLE_ROW_HEIGHT - 2) {
                        openedArticle = articles.get(articleScroll + index);
                        bodyScroll = 0;
                        rebuildBodyLines();
                        rebuildWidgets();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void openFaction(NewsPayloads.NewsFactionEntry entry) {
        openedFactionId = entry.factionId();
        openedFactionName = entry.name();
        articles = null;
        articleScroll = 0;
        rebuildWidgets();
        PacketDistributor.sendToServer(new NewsPayloads.C2SRequestFactionNews(entry.factionId()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int delta = (int) Math.signum(scrollY);
        if (openedArticle != null) {
            int updated = Math.clamp(bodyScroll - delta, 0, maxBodyScroll());
            if (updated != bodyScroll) {
                bodyScroll = updated;
                return true;
            }
        } else if (openedFactionId != null && articles != null) {
            int updated = Math.clamp(articleScroll - delta, 0, maxArticleScroll());
            if (updated != articleScroll) {
                articleScroll = updated;
                return true;
            }
        } else if (factions != null) {
            int updated = Math.clamp(factionScroll - delta, 0, maxFactionScroll());
            if (updated != factionScroll) {
                factionScroll = updated;
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && openedFactionId != null) {
            goBack();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private int maxFactionScroll() {
        return factions == null ? 0 : Math.max(0, factions.size() - FACTION_ROWS);
    }

    private int maxArticleScroll() {
        return articles == null ? 0 : Math.max(0, articles.size() - ARTICLE_ROWS);
    }

    private int visibleBodyLines() {
        return Math.max(1, (BODY_BOTTOM - BODY_TOP) / LINE_HEIGHT);
    }

    private int maxBodyScroll() {
        return Math.max(0, bodyLines.size() - visibleBodyLines());
    }

    private static String formatDate(long millis) {
        if (millis <= 0L) {
            return "";
        }
        return DATE_FORMAT.format(Instant.ofEpochMilli(millis));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
