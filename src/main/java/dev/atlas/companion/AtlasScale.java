package dev.atlas.companion;

import java.util.Locale;
import java.util.regex.Pattern;

/** Scale grammar and geometry are independent of client and server state. */
public final class AtlasScale {
    public static final float MIN = 0.125f;
    public static final float MAX = 128;
    public static final String ITEM_KEY = "AtlasScale";
    private static final Pattern NUMBER = Pattern.compile("(?<![\\w])(-?\\d+(?:[.,]\\d+)?)");
    public record Request(double value, boolean relative) {
        public float resolve(double current, double maximum) { return validate(relative ? current * value : value, maximum); }
    }
    private AtlasScale() {}
    public static float validate(double value, double maximum) {
        if (!Double.isFinite(value) || value < MIN || value > Math.min(MAX, maximum))
            throw new IllegalArgumentException("Масштаб должен быть от " + MIN + " до " + format((float)maximum) + "×.");
        return (float)value;
    }
    public static float sanitize(double value, double maximum) {
        if (!Double.isFinite(value) || value <= 0) value = 1;
        return (float)Math.max(MIN, Math.min(Math.min(MAX, maximum), value));
    }
    public static double hoverHeight(float scale) { return 1.05 + Math.max(0, scale - 1) * 1.15; }
    public static String format(float scale) { return String.format(Locale.ROOT, "%.3f", scale).replaceAll("0+$", "").replaceAll("\\.$", ""); }
    public static Request parse(String input) {
        String s = input.toLowerCase(Locale.ROOT).replace('ё', 'е').strip();
        boolean self = has(s, "увеличься", "увеличивайся", "вырасти", "подрасти", "уменьшись", "уменьшайся", "сожмись", "себя", "grow", "shrink");
        boolean parameter = has(s, "масштаб", "размер", "scale");
        if (has(s, "стань") && has(s, "больш", "меньш", "маленьк", "крошеч", "огром", "гигант", "колоссаль", "нормальн", "обычн")) self = true;
        boolean bare = s.matches("(?:немного )?(?:побольше|поменьше|огроменно|огромный|гигантский|маленький|крошечный)[.!]?");
        if (!(self || parameter || bare)) return null;
        if (!self && has(s, "здоров", "серд", "дом", "башн", "платформ", "построй", "инвентар")) return null;
        if (parameter && has(s, "увелич", "уменьш", "больше", "меньше")) self = true;
        if (has(s, "обычн", "нормальн", "исходн", "как раньше", "reset scale", "normal size")) return new Request(1, false);
        boolean smaller = has(s, "уменьш", "поменьше", "меньше", "сожм", "shrink", "smaller");
        var number = NUMBER.matcher(s);
        if (number.find()) {
            double value = Double.parseDouble(number.group(1).replace(',', '.'));
            if (s.contains("%")) return new Request(1 + (smaller ? -value : value) / 100.0, true);
            if (has(s, "раза", "раз ", "раз.", "раз!", "times") || s.endsWith("раз"))
                return new Request(smaller ? 1.0 / value : value, true);
            return new Request(value, false);
        }
        if (has(s, "крошеч", "tiny")) return new Request(0.25, false);
        if (has(s, "маленьк", "small size")) return new Request(0.5, false);
        if (has(s, "колоссаль", "максималь", "colossal")) return new Request(MAX, false);
        if (has(s, "гигант", "исполин", "gigantic")) return new Request(64, false);
        if (has(s, "огром", "огроменн", "huge")) return new Request(32, false);
        if (has(s, "большим", "большой", "большого")) return new Request(4, false);
        if (parameter && !self && !bare) return new Request(Double.NaN, false);
        double factor = has(s, "немного", "чуть", "слегка", "slightly") ? 1.5 : 2;
        return new Request(smaller ? 1.0 / factor : factor, true);
    }
    private static boolean has(String value, String... parts) {
        for (String part : parts) if (value.contains(part)) return true;
        return false;
    }
}
