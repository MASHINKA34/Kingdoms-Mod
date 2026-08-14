package com.geydev.kalfactions.dungeon;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ChestTemplateSerializationTest {
    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrap() {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = VanillaRegistries.createLookup();
    }

    @Test
    void enchantedAndRenamedItemsSurviveTheRoundTrip() {
        ChestTemplate template = template("Клад", sword(), new ItemStack(Items.DIAMOND));

        ChestTemplate restored = ChestTemplate
                .load(template.save(registries), registries)
                .orElseThrow();

        assertEquals(template.id(), restored.id());
        assertEquals("Клад", restored.name());
        assertEquals(template.author(), restored.author());
        assertEquals(template.createdAt(), restored.createdAt());
        assertEquals(template.cooldownHours(), restored.cooldownHours());
        assertTrue(ItemStack.matches(template.entry(0).stack(), restored.entry(0).stack()));
        assertEquals(
                "Меч короля",
                restored.entry(0).stack().get(DataComponents.CUSTOM_NAME).getString()
        );
        assertEquals(
                4,
                restored.entry(0).stack().get(DataComponents.ENCHANTMENTS).getLevel(sharpness())
        );
        assertEquals(37, restored.entry(0).chance());
        assertEquals(2, restored.entry(0).minCount());
        assertEquals(9, restored.entry(0).maxCount());
        assertTrue(ItemStack.matches(template.entry(1).stack(), restored.entry(1).stack()));
        assertArrayEquals(bytes(template), bytes(restored));
    }

    @Test
    void templateKeepsExactlyTwentySevenSlots() {
        ChestTemplate template = new ChestTemplate(
                UUID.randomUUID(),
                "Короткий",
                "Автор",
                1L,
                -1,
                List.of(new ChestTemplate.Entry(new ItemStack(Items.DIAMOND), 100, 1, 1))
        );

        assertEquals(ChestTemplate.SIZE, template.entries().size());
        assertEquals(1, template.filledSlots());
        assertTrue(template.entry(26).stack().isEmpty());
    }

    @Test
    void entryClampsChanceAndCountRange() {
        ChestTemplate.Entry entry = new ChestTemplate.Entry(new ItemStack(Items.DIAMOND), 500, 9, 2);

        assertEquals(100, entry.chance());
        assertEquals(2, entry.minCount());
        assertEquals(9, entry.maxCount());
        assertEquals(1, new ChestTemplate.Entry(ItemStack.EMPTY, -5, 0, 999).minCount());
        assertEquals(64, new ChestTemplate.Entry(ItemStack.EMPTY, -5, 0, 999).maxCount());
        assertEquals(0, new ChestTemplate.Entry(ItemStack.EMPTY, -5, 0, 999).chance());
    }

    @Test
    void emptyTemplateIsNeverStored() {
        ChestTemplateManager manager = new ChestTemplateManager();

        ChestTemplateManager.SaveResult result = manager.put(
                new ChestTemplate(UUID.randomUUID(), "Пустой", "Автор", 1L, -1, List.of()),
                false,
                new ChestTemplateManager.Limits(8, 64 * 1024L),
                registries
        );

        assertEquals(ChestTemplateManager.Reason.TEMPLATE_EMPTY, result.reason());
        assertEquals(0, manager.count());
    }

    @Test
    void nameCollisionNeedsOverwriteAndKeepsTheIdentity() {
        ChestTemplateManager manager = new ChestTemplateManager();
        ChestTemplateManager.Limits limits = new ChestTemplateManager.Limits(8, 64 * 1024L);
        ChestTemplate first = template("Клад", sword(), ItemStack.EMPTY);
        assertTrue(manager.put(first, false, limits, registries).successful());

        ChestTemplate second = template("клад", new ItemStack(Items.DIRT), ItemStack.EMPTY);
        assertEquals(
                ChestTemplateManager.Reason.NAME_TAKEN,
                manager.put(second, false, limits, registries).reason()
        );

        ChestTemplateManager.SaveResult overwritten = manager.put(second, true, limits, registries);

        assertTrue(overwritten.successful());
        assertEquals(first.id(), overwritten.template().id());
        assertEquals(1, manager.count());
        assertTrue(manager.byId(first.id()).orElseThrow().entry(0).stack().is(Items.DIRT));
    }

    @Test
    void templateCountAndTotalSizeAreCapped() {
        ChestTemplateManager manager = new ChestTemplateManager();
        ChestTemplateManager.Limits limits = new ChestTemplateManager.Limits(2, 64 * 1024L);
        assertTrue(manager.put(template("Первый", sword(), ItemStack.EMPTY), false, limits, registries).successful());
        assertTrue(manager.put(template("Второй", sword(), ItemStack.EMPTY), false, limits, registries).successful());

        assertEquals(
                ChestTemplateManager.Reason.TOO_MANY,
                manager.put(template("Третий", sword(), ItemStack.EMPTY), false, limits, registries).reason()
        );

        ChestTemplateManager tight = new ChestTemplateManager();
        assertEquals(
                ChestTemplateManager.Reason.TOO_LARGE,
                tight.put(
                        template("Большой", sword(), ItemStack.EMPTY),
                        false,
                        new ChestTemplateManager.Limits(8, 16L),
                        registries
                ).reason()
        );
        assertEquals(0, tight.count());
    }

    @Test
    void storedTemplatesSurviveTheSavedDataRoundTrip() {
        ChestTemplateManager manager = new ChestTemplateManager();
        ChestTemplate template = template("Клад", sword(), new ItemStack(Items.DIAMOND, 1));
        assertTrue(manager.put(
                template,
                false,
                new ChestTemplateManager.Limits(8, 64 * 1024L),
                registries
        ).successful());

        ChestTemplateManager reloaded = ChestTemplateManager.load(
                manager.save(new CompoundTag(), registries),
                registries
        );
        ChestTemplate restored = reloaded.byName("Клад").orElse(null);

        assertNotNull(restored);
        assertEquals(1, reloaded.count());
        assertTrue(ItemStack.matches(template.entry(0).stack(), restored.entry(0).stack()));
        assertEquals(12, restored.cooldownHours());
        assertEquals(template.id(), restored.id());
    }

    @Test
    void namesAreNormalizedAndCapped() {
        assertEquals("Клад севера", ChestTemplate.normalizeName("  Клад   севера \n"));
        assertEquals("", ChestTemplate.normalizeName(null));
        assertEquals(
                ChestTemplate.MAX_NAME_LENGTH,
                ChestTemplate.normalizeName("x".repeat(80)).length()
        );
    }

    private static ChestTemplate template(String name, ItemStack first, ItemStack second) {
        List<ChestTemplate.Entry> entries = new ArrayList<>(ChestTemplate.SIZE);
        entries.add(new ChestTemplate.Entry(first, 37, 2, 9));
        entries.add(new ChestTemplate.Entry(second, 100, 1, 3));
        return new ChestTemplate(UUID.randomUUID(), name, "Оператор", 1_700_000_000_000L, 12, entries);
    }

    private static ItemStack sword() {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Меч короля"));
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(sharpness(), 4);
        stack.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        stack.set(DataComponents.DAMAGE, 137);
        return stack;
    }

    private static net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> sharpness() {
        return registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
    }

    private static byte[] bytes(ChestTemplate template) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream stream = new DataOutputStream(buffer)) {
            NbtIo.write(template.save(registries), stream);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        return buffer.toByteArray();
    }
}
