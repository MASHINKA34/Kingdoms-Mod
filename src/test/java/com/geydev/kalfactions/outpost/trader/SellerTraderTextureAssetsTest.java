package com.geydev.kalfactions.outpost.trader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class SellerTraderTextureAssetsTest {
    private static final String WANDERING =
            "/assets/kingdoms/textures/entity/seller_trader_wandering.png";
    private static final String CONTRABAND =
            "/assets/kingdoms/textures/entity/seller_trader_contraband.png";

    @Test
    void roleTexturesAreValidDistinctMinecraftSkins() throws IOException, NoSuchAlgorithmException {
        BufferedImage wandering = read(WANDERING);
        BufferedImage contraband = read(CONTRABAND);

        assertSkinAtlas(wandering);
        assertSkinAtlas(contraband);
        assertNotEquals(digest(WANDERING), digest(CONTRABAND));
    }

    private static BufferedImage read(String path) throws IOException {
        try (InputStream input = SellerTraderTextureAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(input, path);
            BufferedImage image = ImageIO.read(input);
            assertNotNull(image, path);
            return image;
        }
    }

    private static void assertSkinAtlas(BufferedImage image) {
        assertEquals(64, image.getWidth());
        assertEquals(64, image.getHeight());
        assertTrue(image.getColorModel().hasAlpha());
    }

    private static String digest(String path) throws IOException, NoSuchAlgorithmException {
        try (InputStream input = SellerTraderTextureAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(input, path);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
        }
    }
}
