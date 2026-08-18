package com.geydev.kalfactions.science;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class ScienceInputs extends SimplePreparableReloadListener<Map<Item, ScienceInputs.Entry>> {
    public static final ResourceLocation FILE =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "science_inputs.json");
    public static final int MAX_SECONDS = 86400;

    private static final ScienceInputs INSTANCE = new ScienceInputs();
    private static final String TAG_REPLACE = "replace";
    private static final String TAG_INPUTS = "inputs";
    private static final String TAG_SCIENCE = "science";
    private static final String TAG_SECONDS = "seconds";

    private static volatile Map<Item, Entry> entries = Map.of();

    private ScienceInputs() {
    }

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    public static Entry entry(ItemStack stack) {
        return stack.isEmpty() ? null : entries.get(stack.getItem());
    }

    public static boolean accepts(ItemStack stack) {
        return entry(stack) != null;
    }

    public static Map<Item, Entry> all() {
        return entries;
    }

    public static void install(Map<Item, Entry> replacement) {
        entries = Map.copyOf(replacement);
    }

    public static int defaultSeconds() {
        return Math.clamp(ModConfigSpec.RESEARCH_BENCH_SECONDS_PER_ITEM.getAsInt(), 1, MAX_SECONDS);
    }

    @Override
    protected Map<Item, Entry> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<Item, Entry> loaded = new LinkedHashMap<>();
        List<Resource> stack;
        try {
            stack = manager.getResourceStack(FILE);
        } catch (RuntimeException exception) {
            KalFactions.LOGGER.error("Could not read {}", FILE, exception);
            return Map.of();
        }
        for (Resource resource : stack) {
            try (BufferedReader reader = resource.openAsReader()) {
                JsonObject root = GsonHelper.parse(reader);
                if (GsonHelper.getAsBoolean(root, TAG_REPLACE, false)) {
                    loaded.clear();
                }
                readInputs(loaded, GsonHelper.getAsJsonObject(root, TAG_INPUTS), resource.sourcePackId());
            } catch (IOException | RuntimeException exception) {
                KalFactions.LOGGER.error("Rejected science inputs from pack {}", resource.sourcePackId(), exception);
            }
        }
        return Map.copyOf(loaded);
    }

    @Override
    protected void apply(Map<Item, Entry> prepared, ResourceManager manager, ProfilerFiller profiler) {
        entries = prepared;
        KalFactions.LOGGER.info("Loaded {} research bench science inputs", prepared.size());
    }

    private static void readInputs(Map<Item, Entry> loaded, JsonObject inputs, String packId) {
        for (Map.Entry<String, JsonElement> input : inputs.entrySet()) {
            try {
                Item item = item(input.getKey());
                loaded.put(item, parseEntry(input.getKey(), input.getValue()));
            } catch (RuntimeException exception) {
                KalFactions.LOGGER.error(
                        "Rejected science input {} from pack {}: {}",
                        input.getKey(),
                        packId,
                        exception.getMessage()
                );
            }
        }
    }

    private static Item item(String id) {
        ResourceLocation itemId = ResourceLocation.tryParse(id);
        if (itemId == null) {
            throw new IllegalArgumentException("not an item id");
        }
        return BuiltInRegistries.ITEM.getOptional(itemId)
                .orElseThrow(() -> new IllegalArgumentException("unknown item"));
    }

    private static Entry parseEntry(String id, JsonElement element) {
        if (element.isJsonPrimitive()) {
            return new Entry(science(GsonHelper.convertToLong(element, id)), 0);
        }
        JsonObject object = GsonHelper.convertToJsonObject(element, id);
        long science = science(GsonHelper.getAsLong(object, TAG_SCIENCE));
        int seconds = GsonHelper.getAsInt(object, TAG_SECONDS, 0);
        if (seconds < 0 || seconds > MAX_SECONDS) {
            throw new IllegalArgumentException("seconds must be between 1 and " + MAX_SECONDS);
        }
        return new Entry(science, seconds);
    }

    private static long science(long value) {
        if (value <= 0L) {
            throw new IllegalArgumentException("science must be positive");
        }
        return value;
    }

    public record Entry(long science, int secondsOverride) {
        public int seconds() {
            return secondsOverride > 0 ? secondsOverride : defaultSeconds();
        }

        public long intervalMillis() {
            return seconds() * 1000L;
        }
    }
}
