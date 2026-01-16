package org.zoobastiks.ztelegram.conf

import org.bukkit.configuration.file.YamlConfiguration
import org.zoobastiks.ztelegram.ZTele
import java.io.File
import java.io.InputStreamReader

/**
 * Класс для умного обновления конфигурационных файлов
 * Добавляет новые ключи из дефолтной конфигурации, не затрагивая пользовательские изменения
 */
class ConfigUpdater(private val plugin: ZTele) {

    /**
     * Список ключей, которые всегда обновляются до дефолтных значений
     * Эти ключи считаются "системными" и не должны изменяться пользователем
     */
    private val forceUpdateKeys = setOf(
        // Версия конфигурации для отслеживания обновлений
        "config-version",

        // Структурные ключи каналов (но не их содержимое)
        "channels",
        "plugin",
        "main-channel",
        "console-channel",
        "register-channel",
        "commands",
        "auto_notifications",
        "messages",
        "status",

        // Новые функции, которые должны быть добавлены
        "debug",
        "commands.stats",
        "commands.top",
        "commands.topbal",
        "auto_notifications.playtime_top",
        "auto_notifications.balance_top",
        "reputation",
        "reputation.messages",
        "help.reputation"
    )

    /**
     * Ключи, которые никогда не обновляются (пользовательские настройки)
     */
    private val neverUpdateKeys = setOf(
        "bot.token",
        "channels.main",
        "channels.console",
        "channels.register"
    )

    /**
     * Обновляет конфигурацию, добавляя новые ключи из дефолтной конфигурации
     */
    fun updateConfig(): Boolean {
        try {
            val configFile = File(plugin.dataFolder, "config.yml")
            if (!configFile.exists()) {
                plugin.logger.info("Config file doesn't exist, creating default...")
                plugin.saveDefaultConfig()
                return true
            }

            // Загружаем текущую конфигурацию
            val currentConfig = YamlConfiguration.loadConfiguration(configFile)

            // Загружаем дефолтную конфигурацию из ресурсов
            val defaultConfigStream = plugin.getResource("config.yml")
                ?: throw IllegalStateException("Default config.yml not found in plugin resources")

            val defaultConfig = YamlConfiguration.loadConfiguration(
                InputStreamReader(defaultConfigStream, "UTF-8")
            )

            // КРИТИЧЕСКИ ВАЖНО: Проверяем наличие секции reputation
            // Если её нет, копируем всю секцию из дефолтного конфига
            var reputationAdded = false
            if (!currentConfig.contains("reputation")) {
                plugin.logger.warning("⚠️ Секция 'reputation' отсутствует! Добавляем из дефолтного конфига...")
                val reputationSection = defaultConfig.getConfigurationSection("reputation")
                if (reputationSection != null) {
                    currentConfig.createSection("reputation", reputationSection.getValues(true))
                    plugin.logger.info("✅ Секция 'reputation' добавлена успешно")
                    reputationAdded = true
                } else {
                    plugin.logger.severe("❌ Секция 'reputation' не найдена даже в дефолтном конфиге!")
                }
            }

            // Проверяем версию конфигурации
            val currentVersion = currentConfig.getString("config-version", "1.0")
            val defaultVersion = defaultConfig.getString("config-version", "1.0")

            // Если версии отличаются или версии нет, обновляем
            if (currentVersion != defaultVersion || !currentConfig.contains("config-version")) {
                plugin.logger.info("Updating config from version $currentVersion to $defaultVersion")

                // Добавляем новые ключи из дефолтной конфигурации
                for (key in defaultConfig.getKeys(true)) {
                    if (shouldUpdateKey(key, currentConfig, defaultConfig)) {
                        val value = defaultConfig.get(key)
                        currentConfig.set(key, value)
                        plugin.logger.info("Added/updated config key: $key")
                    }
                }

                // Обновляем версию конфигурации
                currentConfig.set("config-version", defaultVersion)

                // Сохраняем обновленную конфигурацию
                currentConfig.save(configFile)

                // КРИТИЧЕСКИ ВАЖНО: Синхронизируем запись на диск
                // чтобы гарантировать что файл записан до следующих операций
                try {
                    configFile.outputStream().use { it.fd.sync() }
                } catch (e: Exception) {
                    plugin.logger.warning("Could not sync config file to disk: ${e.message}")
                }

                plugin.logger.info("Config updated and synced to disk successfully!")

                return true
            }

            // Даже если версии совпадают, но мы добавили reputation - нужно сохранить
            if (reputationAdded) {
                plugin.logger.info("Saving config due to reputation section addition...")
                currentConfig.save(configFile)
                try {
                    configFile.outputStream().use { it.fd.sync() }
                } catch (e: Exception) {
                    plugin.logger.warning("Could not sync config file to disk: ${e.message}")
                }
                plugin.logger.info("Config saved successfully with reputation section!")
                return true
            }

            return false

        } catch (e: Exception) {
            plugin.logger.severe("Failed to update config: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Определяет, нужно ли обновлять конкретный ключ
     */
    private fun shouldUpdateKey(
        key: String,
        currentConfig: YamlConfiguration,
        defaultConfig: YamlConfiguration
    ): Boolean {
        // Никогда не обновляем эти ключи
        if (neverUpdateKeys.any { key.startsWith(it) }) {
            return false
        }

        // Если ключа нет в текущей конфигурации - добавляем
        if (!currentConfig.contains(key)) {
            return true
        }

        // Если это системный ключ - обновляем принудительно
        if (forceUpdateKeys.any { key.startsWith(it) }) {
            // Но только если это структурный ключ (не конечное значение)
            val value = defaultConfig.get(key)
            if (value is Map<*, *> || value is List<*>) {
                return true
            }
        }

        // Для всех остальных случаев - не обновляем (сохраняем пользовательские изменения)
        return false
    }

    /**
     * Обновляет game.yml файл аналогичным образом
     */
    fun updateGameConfig(): Boolean {
        try {
            val gameFile = File(plugin.dataFolder, "game.yml")
            if (!gameFile.exists()) {
                plugin.logger.info("Game config doesn't exist, creating default...")
                plugin.saveResource("game.yml", false)
                return true
            }

            val currentGameConfig = YamlConfiguration.loadConfiguration(gameFile)
            val defaultGameStream = plugin.getResource("game.yml")
                ?: return false // Если нет дефолтного game.yml, пропускаем

            val defaultGameConfig = YamlConfiguration.loadConfiguration(
                InputStreamReader(defaultGameStream, "UTF-8")
            )

            var hasUpdates = false

            // Добавляем недостающие ключи
            for (key in defaultGameConfig.getKeys(true)) {
                if (!currentGameConfig.contains(key)) {
                    val value = defaultGameConfig.get(key)
                    currentGameConfig.set(key, value)
                    plugin.logger.info("Added game config key: $key")
                    hasUpdates = true
                }
            }

            if (hasUpdates) {
                currentGameConfig.save(gameFile)

                // Синхронизируем запись на диск
                try {
                    gameFile.outputStream().use { it.fd.sync() }
                } catch (e: Exception) {
                    plugin.logger.warning("Could not sync game config file to disk: ${e.message}")
                }

                plugin.logger.info("Game config updated and synced to disk successfully!")
                return true
            }

            return false

        } catch (e: Exception) {
            plugin.logger.warning("Failed to update game config: ${e.message}")
            return false
        }
    }
}
