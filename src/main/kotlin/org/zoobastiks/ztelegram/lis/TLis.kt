package org.zoobastiks.ztelegram.lis

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.zoobastiks.ztelegram.ZTele
import org.zoobastiks.ztelegram.bot.TBot
import org.bukkit.Bukkit

class TLis(private val plugin: ZTele) : Listener {
    private val bot: TBot
        get() = ZTele.bot

    // Флаг для отслеживания обработанных сообщений чата
    private val processedMessages = mutableMapOf<String, Long>()

    // Время хранения кэша сообщений (в миллисекундах)
    private val MESSAGE_CACHE_EXPIRY = 1000L // 1 секунда

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val playerName = event.player.name
        val playerUuid = event.player.uniqueId

        // Записываем статистику входа игрока
        ZTele.stats.recordPlayerJoin(playerUuid, playerName)

        bot.sendPlayerJoinMessage(playerName)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val playerName = event.player.name
        val playerUuid = event.player.uniqueId

        // Записываем статистику выхода игрока (для подсчета времени сессии)
        ZTele.stats.recordPlayerQuit(playerUuid, playerName)

        bot.sendPlayerQuitMessage(playerName)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity

        // Получаем оригинальное английское сообщение о смерти через новый API (Paper 1.19+)
        val englishDeathMessage = try {
            val deathComponent = event.deathMessage()
            if (deathComponent != null) {
                PlainTextComponentSerializer.plainText().serialize(deathComponent)
            } else {
                "died" // Запасной вариант
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to get death message component: ${e.message}")
            "died" // Запасной вариант
        }

        // Определяем, какое сообщение отправлять (английское или русское)
        val finalDeathMessage = if (ZTele.conf.chatPlayerDeathUseRussianMessages) {
            // Получаем убийцу (если есть)
            val killer = player.killer ?: event.entity.lastDamageCause?.let { damageEvent ->
                if (damageEvent is org.bukkit.event.entity.EntityDamageByEntityEvent) {
                    damageEvent.damager
                } else {
                    null
                }
            }

            // Получаем причину смерти
            val damageCause = event.entity.lastDamageCause?.cause

            // Переводим сообщение на русский с помощью DeathMessageManager
            val russianMessage = ZTele.deathMessages.getDeathMessage(
                player = player,
                deathMessage = englishDeathMessage,
                killer = killer,
                cause = damageCause
            )

            // Если включен режим отладки, показываем оба сообщения
            if (ZTele.conf.chatPlayerDeathDebugMessages) {
                plugin.logger.info("╔════════════════════════════════════════════════════╗")
                plugin.logger.info("║ [Death Message Debug]                              ║")
                plugin.logger.info("╠════════════════════════════════════════════════════╣")
                plugin.logger.info("║ Player: ${player.name}")
                plugin.logger.info("║ Killer: ${killer?.name ?: "none"}")
                plugin.logger.info("║ Cause: ${damageCause?.name ?: "none"}")
                plugin.logger.info("║ Original (EN): $englishDeathMessage")
                plugin.logger.info("║ Translated (RU): $russianMessage")
                plugin.logger.info("╚════════════════════════════════════════════════════╝")
            }

            russianMessage
        } else {
            // Используем оригинальное английское сообщение
            englishDeathMessage
        }

        // Отправляем сообщение о смерти в Telegram
        bot.sendPlayerDeathMessage(player.name, finalDeathMessage)
    }

    /**
     * Обработчик нового API чата PaperMC
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onAsyncChat(event: AsyncChatEvent) {
        try {
            val playerName = event.player.name
            val message = PlainTextComponentSerializer.plainText().serialize(event.message())

            // Проверяем, не обрабатывали ли мы уже такое сообщение недавно
            if (!hasRecentlySentMessage(playerName, message)) {
                bot.sendPlayerChatMessage(playerName, message)
                // Сохраняем сообщение в кэше
                markMessageAsProcessed(playerName, message)
                plugin.logger.info("Message sent to Telegram from AsyncChatEvent: $playerName - $message")
            } else {
                plugin.logger.info("Skipping duplicate message from AsyncChatEvent: $playerName - $message")
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error processing AsyncChatEvent: ${e.message}")
        }
    }



    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerCommand(event: PlayerCommandPreprocessEvent) {
        val playerName = event.player.name
        val command = event.message.substring(1) // Remove the leading '/'

        // Log command to Telegram
        bot.sendPlayerCommandMessage(playerName, command)
    }

    /**
     * Проверяет, было ли недавно отправлено аналогичное сообщение
     */
    private fun hasRecentlySentMessage(playerName: String, message: String): Boolean {
        val key = "$playerName:$message"
        val currentTime = System.currentTimeMillis()

        // Очищаем старые записи
        val expiredKeys = processedMessages.filter { currentTime - it.value > MESSAGE_CACHE_EXPIRY }.keys
        expiredKeys.forEach { processedMessages.remove(it) }

        return processedMessages.containsKey(key)
    }

    /**
     * Отмечает сообщение как обработанное, чтобы избежать дублирования
     */
    private fun markMessageAsProcessed(playerName: String, message: String) {
        val key = "$playerName:$message"
        processedMessages[key] = System.currentTimeMillis()
    }
}
