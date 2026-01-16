package org.zoobastiks.ztelegram.mgr

import org.bukkit.Bukkit
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.zoobastiks.ztelegram.ZTele
import org.zoobastiks.ztelegram.conf.TConf
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class PMgr(private val plugin: ZTele) {
    private val conf: TConf
        get() = ZTele.conf

    private val playersConfig: YamlConfiguration
        get() = conf.getPlayersConfig()

    private val hiddenPlayers = mutableSetOf<String>()
    private val registeredPlayers = mutableMapOf<String, PlayerData>()
    private val blacklist = mutableSetOf<String>()
    private val whitelist = mutableSetOf<String>()

    // Словарь для хранения оригинального написания ников игроков
    private val originalPlayerNames = mutableMapOf<String, String>()

    // Хранение активных кодов регистрации: код -> (playerName, время истечения)
    private val activeCodes = ConcurrentHashMap<String, Pair<String, LocalDateTime>>()

    init {
        loadPlayers()
    }

    fun reload() {
        hiddenPlayers.clear()
        registeredPlayers.clear()
        blacklist.clear()
        whitelist.clear()
        loadPlayers()
    }

    private fun loadPlayers() {
        if (conf.databaseEnabled && ZTele.database.databaseExists()) {
            loadPlayersFromDatabase()
        } else {
            loadPlayersFromYaml()
        }
    }
    
    private fun loadPlayersFromDatabase() {
        try {
            // Загружаем скрытых игроков
            ZTele.database.executeQuery("SELECT player_name FROM hidden_players") { rs ->
                while (rs.next()) {
                    hiddenPlayers.add(rs.getString("player_name"))
                }
            }
            
            // Загружаем черный список
            ZTele.database.executeQuery("SELECT telegram_id FROM blacklist") { rs ->
                while (rs.next()) {
                    blacklist.add(rs.getString("telegram_id"))
                }
            }
            
            // Загружаем белый список
            ZTele.database.executeQuery("SELECT telegram_id FROM whitelist") { rs ->
                while (rs.next()) {
                    whitelist.add(rs.getString("telegram_id"))
                }
            }
            
            // Загружаем зарегистрированных игроков
            var playersCount = 0
            ZTele.database.executeQuery("SELECT telegram_id, player_name, registered_date, gender, unlinked, original_name FROM players") { rs ->
                while (rs.next()) {
                    val playerName = rs.getString("player_name")
                    val telegramId = rs.getString("telegram_id")
                    val registered = rs.getString("registered_date")
                    val gender = rs.getString("gender")
                    val unlinked = rs.getInt("unlinked") == 1
                    val originalName = rs.getString("original_name") ?: playerName
                    
                    registeredPlayers[playerName.lowercase()] = PlayerData(telegramId, registered, gender, unlinked)
                    originalPlayerNames[playerName.lowercase()] = originalName
                    playersCount++
                }
            }
            
            plugin.logger.info("[PMgr] ✅ Загружено из БД: ${hiddenPlayers.size} скрытых, ${blacklist.size} в черном списке, ${whitelist.size} в белом, $playersCount зарегистрированных")
            
            if (playersCount == 0 && File(plugin.dataFolder, "players.yml").exists()) {
                plugin.logger.warning("[PMgr] ⚠️ В БД нет игроков, но файл players.yml существует. Возможно, миграция не выполнилась. Попробуйте удалить ztelegram.db и перезапустить сервер.")
            }
        } catch (e: Exception) {
            plugin.logger.warning("Ошибка загрузки игроков из БД, переключаемся на YAML: ${e.message}")
            e.printStackTrace()
            loadPlayersFromYaml()
        }
    }
    
    private fun loadPlayersFromYaml() {
        val config = ZTele.conf.getPlayersConfig()

        // Load hidden players
        val hiddenList = config.getStringList("hidden-players")
        hiddenPlayers.addAll(hiddenList)

        // Load blacklist
        val blacklistArray = config.getStringList("blacklist")
        blacklist.addAll(blacklistArray)

        // Load whitelist
        val whitelistArray = config.getStringList("whitelist")
        whitelist.addAll(whitelistArray)

        // Load registered players
        val playersSection = config.getConfigurationSection("players")
        if (playersSection != null) {
            for (playerName in playersSection.getKeys(false)) {
                val playerSection = playersSection.getConfigurationSection(playerName)
                if (playerSection != null) {
                    val telegramId = playerSection.getString("telegram-id") ?: continue
                    val registered = playerSection.getString("registered")
                    val gender = playerSection.getString("gender")
                    val unlinked = playerSection.getBoolean("unlinked", false)

                    registeredPlayers[playerName.lowercase()] = PlayerData(telegramId, registered, gender, unlinked)

                    // Загружаем оригинальное написание имени
                    val originalName = playerSection.getString("original-name")
                    if (originalName != null) {
                        originalPlayerNames[playerName.lowercase()] = originalName
                    } else {
                        // Если нет оригинального имени, используем имя из конфигурации
                        originalPlayerNames[playerName.lowercase()] = playerName
                    }
                }
            }
        }
    }

    fun isPlayerHidden(name: String): Boolean {
        if (conf.databaseEnabled && ZTele.database.databaseExists()) {
            val result = ZTele.database.executeQuery(
                "SELECT 1 FROM hidden_players WHERE player_name = ?",
                listOf(name.lowercase())
            ) { rs -> rs.next() }
            return result ?: false
        } else {
            val hiddenPlayers = playersConfig.getStringList("hidden-players")
            return hiddenPlayers.contains(name.lowercase())
        }
    }

    fun addHiddenPlayer(name: String) {
        val lowerName = name.lowercase()
        hiddenPlayers.add(lowerName)
        
        if (conf.databaseEnabled && ZTele.database.databaseExists()) {
            ZTele.database.executeUpdate(
                "INSERT OR IGNORE INTO hidden_players (player_name) VALUES (?)",
                listOf(lowerName)
            )
        } else {
            val hiddenPlayersList = playersConfig.getStringList("hidden-players").toMutableList()
            hiddenPlayersList.add(lowerName)
            playersConfig.set("hidden-players", hiddenPlayersList)
            conf.savePlayersConfig()
        }
    }

    fun removeHiddenPlayer(name: String) {
        val lowerName = name.lowercase()
        hiddenPlayers.remove(lowerName)
        
        if (conf.databaseEnabled && ZTele.database.databaseExists()) {
            ZTele.database.executeUpdate(
                "DELETE FROM hidden_players WHERE player_name = ?",
                listOf(lowerName)
            )
        } else {
            val hiddenPlayersList = playersConfig.getStringList("hidden-players").toMutableList()
            hiddenPlayersList.remove(lowerName)
            playersConfig.set("hidden-players", hiddenPlayersList)
            conf.savePlayersConfig()
        }
    }

    // Метод для получения списка скрытых игроков
    fun getHiddenPlayers(): List<String> {
        if (conf.databaseEnabled && ZTele.database.databaseExists()) {
            val result = mutableListOf<String>()
            ZTele.database.executeQuery("SELECT player_name FROM hidden_players") { rs ->
                while (rs.next()) {
                    result.add(rs.getString("player_name"))
                }
            }
            return result
        } else {
            return playersConfig.getStringList("hidden-players")
        }
    }

    // Методы для работы с черным списком
    fun isPlayerBlacklisted(telegramId: String): Boolean {
        return blacklist.contains(telegramId)
    }

    fun addToBlacklist(telegramId: String): Boolean {
        if (blacklist.add(telegramId)) {
            if (conf.databaseEnabled && ZTele.database.databaseExists()) {
                ZTele.database.executeUpdate(
                    "INSERT OR IGNORE INTO blacklist (telegram_id) VALUES (?)",
                    listOf(telegramId)
                )
            } else {
                val blacklistArray = playersConfig.getStringList("blacklist").toMutableList()
                blacklistArray.add(telegramId)
                playersConfig.set("blacklist", blacklistArray)
                conf.savePlayersConfig()
            }
            return true
        }
        return false
    }

    fun removeFromBlacklist(telegramId: String): Boolean {
        if (blacklist.remove(telegramId)) {
            if (conf.databaseEnabled && ZTele.database.databaseExists()) {
                ZTele.database.executeUpdate(
                    "DELETE FROM blacklist WHERE telegram_id = ?",
                    listOf(telegramId)
                )
            } else {
                val blacklistArray = playersConfig.getStringList("blacklist").toMutableList()
                blacklistArray.remove(telegramId)
                playersConfig.set("blacklist", blacklistArray)
                conf.savePlayersConfig()
            }
            return true
        }
        return false
    }

    fun getBlacklist(): Set<String> {
        return blacklist.toSet()
    }

    // Методы для работы с белым списком
    fun isPlayerWhitelisted(telegramId: String): Boolean {
        // Логируем значения для отладки
        if (conf.debugEnabled) {
            plugin.logger.info("Checking whitelist for telegramId: $telegramId")
            plugin.logger.info("Whitelist enabled: ${conf.whitelistEnabled}")
            plugin.logger.info("Whitelist content: ${whitelist.joinToString(", ")}")
        }

        // Если белый список отключен, разрешаем всех
        if (!conf.whitelistEnabled) {
            if (conf.debugEnabled) {
                plugin.logger.info("Whitelist is disabled, allowing message")
            }
            return true
        }

        // Если белый список включен, но пуст, никто не имеет доступа
        if (whitelist.isEmpty()) {
            if (conf.debugEnabled) {
                plugin.logger.info("Whitelist is enabled but empty, blocking message")
            }
            return false
        }

        // Проверяем, находится ли ID в белом списке
        val allowed = whitelist.contains(telegramId)
        if (conf.debugEnabled) {
            plugin.logger.info("Telegram ID $telegramId is ${if (allowed) "in" else "not in"} whitelist")
        }
        return allowed
    }

    fun addToWhitelist(telegramId: String): Boolean {
        if (whitelist.add(telegramId)) {
            if (conf.databaseEnabled && ZTele.database.databaseExists()) {
                ZTele.database.executeUpdate(
                    "INSERT OR IGNORE INTO whitelist (telegram_id) VALUES (?)",
                    listOf(telegramId)
                )
            } else {
                val whitelistArray = playersConfig.getStringList("whitelist").toMutableList()
                whitelistArray.add(telegramId)
                playersConfig.set("whitelist", whitelistArray)
                conf.savePlayersConfig()
            }
            return true
        }
        return false
    }

    fun removeFromWhitelist(telegramId: String): Boolean {
        if (whitelist.remove(telegramId)) {
            if (conf.databaseEnabled && ZTele.database.databaseExists()) {
                ZTele.database.executeUpdate(
                    "DELETE FROM whitelist WHERE telegram_id = ?",
                    listOf(telegramId)
                )
            } else {
                val whitelistArray = playersConfig.getStringList("whitelist").toMutableList()
                whitelistArray.remove(telegramId)
                playersConfig.set("whitelist", whitelistArray)
                conf.savePlayersConfig()
            }
            return true
        }
        return false
    }

    fun getWhitelist(): Set<String> {
        return whitelist.toSet()
    }

    // Новые методы для работы с белым списком
    fun addPlayerToWhitelist(telegramId: String): Boolean {
        return addToWhitelist(telegramId)
    }

    fun removePlayerFromWhitelist(telegramId: String): Boolean {
        return removeFromWhitelist(telegramId)
    }

    fun getWhitelistedPlayers(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (telegramId in whitelist) {
            val playerName = getPlayerByTelegramId(telegramId) ?: ""
            result[telegramId] = playerName
        }
        return result
    }

    // Новые методы для работы с черным списком
    fun addPlayerToBlacklist(telegramId: String): Boolean {
        return addToBlacklist(telegramId)
    }

    fun removePlayerFromBlacklist(telegramId: String): Boolean {
        return removeFromBlacklist(telegramId)
    }

    fun getBlacklistedPlayers(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (telegramId in blacklist) {
            val playerName = getPlayerByTelegramId(telegramId) ?: ""
            result[telegramId] = playerName
        }
        return result
    }

    fun isPlayerRegistered(playerName: String): Boolean {
        val playerData = registeredPlayers[playerName.lowercase()]
        return playerData != null && !playerData.unlinked && playerData.telegramId.isNotEmpty()
    }

    fun isPlayerUnlinked(playerName: String): Boolean {
        val playerData = registeredPlayers[playerName.lowercase()]
        return playerData != null && playerData.unlinked
    }

    fun isPlayerEverRegistered(playerName: String): Boolean {
        return registeredPlayers.containsKey(playerName.lowercase())
    }

    private fun isValidUsername(username: String): Boolean {
        // Проверяем, что никнейм содержит только английские буквы, цифры и подчеркивания
        // Длина должна быть от 3 до 16 символов (стандарт Minecraft)
        val validPattern = Regex("^[a-zA-Z0-9_]{3,16}$")
        return validPattern.matches(username)
    }

    fun registerPlayer(name: String, telegramId: String): Boolean {
        // Проверяем валидность никнейма
        if (!isValidUsername(name)) {
            plugin.logger.warning("Invalid username format: $name")
            return false
        }

        val lowerName = name.lowercase()

        // Проверяем, существует ли игрок в базе данных
        val existingData = registeredPlayers[lowerName]

        // Если игрок существует, проверяем, был ли он отвязан (unlinked)
        if (existingData != null) {
            // Если игрок уже привязан к другому telegramId и не был отвязан, запрещаем регистрацию
            if (!existingData.unlinked && existingData.telegramId.isNotEmpty()) {
                plugin.logger.warning("Player $name is already registered with telegramId: ${existingData.telegramId}")
                return false
            }

            // Если игрок был отвязан или имеет пустой telegramId, разрешаем повторную регистрацию
            if (existingData.unlinked || existingData.telegramId.isEmpty()) {
                plugin.logger.info("Re-registering player $name with new telegramId: $telegramId (was unlinked: ${existingData.unlinked})")

                // Обновляем данные игрока, сохраняя существующие данные, но обновляя telegramId и сбрасывая флаг unlinked
                val updatedData = existingData.copy(
                    telegramId = telegramId,
                    unlinked = false
                )

                registeredPlayers[lowerName] = updatedData

                // Сохраняем оригинальное написание ника, если оно ещё не сохранено
                if (!originalPlayerNames.containsKey(lowerName)) {
                    originalPlayerNames[lowerName] = name
                }

                // Автоматически добавляем игрока в белый список при повторной регистрации
                addToWhitelist(telegramId)

                savePlayers()
                return true
            }
        }

        // Если игрок не существует, регистрируем его как нового
        val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val registered = LocalDateTime.now().format(dateFormat)

        registeredPlayers[lowerName] = PlayerData(telegramId, registered, null)

        // Сохраняем оригинальное написание ника
        originalPlayerNames[lowerName] = name

        // Автоматически добавляем игрока в белый список при регистрации
        addToWhitelist(telegramId)

        savePlayers()
        return true
    }

    fun unregisterPlayer(playerName: String): Boolean {
        val name = playerName.lowercase()
        if (!registeredPlayers.containsKey(name)) {
            return false
        }

        // Получаем telegramId перед удалением
        val telegramId = registeredPlayers[name]?.telegramId

        // Удаляем из зарегистрированных игроков
        registeredPlayers.remove(name)

        // Удаляем из белого списка, если был в нем
        if (telegramId != null) {
            removeFromWhitelist(telegramId)
        }

        savePlayers()
        return true
    }

    fun unlinkPlayer(playerName: String): Boolean {
        val name = playerName.lowercase()
        val playerData = registeredPlayers[name] ?: return false

        // Получаем telegramId перед отключением
        val telegramId = playerData.telegramId

        // Сохраняем дату регистрации и другие данные, но удаляем привязку к Telegram ID
        registeredPlayers[name] = playerData.copy(telegramId = "", unlinked = true)

        // Удаляем из белого списка
        removeFromWhitelist(telegramId)

        savePlayers()
        return true
    }

    fun getPlayerData(playerName: String): PlayerData? {
        return registeredPlayers[playerName.lowercase()]
    }

    fun getPlayerByTelegramId(telegramId: String): String? {
        for ((player, data) in registeredPlayers) {
            if (data.telegramId == telegramId && !data.unlinked) {
                // Возвращаем оригинальное написание ника, если оно есть
                return originalPlayerNames[player] ?: player
            }
        }
        return null
    }
    
    /**
     * Получает оригинальное написание имени игрока (с правильным регистром)
     */
    fun getOriginalPlayerName(playerName: String): String {
        val lowerName = playerName.lowercase()
        return originalPlayerNames[lowerName] ?: playerName
    }

    fun setPlayerGender(playerName: String, gender: String): Boolean {
        val name = playerName.lowercase()
        val playerData = registeredPlayers[name] ?: return false

        registeredPlayers[name] = playerData.copy(gender = gender)
        savePlayers()
        return true
    }

    // Методы для работы с кодами регистрации
    fun generateRegistrationCode(playerName: String): String {
        plugin.logger.info("Generating registration code for player: $playerName")

        // Удаляем устаревшие коды
        cleanExpiredCodes()

        // Проверяем, есть ли уже активный код для этого игрока
        val existingCode = getCodeForPlayer(playerName)
        if (existingCode != null) {
            plugin.logger.info("Found existing code for player $playerName: $existingCode")
            return existingCode
        }

        // Генерируем новый код
        val codeLength = conf.linkCodeLength
        val characters = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val code = (1..codeLength)
            .map { characters[Random.nextInt(characters.length)] }
            .joinToString("")

        // Устанавливаем время истечения
        val expirationTime = LocalDateTime.now().plusMinutes(conf.linkCodeExpirationMinutes.toLong())

        // Сохраняем код
        activeCodes[code] = Pair(playerName.lowercase(), expirationTime)
        plugin.logger.info("Generated new registration code for player $playerName: $code, expires at: $expirationTime")

        return code
    }

    fun validateRegistrationCode(code: String, telegramId: String): Boolean {
        plugin.logger.info("Validating code: $code for telegramId: $telegramId")

        // Проверяем, существует ли код в словаре активных кодов
        if (!activeCodes.containsKey(code)) {
            plugin.logger.warning("Code $code not found in active codes")
            return false
        }

        val (playerName, expirationTime) = activeCodes[code]!!
        plugin.logger.info("Found code for player: $playerName, expires at: $expirationTime")

        // Проверяем, не истек ли срок действия
        if (LocalDateTime.now().isAfter(expirationTime)) {
            plugin.logger.warning("Code $code has expired")
            activeCodes.remove(code)
            return false
        }

        // Получаем оригинальное имя игрока из Bukkit для сохранения регистра
        val player = Bukkit.getPlayerExact(playerName)
        val originalPlayerName = player?.name ?: playerName
        plugin.logger.info("Using original player name: $originalPlayerName")

        // Регистрируем игрока и удаляем код
        try {
            val registered = registerPlayer(originalPlayerName, telegramId)
            if (registered) {
                plugin.logger.info("Successfully registered player $originalPlayerName with telegramId $telegramId")
            } else {
                plugin.logger.warning("Failed to register player $originalPlayerName with telegramId $telegramId")
            }
            activeCodes.remove(code)
            return registered
        } catch (e: Exception) {
            plugin.logger.severe("Error during registration: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    private fun getCodeForPlayer(playerName: String): String? {
        val name = playerName.lowercase()
        for ((code, pair) in activeCodes) {
            if (pair.first.equals(name, ignoreCase = true)) {
                return code
            }
        }
        return null
    }

    private fun cleanExpiredCodes() {
        val now = LocalDateTime.now()
        val expiredCodes = activeCodes.entries
            .filter { now.isAfter(it.value.second) }
            .map { it.key }

        for (code in expiredCodes) {
            activeCodes.remove(code)
        }
    }

    private fun savePlayers() {
        if (conf.databaseEnabled && ZTele.database.databaseExists()) {
            savePlayersToDatabase()
        } else {
            savePlayersToYaml()
        }
    }
    
    private fun savePlayersToDatabase() {
        try {
            ZTele.database.executeTransaction { conn ->
                // Сохраняем зарегистрированных игроков
                for ((playerName, data) in registeredPlayers) {
                    val originalName = originalPlayerNames[playerName] ?: playerName
                    
                    conn.prepareStatement("""
                        INSERT OR REPLACE INTO players 
                        (telegram_id, player_name, registered_date, gender, unlinked, original_name, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, datetime('now'))
                    """).use { stmt ->
                        stmt.setString(1, data.telegramId)
                        stmt.setString(2, playerName)
                        stmt.setString(3, data.registered ?: "")
                        stmt.setString(4, data.gender ?: "")
                        stmt.setInt(5, if (data.unlinked) 1 else 0)
                        stmt.setString(6, originalName)
                        stmt.executeUpdate()
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Ошибка сохранения игроков в БД: ${e.message}")
            savePlayersToYaml() // Fallback на YAML
        }
    }
    
    private fun savePlayersToYaml() {
        // Не сохраняем в YAML если база данных включена
        if (ZTele.conf.databaseEnabled) {
            return
        }
        
        val config = ZTele.conf.getPlayersConfig()

        // Save hidden players
        config.set("hidden-players", hiddenPlayers.toList())

        // Save blacklist
        config.set("blacklist", blacklist.toList())

        // Save whitelist
        config.set("whitelist", whitelist.toList())

        // Save registered players
        config.set("players", null) // Clear existing players

        for ((playerName, data) in registeredPlayers) {
            val path = "players.$playerName"
            config.set("$path.telegram-id", data.telegramId)
            config.set("$path.registered", data.registered)
            config.set("$path.gender", data.gender)
            config.set("$path.unlinked", data.unlinked)

            // Сохраняем оригинальное написание имени
            val originalName = originalPlayerNames[playerName]
            if (originalName != null) {
                config.set("$path.original-name", originalName)
            }
        }

        ZTele.conf.savePlayersConfig()
    }


    /**
     * Получает всех зарегистрированных игроков
     */
    fun getAllRegisteredPlayers(): Map<String, Long> {
        return registeredPlayers.mapNotNull { (playerName, playerData) ->
            if (!playerData.unlinked && playerData.telegramId.isNotEmpty()) {
                try {
                    playerName to playerData.telegramId.toLong()
                } catch (e: NumberFormatException) {
                    null
                }
            } else {
                null
            }
        }.toMap()
    }

    data class PlayerData(
        val telegramId: String,
        val registered: String? = null,
        val gender: String? = null,
        val unlinked: Boolean = false
    )
}
