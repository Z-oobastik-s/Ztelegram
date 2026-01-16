package org.zoobastiks.ztelegram.mgr

import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitTask
import org.zoobastiks.ztelegram.ZTele
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * Менеджер отложенного рестарта сервера с уведомлениями
 */
class RestartManager(private val plugin: ZTele) {
    
    private var activeRestartTask: RestartTask? = null
    private val scheduledTasks = ConcurrentHashMap<String, BukkitTask>()
    
    /**
     * Планирует рестарт сервера через указанное количество минут
     */
    fun scheduleRestart(delayMinutes: Int, initiator: String): Boolean {
        // Проверяем, есть ли уже активный таймер
        if (activeRestartTask != null) {
            return false // Таймер уже активен
        }
        
        plugin.logger.info("⏰ Планирование рестарта через $delayMinutes минут от $initiator")
        
        // Создаем новую задачу рестарта
        activeRestartTask = RestartTask(delayMinutes, initiator)
        
        // Отправляем начальные уведомления
        sendInitialNotifications(delayMinutes, initiator)
        
        // Планируем все уведомления
        scheduleWarnings(delayMinutes)
        
        // Планируем финальный рестарт
        scheduleFinalRestart(delayMinutes)
        
        return true
    }
    
    /**
     * Отменяет запланированный рестарт
     */
    fun cancelScheduledRestart(admin: String): Boolean {
        if (activeRestartTask == null) {
            return false
        }
        
        // Отменяем все запланированные задачи
        scheduledTasks.values.forEach { task ->
            task.cancel()
        }
        scheduledTasks.clear()
        
        // Очищаем активную задачу
        activeRestartTask = null
        
        // Отправляем уведомления об отмене
        sendCancellationNotifications(admin)
        
        plugin.logger.info("🚫 Рестарт отменен администратором: $admin")
        return true
    }
    
    /**
     * Получает информацию об активном таймере
     */
    fun getActiveRestartInfo(): RestartTask? = activeRestartTask
    
    /**
     * Отправляет начальные уведомления о запуске таймера
     */
     private fun sendInitialNotifications(delayMinutes: Int, @Suppress("UNUSED_PARAMETER") initiator: String) {
        // Серверное уведомление
        val serverCommand = ZTele.conf.restartServerTimerStarted
            .replace("%time%", formatTime(delayMinutes))
        
        executeServerCommand(serverCommand)
        
        // Telegram уведомление будет отправлено из TBot.kt
    }
    
    /**
     * Планирует предупреждения по времени
     */
    private fun scheduleWarnings(totalMinutes: Int) {
        if (ZTele.conf.debugEnabled) {
            plugin.logger.info("⏰ [RestartManager] Планируем предупреждения для рестарта через $totalMinutes минут")
            plugin.logger.info("⏰ [RestartManager] Загружено предупреждений в минутах: ${ZTele.conf.restartWarningMinutes.size}")
            plugin.logger.info("⏰ [RestartManager] Загружено предупреждений в секундах: ${ZTele.conf.restartWarningSeconds.size}")
        }
        
        // Предупреждения в минутах
        ZTele.conf.restartWarningMinutes.forEach { warning ->
            val warningTime = warning.time
            val warningCommand = warning.command
            
            if (warningTime <= totalMinutes) {
                val delayTicks = (totalMinutes - warningTime) * 60 * 20L
                if (ZTele.conf.debugEnabled) {
                    plugin.logger.info("⏰ [RestartManager] Планируем предупреждение через ${totalMinutes - warningTime} минут: $warningCommand")
                }
                
                val task = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                    if (ZTele.conf.debugEnabled) {
                        plugin.logger.info("⏰ [RestartManager] Выполняем предупреждение: $warningCommand")
                    }
                    executeServerCommand(warningCommand)
                }, delayTicks)
                
                scheduledTasks["warning_${warningTime}m"] = task
            } else {
                if (ZTele.conf.debugEnabled) {
                    plugin.logger.info("⏰ [RestartManager] Пропускаем предупреждение $warningTime минут (больше общего времени $totalMinutes)")
                }
            }
        }
        
        // Предупреждения в секундах
        if (totalMinutes >= 1) {
            ZTele.conf.restartWarningSeconds.forEach { warning ->
                val warningTimeSeconds = warning.time
                val warningCommand = warning.command
                
                val totalSeconds = totalMinutes * 60
                if (warningTimeSeconds <= totalSeconds) {
                    val delayTicks = (totalSeconds - warningTimeSeconds) * 20L
                    if (ZTele.conf.debugEnabled) {
                        plugin.logger.info("⏰ [RestartManager] Планируем предупреждение через ${totalSeconds - warningTimeSeconds} секунд: $warningCommand")
                    }
                    
                    val task = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                        if (ZTele.conf.debugEnabled) {
                            plugin.logger.info("⏰ [RestartManager] Выполняем предупреждение: $warningCommand")
                        }
                        executeServerCommand(warningCommand)
                    }, delayTicks)
                    
                    scheduledTasks["warning_${warningTimeSeconds}s"] = task
                } else {
                    if (ZTele.conf.debugEnabled) {
                        plugin.logger.info("⏰ [RestartManager] Пропускаем предупреждение $warningTimeSeconds секунд (больше общего времени $totalSeconds секунд)")
                    }
                }
            }
        } else {
            if (ZTele.conf.debugEnabled) {
                plugin.logger.info("⏰ [RestartManager] Пропускаем предупреждения в секундах (общее время $totalMinutes минут < 1)")
            }
        }
    }
    
    /**
     * Планирует финальный рестарт
     */
    private fun scheduleFinalRestart(delayMinutes: Int) {
        val delayTicks = delayMinutes * 60 * 20L
        
        val task = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            executeRestart()
        }, delayTicks)
        
        scheduledTasks["final_restart"] = task
    }
    
    /**
     * Выполняет финальный рестарт
     */
    private fun executeRestart() {
        // Финальное сообщение
        executeServerCommand(ZTele.conf.restartServerFinalCommand)
        
        // Выполняем команды перед рестартом
        ZTele.conf.restartPreCommands.forEach { command ->
            executeServerCommand(command)
        }
        
        // Небольшая задержка перед рестартом
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            executeServerCommand(ZTele.conf.restartCommand)
            activeRestartTask = null
        }, 60L) // 3 секунды задержка
    }
    
    /**
     * Отправляет уведомления об отмене
     */
    private fun sendCancellationNotifications(@Suppress("UNUSED_PARAMETER") admin: String) {
        // Серверное уведомление
        executeServerCommand(ZTele.conf.restartServerTimerCancelled)
        
        // Telegram уведомление будет отправлено из TBot.kt
    }
    
    /**
     * Выполняет команду на сервере
     */
    private fun executeServerCommand(command: String) {
        Bukkit.getScheduler().runTask(plugin, Runnable {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
        })
    }
    
    /**
     * Форматирует время в читаемый вид
     */
    private fun formatTime(minutes: Int): String {
        return when {
            minutes == 1 -> "1 минуту"
            minutes < 5 -> "$minutes минуты"
            else -> "$minutes минут"
        }
    }
    
    /**
     * Получает оставшееся время до рестарта
     */
    fun getRemainingTime(): String? {
        val task = activeRestartTask ?: return null
        val elapsed = System.currentTimeMillis() - task.startTime
        val remaining = (task.delayMinutes * 60 * 1000) - elapsed
        
        if (remaining <= 0) return "менее минуты"
        
        val remainingMinutes = (remaining / (60 * 1000)).toInt()
        return formatTime(remainingMinutes)
    }
    
    /**
     * Класс для хранения информации о задаче рестарта
     */
    data class RestartTask(
        val delayMinutes: Int,
        val initiator: String,
        val startTime: Long = System.currentTimeMillis()
    )
    
    /**
     * Класс для конфигурации предупреждений
     */
    data class WarningConfig(
        val time: Int,
        val command: String
    )
}
