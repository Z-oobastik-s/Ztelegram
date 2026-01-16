package org.zoobastiks.ztelegram.utils

import org.zoobastiks.ztelegram.ZTele
import java.util.regex.Pattern

/**
 * Профессиональный процессор плейсхолдеров для топов
 * с безопасной обработкой и валидацией данных
 * 
 * @author Zoobastiks
 */
class TopPlaceholderProcessor {
    
    companion object {
        // Паттерны для различных типов плейсхолдеров
        private val PLAYER_PATTERN = Pattern.compile("player_(\\d+)")
        private val TIME_PATTERN = Pattern.compile("time_(\\d+)")
        private val BALANCE_PATTERN = Pattern.compile("balance_(\\d+)")
        private val GENERAL_PLACEHOLDER_PATTERN = Pattern.compile("%([^%]+)%")
        
        /**
         * Обрабатывает плейсхолдеры топа по времени игры
         */
        fun processPlaytimeTop(template: String, period: String?): String {
            if (ZTele.conf.debugEnabled) {
            ZTele.instance.logger.info("[TopPlaceholderProcessor] Processing playtime top for period: $period")
        }
            if (template.isBlank()) return template
            
            return try {
                val topResult = TopManager.getPlaytimeTop(period)
                
                when (topResult) {
                    is TopManager.TopResult.Success -> {
                        processTemplate(template) { placeholder ->
                            resolvePlaytimePlaceholder(placeholder, topResult.entries, topResult.period)
                        }
                    }
                    is TopManager.TopResult.Error -> {
                        ZTele.instance.logger.warning("Error in playtime top: ${topResult.message}")
                        "❌ Ошибка получения данных топа по времени игры"
                    }
                }
            } catch (e: Exception) {
                ZTele.instance.logger.severe("Critical error in processPlaytimeTop: ${e.message}")
                "❌ Критическая ошибка обработки топа"
            }
        }
        
        /**
         * Обрабатывает плейсхолдеры топа по балансу
         */
        fun processBalanceTop(template: String): String {
            if (ZTele.conf.debugEnabled) {
            ZTele.instance.logger.info("[TopPlaceholderProcessor] Processing balance top")
        }
            if (template.isBlank()) return template
            
            return try {
                val topResult = TopManager.getBalanceTop()
                
                when (topResult) {
                    is TopManager.TopResult.Success -> {
                        processTemplate(template) { placeholder ->
                            resolveBalancePlaceholder(placeholder, topResult.entries)
                        }
                    }
                    is TopManager.TopResult.Error -> {
                        ZTele.instance.logger.warning("Error in balance top: ${topResult.message}")
                        "❌ Ошибка получения данных топа по балансу"
                    }
                }
            } catch (e: Exception) {
                ZTele.instance.logger.severe("Critical error in processBalanceTop: ${e.message}")
                "❌ Критическая ошибка обработки топа"
            }
        }
        
        /**
         * Универсальный процессор шаблонов с безопасной заменой
         */
        private fun processTemplate(template: String, resolver: (String) -> String?): String {
            val result = StringBuilder(template)
            val matcher = GENERAL_PLACEHOLDER_PATTERN.matcher(template)
            val replacements = mutableListOf<Replacement>()
            
            // Собираем все замены
            while (matcher.find()) {
                val placeholder = matcher.group(1)
                val replacement = resolver(placeholder)
                
                if (replacement != null) {
                    replacements.add(
                        Replacement(
                            start = matcher.start(),
                            end = matcher.end(),
                            replacement = replacement
                        )
                    )
                }
            }
            
            // Применяем замены в обратном порядке для сохранения позиций
            replacements.sortedByDescending { it.start }.forEach { replacement ->
                result.replace(replacement.start, replacement.end, replacement.replacement)
            }
            
            return result.toString()
        }
        
        /**
         * Разрешает плейсхолдеры для топа по времени игры
         */
        private fun resolvePlaytimePlaceholder(
            placeholder: String, 
            entries: List<TopManager.PlaytimeEntry>,
            period: String
        ): String? {
            if (ZTele.conf.debugEnabled) {
                ZTele.instance.logger.info("[TopPlaceholderProcessor] Resolving playtime placeholder: $placeholder, entries size: ${entries.size}")
            }
            return when {
                // Имена игроков (player_1, player_2, ...)
                PLAYER_PATTERN.matcher(placeholder).matches() -> {
                    val matcher = PLAYER_PATTERN.matcher(placeholder)
                    if (matcher.find()) {
                        val position = matcher.group(1).toIntOrNull()
                        if (position != null && position >= 1 && position <= 10) {
                            val result = entries.getOrNull(position - 1)?.playerName ?: "—"
                            if (ZTele.conf.debugEnabled) {
                            ZTele.instance.logger.info("[TopPlaceholderProcessor] Player placeholder $placeholder -> position $position -> result: $result")
                        }
                        result
                        } else {
                            ZTele.instance.logger.warning("Invalid player position: $position in placeholder: $placeholder")
                            "—"
                        }
                    } else "—"
                }
                
                // Время игры (time_1, time_2, ...)
                TIME_PATTERN.matcher(placeholder).matches() -> {
                    val matcher = TIME_PATTERN.matcher(placeholder)
                    if (matcher.find()) {
                        val position = matcher.group(1).toIntOrNull()
                        if (position != null && position >= 1 && position <= 10) {
                            val result = entries.getOrNull(position - 1)?.formattedTime ?: "—"
                            if (ZTele.conf.debugEnabled) {
                            ZTele.instance.logger.info("[TopPlaceholderProcessor] Time placeholder $placeholder -> position $position -> result: $result")
                        }
                        result
                        } else {
                            ZTele.instance.logger.warning("Invalid time position: $position in placeholder: $placeholder")
                            "—"
                        }
                    } else "—"
                }
                
                // Период
                placeholder == "period" -> period
                
                // Количество игроков в топе
                placeholder == "count" -> entries.size.toString()
                
                else -> {
                    ZTele.instance.logger.info("[TopPlaceholderProcessor] Unknown playtime placeholder: $placeholder")
                    null
                }
            }
        }
        
        /**
         * Разрешает плейсхолдеры для топа по балансу
         */
        private fun resolveBalancePlaceholder(
            placeholder: String, 
            entries: List<TopManager.BalanceEntry>
        ): String? {
            if (ZTele.conf.debugEnabled) {
                ZTele.instance.logger.info("[TopPlaceholderProcessor] Resolving balance placeholder: $placeholder, entries size: ${entries.size}")
            }
            return when {
                // Имена игроков (player_1, player_2, ...)
                PLAYER_PATTERN.matcher(placeholder).matches() -> {
                    val matcher = PLAYER_PATTERN.matcher(placeholder)
                    if (matcher.find()) {
                        val position = matcher.group(1).toIntOrNull()
                        if (position != null && position >= 1 && position <= 10) {
                            val result = entries.getOrNull(position - 1)?.playerName ?: "—"
                            if (ZTele.conf.debugEnabled) {
                            ZTele.instance.logger.info("[TopPlaceholderProcessor] Player placeholder $placeholder -> position $position -> result: $result")
                        }
                        result
                        } else {
                            ZTele.instance.logger.warning("Invalid player position: $position in placeholder: $placeholder")
                            "—"
                        }
                    } else "—"
                }
                
                // Балансы (balance_1, balance_2, ...)
                BALANCE_PATTERN.matcher(placeholder).matches() -> {
                    val matcher = BALANCE_PATTERN.matcher(placeholder)
                    if (matcher.find()) {
                        val position = matcher.group(1).toIntOrNull()
                        if (position != null && position >= 1 && position <= 10) {
                            val result = entries.getOrNull(position - 1)?.formattedBalance ?: "0.00"
                            if (ZTele.conf.debugEnabled) {
                            ZTele.instance.logger.info("[TopPlaceholderProcessor] Balance placeholder $placeholder -> position $position -> result: $result")
                        }
                        result
                        } else {
                            ZTele.instance.logger.warning("Invalid balance position: $position in placeholder: $placeholder")
                            "0.00"
                        }
                    } else "0.00"
                }
                
                // Количество игроков в топе
                placeholder == "count" -> entries.size.toString()
                
                else -> {
                    ZTele.instance.logger.info("[TopPlaceholderProcessor] Unknown balance placeholder: $placeholder")
                    null
                }
            }
        }
        
        /**
         * Получает информацию о конкретном игроке из топа по времени
         */
        fun getPlaytimePlayerInfo(position: Int, period: String?): PlaytimePlayerInfo? {
            return try {
                val entry = TopManager.getPlaytimePlayer(position, period)
                entry?.let {
                    PlaytimePlayerInfo(
                        position = position,
                        playerName = it.playerName,
                        minutes = it.minutes,
                        formattedTime = it.formattedTime
                    )
                }
            } catch (e: Exception) {
                ZTele.instance.logger.warning("Error getting playtime player info for position $position: ${e.message}")
                null
            }
        }
        
        /**
         * Получает информацию о конкретном игроке из топа по балансу
         */
        fun getBalancePlayerInfo(position: Int): BalancePlayerInfo? {
            return try {
                val entry = TopManager.getBalancePlayer(position)
                entry?.let {
                    BalancePlayerInfo(
                        position = position,
                        playerName = it.playerName,
                        balance = it.balance,
                        formattedBalance = it.formattedBalance
                    )
                }
            } catch (e: Exception) {
                ZTele.instance.logger.warning("Error getting balance player info for position $position: ${e.message}")
                null
            }
        }
    }
    
    // Data classes
    
    data class PlaytimePlayerInfo(
        val position: Int,
        val playerName: String,
        val minutes: Long,
        val formattedTime: String
    )
    
    data class BalancePlayerInfo(
        val position: Int,
        val playerName: String,
        val balance: Double,
        val formattedBalance: String
    )
    
    private data class Replacement(
        val start: Int,
        val end: Int,
        val replacement: String
    )
}
