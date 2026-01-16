package org.zoobastiks.ztelegram.mgr

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.entity.EntityDamageEvent
import org.zoobastiks.ztelegram.ZTele
import java.io.File
import kotlin.random.Random

/**
 * Менеджер для обработки сообщений о смерти игроков
 * Переводит английские сообщения о смерти в русские из PlayerDeathMessages.yml
 *
 * @author Zoobastiks
 */
class DeathMessageManager(private val plugin: ZTele) {

    private val deathMessagesFile = File(plugin.dataFolder, "PlayerDeathMessages.yml")
    private lateinit var deathConfig: YamlConfiguration

    init {
        loadDeathMessages()
    }

    /**
     * Загружает конфигурацию сообщений о смерти
     */
    private fun loadDeathMessages() {
        // Создаем файл если его нет
        if (!deathMessagesFile.exists()) {
            plugin.saveResource("PlayerDeathMessages.yml", false)
        }

        try {
            deathConfig = YamlConfiguration.loadConfiguration(deathMessagesFile)
            plugin.logger.info("PlayerDeathMessages.yml loaded successfully")
        } catch (e: Exception) {
            plugin.logger.severe("Failed to load PlayerDeathMessages.yml: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Перезагружает конфигурацию сообщений о смерти
     */
    fun reload() {
        loadDeathMessages()
    }

    /**
     * Получает русское сообщение о смерти на основе события
     *
     * @param player Погибший игрок
     * @param deathMessage Оригинальное английское сообщение о смерти
     * @param killer Убийца (если есть)
     * @param cause Причина смерти
     * @return Русское сообщение о смерти
     */
    fun getDeathMessage(
        player: Player,
        deathMessage: String,
        killer: Entity?,
        cause: EntityDamageEvent.DamageCause?
    ): String {
        try {
            // Определяем тип смерти
            val deathType = detectDeathType(deathMessage, killer, cause)

            // Получаем путь к сообщению в конфиге
            val messagePath = buildMessagePath(deathType, killer)

            if (ZTele.conf.debugEnabled) {
                plugin.logger.info("[DeathMessage] Player: ${player.name}")
                plugin.logger.info("[DeathMessage] Original: $deathMessage")
                plugin.logger.info("[DeathMessage] Death type: $deathType")
                plugin.logger.info("[DeathMessage] Message path: $messagePath")
                plugin.logger.info("[DeathMessage] Killer: ${killer?.name ?: "none"}")
                plugin.logger.info("[DeathMessage] Cause: ${cause?.name ?: "none"}")
            }

            // Получаем список сообщений из конфига
            val messages = deathConfig.getStringList(messagePath)

            if (messages.isEmpty()) {
                // Если нет сообщения для конкретного типа, пытаемся использовать общее
                val fallbackMessage = getFallbackMessage(deathType, killer)
                if (fallbackMessage.isNotEmpty()) {
                    return processPlaceholders(fallbackMessage, player, killer, deathMessage)
                }

                // Если и общего нет, возвращаем переведенное базовое сообщение
                return translateBasicMessage(deathMessage, player, killer)
            }

            // Выбираем случайное сообщение из списка
            val randomMessage = messages.random()

            // Обрабатываем плейсхолдеры
            return processPlaceholders(randomMessage, player, killer, deathMessage)

        } catch (e: Exception) {
            plugin.logger.warning("Error processing death message: ${e.message}")
            // В случае ошибки возвращаем базовый перевод
            return translateBasicMessage(deathMessage, player, killer)
        }
    }

    /**
     * Определяет тип смерти на основе сообщения и причины
     */
    private fun detectDeathType(
        deathMessage: String,
        killer: Entity?,
        cause: EntityDamageEvent.DamageCause?
    ): DeathType {
        val lowerMessage = deathMessage.lowercase()

        // Проверяем причину смерти
        return when (cause) {
            EntityDamageEvent.DamageCause.VOID -> DeathType.VOID
            EntityDamageEvent.DamageCause.FALL -> DeathType.FALL
            EntityDamageEvent.DamageCause.FIRE,
            EntityDamageEvent.DamageCause.FIRE_TICK -> DeathType.FIRE
            EntityDamageEvent.DamageCause.LAVA -> DeathType.LAVA
            EntityDamageEvent.DamageCause.DROWNING -> DeathType.DROWNING
            EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,
            EntityDamageEvent.DamageCause.ENTITY_EXPLOSION -> {
                if (killer != null) {
                    DeathType.MOB_EXPLOSION
                } else {
                    DeathType.EXPLOSION
                }
            }
            EntityDamageEvent.DamageCause.LIGHTNING -> DeathType.LIGHTNING
            EntityDamageEvent.DamageCause.STARVATION -> DeathType.STARVATION
            EntityDamageEvent.DamageCause.POISON -> DeathType.POISON
            EntityDamageEvent.DamageCause.MAGIC -> DeathType.MAGIC
            EntityDamageEvent.DamageCause.WITHER -> DeathType.WITHER
            EntityDamageEvent.DamageCause.FALLING_BLOCK -> DeathType.FALLING_BLOCK
            EntityDamageEvent.DamageCause.THORNS -> DeathType.THORNS
            EntityDamageEvent.DamageCause.DRAGON_BREATH -> DeathType.DRAGON_BREATH
            EntityDamageEvent.DamageCause.FLY_INTO_WALL -> DeathType.FLY_INTO_WALL
            EntityDamageEvent.DamageCause.HOT_FLOOR -> DeathType.HOT_FLOOR
            EntityDamageEvent.DamageCause.CRAMMING -> DeathType.CRAMMING
            EntityDamageEvent.DamageCause.DRYOUT -> DeathType.DRYOUT
            EntityDamageEvent.DamageCause.FREEZE -> DeathType.FREEZE
            EntityDamageEvent.DamageCause.SONIC_BOOM -> DeathType.SONIC_BOOM
            EntityDamageEvent.DamageCause.ENTITY_ATTACK -> {
                if (killer != null) {
                    if (killer is Projectile) {
                        DeathType.MOB_PROJECTILE
                    } else {
                        DeathType.MOB_MELEE
                    }
                } else {
                    DeathType.UNKNOWN
                }
            }
            EntityDamageEvent.DamageCause.PROJECTILE -> DeathType.MOB_PROJECTILE
            EntityDamageEvent.DamageCause.SUFFOCATION -> DeathType.SUFFOCATION
            EntityDamageEvent.DamageCause.CONTACT -> DeathType.CONTACT
            else -> {
                // Анализируем текст сообщения для определения типа
                when {
                    lowerMessage.contains("shot") || lowerMessage.contains("arrow") -> DeathType.MOB_PROJECTILE
                    lowerMessage.contains("blown up") || lowerMessage.contains("explosion") -> DeathType.MOB_EXPLOSION
                    lowerMessage.contains("slain") || lowerMessage.contains("killed") -> DeathType.MOB_MELEE
                    lowerMessage.contains("drowned") -> DeathType.DROWNING
                    lowerMessage.contains("fell") -> DeathType.FALL
                    lowerMessage.contains("burned") || lowerMessage.contains("fire") -> DeathType.FIRE
                    lowerMessage.contains("lava") -> DeathType.LAVA
                    lowerMessage.contains("suffocated") -> DeathType.SUFFOCATION
                    lowerMessage.contains("starved") -> DeathType.STARVATION
                    lowerMessage.contains("magic") -> DeathType.MAGIC
                    lowerMessage.contains("withered") -> DeathType.WITHER
                    else -> DeathType.UNKNOWN
                }
            }
        }
    }

    /**
     * Строит путь к сообщению в конфиге
     */
    private fun buildMessagePath(deathType: DeathType, killer: Entity?): String {
        return when {
            // Если есть убийца (моб или игрок)
            killer != null -> {
                val mobType = getMobType(killer)
                val attackType = when (deathType) {
                    DeathType.MOB_PROJECTILE -> "Projectile-Arrow"
                    DeathType.MOB_EXPLOSION -> "Explosion"
                    DeathType.SONIC_BOOM -> "Sonic-Boom"
                    else -> "Melee"
                }
                "Mobs.$mobType.Solo.$attackType"
            }
            // Естественные причины смерти
            else -> {
                val causeType = when (deathType) {
                    DeathType.VOID -> "Void"
                    DeathType.FALL -> "Fall"
                    DeathType.FIRE -> "Fire-Tick"
                    DeathType.LAVA -> "Lava"
                    DeathType.DROWNING -> "Drowning"
                    DeathType.EXPLOSION -> "Explosion"
                    DeathType.LIGHTNING -> "Lightning"
                    DeathType.STARVATION -> "Starvation"
                    DeathType.POISON -> "Poison"
                    DeathType.MAGIC -> "Magic"
                    DeathType.WITHER -> "Wither"
                    DeathType.FALLING_BLOCK -> "Falling-Block"
                    DeathType.THORNS -> "Thorns"
                    DeathType.DRAGON_BREATH -> "Dragon-Breath"
                    DeathType.FLY_INTO_WALL -> "Fly-Into-Wall"
                    DeathType.HOT_FLOOR -> "Hot-Floor"
                    DeathType.CRAMMING -> "Cramming"
                    DeathType.DRYOUT -> "Dryout"
                    DeathType.FREEZE -> "Freeze"
                    DeathType.SUFFOCATION -> "Suffocation"
                    DeathType.CONTACT -> "Contact"
                    DeathType.SONIC_BOOM -> "Sonic-Boom"
                    else -> "Unknown"
                }
                "Natural-Cause.$causeType"
            }
        }
    }

    /**
     * Получает тип моба для конфига
     */
    private fun getMobType(entity: Entity): String {
        // Если это игрок
        if (entity is Player) {
            return "player"
        }

        // Маппинг типов мобов на названия в конфиге
        val mobType = when (entity.type.name.lowercase()) {
            "zombie" -> "zombie"
            "skeleton" -> "skeleton"
            "creeper" -> "creeper"
            "spider" -> "spider"
            "cave_spider" -> "cavespider"
            "enderman" -> "enderman"
            "blaze" -> "blaze"
            "witch" -> "witch"
            "wither_skeleton" -> "witherskeleton"
            "stray" -> "stray"
            "husk" -> "husk"
            "drowned" -> "drowned"
            "phantom" -> "phantom"
            "ghast" -> "ghast"
            "magma_cube" -> "magmacube"
            "slime" -> "slime"
            "ender_dragon" -> "enderdragon"
            "wither" -> "wither"
            "guardian" -> "guardian"
            "elder_guardian" -> "elderguardian"
            "shulker" -> "shulker"
            "pillager" -> "pillager"
            "ravager" -> "ravager"
            "vindicator" -> "vindicator"
            "evoker" -> "evoker"
            "vex" -> "vex"
            "zombie_villager" -> "zombievillager"
            "wolf" -> "wolf"
            "iron_golem" -> "irongolem"
            "polar_bear" -> "polarbear"
            "llama" -> "llama"
            "bee" -> "bee"
            "hoglin" -> "hoglin"
            "piglin" -> "piglin"
            "piglin_brute" -> "piglinbrute"
            "zoglin" -> "zoglin"
            "warden" -> "warden"
            "bogged" -> "bogged"
            "breeze" -> "breeze"
            "pig" -> "pig"
            "cow" -> "cow"
            "sheep" -> "sheep"
            "chicken" -> "chicken"
            "horse" -> "horse"
            else -> entity.type.name.lowercase()
        }

        return mobType
    }

    /**
     * Получает запасное сообщение если конкретного не найдено
     */
    private fun getFallbackMessage(deathType: DeathType, killer: Entity?): String {
        return if (killer != null) {
            // Для смерти от моба используем базовое сообщение
            "был убит"
        } else {
            // Для естественных смертей
            when (deathType) {
                DeathType.VOID -> "упал в пустоту"
                DeathType.FALL -> "разбился при падении"
                DeathType.FIRE -> "сгорел"
                DeathType.LAVA -> "искупался в лаве"
                DeathType.DROWNING -> "утонул"
                DeathType.EXPLOSION -> "взорвался"
                DeathType.STARVATION -> "умер от голода"
                else -> "умер"
            }
        }
    }

    /**
     * Обрабатывает плейсхолдеры в сообщении
     */
    private fun processPlaceholders(
        message: String,
        player: Player,
        killer: Entity?,
        originalMessage: String
    ): String {
        var processed = message

        // Базовые плейсхолдеры
        processed = processed.replace("%player%", player.name)

        // Плейсхолдеры убийцы
        if (killer != null) {
            val killerName = when (killer) {
                is Player -> killer.name
                else -> getLocalizedMobName(killer)
            }
            processed = processed.replace("%killer%", killerName)
            processed = processed.replace("%killer_type%", getLocalizedMobName(killer))
        }

        // Локация смерти
        processed = processed.replace("%x%", player.location.blockX.toString())
        processed = processed.replace("%y%", player.location.blockY.toString())
        processed = processed.replace("%z%", player.location.blockZ.toString())
        processed = processed.replace("%world%", player.world.name)

        // Расстояние до убийцы
        if (killer != null) {
            val distance = player.location.distance(killer.location).toInt()
            processed = processed.replace("%distance%", distance.toString())
        }

        // Оружие (если есть в оригинальном сообщении)
        val weaponMatch = Regex("using (.+)").find(originalMessage)
        if (weaponMatch != null) {
            processed = processed.replace("%weapon%", weaponMatch.groupValues[1])
        } else {
            processed = processed.replace("%weapon%", "оружие")
        }

        return processed
    }

    /**
     * Переводит базовое английское сообщение на русский
     */
    private fun translateBasicMessage(
        englishMessage: String,
        player: Player,
        killer: Entity?
    ): String {
        val lowerMessage = englishMessage.lowercase()

        return when {
            lowerMessage.contains("shot") && killer != null ->
                "${player.name} был застрелен ${getLocalizedMobName(killer)}"

            lowerMessage.contains("blown up") && killer != null ->
                "${player.name} был взорван ${getLocalizedMobName(killer)}"

            lowerMessage.contains("slain") && killer != null ->
                "${player.name} был убит ${getLocalizedMobName(killer)}"

            lowerMessage.contains("killed") && killer != null ->
                "${player.name} был убит ${getLocalizedMobName(killer)}"

            lowerMessage.contains("drowned") ->
                "${player.name} утонул"

            lowerMessage.contains("fell") || lowerMessage.contains("fall") ->
                "${player.name} разбился при падении"

            lowerMessage.contains("burned") || lowerMessage.contains("fire") ->
                "${player.name} сгорел"

            lowerMessage.contains("lava") ->
                "${player.name} искупался в лаве"

            lowerMessage.contains("suffocated") ->
                "${player.name} задохнулся в стене"

            lowerMessage.contains("starved") ->
                "${player.name} умер от голода"

            lowerMessage.contains("void") ->
                "${player.name} упал в пустоту"

            lowerMessage.contains("withered") ->
                "${player.name} иссох"

            lowerMessage.contains("lightning") ->
                "${player.name} был убит молнией"

            lowerMessage.contains("magic") ->
                "${player.name} был убит магией"

            else -> "${player.name} умер"
        }
    }

    /**
     * Получает локализованное название моба
     */
    private fun getLocalizedMobName(entity: Entity): String {
        // Если у моба есть кастомное имя, используем его
        if (entity.customName() != null) {
            return entity.name
        }

        // Переводы типов мобов
        return when (entity.type.name.uppercase()) {
            "ZOMBIE" -> "зомби"
            "SKELETON" -> "скелетом"
            "CREEPER" -> "крипером"
            "SPIDER" -> "пауком"
            "CAVE_SPIDER" -> "пещерным пауком"
            "ENDERMAN" -> "эндерменом"
            "BLAZE" -> "ифритом"
            "WITCH" -> "ведьмой"
            "WITHER_SKELETON" -> "скелетом-иссушителем"
            "STRAY" -> "зимогором"
            "HUSK" -> "кадавром"
            "DROWNED" -> "утопленником"
            "PHANTOM" -> "фантомом"
            "GHAST" -> "гастом"
            "MAGMA_CUBE" -> "лавовым кубом"
            "SLIME" -> "слизнем"
            "ENDER_DRAGON" -> "драконом Края"
            "WITHER" -> "иссушителем"
            "GUARDIAN" -> "стражем"
            "ELDER_GUARDIAN" -> "древним стражем"
            "SHULKER" -> "шалкером"
            "PILLAGER" -> "разбойником"
            "RAVAGER" -> "разорителем"
            "VINDICATOR" -> "поборником"
            "EVOKER" -> "магом"
            "VEX" -> "досаждателем"
            "ZOMBIE_VILLAGER" -> "зомби-жителем"
            "WOLF" -> "волком"
            "IRON_GOLEM" -> "железным големом"
            "POLAR_BEAR" -> "белым медведем"
            "LLAMA" -> "ламой"
            "BEE" -> "пчелой"
            "HOGLIN" -> "хоглином"
            "PIGLIN" -> "пиглином"
            "PIGLIN_BRUTE" -> "пиглином-громилой"
            "ZOGLIN" -> "зоглином"
            "WARDEN" -> "хранителем"
            "BOGGED" -> "трясинником"
            "BREEZE" -> "бризом"
            "PIG" -> "свиньёй"
            "COW" -> "коровой"
            "SHEEP" -> "овцой"
            "CHICKEN" -> "курицей"
            "HORSE" -> "лошадью"
            "PLAYER" -> if (entity is Player) entity.name else "игроком"
            else -> entity.type.name.lowercase().replace("_", " ")
        }
    }

    /**
     * Перечисление типов смерти
     */
    private enum class DeathType {
        VOID,
        FALL,
        FIRE,
        LAVA,
        DROWNING,
        EXPLOSION,
        LIGHTNING,
        STARVATION,
        POISON,
        MAGIC,
        WITHER,
        FALLING_BLOCK,
        THORNS,
        DRAGON_BREATH,
        FLY_INTO_WALL,
        HOT_FLOOR,
        CRAMMING,
        DRYOUT,
        FREEZE,
        SUFFOCATION,
        CONTACT,
        SONIC_BOOM,
        MOB_MELEE,
        MOB_PROJECTILE,
        MOB_EXPLOSION,
        UNKNOWN
    }
}
