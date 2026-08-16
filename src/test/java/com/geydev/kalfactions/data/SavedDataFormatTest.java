package com.geydev.kalfactions.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class SavedDataFormatTest {
    @Test
    void aTagWithoutAVersionReadsAsLegacy() {
        SavedDataFormat format = new SavedDataFormat(1);
        CompoundTag tag = new CompoundTag();

        assertEquals(SavedDataFormat.LEGACY_VERSION, format.versionOf(tag));
        assertTrue(format.outdated(tag));
    }

    @Test
    void upgradingWithoutMigrationsLeavesTheTagUntouched() {
        SavedDataFormat format = new SavedDataFormat(3);
        CompoundTag tag = new CompoundTag();
        tag.putString("name", "Кальтхейм");
        CompoundTag expected = tag.copy();

        assertSame(tag, format.upgrade(tag));
        assertEquals(expected, tag);
    }

    @Test
    void stampingMarksTheCurrentVersion() {
        SavedDataFormat format = new SavedDataFormat(4);
        CompoundTag tag = format.stamp(new CompoundTag());

        assertEquals(4, tag.getInt(SavedDataFormat.TAG_VERSION));
        assertEquals(4, format.versionOf(tag));
        assertFalse(format.outdated(tag));
    }

    @Test
    void migrationsRunInOrderFromTheStoredVersion() {
        SavedDataFormat format = new SavedDataFormat(3)
                .migration(SavedDataFormat.LEGACY_VERSION, tag -> {
                    tag.putString("steps", tag.getString("steps") + "0>1;");
                    return tag;
                })
                .migration(1, tag -> {
                    tag.putString("steps", tag.getString("steps") + "1>2;");
                    return tag;
                })
                .migration(2, tag -> {
                    tag.putString("steps", tag.getString("steps") + "2>3;");
                    return tag;
                });

        assertEquals("0>1;1>2;2>3;", format.upgrade(new CompoundTag()).getString("steps"));

        CompoundTag partial = new CompoundTag();
        partial.putInt(SavedDataFormat.TAG_VERSION, 2);

        assertEquals("2>3;", format.upgrade(partial).getString("steps"));
    }

    @Test
    void aCurrentTagSkipsEveryMigration() {
        SavedDataFormat format = new SavedDataFormat(2)
                .migration(SavedDataFormat.LEGACY_VERSION, tag -> {
                    tag.putBoolean("ran", true);
                    return tag;
                })
                .migration(1, tag -> {
                    tag.putBoolean("ran", true);
                    return tag;
                });
        CompoundTag tag = format.stamp(new CompoundTag());

        assertFalse(format.upgrade(tag).getBoolean("ran"));
    }

    @Test
    void aMigrationOutsideTheVersionRangeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SavedDataFormat(0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SavedDataFormat(2).migration(2, tag -> tag)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SavedDataFormat(2).migration(-1, tag -> tag)
        );
    }
}
