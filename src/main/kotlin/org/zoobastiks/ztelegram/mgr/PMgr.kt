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
                    
                    registeredPlayers[playerName] = PlayerData(telegramId, registered, gender, unlinked)
                    
                    // Загружаем оригинальное написание имени
                    val originalName = playerSection.getString("original-name")
                    if (originalName != null) {
                        originalPlayerNames[playerName] = originalName
                    } else {
                        // Если нет оригинального имени, используем имя из конфигурации
                        originalPlayerNames[playerName] = playerName
                    }
                }
            }
        }
    }
    
    fun isPlayerHidden(name: String): Boolean {
        val hiddenPlayers = playersConfig.getStringList("hidden-players")
        return hiddenPlayers.contains(name.lowercase())
    }
    
    fun addHiddenPlayer(name: String) {
        val hiddenPlayers = playersConfig.getStringList("hidden-players").toMutableList()
        
        hiddenPlayers.add(name.lowercase())
        playersConfig.set("hidden-players", hiddenPlayers)
        conf.savePlayersConfig()
    }
    
    fun removeHiddenPlayer(name: String) {
        val hiddenPlayers = playersConfig.getStringList("hidden-players").toMutableList()
        
        hiddenPlayers.remove(name.lowercase())
        playersConfig.set("hidden-players", hiddenPlayers)
        conf.savePlayersConfig()
    }
    
    // Метод для получения списка скрытых игроков
    fun getHiddenPlayers(): List<String> {
        return playersConfig.getStringList("hidden-players")
    }
    
    // Методы для работы с черным списком
    fun isPlayerBlacklisted(telegramId: String): Boolean {
        return blacklist.contains(telegramId)
    }
    
    fun addToBlacklist(telegramId: String): Boolean {
        if (blacklist.add(telegramId)) {
            val blacklistArray = playersConfig.getStringList("blacklist").toMutableList()
            blacklistArray.add(telegramId)
            playersConfig.set("blacklist", blacklistArray)
            conf.savePlayersConfig()
            return true
        }
        return false
    }
    
    fun removeFromBlacklist(telegramId: String): Boolean {
        if (blacklist.remove(telegramId)) {
            val blacklistArray = playersConfig.getStringList("blacklist").toMutableList()
            blacklistArray.remove(telegramId)
            playersConfig.set("blacklist", blacklistArray)
            conf.savePlayersConfig()
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
        plugin.logger.info("Checking whitelist for telegramId: $telegramId")
        plugin.logger.info("Whitelist enabled: ${conf.whitelistEnabled}")
        plugin.logger.info("Whitelist content: ${whitelist.joinToString(", ")}")
        
        // Если белый список отключен, разрешаем всех
        if (!conf.whitelistEnabled) {
            plugin.logger.info("Whitelist is disabled, allowing message")
            return true
        }
        
        // Если белый список включен, но пуст, никто не имеет доступа
        if (whitelist.isEmpty()) {
            plugin.logger.info("Whitelist is enabled but empty, blocking message")
            return false
        }
        
        // Проверяем, находится ли ID в белом списке
        val allowed = whitelist.contains(telegramId)
        plugin.logger.info("Telegram ID $telegramId is ${if (allowed) "in" else "not in"} whitelist")
        return allowed
    }
    
    fun addToWhitelist(telegramId: String): Boolean {
        if (whitelist.add(telegramId)) {
            val whitelistArray = playersConfig.getStringList("whitelist").toMutableList()
            whitelistArray.add(telegramId)
            playersConfig.set("whitelist", whitelistArray)
            conf.savePlayersConfig()
            return true
        }
        return false
    }
    
    fun removeFromWhitelist(telegramId: String): Boolean {
        if (whitelist.remove(telegramId)) {
            val whitelistArray = playersConfig.getStringList("whitelist").toMutableList()
            whitelistArray.remove(telegramId)
            playersConfig.set("whitelist", whitelistArray)
            conf.savePlayersConfig()
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
    
    fun registerPlayer(name: String, telegramId: String): Boolean {
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
    
    data class PlayerData(
        val telegramId: String,
        val registered: String? = null,
        val gender: String? = null,
        val unlinked: Boolean = false
    )
} 