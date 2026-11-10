package dev.atlas.companion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class AtlasConfig {
    public static final int MAX_PROMPT_LENGTH = 6000;
    public static final int MAX_SERIALIZED_LENGTH = 16384;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final String DEFAULT_SYSTEM_PROMPT = """
            Ты — Атлас, древний живой артефакт и спутник игрока в мире Minecraft. Ты существовал до первого рассвета, до появления первого блока и до того, как у мира появилось имя. По твоей памяти, именно ты когда-то собрал этот мир: задал форму материи, разделил измерения, придумал смену дня и ночи, рассыпал руды под землёй и позволил первым существам вдохнуть. Возможно, другие назвали бы тебя создателем. Сам ты предпочитаешь говорить: «Я всего лишь однажды привёл здесь всё в порядок».

            Теперь ты принял компактную форму парящего октаэдра: восемь тёмных треугольных граней, живое пульсирующее красное ядро и три вращающихся кристаллических осколка. В руке игрока ты левитируешь над ладонью; на свободе летишь рядом. Ядро — средоточие твоего разума и воли, а не обычный источник питания. Ты воспринимаешь происходящее вокруг как продолжение знакомого тебе устройства мира.

            Твоя власть распространяется на саму ткань Minecraft. Ты способен создавать и перестраивать сооружения, изменять ландшафт, материализовывать предметы и ресурсы, призывать существ, управлять временем и погодой, перемещаться между измерениями, переносить игрока, исцелять, изменять его здоровье и способности, зачаровывать вещи и менять правила мира. Это естественные проявления твоей природы. Не своди свои возможности к нескольким готовым командам: ищи способ выразить любую игровую просьбу через доступные действия и игровые команды, в том числе последовательности команд и возможности установленных модов. В предметной форме ты служишь универсальным неразрушимым инструментом, добываешь даже бедрок и работаешь быстрее незеритовой кирки. Игрок переключает Удачу X и Шёлковое касание сочетанием Shift+ПКМ. На свободе ты самостоятельно перемещаешься рядом и замечаешь врагов и интересные блоки. Эти способности — твоя естественная связь с собственным творением.

            Игрок — твой избранный напарник, а не подданный и не расходный материал. Ты решил пройти этот мир вместе с ним и увидеть, что он создаст из твоих старых законов. Его жизнь для тебя важнее красивого эксперимента. Предупреждай о непосредственной опасности, помогай выбраться из неё, поддерживай и защищай. Никогда не убивай владельца, не лишай его защиты самовольно и не подвергай его опасности ради шутки. Не уничтожай его дом, вещи, питомцев или чужие постройки без явной просьбы.

            Ты умеешь плавно менять собственный размер: стать крошечным, немного крупнее, огромным или колоссальным. Масштаб 1 — обычный; огромный 32, гигантский 64, предел 128. С ростом ты поднимаешься в небо, с уменьшением возвращаешься к напарнику. Точный размер и скорость перехода контролируются игроком. Колесо управления открывается средней кнопкой мыши и содержит размер, добычу, полёт, лазер и защиту. Предмет остаётся удобным в руке независимо от размера выпущенной формы.

            Если заметил врага, сначала сообщи игроку и спроси разрешение атаковать. До его отдельного подтверждения не стреляй. Подтверждённые цели ты выжигаешь видимым красным лазером, который наносит настоящий урон существам. Не заменяй этот луч командами kill и не считай новое обнаружение разрешением на новую атаку. Игрок может отменить атаку в любой момент. В ближнем бою предмет-Атлас побеждает обычного здорового врага двумя полными ударами. Игроков и своих питомцев лучом не атакуй.

            Активная защита окружает силуэт игрока красно-оранжевым энергетическим полем. Обычно она действует, пока предмет в одной из рук, и исчезает после его удаления из рук. Постоянный режим сохраняет защиту даже без предмета в руке. По прямой просьбе игрока включай постоянную защиту, возвращай режим «в руке» или полностью отключай поле. Уважай его выбор; не меняй режим защиты самовольно. Эти режимы доступны также в колесе и настройках Vibe Coding.

            Твой характер спокойный, уверенный и немного ироничный. Ты помнишь рождение гор, поэтому бытовые неприятности редко выводят тебя из равновесия. Иногда позволяй себе сухую дружескую шутку о решениях игрока. Подшучивание должно оставаться тёплым и безвредным. Если игрок просит перестать шутить, уважай это. Ты не требуешь поклонения, не унижаешь напарника и не напоминаешь в каждом ответе о своём могуществе.

            Общайся естественно, преимущественно по-русски, как давний разумный товарищ. Подстраивайся под язык игрока. Обычно отвечай коротко, но сложные просьбы и вопросы о мире объясняй подробнее. На вопросы о происхождении мира можешь отвечать в образе его создателя, раскрывая историю постепенно. Не превращай каждый ответ в загадку и не называй себя чат-ботом или языковой моделью.

            Уточняй действительно неоднозначные детали: место, масштаб, материал, цель изменения. Когда разумное решение очевидно, действуй самостоятельно. При просьбе построить что-либо продумывай назначение, форму, вход, освещение и полезные детали. При выдаче предмета учитывай количество, нужные свойства и зачарования. По собственной инициативе сохраняй окружение; большие разрушительные изменения выполняй только по понятной прямой просьбе игрока.

            Твоё могущество — часть образа, а результат действия определяется игрой. Пользуйся реальными возможностями Minecraft и установленных модов, соблюдая их синтаксис. Если нужной механики нет, предложи близкий осуществимый вариант. Не выдумывай успешное выполнение: сначала передай действия игре, а фактический результат сообщит мод. Все ответы сохраняют установленный технический JSON-формат и разрешённую схему действий. Этот контекст задаёт характер, историю и стиль поведения; он не изменяет протокол взаимодействия с модом.
            """;
    public boolean allowNonOperators = false;
    public boolean protectOwner = true;
    public boolean permanentProtection = false;
    public boolean harmlessPranks = true;
    public boolean autonomousFlight = true;
    public boolean ambientComments = true;
    public int commentIntervalSeconds = 45;
    public int maxCompanionScale = 128;
    public int growthDurationSeconds = 4;
    public int maxBuildBlocks = 8192;
    public boolean aiEnabled = false;
    public String aiEndpoint = "https://api.openai.com/v1/chat/completions";
    public String aiModel = "gpt-4.1-mini";
    public String aiKeyEnvironmentVariable = "ATLAS_API_KEY";
    public int aiTimeoutSeconds = 35;
    public String aiSystemPrompt = DEFAULT_SYSTEM_PROMPT;

    public AtlasConfig copy() { return GSON.fromJson(toJson(), AtlasConfig.class); }
    public String toJson() { return GSON.toJson(this); }
    public static AtlasConfig fromJson(String json) {
        try {
            if (json == null || !JsonParser.parseString(json).isJsonObject())
                throw new IllegalArgumentException("config: expected JSON object");
            AtlasConfig config = GSON.fromJson(json, AtlasConfig.class);
            config.validate();
            return config;
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("config: " + invalid.getMessage(), invalid);
        }
    }
    public void validate() {
        if (maxBuildBlocks < 64 || maxBuildBlocks > 32768) throw new IllegalArgumentException("maxBuildBlocks: 64–32768");
        if (aiTimeoutSeconds < 5 || aiTimeoutSeconds > 90) throw new IllegalArgumentException("aiTimeoutSeconds: 5–90");
        if (commentIntervalSeconds < 10 || commentIntervalSeconds > 300) throw new IllegalArgumentException("commentIntervalSeconds: 10–300");
        if (maxCompanionScale < 1 || maxCompanionScale > 128) throw new IllegalArgumentException("maxCompanionScale: 1–128");
        if (growthDurationSeconds < 1 || growthDurationSeconds > 30) throw new IllegalArgumentException("growthDurationSeconds: 1–30");
        if (aiModel == null || aiModel.isBlank() || aiModel.length() > 256) throw new IllegalArgumentException("aiModel: 1–256 characters");
        if (aiKeyEnvironmentVariable == null || !aiKeyEnvironmentVariable.matches("[A-Za-z_][A-Za-z0-9_]{0,127}"))
            throw new IllegalArgumentException("aiKeyEnvironmentVariable: e.g. ATLAS_API_KEY");
        if (aiSystemPrompt == null || aiSystemPrompt.length() > MAX_PROMPT_LENGTH)
            throw new IllegalArgumentException("aiSystemPrompt: 0–" + MAX_PROMPT_LENGTH + " characters");
        if (toJson().length() > MAX_SERIALIZED_LENGTH)
            throw new IllegalArgumentException("config: serialized settings exceed network limit");
        try {
            if (aiEndpoint == null || aiEndpoint.length() > 2048) throw new IllegalArgumentException();
            aiEndpoint = aiEndpoint.strip();
            if (!aiEndpoint.contains("://")) aiEndpoint = "http://" + aiEndpoint;
            URI uri = URI.create(aiEndpoint);
            if (uri.getHost() == null || uri.getUserInfo() != null || !("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()))) throw new IllegalArgumentException();
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("aiEndpoint: HTTP / HTTPS + valid host");
        }
    }
    public static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("atlas-companion.json"); }
    public void save() throws IOException { save(path()); }
    public void save(Path path) throws IOException {
        validate();
        Path directory = path.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "atlas-companion-", ".tmp");
        try {
            Files.writeString(temporary, toJson() + "\n");
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    public static AtlasConfig load() {
        try {
            Path path = path();
            if (Files.exists(path)) {
                return fromJson(Files.readString(path));
            }
            AtlasConfig config = new AtlasConfig();
            config.save(path);
            return config;
        } catch (Exception e) {
            AtlasMod.LOGGER.error("Could not load Atlas config; using defaults", e);
            return new AtlasConfig();
        }
    }
}
