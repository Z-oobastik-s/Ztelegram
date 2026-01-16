package org.zoobastiks.ztelegram

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.zoobastiks.ztelegram.bot.TBot
import org.zoobastiks.ztelegram.cmd.TCmds
import org.zoobastiks.ztelegram.conf.TConf
import org.zoobastiks.ztelegram.conf.ConfigUpdater
import org.zoobastiks.ztelegram.lis.TLis
import org.zoobastiks.ztelegram.mgr.PMgr
import org.zoobastiks.ztelegram.mgr.UnregCooldownManager
import org.zoobastiks.ztelegram.mgr.SchedulerManager
import org.zoobastiks.ztelegram.mgr.RestartManager
import org.zoobastiks.ztelegram.mgr.RandomManager
import org.zoobastiks.ztelegram.menu.TelegramMenuManager
import org.zoobastiks.ztelegram.game.GameManager
import org.zoobastiks.ztelegram.stats.StatsManager
import org.zoobastiks.ztelegram.reputation.ReputationManager
import org.zoobastiks.ztelegram.commands.ReputationCommand
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
        lateinit var stats: StatsManager
            private set
        lateinit var tpsTracker: TpsTracker
            private set
        lateinit var unregCooldowns: UnregCooldownManager
            private set
        lateinit var scheduler: SchedulerManager
            private set
        lateinit var restartManager: RestartManager
            private set
        lateinit var reputation: ReputationManager
            private set
        lateinit var deathMessages: org.zoobastiks.ztelegram.mgr.DeathMessageManager
            private set
        lateinit var randomManager: RandomManager
            private set
        lateinit var menuManager: TelegramMenuManager
            private set
        lateinit var registerMenuManager: org.zoobastiks.ztelegram.menu.RegisterMenuManager
            private set
        lateinit var database: org.zoobastiks.ztelegram.database.DatabaseManager
            private set
        lateinit var paymentManager: org.zoobastiks.ztelegram.mgr.PaymentManager
            private set
        // Старая система переподключений удалена - теперь используется встроенная в TBot

        // Economy provider (Vault)
        var economy: net.milkbowl.vault.economy.Economy? = null
            private set
    }

    // Переменная reconnectTask больше не нужна - используется встроенная система в TBot

    override fun onEnable() {
        instance = this

        // Сначала создаем дефолтный конфиг если его нет
        saveDefaultConfig()

        // Затем обновляем конфигурацию добавляя новые секции
        val configUpdater = ConfigUpdater(this)
        val configUpdated = configUpdater.updateConfig()
        val gameConfigUpdated = configUpdater.updateGameConfig()

        if (configUpdated || gameConfigUpdated) {
            logger.info("Configuration files have been updated with new features!")
        }

        // КРИТИЧЕСКИ ВАЖНО: Принудительно перезагружаем конфиг из файла
        // чтобы Bukkit API получил актуальные данные после ConfigUpdater
        reloadConfig()
        logger.info("Configuration reloaded from disk to ensure fresh data")

        conf = TConf(this)
        
        // Инициализируем базу данных SQLite (если включена)
        database = org.zoobastiks.ztelegram.database.DatabaseManager(this)
        if (conf.databaseEnabled) {
            database.initialize()
            // Выполняем миграцию данных из YAML в SQLite при первом запуске
            val migrator = org.zoobastiks.ztelegram.database.DataMigrator(this, database)
            migrator.migrateAll()
        } else {
            logger.info("База данных SQLite отключена в конфигурации")
        }
        
        mgr = PMgr(this)
        game = GameManager(this)
        stats = StatsManager(this)
        unregCooldowns = UnregCooldownManager(this)
        scheduler = SchedulerManager(this)
        restartManager = RestartManager(this)
        reputation = ReputationManager(this)
        deathMessages = org.zoobastiks.ztelegram.mgr.DeathMessageManager(this)
        randomManager = RandomManager(this)
        randomManager.loadConfig()

        tpsTracker = TpsTracker(this)
        tpsTracker.start()

        // Инициализируем экономику Vault
        setupEconomy()

        // Валидируем конфигурацию (если включено в настройках)
        if (conf.validationEnabled) {
            val validator = org.zoobastiks.ztelegram.utils.ConfigValidator(this)
            val isValid = validator.validateAll()

            if (!isValid) {
                logger.warning("╔════════════════════════════════════════════════════════════════╗")
                logger.warning("║  ⚠️  ОБНАРУЖЕНЫ ОШИБКИ В КОНФИГУРАЦИИ!                        ║")
                logger.warning("║  Плагин запущен, но некоторые функции могут работать          ║")
                logger.warning("║  некорректно. Исправьте ошибки выше для полной функциональности║")
                logger.warning("╚════════════════════════════════════════════════════════════════╝")
            }
        }

        registerCommands()
        registerListeners()
        startBot()

        server.scheduler.runTaskLater(this, Runnable {
            bot.sendServerStartMessage()
        }, 100L)

        // Инициализируем bStats для статистики использования плагина
        try {
            val metrics = org.bstats.bukkit.Metrics(this, 28653)
            
            // Добавляем кастомные графики
            metrics.addCustomChart(org.bstats.charts.SimplePie("database_type") {
                if (conf.databaseEnabled) "SQLite" else "YAML"
            })
            
            metrics.addCustomChart(org.bstats.charts.SimplePie("menu_enabled") {
                if (conf.menuEnabled) "Enabled" else "Disabled"
            })
            
            metrics.addCustomChart(org.bstats.charts.SimplePie("random_enabled") {
                if (conf.enabledRandomCommand) "Enabled" else "Disabled"
            })
            
            logger.info("✅ bStats metrics initialized (Plugin ID: 28653)")
        } catch (e: Exception) {
            logger.warning("⚠️ Failed to initialize bStats metrics: ${e.message}")
        }

        // Добавляем пустую строку перед сообщением
        Bukkit.getConsoleSender().sendMessage("")

        // Выводим красивое сообщение о запуске
        val pluginVersion = pluginMeta.version
        val startMessage = arrayOf(
            "§b╭━─━─━─━─━━─≪§e✠§b≫─━──━─━─━─━╮",
            "§b│ §aAuthor §eZoobastiks                         §b│",
            "§b│ §aSupport §ehttps://t.me/Zoobastiks           §b│",
            "§b│ §aPlugin version §ev$pluginVersion            §b│",
            "§b│ §aSupport version §e1.21.4 - 1.21.8           §b│",
            "§b╰━─━─━─━─━━─≪§e✠§b≫─━──━─━─━─━╯"
        )

        // Выводим каждую строку через ConsoleCommandSender
        startMessage.forEach { Bukkit.getConsoleSender().sendMessage(it) }

        // Добавляем пустую строку после сообщения
        Bukkit.getConsoleSender().sendMessage("")
    }

    override fun onDisable() {
        bot.sendServerStopMessage()
        bot.stop()
        game.cancelAllGames()
        stats.shutdown()
        scheduler.stop()
        reputation.saveData()
        
        // Закрываем базу данных
        if (conf.databaseEnabled) {
            database.close()
        }

        tpsTracker.stop()

        // Добавляем пустую строку перед сообщением
        Bukkit.getConsoleSender().sendMessage("")

        // Выводим красивое сообщение о выключении
        val pluginVersion = pluginMeta.version
        val stopMessage = arrayOf(
            "§b╭━─━─━─━──━─━─≪§e✠§b≫─━─━━─━─━─━╮",
            "§b│ §cPlugin disabled                             §b│",
            "§b│ §cVersion §ev$pluginVersion                   §b│",
            "§b╰━─━─━─━──━─━─≪§e✠§b≫─━─━━─━─━─━╯"
        )

        // Выводим каждую строку через ConsoleCommandSender
        stopMessage.forEach { Bukkit.getConsoleSender().sendMessage(it) }

        // Добавляем пустую строку после сообщения
        Bukkit.getConsoleSender().sendMessage("")
    }

    fun reload() {
        // Обновляем конфигурацию перед перезагрузкой
        val configUpdater = ConfigUpdater(this)
        val configUpdated = configUpdater.updateConfig()
        val gameConfigUpdated = configUpdater.updateGameConfig()

        if (configUpdated || gameConfigUpdated) {
            logger.info("Configuration files updated during reload!")
        }

        // Перезагружаем конфигурацию
        reloadConfig()
        conf.reload()
        mgr.reload()
        game.reload()
        stats.reload()
        scheduler.reload()
        reputation.loadData()
        deathMessages.reload()
        randomManager.loadConfig()

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

                            // Планируем автоматические уведомления
                            bot.scheduleAutoNotifications()

                            // Запускаем планировщик команд
                            scheduler.start()

                            logger.info("Plugin reload complete!")
                        } catch (e: Exception) {
                            logger.severe("Failed to restart bot: ${e.message}")
                            logger.info("Automatic reconnection will be handled by the built-in system")
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

                        // Планируем автоматические уведомления
                        bot.scheduleAutoNotifications()

                        // Запускаем планировщик команд
                        scheduler.start()

                        // Запускаем бота
                        bot.start()
                    } catch (ex: Exception) {
                        logger.severe("Failed to recover: ${ex.message}")
                        logger.info("Automatic reconnection will be handled by the built-in system")
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

        // Регистрируем команду репутации
        val repCmd = getCommand("rep")
        if (repCmd != null) {
            val repExecutor = ReputationCommand(this)
            repCmd.setExecutor(repExecutor)
            repCmd.tabCompleter = repExecutor
        }
    }

    private fun registerListeners() {
        server.pluginManager.registerEvents(TLis(this), this)
    }

    private fun startBot() {
        server.scheduler.runTaskAsynchronously(this, Runnable {
            try {
                bot = TBot(this)
                bot.start()
                
                // Инициализируем менеджер меню после создания бота
                menuManager = TelegramMenuManager(bot, this)
                registerMenuManager = org.zoobastiks.ztelegram.menu.RegisterMenuManager(bot, this)
                paymentManager = org.zoobastiks.ztelegram.mgr.PaymentManager(this)

                // Планируем автоматические уведомления
                bot.scheduleAutoNotifications()

                // Запускаем планировщик команд
                scheduler.start()

                server.scheduler.runTask(this, Runnable {
                    logger.info("Telegram bot initialization completed!")
                })
            } catch (e: Exception) {
                // Обработка ошибок теперь ведется внутри TBot с автоматическим переподключением
                logger.warning("Error starting Telegram bot: ${e.javaClass.simpleName}")
                logger.info("Automatic reconnection will be handled by the built-in system")
            }
        })
    }

    // Старые методы scheduleReconnect() и stopReconnectScheduler() удалены
    // Теперь используется встроенная система переподключений в TBot

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
            task = plugin.server.scheduler.scheduleSyncRepeatingTask(plugin, Runnable {
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
                val craftServerClass = Class.forName("org.bukkit.craftbukkit.CraftServer")

                val nmsServer = try {
                    val getServerMethod = craftServerClass.getMethod("getServer")
                    getServerMethod.invoke(Bukkit.getServer())
                } catch (e: Exception) {
                    // Пробуем другой способ получения сервера
                    val getHandleMethod = craftServerClass.getDeclaredMethod("getHandle")
                    getHandleMethod.invoke(Bukkit.getServer())
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

    /**
     * Настройка экономики Vault
     */
    private fun setupEconomy(): Boolean {
        if (server.pluginManager.getPlugin("Vault") == null) {
            logger.info("Vault not found - economy features disabled")
            return false
        }

        val rsp = server.servicesManager.getRegistration(net.milkbowl.vault.economy.Economy::class.java)
        if (rsp == null) {
            logger.info("No economy provider found - economy features disabled")
            return false
        }

        economy = rsp.provider
        logger.info("Successfully hooked into economy provider: ${economy?.name}")
        return true
    }
}
