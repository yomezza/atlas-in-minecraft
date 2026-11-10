package dev.atlas.companion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IntentParserTest {
    @Test void chatOnlyRespondsWhenAddressed() {
        assertEquals("дай 64 алмаза", IntentParser.addressedMessage("Атлас, дай 64 алмаза"));
        assertEquals("help", IntentParser.addressedMessage("@Atlas: help"));
        assertNull(IntentParser.addressedMessage("Нашёл атлас в сундуке"));
        assertNull(IntentParser.addressedMessage("Атласный халат"));
    }
    @Test void russianAndRegistryItemRequestsWork() {
        assertEquals(new IntentParser.Intent("give", "minecraft:diamond", 64), IntentParser.parse("дай мне 64 алмаза"));
        assertEquals(new IntentParser.Intent("give", "minecraft:oak_log", 64), IntentParser.parse("дай minecraft:oak_log 64"));
        assertEquals(new IntentParser.Intent("give", "minecraft:torch", 2304), IntentParser.parse("выдай 999999999 факелов"));
    }
    @Test void healthAndBuildingHaveSeparateIntents() {
        assertEquals(new IntentParser.Intent("health", "", 40), IntentParser.parse("увеличь здоровье до 40"));
        assertEquals(new IntentParser.Intent("health", "", 200), IntentParser.parse("увеличь здоровье до 999"));
        assertEquals("heal", IntentParser.parse("вылечи меня").type());
        assertEquals(new IntentParser.Intent("build", "tower", 0), IntentParser.parse("построй башню"));
        assertEquals("unknown", IntentParser.parse("построй космическую станцию").type());
    }
    @Test void undoAndPrankOptOutTakePriority() {
        assertEquals("undo", IntentParser.parse("отмени постройку").type());
        assertEquals("pranks_off", IntentParser.parse("не шути").type());
        assertEquals("follow", IntentParser.parse("следуй за мной").type());
        assertEquals("recall", IntentParser.parse("вернись в руку").type());
    }
}
