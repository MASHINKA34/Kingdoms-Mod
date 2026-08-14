package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.integration.xaero.archive.ArchiveHashing;
import com.geydev.kalfactions.music.MusicLimits;
import com.geydev.kalfactions.music.MusicPayloads;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

@EventBusSubscriber(modid = KalFactions.MOD_ID, value = Dist.CLIENT)
public final class ClientMusicUpload {
    private static final int CHUNKS_PER_TICK = 4;

    private static UUID sessionId;
    private static BlockPos speakerPos;
    private static byte[] data;
    private static int nextIndex;
    private static int sentBytes;
    private static boolean sending;
    private static boolean picking;
    private static long serverReceived;
    private static long serverTotal;
    private static String statusKey = "";
    private static boolean failed;

    public static synchronized boolean busy() {
        return picking || data != null;
    }

    public static synchronized String statusKey() {
        return statusKey;
    }

    public static synchronized boolean failed() {
        return failed;
    }

    public static synchronized int percent() {
        if (data == null || data.length == 0) {
            return serverTotal > 0L ? (int) (serverReceived * 100L / serverTotal) : 0;
        }
        return (int) ((long) sentBytes * 100L / data.length);
    }

    public static void pickFile(BlockPos pos, int maxBytes) {
        synchronized (ClientMusicUpload.class) {
            if (busy()) {
                return;
            }
            picking = true;
            statusKey = "screen.kingdoms.music.status.picking";
            failed = false;
        }
        CompletableFuture.runAsync(() -> {
            String path;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(1);
                filters.put(stack.UTF8("*.ogg"));
                filters.flip();
                path = TinyFileDialogs.tinyfd_openFileDialog(
                        "Ogg Vorbis", "", filters, "Ogg Vorbis (*.ogg)", false);
            }
            if (path == null) {
                Minecraft.getInstance().execute(() -> reset(""));
                return;
            }
            try {
                Path file = Path.of(path);
                long size = Files.size(file);
                if (size <= 0L || size > maxBytes) {
                    Minecraft.getInstance().execute(() -> reset("screen.kingdoms.music.status.too_large"));
                    return;
                }
                byte[] bytes = Files.readAllBytes(file);
                if (!MusicLimits.hasOggSignature(bytes)) {
                    Minecraft.getInstance().execute(() -> reset("screen.kingdoms.music.status.not_ogg"));
                    return;
                }
                String name = trackName(file);
                String checksum = ArchiveHashing.sha256(bytes);
                Minecraft.getInstance().execute(() -> begin(pos, bytes, name, checksum));
            } catch (IOException | RuntimeException exception) {
                KalFactions.LOGGER.warn("Failed to read music file", exception);
                Minecraft.getInstance().execute(() -> reset("screen.kingdoms.music.status.read_failed"));
            }
        }, Util.ioPool());
    }

    public static synchronized void acceptStatus(MusicPayloads.S2CUploadStatus payload) {
        if (sessionId == null || !sessionId.equals(payload.sessionId())) {
            if (payload.failed()) {
                statusKey = payload.messageKey();
                failed = true;
            }
            return;
        }
        statusKey = payload.messageKey();
        serverReceived = payload.received();
        serverTotal = payload.total();
        if (payload.failed()) {
            failed = true;
            clearSession();
            return;
        }
        if (payload.done()) {
            failed = false;
            clearSession();
            return;
        }
        sending = true;
    }

    public static synchronized void cancel() {
        if (sessionId != null) {
            PacketDistributor.sendToServer(new MusicPayloads.C2SCancelUpload(sessionId));
        }
        clearSession();
        picking = false;
        statusKey = "";
        failed = false;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        synchronized (ClientMusicUpload.class) {
            if (!sending || data == null || sessionId == null) {
                return;
            }
            for (int count = 0; count < CHUNKS_PER_TICK && sentBytes < data.length; count++) {
                int length = Math.min(MusicLimits.CHUNK_SIZE, data.length - sentBytes);
                byte[] chunk = new byte[length];
                System.arraycopy(data, sentBytes, chunk, 0, length);
                PacketDistributor.sendToServer(new MusicPayloads.C2SUploadChunk(sessionId, nextIndex, chunk));
                sentBytes += length;
                nextIndex++;
            }
            if (sentBytes >= data.length) {
                sending = false;
            }
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        synchronized (ClientMusicUpload.class) {
            clearSession();
            picking = false;
            statusKey = "";
            failed = false;
        }
    }

    private static synchronized void begin(BlockPos pos, byte[] bytes, String name, String checksum) {
        picking = false;
        sessionId = UUID.randomUUID();
        speakerPos = pos.immutable();
        data = bytes;
        nextIndex = 0;
        sentBytes = 0;
        sending = false;
        serverReceived = 0L;
        serverTotal = bytes.length;
        failed = false;
        statusKey = "screen.kingdoms.music.status.starting";
        PacketDistributor.sendToServer(
                new MusicPayloads.C2SBeginUpload(sessionId, speakerPos, name, bytes.length, checksum));
    }

    private static synchronized void reset(String message) {
        clearSession();
        picking = false;
        statusKey = message;
        failed = !message.isEmpty();
    }

    private static synchronized void clearSession() {
        sessionId = null;
        speakerPos = null;
        data = null;
        nextIndex = 0;
        sentBytes = 0;
        sending = false;
    }

    private static String trackName(Path file) {
        String fileName = file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String sanitized = MusicLimits.sanitizeName(base);
        return sanitized.isEmpty() ? "track" : sanitized;
    }

    private ClientMusicUpload() {
    }
}
