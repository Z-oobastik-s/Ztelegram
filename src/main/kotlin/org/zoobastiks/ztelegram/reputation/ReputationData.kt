package org.zoobastiks.ztelegram.reputation

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Данные о репутации игрока
 */
data class ReputationData(
    val playerName: String,
    var positiveRep: Int = 0,
    var negativeRep: Int = 0,
    val receivedFrom: MutableMap<String, ReputationEntry> = mutableMapOf()
) {
    /**
     * Общий рейтинг репутации
     */
    val totalReputation: Int
        get() = positiveRep - negativeRep
    
    /**
     * Процент положительной репутации
     */
    val positivePercentage: Double
        get() {
            val total = positiveRep + negativeRep
            return if (total > 0) (positiveRep.toDouble() / total * 100) else 0.0
        }
    
    /**
     * Уровень репутации (для красивого отображения)
     */
    val reputationLevel: ReputationLevel
        get() = when {
            totalReputation >= 100 -> ReputationLevel.LEGENDARY
            totalReputation >= 50 -> ReputationLevel.EXCELLENT
            totalReputation >= 25 -> ReputationLevel.VERY_GOOD
            totalReputation >= 10 -> ReputationLevel.GOOD
            totalReputation >= 0 -> ReputationLevel.NEUTRAL
            totalReputation >= -10 -> ReputationLevel.BAD
            totalReputation >= -25 -> ReputationLevel.VERY_BAD
            else -> ReputationLevel.TERRIBLE
        }
    
    /**
     * Может ли игрок получить репутацию от указанного источника
     */
    fun canReceiveFrom(source: String, cooldownMinutes: Int): Boolean {
        val entry = receivedFrom[source] ?: return true
        return entry.canGiveAgain(cooldownMinutes)
    }
    
    /**
     * Добавляет положительную репутацию
     */
    fun addPositive(source: String, reason: String? = null) {
        positiveRep++
        receivedFrom[source] = ReputationEntry(
            source = source,
            isPositive = true,
            timestamp = LocalDateTime.now(),
            reason = reason
        )
    }
    
    /**
     * Добавляет отрицательную репутацию
     */
    fun addNegative(source: String, reason: String? = null) {
        negativeRep++
        receivedFrom[source] = ReputationEntry(
            source = source,
            isPositive = false,
            timestamp = LocalDateTime.now(),
            reason = reason
        )
    }
    
    /**
     * Получает последнюю запись от источника
     */
    fun getLastEntryFrom(source: String): ReputationEntry? {
        return receivedFrom[source]
    }
    
    /**
     * Получает топ-5 последних записей
     */
    fun getRecentEntries(limit: Int = 5): List<ReputationEntry> {
        return receivedFrom.values
            .sortedByDescending { it.timestamp }
            .take(limit)
    }
}

/**
 * Запись о полученной репутации
 */
data class ReputationEntry(
    val source: String,
    val isPositive: Boolean,
    val timestamp: LocalDateTime,
    val reason: String? = null
) {
    /**
     * Может ли источник снова дать репутацию
     */
    fun canGiveAgain(cooldownMinutes: Int): Boolean {
        val now = LocalDateTime.now()
        val minutesPassed = java.time.Duration.between(timestamp, now).toMinutes()
        return minutesPassed >= cooldownMinutes
    }
    
    /**
     * Сколько минут осталось до следующей возможности
     */
    fun getRemainingCooldown(cooldownMinutes: Int): Long {
        val now = LocalDateTime.now()
        val minutesPassed = java.time.Duration.between(timestamp, now).toMinutes()
        return maxOf(0, cooldownMinutes - minutesPassed)
    }
    
    /**
     * Форматированная дата
     */
    fun getFormattedDate(): String {
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        return timestamp.format(formatter)
    }
}

/**
 * Уровни репутации
 */
enum class ReputationLevel(
    val displayName: String,
    val emoji: String,
    val color: String
) {
    LEGENDARY("Легендарный", "👑", "§6"),
    EXCELLENT("Отличный", "⭐", "§e"),
    VERY_GOOD("Очень хороший", "✨", "§a"),
    GOOD("Хороший", "👍", "§2"),
    NEUTRAL("Нейтральный", "➖", "§7"),
    BAD("Плохой", "👎", "§c"),
    VERY_BAD("Очень плохой", "💢", "§4"),
    TERRIBLE("Ужасный", "☠", "§4§l");
    
    /**
     * Полное отображение с эмодзи
     */
    fun getFullDisplay(): String = "$emoji $displayName"
    
    /**
     * Отображение с цветом для Minecraft
     */
    fun getColoredDisplay(): String = "$color$displayName"
}
