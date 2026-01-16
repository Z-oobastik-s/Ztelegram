package org.zoobastiks.ztelegram.utils

import java.util.UUID

/**
 * Профессиональный процессор плейсхолдеров с полной защитой от конфликтов
 * Использует UUID токены для гарантированной безопасности замен
 * 
 * DEPRECATED: Используйте PlaceholderEngine для новых функций
 * Этот класс сохранен для обратной совместимости
 */
class PlaceholderProcessor {
    
    companion object {
        /**
         * АБСОЛЮТНО БЕЗОПАСНАЯ замена плейсхолдеров
         * Использует UUID токены для полного исключения конфликтов
         * 
         * @deprecated Используйте PlaceholderEngine.process() для новых функций
         */
        fun process(template: String, placeholders: Map<String, String>): String {
            if (placeholders.isEmpty()) return template
            
            // Используем новый движок с кастомными плейсхолдерами
            val context = PlaceholderEngine.createCustomContext(placeholders)
            return PlaceholderEngine.process(template, context)
        }
        
        /**
         * Создает билдер для плейсхолдеров топа (1-10 позиций)
         * @deprecated Используйте PlaceholderEngine.createTopContext(TopType.PLAYTIME)
         */
        fun createTopBuilder(): TopPlaceholderBuilder {
            return TopPlaceholderBuilder()
        }
        
        /**
         * Создает билдер для плейсхолдеров топа балансов (1-10 позиций)
         * @deprecated Используйте PlaceholderEngine.createTopContext(TopType.BALANCE)
         */
        fun createTopBalBuilder(): TopBalPlaceholderBuilder {
            return TopBalPlaceholderBuilder()
        }
        
        /**
         * Создает билдер для общих плейсхолдеров
         * @deprecated Используйте PlaceholderEngine.createCustomContext()
         */
        fun createBuilder(): PlaceholderBuilder {
            return PlaceholderBuilder()
        }
    }
    
    /**
     * Билдер для создания плейсхолдеров топа по времени игры
     * @deprecated Используйте PlaceholderEngine.createTopContext(TopType.PLAYTIME)
     */
    class TopPlaceholderBuilder {
        private val placeholders = mutableMapOf<String, String>()
        
        fun setPeriod(period: String): TopPlaceholderBuilder {
            placeholders["%period%"] = period
            return this
        }
        
        fun setCount(count: Int): TopPlaceholderBuilder {
            placeholders["%count%"] = count.toString()
            return this
        }
        
        fun addPlayer(position: Int, playerName: String, playtime: String): TopPlaceholderBuilder {
            if (position in 1..10) {
                placeholders["%player_$position%"] = playerName
                placeholders["%time_$position%"] = playtime
            }
            return this
        }
        
        fun fillEmpty(from: Int, to: Int = 10): TopPlaceholderBuilder {
            for (i in from..to) {
                if (!placeholders.containsKey("%player_$i%")) {
                    placeholders["%player_$i%"] = "—"
                    placeholders["%time_$i%"] = "—"
                }
            }
            return this
        }
        
        fun build(): Map<String, String> = placeholders.toMap()
    }
    
    /**
     * Билдер для создания плейсхолдеров топа по балансу
     * @deprecated Используйте PlaceholderEngine.createTopContext(TopType.BALANCE)
     */
    class TopBalPlaceholderBuilder {
        private val placeholders = mutableMapOf<String, String>()
        
        fun setCount(count: Int): TopBalPlaceholderBuilder {
            placeholders["%count%"] = count.toString()
            return this
        }
        
        fun addPlayer(position: Int, playerName: String, balance: String): TopBalPlaceholderBuilder {
            if (position in 1..10) {
                placeholders["%player_$position%"] = playerName
                placeholders["%balance_$position%"] = balance
            }
            return this
        }
        
        fun fillEmpty(from: Int, to: Int = 10): TopBalPlaceholderBuilder {
            for (i in from..to) {
                if (!placeholders.containsKey("%player_$i%")) {
                    placeholders["%player_$i%"] = "—"
                    placeholders["%balance_$i%"] = "0.00"
                }
            }
            return this
        }
        
        fun build(): Map<String, String> = placeholders.toMap()
    }
    
    /**
     * Универсальный билдер для любых плейсхолдеров
     * @deprecated Используйте PlaceholderEngine.createCustomContext()
     */
    class PlaceholderBuilder {
        private val placeholders = mutableMapOf<String, String>()
        
        fun add(key: String, value: String): PlaceholderBuilder {
            placeholders[key] = value
            return this
        }
        
        fun add(key: String, value: Int): PlaceholderBuilder {
            placeholders[key] = value.toString()
            return this
        }
        
        fun addAll(map: Map<String, String>): PlaceholderBuilder {
            placeholders.putAll(map)
            return this
        }
        
        fun build(): Map<String, String> = placeholders.toMap()
    }
}
