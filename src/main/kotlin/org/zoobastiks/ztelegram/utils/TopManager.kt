package org.zoobastiks.ztelegram.utils

import org.bukkit.Bukkit
import org.zoobastiks.ztelegram.ZTele
import org.zoobastiks.ztelegram.stats.StatsManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Профессиональная система управления топами с надежным кэшированием
 * и безопасной обработкой данных
 * 
 * @author Zoobastiks
 */
class TopManager {
    
    companion object {
        private const val CACHE_DURATION_MS = 30_000L // 30 секунд
        private const val MAX_TOP_SIZE = 10
        
        // Thread-safe кэш с блокировками для чтения/записи
        private val cache = ConcurrentHashMap<String, CacheEntry>()
        private val cacheLock = ReentrantReadWriteLock()
        
        /**
         * Получает топ по времени игры с безопасной обработкой
         */
        fun getPlaytimeTop(period: String?, maxSize: Int = MAX_TOP_SIZE): TopResult<PlaytimeEntry> {
            if (ZTele.conf.debugEnabled) {
            ZTele.instance.logger.info("[TopManager] Getting playtime top for period: $period, maxSize: $maxSize")
        }
            val cacheKey = "playtime_${period ?: "today"}_$maxSize"
            
            return cacheLock.read {
                val cached = cache[cacheKey]
                val now = System.currentTimeMillis()
                
                if (cached != null && (now - cached.timestamp) < CACHE_DURATION_MS) {
                    @Suppress("UNCHECKED_CAST")
                    cached.data as TopResult<PlaytimeEntry>
                } else {
                    cacheLock.write {
                        // Double-check после получения write lock
                        val recheck = cache[cacheKey]
                        if (recheck != null && (now - recheck.timestamp) < CACHE_DURATION_MS) {
                            @Suppress("UNCHECKED_CAST")
                            recheck.data as TopResult<PlaytimeEntry>
                        } else {
                            val result = fetchPlaytimeTop(period, maxSize)
                            cache[cacheKey] = CacheEntry(result, now)
                            result
                        }
                    }
                }
            }
        }
        
        /**
         * Получает топ по балансу с безопасной обработкой
         */
        fun getBalanceTop(maxSize: Int = MAX_TOP_SIZE): TopResult<BalanceEntry> {
            if (ZTele.conf.debugEnabled) {
            ZTele.instance.logger.info("[TopManager] Getting balance top, maxSize: $maxSize")
        }
            val cacheKey = "balance_$maxSize"
            
            return cacheLock.read {
                val cached = cache[cacheKey]
                val now = System.currentTimeMillis()
                
                if (cached != null && (now - cached.timestamp) < CACHE_DURATION_MS) {
                    @Suppress("UNCHECKED_CAST")
                    cached.data as TopResult<BalanceEntry>
                } else {
                    cacheLock.write {
                        val recheck = cache[cacheKey]
                        if (recheck != null && (now - recheck.timestamp) < CACHE_DURATION_MS) {
                            @Suppress("UNCHECKED_CAST")
                            recheck.data as TopResult<BalanceEntry>
                        } else {
                            val result = fetchBalanceTop(maxSize)
                            cache[cacheKey] = CacheEntry(result, now)
                            result
                        }
                    }
                }
            }
        }
        
        /**
         * Получает конкретного игрока из топа по времени игры
         */
        fun getPlaytimePlayer(position: Int, period: String?): PlaytimeEntry? {
            if (position < 1 || position > MAX_TOP_SIZE) {
                ZTele.instance.logger.warning("Invalid position requested: $position. Must be between 1 and $MAX_TOP_SIZE")
                return null
            }
            
            val top = getPlaytimeTop(period)
            return when (top) {
                is TopResult.Success -> top.entries.getOrNull(position - 1)
                is TopResult.Error -> null
            }
        }
        
        /**
         * Получает конкретного игрока из топа по балансу
         */
        fun getBalancePlayer(position: Int): BalanceEntry? {
            if (position < 1 || position > MAX_TOP_SIZE) {
                ZTele.instance.logger.warning("Invalid position requested: $position. Must be between 1 and $MAX_TOP_SIZE")
                return null
            }
            
            val top = getBalanceTop()
            return when (top) {
                is TopResult.Success -> top.entries.getOrNull(position - 1)
                is TopResult.Error -> null
            }
        }
        
        /**
         * Очищает кэш принудительно
         */
        fun clearCache() {
            cacheLock.write {
                cache.clear()
            }
            ZTele.instance.logger.info("TopManager cache cleared")
        }
        
        /**
         * Очищает кэш для конкретного типа
         */
        fun clearCache(type: TopType) {
            cacheLock.write {
                val keysToRemove = cache.keys.filter { key ->
                    when (type) {
                        TopType.PLAYTIME -> key.startsWith("playtime_")
                        TopType.BALANCE -> key.startsWith("balance_")
                    }
                }
                keysToRemove.forEach { cache.remove(it) }
            }
            ZTele.instance.logger.info("TopManager cache cleared for type: $type")
        }
        
        /**
         * Получает статистику кэша
         */
        fun getCacheStats(): CacheStats {
            return cacheLock.read {
                CacheStats(
                    totalEntries = cache.size,
                    playtimeEntries = cache.keys.count { it.startsWith("playtime_") },
                    balanceEntries = cache.keys.count { it.startsWith("balance_") }
                )
            }
        }
        
        // Приватные методы для получения данных
        
        private fun fetchPlaytimeTop(period: String?, maxSize: Int): TopResult<PlaytimeEntry> {
            return try {
                val entries = when (period) {
                    null, "today", "сегодня" -> {
                        ZTele.stats.getPlaytimeTop(StatsManager.StatsPeriod.TODAY, maxSize)
                    }
                    else -> {
                        val hours = ZTele.stats.parsePeriodToHours(period)
                        if (hours != null) {
                            ZTele.stats.getPlaytimeTopCustom(period, maxSize)
                        } else {
                            ZTele.stats.getPlaytimeTop(StatsManager.StatsPeriod.TODAY, maxSize)
                        }
                    }
                }
                
                val validEntries = entries.map { entry ->
                    PlaytimeEntry(
                        playerName = entry.playerName,
                        minutes = entry.minutes,
                        formattedTime = ZTele.stats.formatPlaytime(entry.minutes)
                    )
                }.take(maxSize)
                
                        if (ZTele.conf.debugEnabled) {
                            ZTele.instance.logger.info("[TopManager] Playtime top result: ${validEntries.size} entries")
                            validEntries.forEachIndexed { index, entry ->
                                ZTele.instance.logger.info("[TopManager] Playtime #${index + 1}: ${entry.playerName} = ${entry.formattedTime}")
                            }
                        }
                
                TopResult.success(validEntries, period ?: "today")
                
            } catch (e: Exception) {
                ZTele.instance.logger.severe("Error fetching playtime top: ${e.message}")
                TopResult.error("Failed to fetch playtime data: ${e.message}")
            }
        }
        
        private fun fetchBalanceTop(maxSize: Int): TopResult<BalanceEntry> {
            return try {
                val economy = ZTele.economy
                if (economy == null) {
                    return TopResult.error("Economy plugin not available")
                }
                
                val allPlayers = mutableListOf<BalanceEntry>()
                
                // Получаем балансы всех игроков с валидацией
                for (offlinePlayer in Bukkit.getOfflinePlayers()) {
                    try {
                        val playerName = offlinePlayer.name
                        if (playerName != null && playerName.isNotBlank()) {
                            val balance = economy.getBalance(offlinePlayer)
                            if (balance > 0 && balance.isFinite()) {
                                allPlayers.add(
                                    BalanceEntry(
                                        playerName = playerName,
                                        balance = balance,
                                        formattedBalance = String.format("%.2f", balance)
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        ZTele.instance.logger.warning("Error getting balance for player ${offlinePlayer.name}: ${e.message}")
                    }
                }
                
                val topEntries = allPlayers
                    .sortedByDescending { it.balance }
                    .take(maxSize)
                
                        if (ZTele.conf.debugEnabled) {
                            ZTele.instance.logger.info("[TopManager] Balance top result: ${topEntries.size} entries")
                            topEntries.forEachIndexed { index, entry ->
                                ZTele.instance.logger.info("[TopManager] Balance #${index + 1}: ${entry.playerName} = ${entry.formattedBalance}")
                            }
                        }
                
                TopResult.success(topEntries, "all_time")
                
            } catch (e: Exception) {
                ZTele.instance.logger.severe("Error fetching balance top: ${e.message}")
                TopResult.error("Failed to fetch balance data: ${e.message}")
            }
        }
    }
    
    // Data classes
    
    data class PlaytimeEntry(
        val playerName: String,
        val minutes: Long,
        val formattedTime: String
    )
    
    data class BalanceEntry(
        val playerName: String,
        val balance: Double,
        val formattedBalance: String
    )
    
    sealed class TopResult<T> {
        data class Success<T>(
            val entries: List<T>,
            val period: String,
            val timestamp: Long = System.currentTimeMillis()
        ) : TopResult<T>()
        
        data class Error<T>(
            val message: String,
            val timestamp: Long = System.currentTimeMillis()
        ) : TopResult<T>()
        
        companion object {
            fun <T> success(entries: List<T>, period: String): TopResult<T> {
                return Success(entries, period)
            }
            
            fun <T> error(message: String): TopResult<T> {
                return Error(message)
            }
        }
        
        val isSuccess: Boolean get() = this is Success
        val isError: Boolean get() = this is Error
    }
    
    enum class TopType {
        PLAYTIME,
        BALANCE
    }
    
    data class CacheStats(
        val totalEntries: Int,
        val playtimeEntries: Int,
        val balanceEntries: Int
    )
    
    private data class CacheEntry(
        val data: Any,
        val timestamp: Long
    )
}
