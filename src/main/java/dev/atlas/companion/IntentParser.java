package dev.atlas.companion;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure parsing, also used when no external model is configured. */
public final class IntentParser {
    public record Intent(String type, String argument, int amount) {}
    private static final Pattern ADDRESS = Pattern.compile("(?iu)^\\s*[@]?(?:атлас|atlas)(?:\\s*[,!:—-]\\s*|\\s+|$)(.*)$");
    private static final Pattern NUMBER = Pattern.compile("(?<![a-z0-9_:])\\b(\\d{1,9})\\b");
    private static final Pattern ID = Pattern.compile("[a-z0-9_]+:[a-z0-9_/]+");
    private static final Map<String, String> ITEMS = Map.ofEntries(
            Map.entry("алмаз", "diamond"), Map.entry("diamond", "diamond"),
            Map.entry("изумруд", "emerald"), Map.entry("emerald", "emerald"),
            Map.entry("незерит", "netherite_ingot"), Map.entry("netherite", "netherite_ingot"),
            Map.entry("желез", "iron_ingot"), Map.entry("iron", "iron_ingot"),
            Map.entry("золот", "gold_ingot"), Map.entry("gold", "gold_ingot"),
            Map.entry("факел", "torch"), Map.entry("torch", "torch"),
            Map.entry("хлеб", "bread"), Map.entry("bread", "bread"),
            Map.entry("яблок", "golden_apple"), Map.entry("apple", "golden_apple"),
            Map.entry("меч", "netherite_sword"), Map.entry("sword", "netherite_sword"),
            Map.entry("кирк", "netherite_pickaxe"), Map.entry("pickaxe", "netherite_pickaxe"),
            Map.entry("элитр", "elytra"), Map.entry("elytra", "elytra"));

    private IntentParser() {}
    public static String addressedMessage(String text) {
        Matcher matcher = ADDRESS.matcher(text);
        return matcher.matches() ? matcher.group(1).strip() : null;
    }
    public static Intent parse(String input) {
        String s = input.toLowerCase(Locale.ROOT).replace('ё', 'е').strip();
        if (has(s, "помощ", "что умеешь", "help")) return intent("help");
        AtlasScale.Request scale = AtlasScale.parse(s);
        if (scale != null) return new Intent(scale.relative() ? "scale_relative" : "scale", Double.toString(scale.value()), 0);
        if (has(s, "найди врагов", "уничтожь врагов", "уничтожить врагов", "выжги врагов", "атакуй врагов", "лазером врагов", "найди цели", "laser attack")) return intent("laser_offer");
        if (has(s, "защит") && has(s, "выключ", "отключ", "убери")) return new Intent("protection", "", has(s, "постоян") ? 0 : 2);
        if (has(s, "защит") && has(s, "постоян", "всегда", "навсегда")) return new Intent("protection", "", 1);
        if (has(s, "защит") && has(s, "в руке", "в руках", "включ")) return new Intent("protection", "", 0);
        if (has(s, "получить спутника", "призови", "summon", "дай спутника")) return intent("summon");
        if (has(s, "отмени", "отмена", "undo")) return intent("undo");
        if (has(s, "в руку", "в инвентарь", "вернись", "recall")) return intent("recall");
        if (has(s, "стой", "останься", "жди", "stay")) return intent("stay");
        if (has(s, "за мной", "следуй", "follow", "ко мне")) return intent("follow");
        if (has(s, "не шути", "без шуток")) return intent("pranks_off");
        if (has(s, "можешь шутить", "включи шутки")) return intent("pranks_on");
        if (has(s, "пошути", "подшути", "prank", "развесели")) return intent("prank");
        if (has(s, "увелич", "максималь", "max health", "сердец" ) && has(s, "здоров", "сердец", "health"))
            return new Intent("health", "", number(s, 40, 20, 200));
        if (has(s, "вылеч", "исцели", "полечи", "heal")) return intent("heal");
        if (has(s, "накорми", "поесть", "feed")) return intent("feed");
        if (has(s, "построй", "построить", "build", "сделай дом")) {
            if (has(s, "башн", "tower")) return new Intent("build", "tower", 0);
            if (has(s, "платформ", "platform")) return new Intent("build", "platform", 0);
            if (has(s, "дом", "house")) return new Intent("build", "house", 0);
            return new Intent("unknown", "", 0);
        }
        if (has(s, "выдай", "дай ", "дай мне", "give ", "хочу ")) {
            Matcher id = ID.matcher(s);
            if (id.find()) return new Intent("give", id.group(), number(s, 1, 1, 2304));
            for (var entry : ITEMS.entrySet()) if (s.contains(entry.getKey()))
                return new Intent("give", "minecraft:" + entry.getValue(), number(s, 1, 1, 2304));
        }
        if (has(s, "сделай день", "утро", "daytime")) return intent("day");
        if (has(s, "сделай ночь", "nighttime")) return intent("night");
        if (has(s, "убери дождь", "ясную погоду", "clear weather")) return intent("clear");
        return intent("unknown");
    }
    private static Intent intent(String type) { return new Intent(type, "", 0); }
    private static boolean has(String input, String... tokens) {
        for (String token : tokens) if (input.contains(token)) return true;
        return false;
    }
    private static int number(String input, int fallback, int min, int max) {
        Matcher matcher = NUMBER.matcher(input);
        if (!matcher.find()) return fallback;
        try { return Math.max(min, Math.min(max, Integer.parseInt(matcher.group(1)))); }
        catch (NumberFormatException e) { return fallback; }
    }
}
