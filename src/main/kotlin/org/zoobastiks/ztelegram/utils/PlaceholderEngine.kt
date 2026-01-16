package org.zoobastiks.ztelegram.utils

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.zoobastiks.ztelegram.ZTele
import org.zoobastiks.ztelegram.stats.StatsManager
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * Универсальная система плейсхолдеров для плагина ZTelegram
 * 
 * Особенности:
 * - Безопасная обработка с UUID токенами
 * - Кэширование дорогих операций
 * - Поддержка динамических плейсхолдеров
 * - Защита от конфликтов при замене
 * 
 * @author Zoobastiks
 */
class PlaceholderEngine {
    
    companion object {
        // Кэш для дорогих операций (топы, статистика)
        private val cache = ConcurrentHashMap<String, CacheEntry>()
        private const val CACHE_DURATION_MS = 30_000L // 30 секунд
        
        // Паттерны для плейсхолдеров
        private val PLACEHOLDER_PATTERN = Pattern.compile("%([^%]+)%")
        private val TOP_NAME_PATTERN = Pattern.compile("top_name_(\\d+)")
        private val TOP_VALUE_PATTERN = Pattern.compile("top_value_(\\d+)")
        private val STAT_PATTERN = Pattern.compile("stat_([^_]+)_([^_]+)")
        
        /**
         * Главный метод обработки плейсхолдеров
         */
        fun process(template: String, context: PlaceholderContext = PlaceholderContext()): String {
            if (template.isBlank()) return template
            
            if (ZTele.conf.debugEnabled) {
                ZTele.instance.logger.info("[PlaceholderEngine] Processing template: $template")
                ZTele.instance.logger.info("[PlaceholderEngine] Context custom: ${context.customPlaceholders}")
            }
            
            var result = template
            val uuidReplacements = mutableMapOf<String, String>()
            
            // Находим все плейсхолдеры в тексте
            val matcher = PLACEHOLDER_PATTERN.matcher(template)
            val placeholders = mutableSetOf<String>()
            
            while (matcher.find()) {
                placeholders.add(matcher.group(1))
            }
            
            // Обрабатываем каждый плейсхолдер
            for (placeholder in placeholders) {
                val value = resolvePlaceholder(placeholder, context)
                if (value != null) {
                    val uuid = "UUID_${UUID.randomUUID().toString().replace("-", "")}_END"
                    uuidReplacements[uuid] = value
                    result = result.replace("%$placeholder%", uuid)
                }
            }
            
            // Заменяем UUID токены на финальные значения
            for ((uuid, finalValue) in uuidReplacements) {
                result = result.replace(uuid, finalValue)
            }
            
            if (ZTele.conf.debugEnabled) {
                ZTele.instance.logger.info("[PlaceholderEngine] Final result: $result")
            }
            
            return result
        }
        
        /**
         * Разрешает конкретный плейсхолдер
         */
        private fun resolvePlaceholder(placeholder: String, context: PlaceholderContext): String? {
            if (ZTele.conf.debugEnabled) {
                ZTele.instance.logger.info("[PlaceholderEngine] Resolving placeholder: $placeholder")
            }
            
            val result = when {
                // ПРИОРИТЕТ: Дополнительные плейсхолдеры из контекста (должны быть первыми!)
                context.customPlaceholders.containsKey(placeholder) -> 
                    context.customPlaceholders[placeholder]
                
                // Стандартные плейсхолдеры
                placeholder == "player" -> context.player?.name ?: context.playerName
                placeholder == "uuid" -> context.player?.uniqueId?.toString()
                placeholder == "online" -> Bukkit.getOnlinePlayers().size.toString()
                placeholder == "max" -> Bukkit.getMaxPlayers().toString()
                placeholder == "time" -> LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                placeholder == "date" -> LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                placeholder == "tps" -> getTpsFormatted()
                placeholder == "status" -> getTpsStatus()
                placeholder == "ping" -> context.player?.ping?.toString()
                placeholder == "players" -> getOnlinePlayersList()
                
                // Топ плейсхолдеры (top_name_1, top_value_1 и т.д.)
                TOP_NAME_PATTERN.matcher(placeholder).matches() -> {
                    val position = TOP_NAME_PATTERN.matcher(placeholder).let { 
                        if (it.find()) it.group(1).toIntOrNull() else null 
                    }
                    position?.let { pos -> getTopPlayerName(pos, context.topType, context.topPeriod) }
                }
                
                TOP_VALUE_PATTERN.matcher(placeholder).matches() -> {
                    val position = TOP_VALUE_PATTERN.matcher(placeholder).let { 
                        if (it.find()) it.group(1).toIntOrNull() else null 
                    }
                    position?.let { pos -> getTopPlayerValue(pos, context.topType, context.topPeriod) }
                }
                
                // Статистика игрока (stat_player_type)
                STAT_PATTERN.matcher(placeholder).matches() -> {
                    val matcher = STAT_PATTERN.matcher(placeholder)
                    if (matcher.find()) {
                        val playerName = matcher.group(1)
                        val statType = matcher.group(2)
                        getPlayerStat(playerName, statType)
                    } else null
                }
                
                // Плейсхолдеры для команд топа
                placeholder.startsWith("player_") -> {
                    val position = placeholder.removePrefix("player_").toIntOrNull()
                    position?.let { pos -> getTopPlayerName(pos, context.topType, context.topPeriod) }
                }
                
                placeholder.startsWith("time_") -> {
                    val position = placeholder.removePrefix("time_").toIntOrNull()
                    position?.let { pos -> getTopPlayerValue(pos, TopType.PLAYTIME, context.topPeriod) }
                }
                
                placeholder.startsWith("balance_") -> {
                    val position = placeholder.removePrefix("balance_").toIntOrNull()
                    position?.let { pos -> getTopPlayerValue(pos, TopType.BALANCE, context.topPeriod) }
                }
                
                // Период и количество
                placeholder == "period" -> context.topPeriod
                placeholder == "count" -> context.topCount?.toString()
                
                else -> null
            }
            
            if (ZTele.conf.debugEnabled) {
                ZTele.instance.logger.info("[PlaceholderEngine] Placeholder $placeholder resolved to: $result")
            }
            
            return result
        }
        
        /**
         * Получает отформатированный TPS
         */
        private fun getTpsFormatted(): String {
            return try {
                val tps = Bukkit.getTPS()[0] // Последняя минута
                String.format("%.2f", tps)
            } catch (e: Exception) {
                "20.00"
            }
        }
        
        /**
         * Получает статус TPS на основе значения
         */
        private fun getTpsStatus(): String {
            return try {
                val tps = Bukkit.getTPS()[0] // Последняя минута
                when {
                    tps >= 19.5 -> ZTele.conf.tpsStatusExcellent
                    tps >= 18.0 -> ZTele.conf.tpsStatusGood
                    tps >= 15.0 -> ZTele.conf.tpsStatusPoor
                    else -> ZTele.conf.tpsStatusCritical
                }
            } catch (e: Exception) {
                ZTele.conf.tpsStatusExcellent
            }
        }
        
        /**
         * Получает список игроков онлайн
         */
        private fun getOnlinePlayersList(): String {
            val players = Bukkit.getOnlinePlayers()
                .filter { !ZTele.mgr.isPlayerHidden(it.name) }
                .map { it.name }
            
            return if (players.isEmpty()) {
                ZTele.conf.onlineCommandNoPlayers
            } else {
                // Используем настраиваемый формат для списка игроков
                val formattedPlayers = players.map { playerName ->
                    ZTele.conf.onlineCommandPlayerFormat.replace("%player%", playerName)
                }
                formattedPlayers.joinToString(ZTele.conf.onlineCommandSeparator)
            }
        }
        
        /**
         * Получает имя игрока из топа
         */
        private fun getTopPlayerName(position: Int, topType: TopType?, period: String?): String {
            if (position < 1 || position > 10) return "—"
            
            return when (topType) {
                TopType.PLAYTIME -> {
                    val cacheKey = "playtime_top_${period ?: "today"}"
                    val topData = getCachedOrFetch(cacheKey) {
                        getPlaytimeTopData(period)
                    } as? List<*>
                    
                    val entry = topData?.getOrNull(position - 1) as? StatsManager.PlaytimeEntry
                    entry?.playerName ?: "—"
                }
                
                TopType.BALANCE -> {
                    val cacheKey = "balance_top"
                    val topData = getCachedOrFetch(cacheKey) {
                        getBalanceTopData()
                    } as? List<*>
                    
                    val entry = topData?.getOrNull(position - 1) as? Pair<*, *>
                    entry?.first?.toString() ?: "—"
                }
                
                null -> "—"
            }
        }
        
        /**
         * Получает значение игрока из топа
         */
        private fun getTopPlayerValue(position: Int, topType: TopType?, period: String?): String {
            if (position < 1 || position > 10) return "—"
            
            return when (topType) {
                TopType.PLAYTIME -> {
                    val cacheKey = "playtime_top_${period ?: "today"}"
                    val topData = getCachedOrFetch(cacheKey) {
                        getPlaytimeTopData(period)
                    } as? List<*>
                    
                    val entry = topData?.getOrNull(position - 1) as? StatsManager.PlaytimeEntry
                    entry?.let { ZTele.stats.formatPlaytime(it.minutes) } ?: "—"
                }
                
                TopType.BALANCE -> {
                    val cacheKey = "balance_top"
                    val topData = getCachedOrFetch(cacheKey) {
                        getBalanceTopData()
                    } as? List<*>
                    
                    val entry = topData?.getOrNull(position - 1) as? Pair<*, *>
                    val balance = (entry?.second as? Double) ?: 0.0
                    String.format("%.2f", balance)
                }
                
                null -> "—"
            }
        }
        
        /**
         * Получает статистику игрока
         */
        private fun getPlayerStat(playerName: String, statType: String): String? {
            val player = Bukkit.getPlayer(playerName)
            
            return when (statType.lowercase()) {
                "balance" -> {
                    try {
                        val economy = ZTele.economy
                        if (economy != null && player != null) {
                            String.format("%.2f", economy.getBalance(player))
                        } else "0.00"
                    } catch (e: Exception) {
                        "0.00"
                    }
                }
                
                "playtime" -> {
                    val playtime = ZTele.stats.getPlayerPlaytime(playerName, StatsManager.StatsPeriod.TODAY)
                    ZTele.stats.formatPlaytime(playtime)
                }
                
                "ping" -> player?.ping?.toString() ?: "—"
                "online" -> if (player?.isOnline == true) "Да" else "Нет"
                
                else -> null
            }
        }
        
        /**
         * Получает данные топа по времени игры
         */
        private fun getPlaytimeTopData(period: String?): List<StatsManager.PlaytimeEntry> {
            return when (period) {
                null, "today", "сегодня" -> ZTele.stats.getPlaytimeTop(StatsManager.StatsPeriod.TODAY, 10)
                else -> {
                    val hours = ZTele.stats.parsePeriodToHours(period)
                    if (hours != null) {
                        ZTele.stats.getPlaytimeTopCustom(period, 10)
                    } else {
                        ZTele.stats.getPlaytimeTop(StatsManager.StatsPeriod.TODAY, 10)
                    }
                }
            }
        }
        
        /**
         * Получает данные топа по балансу
         */
        fun getBalanceTopData(): List<Pair<String, Double>> {
            return try {
                val economy = ZTele.economy
                if (economy == null) return emptyList()
                
                val allPlayers = mutableListOf<Pair<String, Double>>()
                
                // Получаем балансы всех игроков
                for (offlinePlayer in Bukkit.getOfflinePlayers()) {
                    if (offlinePlayer.name != null) {
                        val balance = economy.getBalance(offlinePlayer)
                        if (balance > 0) {
                            allPlayers.add(Pair(offlinePlayer.name!!, balance))
                        }
                    }
                }
                
                // Сортируем по убыванию баланса и берем топ-10
                allPlayers.sortedByDescending { it.second }.take(10)
            } catch (e: Exception) {
                emptyList()
            }
        }
        
        /**
         * Кэширование с автоматическим обновлением
         */
        private fun getCachedOrFetch(key: String, fetcher: () -> Any): Any {
            val cached = cache[key]
            val now = System.currentTimeMillis()
            
            return if (cached != null && (now - cached.timestamp) < CACHE_DURATION_MS) {
                cached.data
            } else {
                val freshData = fetcher()
                cache[key] = CacheEntry(freshData, now)
                freshData
            }
        }
        
        /**
         * Очищает кэш
         */
        fun clearCache() {
            cache.clear()
        }
        
        /**
         * Создает builder для быстрой настройки контекста топа
         */
        fun createTopContext(topType: TopType, period: String? = null): PlaceholderContext {
            return PlaceholderContext().apply {
                this.topType = topType
                this.topPeriod = period ?: "сегодня"
            }
        }
        
        /**
         * Создает builder для игрока
         */
        fun createPlayerContext(player: Player): PlaceholderContext {
            return PlaceholderContext().apply {
                this.player = player
                this.playerName = player.name
            }
        }
        
        /**
         * Создает builder для произвольных плейсхолдеров
         */
        fun createCustomContext(placeholders: Map<String, String>): PlaceholderContext {
            return PlaceholderContext().apply {
                this.customPlaceholders.putAll(placeholders)
            }
        }
    }
    
    /**
     * Запись кэша
     */
    private data class CacheEntry(
        val data: Any,
        val timestamp: Long
    )
    
    /**
     * Типы топов
     */
    enum class TopType {
        PLAYTIME,
        BALANCE
    }
    
    /**
     * Контекст для обработки плейсхолдеров
     */
    class PlaceholderContext {
        var player: Player? = null
        var playerName: String? = null
        var topType: TopType? = null
        var topPeriod: String? = null
        var topCount: Int? = null
        val customPlaceholders = mutableMapOf<String, String>()
        
        fun addCustom(key: String, value: String): PlaceholderContext {
            customPlaceholders[key] = value
            return this
        }
        
        fun addCustom(key: String, value: Int): PlaceholderContext {
            customPlaceholders[key] = value.toString()
            return this
        }
        
        fun addCustom(placeholders: Map<String, String>): PlaceholderContext {
            customPlaceholders.putAll(placeholders)
            return this
        }
    }
}
