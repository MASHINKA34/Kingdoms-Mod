package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.ClientMusicPlayer;
import com.geydev.kalfactions.client.ClientMusicUpload;
import com.geydev.kalfactions.client.MusicClientSettings;
import com.geydev.kalfactions.music.MusicPayloads;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public final class MusicBlockScreen extends Screen {
    private static final ResourceLocation PANEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/faction/panel.png");
    private static final int PANEL_WIDTH = 330;
    private static final int PANEL_HEIGHT = 220;
    private static final int CONTENT_LEFT = 28;
    private static final int CONTENT_WIDTH = 274;
    private static final int TEXT_DARK = 0xFF3F2A19;
    private static final int TEXT_MUTED = 0xFF5B452E;
    private static final int LIST_TOP = 82;
    private static final int LIST_ROWS = 4;
    private static final int LIST_ROW_HEIGHT = 13;
    private static final int ROW_SELECTED = 0x60C9A24C;
    private static final int ROW_HOVER = 0x30000000;

    private MusicPayloads.S2COpenSpeaker data;
    private String selectedHash = "";
    private int scroll;
    private boolean confirmDelete;
    private int left;
    private int top;
    private KingdomsButton playButton;
    private KingdomsButton loopButton;
    private KingdomsButton redstoneButton;
    private KingdomsButton silenceButton;
    private KingdomsButton deleteButton;
    private SpeakerVolumeSlider speakerVolume;
    private RadiusSlider radiusSlider;
    private PersonalVolumeSlider personalVolume;

    private MusicBlockScreen(MusicPayloads.S2COpenSpeaker data) {
        super(Component.translatable("screen.kingdoms.music.title"));
        this.data = data;
        this.selectedHash = data.hash();
    }

    public static void handleOpen(MusicPayloads.S2COpenSpeaker payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof MusicBlockScreen screen && screen.data.pos().equals(payload.pos())) {
                screen.accept(payload);
            } else {
                minecraft.setScreen(new MusicBlockScreen(payload));
            }
        });
    }

    public static void handleUploadStatus(MusicPayloads.S2CUploadStatus payload) {
        ClientMusicUpload.acceptStatus(payload);
    }

    private void accept(MusicPayloads.S2COpenSpeaker payload) {
        this.data = payload;
        if (!payload.hash().isEmpty()) {
            this.selectedHash = payload.hash();
        } else if (tracks().stream().noneMatch(entry -> entry.hash().equals(selectedHash))) {
            this.selectedHash = "";
        }
        this.confirmDelete = false;
        this.scroll = Math.clamp(scroll, 0, maxScroll());
        rebuildWidgets();
    }

    private List<MusicPayloads.TrackEntry> tracks() {
        return data.tracks();
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        boolean editable = data.canEdit();

        speakerVolume = addRenderableWidget(new SpeakerVolumeSlider(
                left + CONTENT_LEFT, top + 136, 130, 16, data.volume()));
        speakerVolume.active = editable;
        radiusSlider = addRenderableWidget(new RadiusSlider(
                left + CONTENT_LEFT + 144, top + 136, 130, 16, data.radius(), data.maxRadius()));
        radiusSlider.active = editable;

        loopButton = addRenderableWidget(KingdomsButton.create(
                loopMessage(),
                button -> {
                    sendUpdate(selectedHash, !data.loop(), data.playing(), data.redstone());
                },
                left + CONTENT_LEFT, top + 156, 86, 18));
        loopButton.active = editable;
        redstoneButton = addRenderableWidget(KingdomsButton.create(
                redstoneMessage(),
                button -> sendUpdate(selectedHash, data.loop(), data.playing(), !data.redstone()),
                left + CONTENT_LEFT + 94, top + 156, 86, 18));
        redstoneButton.active = editable;
        silenceButton = addRenderableWidget(KingdomsButton.create(
                silenceMessage(),
                button -> toggleSilence(),
                left + CONTENT_LEFT + 188, top + 156, 86, 18));

        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.music.upload"),
                button -> ClientMusicUpload.pickFile(data.pos(), data.maxTrackBytes()),
                left + CONTENT_LEFT, top + 176, 118, 18)).active = editable && !ClientMusicUpload.busy();
        playButton = addRenderableWidget(KingdomsButton.create(
                playMessage(),
                button -> sendUpdate(selectedHash, data.loop(), !data.playing(), data.redstone()),
                left + CONTENT_LEFT + 126, top + 176, 64, 18));
        playButton.active = editable && !selectedHash.isEmpty();
        deleteButton = addRenderableWidget(KingdomsButton.create(
                deleteMessage(),
                button -> deleteSelected(),
                left + CONTENT_LEFT + 198, top + 176, 76, 18));
        deleteButton.active = editable && !selectedHash.isEmpty();

        personalVolume = addRenderableWidget(new PersonalVolumeSlider(
                left + CONTENT_LEFT, top + 196, 196, 16, MusicClientSettings.volume()));
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.music.close"),
                button -> onClose(),
                left + CONTENT_LEFT + 204, top + 196, 70, 18));
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
        Component current = data.trackName().isEmpty()
                ? Component.translatable("screen.kingdoms.music.no_track")
                : Component.translatable("screen.kingdoms.music.current", data.trackName());
        graphics.drawString(font, current, left + CONTENT_LEFT, top + 62, TEXT_DARK, false);
        graphics.drawString(font, statusLine(), left + CONTENT_LEFT, top + 72, TEXT_MUTED, false);
        renderTrackList(graphics, mouseX, mouseY);
        refreshMessages();
    }

    private void renderTrackList(GuiGraphics graphics, int mouseX, int mouseY) {
        List<MusicPayloads.TrackEntry> tracks = tracks();
        if (tracks.isEmpty()) {
            Component empty = Component.translatable("screen.kingdoms.music.empty");
            graphics.drawString(font, empty, left + CONTENT_LEFT, top + LIST_TOP + 8, TEXT_MUTED, false);
            return;
        }
        int shown = Math.min(LIST_ROWS, tracks.size() - scroll);
        for (int index = 0; index < shown; index++) {
            MusicPayloads.TrackEntry entry = tracks.get(scroll + index);
            int rowTop = top + LIST_TOP + index * LIST_ROW_HEIGHT;
            int rowLeft = left + CONTENT_LEFT;
            boolean hovered = mouseX >= rowLeft && mouseX < rowLeft + CONTENT_WIDTH
                    && mouseY >= rowTop && mouseY < rowTop + LIST_ROW_HEIGHT;
            if (entry.hash().equals(selectedHash)) {
                graphics.fill(rowLeft, rowTop, rowLeft + CONTENT_WIDTH, rowTop + LIST_ROW_HEIGHT, ROW_SELECTED);
            } else if (hovered) {
                graphics.fill(rowLeft, rowTop, rowLeft + CONTENT_WIDTH, rowTop + LIST_ROW_HEIGHT, ROW_HOVER);
            }
            graphics.drawString(font, entry.name(), rowLeft + 3, rowTop + 3, TEXT_DARK, false);
            Component size = Component.translatable(
                    "screen.kingdoms.music.size", entry.size() / 1024L);
            graphics.drawString(font, size, rowLeft + CONTENT_WIDTH - font.width(size) - 3, rowTop + 3,
                    TEXT_MUTED, false);
        }
        if (tracks.size() > LIST_ROWS) {
            Component page = Component.translatable(
                    "screen.kingdoms.music.page", scroll + shown, tracks.size());
            graphics.drawString(font, page, left + CONTENT_LEFT + CONTENT_WIDTH - font.width(page),
                    top + LIST_TOP + LIST_ROWS * LIST_ROW_HEIGHT + 2, TEXT_MUTED, false);
        }
    }

    private Component statusLine() {
        String key = ClientMusicUpload.statusKey();
        if (!key.isEmpty()) {
            if (ClientMusicUpload.busy()) {
                return Component.translatable("screen.kingdoms.music.progress",
                        Component.translatable(key), ClientMusicUpload.percent());
            }
            return Component.translatable(key);
        }
        if (!selectedHash.isEmpty() && !ClientMusicPlayer.isCached(selectedHash)) {
            return Component.translatable("screen.kingdoms.music.not_cached");
        }
        if (!data.canEdit()) {
            return Component.translatable("screen.kingdoms.music.read_only");
        }
        return Component.translatable("screen.kingdoms.music.hint");
    }

    private void refreshMessages() {
        if (playButton != null) {
            playButton.setMessage(playMessage());
            playButton.active = data.canEdit() && !selectedHash.isEmpty();
        }
        if (loopButton != null) {
            loopButton.setMessage(loopMessage());
        }
        if (redstoneButton != null) {
            redstoneButton.setMessage(redstoneMessage());
        }
        if (silenceButton != null) {
            silenceButton.setMessage(silenceMessage());
        }
        if (deleteButton != null) {
            deleteButton.setMessage(deleteMessage());
            deleteButton.active = data.canEdit() && !selectedHash.isEmpty();
        }
    }

    private Component playMessage() {
        return Component.translatable(data.playing()
                ? "screen.kingdoms.music.stop"
                : "screen.kingdoms.music.play");
    }

    private Component loopMessage() {
        return Component.translatable(data.loop()
                ? "screen.kingdoms.music.loop_on"
                : "screen.kingdoms.music.loop_off");
    }

    private Component redstoneMessage() {
        return Component.translatable(data.redstone()
                ? "screen.kingdoms.music.redstone_on"
                : "screen.kingdoms.music.redstone_off");
    }

    private Component silenceMessage() {
        return Component.translatable(isSilenced()
                ? "screen.kingdoms.music.silenced_on"
                : "screen.kingdoms.music.silenced_off");
    }

    private Component deleteMessage() {
        return Component.translatable(confirmDelete
                ? "screen.kingdoms.music.delete_confirm"
                : "screen.kingdoms.music.delete");
    }

    private boolean isSilenced() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null
                && MusicClientSettings.isSilenced(minecraft.level.dimension(), data.pos());
    }

    private void toggleSilence() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        boolean silenced = !isSilenced();
        MusicClientSettings.setSilenced(minecraft.level.dimension(), data.pos(), silenced);
        ClientMusicPlayer.refreshSpeaker(data.pos());
    }

    private void deleteSelected() {
        if (selectedHash.isEmpty()) {
            return;
        }
        if (!confirmDelete) {
            confirmDelete = true;
            return;
        }
        confirmDelete = false;
        PacketDistributor.sendToServer(new MusicPayloads.C2SDeleteTrack(data.pos(), selectedHash));
        selectedHash = "";
    }

    private void sendUpdate(String hash, boolean loop, boolean playing, boolean redstone) {
        PacketDistributor.sendToServer(new MusicPayloads.C2SUpdateSpeaker(
                data.pos(),
                hash,
                speakerVolume == null ? data.volume() : (float) speakerVolume.currentValue(),
                radiusSlider == null ? data.radius() : radiusSlider.currentRadius(),
                loop,
                playing,
                redstone
        ));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<MusicPayloads.TrackEntry> tracks = tracks();
        int rowLeft = left + CONTENT_LEFT;
        if (mouseX >= rowLeft && mouseX < rowLeft + CONTENT_WIDTH) {
            int shown = Math.min(LIST_ROWS, tracks.size() - scroll);
            for (int index = 0; index < shown; index++) {
                int rowTop = top + LIST_TOP + index * LIST_ROW_HEIGHT;
                if (mouseY >= rowTop && mouseY < rowTop + LIST_ROW_HEIGHT) {
                    selectedHash = tracks.get(scroll + index).hash();
                    confirmDelete = false;
                    if (data.canEdit()) {
                        sendUpdate(selectedHash, data.loop(), data.playing(), data.redstone());
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int updated = Math.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
        if (updated != scroll) {
            scroll = updated;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int maxScroll() {
        return Math.max(0, tracks().size() - LIST_ROWS);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private final class SpeakerVolumeSlider extends AbstractSliderButton {
        private SpeakerVolumeSlider(int x, int y, int width, int height, float initial) {
            super(x, y, width, height, Component.empty(), Math.clamp(initial, 0.0F, 1.0F));
            updateMessage();
        }

        private double currentValue() {
            return value;
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("screen.kingdoms.music.volume", (int) Math.round(value * 100.0D)));
        }

        @Override
        protected void applyValue() {
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            super.onRelease(mouseX, mouseY);
            sendUpdate(selectedHash, data.loop(), data.playing(), data.redstone());
        }
    }

    private final class RadiusSlider extends AbstractSliderButton {
        private final int maxRadius;

        private RadiusSlider(int x, int y, int width, int height, int initial, int maxRadius) {
            super(x, y, width, height, Component.empty(),
                    maxRadius <= 1 ? 1.0D : (double) (Math.clamp(initial, 1, maxRadius) - 1) / (maxRadius - 1));
            this.maxRadius = Math.max(1, maxRadius);
            updateMessage();
        }

        private int currentRadius() {
            return 1 + (int) Math.round(value * (maxRadius - 1));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("screen.kingdoms.music.radius", currentRadius()));
        }

        @Override
        protected void applyValue() {
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            super.onRelease(mouseX, mouseY);
            sendUpdate(selectedHash, data.loop(), data.playing(), data.redstone());
        }
    }

    private final class PersonalVolumeSlider extends AbstractSliderButton {
        private PersonalVolumeSlider(int x, int y, int width, int height, float initial) {
            super(x, y, width, height, Component.empty(), Math.clamp(initial, 0.0F, 1.0F));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(
                    "screen.kingdoms.music.personal_volume", (int) Math.round(value * 100.0D)));
        }

        @Override
        protected void applyValue() {
            MusicClientSettings.setVolume((float) value);
        }
    }
}
