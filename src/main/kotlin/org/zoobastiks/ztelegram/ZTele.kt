package org.zoobastiks.ztelegram

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.zoobastiks.ztelegram.bot.TBot
import org.zoobastiks.ztelegram.cmd.TCmds
import org.zoobastiks.ztelegram.conf.TConf
import org.zoobastiks.ztelegram.lis.TLis
import org.zoobastiks.ztelegram.mgr.PMgr
import org.zoobastiks.ztelegram.game.GameManager
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.LinkedList

class ZTele : JavaPlugin() {
    companion object {
        lateinit var instance: ZTele
            private set
        lateinit var conf: TConf
            private set
        lateinit var bot: TBot
            private set
        lateinit var mgr: PMgr
            private set
        lateinit var game: GameManager
            private set
        lateinit var tpsTracker: TpsTracker
            private set
        private var reconnectScheduler: ScheduledExecutorService? = null
        private val reconnectDelays = arrayOf(30L, 60L, 300L, 600L, 1800L)
        private var reconnectAttempt = 0
    }

    private var reconnectTask: Int = -1

    override fun onEnable() {
        instance = this
        saveDefaultConfig()
        conf = TConf(this)
        mgr = PMgr(this)
        game = GameManager(this)
        
        tpsTracker = TpsTracker(this)
        tpsTracker.start()
        
        registerCommands()
        registerListeners()
        startBot()
        
        server.scheduler.runTaskLater(this, Runnable {
            bot.sendServerStartMessage()
        }, 100L)
        
        logger.info("§a${description.name} v${description.version} enabled!")
    }

    override fun onDisable() {
        bot.sendServerStopMessage()
        bot.stop()
        stopReconnectScheduler()
        game.cancelAllGames()
        
        tpsTracker.stop()
        
        logger.info("§c${description.name} v${description.version} disabled!")
    }
    
    fun reload() {
        // Перезагружаем конфигурацию
        reloadConfig()
        conf.reload()
        mgr.reload()
        game.reload()
        
        // Перезапускаем трекер TPS
        tpsTracker.stop()
        tpsTracker = TpsTracker(this)
        tpsTracker.start()
        
        // Выполняем в отдельном потоке, чтобы не блокировать основной поток сервера
        server.scheduler.runTaskAsynchronously(this, Runnable {
            try {
                // Логируем начало процесса
                logger.info("Stopping Telegram bot for reload...")
                
                // Останавливаем текущего бота (если есть)
                try {
                    bot.stop()
                } catch (e: Exception) {
                    logger.warning("Error during bot stop: ${e.message}")
                    // Продолжаем выполнение, даже если остановка не удалась
                }
                
                // Добавляем задержку для гарантированного завершения предыдущей сессии
                try {
                    Thread.sleep(3000) // Увеличиваем до 3 секунд
                } catch (e: InterruptedException) {
                    logger.warning("Sleep interrupted during bot reload")
                }
                
                // Запускаем сборщик мусора для освобождения ресурсов
                try {
                    System.gc()
                    Thread.sleep(1000) // Даем время для сборки мусора
                } catch (e: Exception) {
                    logger.warning("Error during garbage collection: ${e.message}")
                }
                
                // Переходим в основной поток для перезапуска
                server.scheduler.runTask(this, Runnable {
                    logger.info("Preparing to restart Telegram bot...")
                    
                    // Останавливаем любые возможно оставшиеся потоки
                    stopAnyActiveBotSessions()
                    
                    // Запускаем бота с задержкой
                    server.scheduler.runTaskLater(this, Runnable {
                        try {
                            // Еще раз проверяем, что нет активных сессий
                            stopAnyActiveBotSessions()
                            
                            logger.info("Starting Telegram bot...")
                            
                            // Создаем новый экземпляр бота
                            bot = TBot(this)
                            
                            // Запускаем бота
                            bot.start()
                            
                            logger.info("Plugin reload complete!")
                        } catch (e: Exception) {
                            logger.severe("Failed to restart bot: ${e.message}")
                            e.printStackTrace()
                            scheduleReconnect()
                        }
                    }, 80L) // Увеличиваем задержку до 4 секунд (80 тиков)
                })
            } catch (e: Exception) {
                logger.severe("Error during reload: ${e.message}")
                e.printStackTrace()
                
                server.scheduler.runTask(this, Runnable {
                    try {
                        // На случай ошибки - создаем новый экземпляр бота
                        bot = TBot(this)
                        
                        // Дополнительная проверка для остановки любых потенциально активных сессий
                        stopAnyActiveBotSessions()
                        
                        // Запускаем бота
                        bot.start()
                    } catch (ex: Exception) {
                        logger.severe("Failed to recover: ${ex.message}")
                        scheduleReconnect()
                    }
                })
            }
        })
    }
    
    // Вспомогательный метод для принудительной остановки любых активных сессий бота
    private fun stopAnyActiveBotSessions() {
        try {
            // Находим все потоки, связанные с Telegram сессиями
            val threadGroup = Thread.currentThread().threadGroup
            val threads = arrayOfNulls<Thread>(threadGroup.activeCount() * 2)
            threadGroup.enumerate(threads, true)
            
            var telegramThreadsFound = false
            val currentThread = Thread.currentThread()
            
            // Прерываем все потоки, связанные с Telegram, кроме текущего
            for (thread in threads) {
                if (thread != null && thread != currentThread && !thread.isInterrupted && 
                   (thread.name.contains("DefaultBotSession") || 
                    thread.name.contains("Telegram") || 
                    thread.name.contains("telegram"))) {
                    try {
                        telegramThreadsFound = true
                        logger.info("Stopping active Telegram thread: ${thread.name}")
                        thread.interrupt()
                    } catch (e: Exception) {
                        logger.warning("Error interrupting thread ${thread.name}: ${e.message}")
                    }
                }
            }
            
            // Если были найдены потоки Telegram, ждем их завершения
            if (telegramThreadsFound) {
                try {
                    // Даем время для завершения потоков
                    Thread.sleep(1000) // Увеличиваем до 1 секунды
                } catch (e: InterruptedException) {
                    logger.warning("Interrupted while waiting for threads to stop")
                }
                
                // Проверяем, остались ли активные потоки
                // Если да, принудительно останавливаем
                threads.filterNotNull()
                      .filter { t -> t != currentThread && !t.isInterrupted && 
                                    (t.name.contains("DefaultBotSession") || 
                                     t.name.contains("Telegram") || 
                                     t.name.contains("telegram")) }
                      .forEach { t ->
                          try {
                              // Более агрессивно прерываем потоки
                              logger.warning("Thread ${t.name} still active, attempting forceful stop")
                              t.interrupt()
                              
                              // На некоторых JVM можно использовать агрессивный способ остановки потоков
                              // Но это небезопасно и может вызвать проблемы
                              // Используем только в крайнем случае
                              /*
                              try {
                                  val stopMethod = t.javaClass.getDeclaredMethod("stop")
                                  stopMethod.isAccessible = true
                                  stopMethod.invoke(t)
                              } catch (ex: Exception) {
                                  // Игнорируем ошибки
                              }
                              */
                          } catch (e: Exception) {
                              // Игнорируем ошибки при принудительной остановке
                          }
                      }
            }
        } catch (e: Exception) {
            logger.warning("Error stopping active bot sessions: ${e.message}")
        } finally {
            // В любом случае пытаемся собрать мусор для освобождения ресурсов
            try {
                System.gc()
            } catch (e: Exception) {
                // Игнорируем ошибки
            }
        }
    }
    
    private fun registerCommands() {
        val cmdExecutor = TCmds(this)
        val telegramCmd = getCommand("telegram")
        if (telegramCmd != null) {
            telegramCmd.setExecutor(cmdExecutor)
            telegramCmd.tabCompleter = cmdExecutor
        }
    }
    
    private fun registerListeners() {
        server.pluginManager.registerEvents(TLis(this), this)
    }
    
    private fun startBot() {
        stopReconnectScheduler()
        reconnectAttempt = 0
        
        server.scheduler.runTaskAsynchronously(this, Runnable {
            try {
                bot = TBot(this)
                
                bot.start()
                
                server.scheduler.runTask(this, Runnable {
                    logger.info("Telegram bot started successfully!")
                })
            } catch (e: Exception) {
                logger.severe("Failed to start Telegram bot: ${e.message}")
                e.printStackTrace()
                
                server.scheduler.runTask(this, Runnable {
                    scheduleReconnect()
                })
            }
        })
    }
    
    fun scheduleReconnect() {
        stopReconnectScheduler()
        
        reconnectAttempt++
        val delaySeconds = if (reconnectAttempt > reconnectDelays.size) {
            reconnectDelays.last()
        } else {
            reconnectDelays[reconnectAttempt - 1]
        }
        
        logger.info("Scheduling bot reconnect attempt in $delaySeconds seconds...")
        
        reconnectTask = server.scheduler.runTaskLater(this, Runnable {
            server.scheduler.runTaskAsynchronously(this, Runnable {
                try {
                    logger.info("Attempting to reconnect Telegram bot (attempt $reconnectAttempt)...")
                    server.scheduler.runTask(this, Runnable {
                        startBot()
                    })
                } catch (ex: Exception) {
                    logger.severe("Failed to reconnect: ${ex.message}")
                    server.scheduler.runTask(this, Runnable {
                        scheduleReconnect()
                    })
                }
            })
        }, delaySeconds * 20L).taskId
    }
    
    private fun stopReconnectScheduler() {
        if (reconnectTask != -1) {
            server.scheduler.cancelTask(reconnectTask)
            reconnectTask = -1
        }
    }
    
    fun reloadGame() {
        game.reload()
        logger.info("Game configuration reloaded!")
    }

    fun getBot(): TBot {
        return bot
    }
    
    // Класс для более точного мониторинга TPS сервера
    class TpsTracker(private val plugin: ZTele) {
        private val tpsHistory1Min = LinkedList<Double>()
        private val tpsHistory5Min = LinkedList<Double>()
        private val tpsHistory15Min = LinkedList<Double>()
        private var task: Int = -1
        private var lastPoll: Long = System.currentTimeMillis()
        
        // Количество записей для хранения истории
        private val historySize1Min = 60  // 1 минута
        private val historySize5Min = 300 // 5 минут
        private val historySize15Min = 900 // 15 минут
        
        fun start() {
            // Сбросить время последнего опроса
            lastPoll = System.currentTimeMillis()
            
            // Запускаем задачу каждую секунду для расчета TPS
            task = plugin.server.scheduler.scheduleSyncRepeatingTask(plugin, {
                val now = System.currentTimeMillis()
                val elapsedTime = now - lastPoll
                lastPoll = now
                
                if (elapsedTime > 0) {
                    try {
                        // Получаем TPS напрямую из сервера
                        val serverTPS = getServerTPS()
                        
                        // Добавляем в историю для 1 минуты
                        tpsHistory1Min.add(serverTPS[0])
                        while (tpsHistory1Min.size > historySize1Min) {
                            tpsHistory1Min.removeFirst()
                        }
                        
                        // Добавляем в историю для 5 минут
                        tpsHistory5Min.add(serverTPS[1])
                        while (tpsHistory5Min.size > historySize5Min) {
                            tpsHistory5Min.removeFirst()
                        }
                        
                        // Добавляем в историю для 15 минут
                        tpsHistory15Min.add(serverTPS[2])
                        while (tpsHistory15Min.size > historySize15Min) {
                            tpsHistory15Min.removeFirst()
                        }
                    } catch (e: Exception) {
                        // В случае ошибки используем старый метод расчета
                        val tps = Math.min(20.0, 1000.0 / elapsedTime * 20.0)
                        
                        tpsHistory1Min.add(tps)
                        while (tpsHistory1Min.size > historySize1Min) {
                            tpsHistory1Min.removeFirst()
                        }
                        
                        tpsHistory5Min.add(tps)
                        while (tpsHistory5Min.size > historySize5Min) {
                            tpsHistory5Min.removeFirst()
                        }
                        
                        tpsHistory15Min.add(tps)
                        while (tpsHistory15Min.size > historySize15Min) {
                            tpsHistory15Min.removeFirst()
                        }
                        
                        plugin.logger.warning("Error getting server TPS: ${e.message}")
                    }
                }
            }, 20L, 20L) // Запускаем через 1 секунду и выполняем каждую секунду
        }
        
        // Получить TPS напрямую из сервера через reflection
        private fun getServerTPS(): DoubleArray {
            try {
                val serverClass = Bukkit.getServer().javaClass
                
                // Для Paper
                try {
                    val getTPS = serverClass.getMethod("getTPS")
                    val tps = getTPS.invoke(Bukkit.getServer()) as DoubleArray
                    return tps
                } catch (e: NoSuchMethodException) {
                    // Метод не найден, возможно это Spigot
                }
                
                // Для Spigot
                try {
                    val minecraftServerClass = Class.forName("net.minecraft.server.MinecraftServer")
                    val getServerMethod = serverClass.getMethod("getServer")
                    val minecraftServer = getServerMethod.invoke(Bukkit.getServer())
                    
                    val getRecentTpsMethod = minecraftServerClass.getMethod("recentTps")
                    return getRecentTpsMethod.invoke(minecraftServer) as DoubleArray
                } catch (e: Exception) {
                    // Этот метод также не сработал
                }
                
                // Fallback: NMS путь для более старых версий
                var nmsServer: Any? = null
                val craftServerClass = Class.forName("org.bukkit.craftbukkit.CraftServer")
                
                try {
                    val getServerMethod = craftServerClass.getMethod("getServer")
                    nmsServer = getServerMethod.invoke(Bukkit.getServer())
                } catch (e: Exception) {
                    // Пробуем другой способ получения сервера
                    val getHandleMethod = craftServerClass.getDeclaredMethod("getHandle")
                    nmsServer = getHandleMethod.invoke(Bukkit.getServer())
                }
                
                if (nmsServer != null) {
                    val recentTpsField = nmsServer.javaClass.getField("recentTps")
                    return recentTpsField.get(nmsServer) as DoubleArray
                }
                
            } catch (e: Exception) {
                plugin.logger.warning("Could not access server TPS: ${e.message}")
            }
            
            // Если все методы не сработали, возвращаем последние известные значения
            // или значения по умолчанию (20.0), если история пуста
            return doubleArrayOf(
                if (tpsHistory1Min.isEmpty()) 20.0 else tpsHistory1Min.average(),
                if (tpsHistory5Min.isEmpty()) 20.0 else tpsHistory5Min.average(),
                if (tpsHistory15Min.isEmpty()) 20.0 else tpsHistory15Min.average()
            )
        }
        
        fun stop() {
            if (task != -1) {
                plugin.server.scheduler.cancelTask(task)
                task = -1
            }
            tpsHistory1Min.clear()
            tpsHistory5Min.clear()
            tpsHistory15Min.clear()
        }
        
        // Получить текущее значение TPS
        fun getCurrentTps(): Double {
            return getServerTPS()[0]
        }
        
        // Получить среднее значение TPS за 1 минуту
        fun getAverageTps1Min(): Double {
            return getServerTPS()[0]
        }
        
        // Получить среднее значение TPS за 5 минут
        fun getAverageTps5Min(): Double {
            return getServerTPS()[1]
        }
        
        // Получить среднее значение TPS за 15 минут
        fun getAverageTps15Min(): Double {
            return getServerTPS()[2]
        }
        
        // Получить все значения TPS в массиве (1min, 5min, 15min)
        fun getAllTps(): DoubleArray {
            return getServerTPS()
        }
    }
} 