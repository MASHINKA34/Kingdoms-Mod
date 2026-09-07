package com.geydev.kalfactions.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class OptionalMixinCoverageTest {
    private static final Path CONFIG = Path.of("src/main/resources/kingdoms.mixins.json");
    private static final Path SOURCES = Path.of("src/main/java/com/geydev/kalfactions/mixin");

    private static final Pattern ANNOTATION = Pattern.compile("@Mixin\\s*\\((.*?)\\)\\s*\\n\\s*(?:public|abstract|final|class)", Pattern.DOTALL);
    private static final Pattern LITERAL_TARGET = Pattern.compile("\"([\\w.$]+)\"");
    private static final Pattern CLASS_TARGET = Pattern.compile("([\\w.]+)\\.class");
    private static final Pattern IMPORT = Pattern.compile("import\\s+([\\w.]+)\\.(\\w+);");

    private static List<String> declaredMixins() {
        JsonObject config = JsonParser.parseString(read(CONFIG)).getAsJsonObject();
        List<String> names = new ArrayList<>();
        for (String section : List.of("mixins", "client", "server")) {
            if (config.has(section)) {
                config.getAsJsonArray(section).forEach(element -> names.add(element.getAsString()));
            }
        }
        return names;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Set<String> targetsOf(String source) {
        Matcher annotation = ANNOTATION.matcher(source);
        Set<String> targets = new LinkedHashSet<>();
        while (annotation.find()) {
            String body = annotation.group(1);
            Matcher literal = LITERAL_TARGET.matcher(body);
            while (literal.find()) {
                targets.add(literal.group(1));
            }
            Matcher byClass = CLASS_TARGET.matcher(body);
            while (byClass.find()) {
                targets.add(qualify(source, byClass.group(1)));
            }
        }
        return targets;
    }

    private static String qualify(String source, String reference) {
        if (reference.contains(".") && Character.isLowerCase(reference.charAt(0))) {
            return reference;
        }
        String head = reference.contains(".") ? reference.substring(0, reference.indexOf('.')) : reference;
        Matcher imports = IMPORT.matcher(source);
        while (imports.find()) {
            if (imports.group(2).equals(head)) {
                return imports.group(1) + "." + reference;
            }
        }
        return reference;
    }

    @Test
    void everyDeclaredMixinHasASourceFile() {
        for (String name : declaredMixins()) {
            assertTrue(Files.isRegularFile(SOURCES.resolve(name + ".java")), name + " is declared but has no source");
        }
    }

    @Test
    void everyMixinIntoAnOptionalModIsGatedByThePlugin() {
        List<String> ungated = new ArrayList<>();
        for (String name : declaredMixins()) {
            Path source = SOURCES.resolve(name + ".java");
            if (!Files.isRegularFile(source)) {
                continue;
            }
            for (String target : targetsOf(read(source))) {
                boolean vanilla = target.startsWith("net.minecraft.") || target.startsWith("com.geydev.");
                if (!vanilla && !KingdomsMixinPlugin.isOptionalTarget(target)) {
                    ungated.add(name + " -> " + target);
                }
            }
        }
        assertEquals(
                List.of(),
                ungated,
                "these mixins target a third-party mod that KingdomsMixinPlugin will not skip when the mod is absent"
        );
    }

    @Test
    void vanillaTargetsAreAlwaysApplied() {
        KingdomsMixinPlugin plugin = new KingdomsMixinPlugin();
        assertTrue(plugin.shouldApplyMixin("net.minecraft.world.item.crafting.RecipeManager", "AnyMixin"));
        assertFalse(KingdomsMixinPlugin.isOptionalTarget("net.minecraft.world.level.block.CrafterBlock"));
    }

    @Test
    void optionalTargetsAreRecognisedForEveryIntegratedMod() {
        assertTrue(KingdomsMixinPlugin.isOptionalTarget("com.simibubi.create.content.contraptions.Contraption"));
        assertTrue(KingdomsMixinPlugin.isOptionalTarget("top.ribs.scguns.entity.projectile.ProjectileEntity"));
        assertTrue(KingdomsMixinPlugin.isOptionalTarget("net.mcreator.protectionpixel.world.inventory.ZongMenu"));
        assertTrue(KingdomsMixinPlugin.isOptionalTarget("xaero.hud.minimap.radar.state.RadarList"));
        assertTrue(KingdomsMixinPlugin.isOptionalTarget("xaero.map.radar.tracker.PlayerTrackerMapElementReader"));
    }

    @Test
    void anAbsentOptionalModDisablesItsMixin() {
        KingdomsMixinPlugin plugin = new KingdomsMixinPlugin();
        assertFalse(plugin.shouldApplyMixin("xaero.nope.NotHere", "XaeroRadarListMixin"));
        assertFalse(plugin.shouldApplyMixin("top.ribs.scguns.nope.NotHere", "ScorchedProjectileMixin"));
    }
}
