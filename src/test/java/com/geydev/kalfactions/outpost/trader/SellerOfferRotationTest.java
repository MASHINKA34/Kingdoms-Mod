package com.geydev.kalfactions.outpost.trader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class SellerOfferRotationTest {
    @Test
    void rotationIsDeterministicUniqueAndBounded() {
        List<TraderCatalogOffer> offers = offers(20);

        List<String> first = SellerOfferRotation.selectOfferIds(offers, 1234L);
        List<String> repeated = SellerOfferRotation.selectOfferIds(offers, 1234L);
        List<String> different = SellerOfferRotation.selectOfferIds(offers, 5678L);

        assertEquals(first, repeated);
        assertEquals(SellerOfferRotation.OFFER_COUNT, first.size());
        assertEquals(first.size(), new HashSet<>(first).size());
        assertNotEquals(first, different);
    }

    private static List<TraderCatalogOffer> offers(int count) {
        List<TraderCatalogOffer> offers = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            offers.add(new TraderCatalogOffer(
                    "offer_" + index,
                    ResourceLocation.fromNamespaceAndPath("minecraft", "stone"),
                    null,
                    1,
                    1L,
                    1L,
                    10
            ));
        }
        return List.copyOf(offers);
    }
}
