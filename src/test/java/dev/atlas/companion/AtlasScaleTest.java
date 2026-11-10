package dev.atlas.companion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtlasScaleTest {
    @Test void presetsAndRelativeGrowthAreDistinct() {
        assertEquals(3, AtlasScale.parse("немного увеличься").resolve(2, 128));
        assertEquals(4, AtlasScale.parse("стань побольше").resolve(2, 128));
        assertEquals(32, AtlasScale.parse("стань огромным").resolve(2, 128));
        assertEquals(64, AtlasScale.parse("стань гигантским").resolve(1, 128));
        assertEquals(128, AtlasScale.parse("стань колоссальным").resolve(1, 128));
        assertEquals(1, AtlasScale.parse("обычный размер").resolve(64, 128));
    }
    @Test void exactDecimalScalesAndShrinkingWork() {
        assertEquals(0.25f, AtlasScale.parse("масштаб 0,25").resolve(1, 128));
        assertEquals(8, AtlasScale.parse("увеличься в 4 раза").resolve(2, 128));
        assertEquals(2, AtlasScale.parse("уменьшись в 4 раза").resolve(8, 128));
        assertEquals(4, AtlasScale.parse("уменьшись на 50%").resolve(8, 128));
    }
    @Test void scaleDoesNotCaptureHealthBuildingOrOrdinaryConversation() {
        assertNull(AtlasScale.parse("увеличь здоровье до 40"));
        assertNull(AtlasScale.parse("построй огромный дом"));
        assertNull(AtlasScale.parse("увеличь размер дома"));
        assertNull(AtlasScale.parse("стань моим другом"));
        assertEquals("health", IntentParser.parse("увеличь здоровье до 40").type());
        assertEquals("build", IntentParser.parse("построй огромный дом").type());
    }
    @Test void invalidAndServerLimitedScalesAreRejected() {
        for (double invalid : new double[]{0, -1, 129, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> AtlasScale.validate(invalid, 128));
        assertThrows(IllegalArgumentException.class, () -> AtlasScale.parse("масштаб 32").resolve(1, 16));
        assertTrue(AtlasScale.hoverHeight(64) > 64);
        assertTrue(AtlasScale.hoverHeight(128) > AtlasScale.hoverHeight(64));
    }
}
