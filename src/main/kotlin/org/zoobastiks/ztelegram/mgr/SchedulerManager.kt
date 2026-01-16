package org.zoobastiks.ztelegram.mgr

import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitTask
import org.zoobastiks.ztelegram.ZTele
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * Менеджер планировщика автоматических команд
 * Управляет ежедневными задачами и их выполнением
 */
class SchedulerManager(private val plugin: ZTele) {
    
    // Активные задачи планировщика
    private val activeTasks = ConcurrentHashMap<String, BukkitTask>()
    
    // Форматтер времени для парсинга HH:MM
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    
    /**
     * Запуск планировщика при включении плагина
     */
    fun start() {
        if (!ZTele.conf.schedulerEnabled) {
            plugin.logger.info("⏰ Планировщик команд отключен в конфигурации")
            return
        }
        
        plugin.logger.info("⏰ Запуск планировщика автоматических команд...")
        scheduleAllTasks()
        plugin.logger.info("✅ Планировщик команд запущен")
    }
    
    /**
     * Остановка планировщика при выключении плагина
     */
    fun stop() {
        plugin.logger.info("⏰ Остановка планировщика команд...")
        activeTasks.values.forEach { task ->
            task.cancel()
        }
        activeTasks.clear()
        plugin.logger.info("✅ Планировщик команд остановлен")
    }
    
    /**
     * Перезагрузка планировщика
     */
    fun reload() {
        stop()
        start()
    }
    
    /**
     * Планирование всех задач из конфигурации
     */
    private fun scheduleAllTasks() {
        val dailyTasks = ZTele.conf.schedulerDailyTasks
        
        for ((taskName, taskConfig) in dailyTasks) {
            if (!taskConfig.enabled) {
                plugin.logger.info("⏭️ Задача '$taskName' отключена, пропускаем")
                continue
            }
            
            try {
                scheduleTask(taskName, taskConfig)
                plugin.logger.info("✅ Задача '$taskName' запланирована на ${taskConfig.time}")
            } catch (e: Exception) {
                plugin.logger.severe("❌ Ошибка планирования задачи '$taskName': ${e.message}")
                plugin.logger.severe("💡 Проверьте формат времени в config.yml (должно быть HH:MM, например '06:00', а не '6:00')")
            }
        }
    }
    
    /**
     * Планирование одной задачи
     */
    private fun scheduleTask(taskName: String, taskConfig: SchedulerTaskConfig) {
        // Нормализуем время: добавляем ведущий ноль если нужно (6:00 -> 06:00)
        val normalizedTime = if (taskConfig.time.length == 4 && taskConfig.time[1] == ':') {
            "0${taskConfig.time}"
        } else {
            taskConfig.time
        }
        
        // Получаем часовой пояс из конфигурации
        val timezone = try {
            ZoneId.of(ZTele.conf.schedulerTimezone)
        } catch (e: Exception) {
            plugin.logger.warning("⚠️ Неверный часовой пояс '${ZTele.conf.schedulerTimezone}', используется UTC")
            ZoneId.of("UTC")
        }
        
        val targetTime = LocalTime.parse(normalizedTime, timeFormatter)
        val now = ZonedDateTime.now(timezone)
        val currentTime = now.toLocalTime()
        
        // Рассчитываем задержку до первого выполнения
        var secondsUntilExecution = targetTime.toSecondOfDay() - currentTime.toSecondOfDay()
        
        // Если время уже прошло сегодня, планируем на завтра
        if (secondsUntilExecution <= 0) {
            secondsUntilExecution += 24 * 60 * 60 // +24 часа
        }
        
        val delayTicks = secondsUntilExecution * 20L // Bukkit тики (20 тиков = 1 секунда)
        val periodTicks = 24 * 60 * 60 * 20L // 24 часа в тиках (повторять каждый день)
        
        // Логируем информацию о планировании
        if (ZTele.conf.debugEnabled) {
            plugin.logger.info("🕐 Задача '$taskName': целевое время ${taskConfig.time}, текущее время $currentTime (${timezone.id})")
            plugin.logger.info("⏱️ Задержка до выполнения: ${secondsUntilExecution / 60} минут")
        }
        
        val task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            executeTask(taskName, taskConfig)
        }, delayTicks, periodTicks)
        
        activeTasks[taskName] = task
    }
    
    /**
     * Выполнение задачи
     */
    private fun executeTask(taskName: String, taskConfig: SchedulerTaskConfig) {
        try {
            if (ZTele.conf.schedulerLoggingConsole) {
                plugin.logger.info("⚡ Выполнение задачи: $taskName")
            }
            
            // Выполняем все команды задачи
            for (command in taskConfig.commands) {
                try {
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        val result = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
                        if (ZTele.conf.schedulerLoggingConsole) {
                            plugin.logger.info("📋 Выполнена команда: $command (результат: $result)")
                        }
                    })
                } catch (e: Exception) {
                    plugin.logger.severe("❌ Ошибка выполнения команды '$command' в задаче '$taskName': ${e.message}")
                }
            }
            
            // Отправляем уведомление в Telegram консольный канал
            if (ZTele.conf.schedulerLoggingTelegram) {
                sendTelegramNotification(taskName, taskConfig)
            }
            
            if (ZTele.conf.schedulerLoggingConsole) {
                plugin.logger.info("✅ Задача '$taskName' выполнена успешно")
            }
            
        } catch (e: Exception) {
            plugin.logger.severe("❌ Критическая ошибка при выполнении задачи '$taskName': ${e.message}")
        }
    }
    
    /**
     * Отправка уведомления в Telegram консольный канал
     */
    private fun sendTelegramNotification(taskName: String, taskConfig: SchedulerTaskConfig) {
        try {
            // Получаем часовой пояс из конфигурации
            val timezone = try {
                ZoneId.of(ZTele.conf.schedulerTimezone)
            } catch (e: Exception) {
                ZoneId.of("UTC")
            }
            
            val currentTime = ZonedDateTime.now(timezone).toLocalTime()
            
            val message = buildString {
                append("⚡ **Автоматическая задача выполнена**\n")
                append("📋 Название: `$taskName`\n")
                append("⏰ Запланировано: `${taskConfig.time}`\n")
                append("📝 Команд выполнено: `${taskConfig.commands.size}`\n")
                append("🕒 Время выполнения: `${currentTime.format(timeFormatter)}` (${timezone.id})")
            }
            
            ZTele.bot.sendMessageToConsole(message)
        } catch (e: Exception) {
            plugin.logger.warning("⚠️ Не удалось отправить уведомление в Telegram: ${e.message}")
        }
    }
    
    /**
     * Получение списка активных задач
     */
    fun getActiveTasks(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val dailyTasks = ZTele.conf.schedulerDailyTasks
        
        for ((taskName, taskConfig) in dailyTasks) {
            if (taskConfig.enabled && activeTasks.containsKey(taskName)) {
                result[taskName] = "${taskConfig.time} (${taskConfig.commands.size} команд)"
            }
        }
        
        return result
    }
    
    /**
     * Планирует рестарт сервера через указанное количество минут
     */
    fun scheduleRestart(delayMinutes: Int, initiator: String) {
        plugin.logger.info("⏰ Планирование рестарта через $delayMinutes минут от $initiator")
        
        // Отправляем уведомления
        val message = "🔄 **Рестарт сервера запланирован!**\n" +
                     "⏰ Сервер будет перезагружен через **$delayMinutes минут**\n" +
                     "👤 Инициатор: $initiator"
        
        ZTele.bot.sendMessageToMain(message)
        
        // Планируем рестарт через указанное время
        val delayTicks = delayMinutes * 60 * 20L // конвертируем минуты в тики
        
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            // Выполняем рестарт
            Bukkit.getScheduler().runTask(plugin, Runnable {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "restart")
            })
        }, delayTicks)
    }
    
    /**
     * Отменяет запланированный рестарт
     */
    fun cancelScheduledRestart(): Boolean {
        // Упрощенная версия - просто возвращаем false, так как у нас нет активного трекинга
        return false
    }
    
    /**
     * Класс конфигурации задачи планировщика
     */
    data class SchedulerTaskConfig(
        val time: String,
        val commands: List<String>,
        val enabled: Boolean
    )
}