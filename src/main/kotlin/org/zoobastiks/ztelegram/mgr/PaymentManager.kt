package org.zoobastiks.ztelegram.mgr

import org.bukkit.Bukkit
import org.zoobastiks.ztelegram.ZTele
import org.zoobastiks.ztelegram.conf.TConf
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Менеджер для управления переводами денег между игроками
 */
class PaymentManager(private val plugin: ZTele) {
    private val conf: TConf
        get() = ZTele.conf
    
    /**
     * Результат перевода
     */
    data class TransferResult(
        val success: Boolean,
        val errorCode: String? = null, // "vault_not_found", "invalid_amount", "min_amount", "max_amount", "same_player", "player_not_found", "insufficient_funds", "withdraw_error", "deposit_error", "general_error"
        val errorMessage: String? = null, // Для ошибок с дополнительным сообщением (например, от Vault API)
        val fromPlayer: String? = null,
        val toPlayer: String? = null,
        val amount: Double? = null,
        val newBalance: Double? = null
    )
    
    /**
     * Выполняет перевод денег от одного игрока другому
     * @param fromPlayerName Имя игрока-отправителя
     * @param toPlayerName Имя игрока-получателя
     * @param amount Сумма перевода
     * @return TransferResult - результат перевода
     */
    fun transferMoney(fromPlayerName: String, toPlayerName: String, amount: Double): TransferResult {
        // Проверяем, что Vault доступен
        val economy = ZTele.economy ?: return TransferResult(false, "vault_not_found")
        
        // Валидация суммы
        if (amount <= 0) {
            return TransferResult(false, "invalid_amount")
        }
        
        if (amount < conf.paymentMinAmount) {
            return TransferResult(false, "min_amount")
        }
        
        if (amount > conf.paymentMaxAmount && conf.paymentMaxAmount > 0) {
            return TransferResult(false, "max_amount")
        }
        
        // Проверяем, что игроки не одинаковые
        if (fromPlayerName.equals(toPlayerName, ignoreCase = true)) {
            return TransferResult(false, "same_player")
        }
        
        // Получаем игроков
        val fromPlayer = Bukkit.getOfflinePlayer(fromPlayerName)
        val toPlayer = Bukkit.getOfflinePlayer(toPlayerName)
        
        // Проверяем, что получатель существует
        if (!toPlayer.hasPlayedBefore() && Bukkit.getPlayer(toPlayerName) == null) {
            return TransferResult(false, "player_not_found")
        }
        
        // Проверяем баланс отправителя
        val fromBalance = economy.getBalance(fromPlayer)
        if (fromBalance < amount) {
            return TransferResult(false, "insufficient_funds")
        }
        
        // Выполняем перевод
        return try {
            // Списываем деньги с отправителя
            val withdrawResponse = economy.withdrawPlayer(fromPlayer, amount)
            if (!withdrawResponse.transactionSuccess()) {
                return TransferResult(false, "withdraw_error", withdrawResponse.errorMessage)
            }
            
            // Зачисляем деньги получателю
            val depositResponse = economy.depositPlayer(toPlayer, amount)
            if (!depositResponse.transactionSuccess()) {
                // Откатываем транзакцию, если зачисление не удалось
                economy.depositPlayer(fromPlayer, amount)
                return TransferResult(false, "deposit_error", depositResponse.errorMessage)
            }
            
            // Сохраняем перевод в историю
            savePaymentHistory(fromPlayerName, toPlayerName, amount)
            
            // Отправляем оповещение в игру
            sendPaymentNotification(fromPlayerName, toPlayerName, amount)
            
            // Очищаем кэш топа баланса, чтобы он обновился после перевода
            org.zoobastiks.ztelegram.utils.TopManager.clearCache(org.zoobastiks.ztelegram.utils.TopManager.TopType.BALANCE)
            
            val newBalance = economy.getBalance(fromPlayer)
            TransferResult(
                success = true,
                fromPlayer = fromPlayerName,
                toPlayer = toPlayerName,
                amount = amount,
                newBalance = newBalance
            )
        } catch (e: Exception) {
            plugin.logger.severe("Ошибка при переводе денег: ${e.message}")
            e.printStackTrace()
            TransferResult(false, "general_error")
        }
    }
    
    /**
     * Сохраняет перевод в историю
     */
    private fun savePaymentHistory(fromPlayer: String, toPlayer: String, amount: Double) {
        if (conf.databaseEnabled && ZTele.database.databaseExists()) {
            try {
                ZTele.database.executeUpdate(
                    "INSERT INTO payments (from_player, to_player, amount, timestamp, status) VALUES (?, ?, ?, ?, ?)",
                    listOf(
                        fromPlayer,
                        toPlayer,
                        amount,
                        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        "completed"
                    )
                )
            } catch (e: Exception) {
                plugin.logger.warning("Ошибка сохранения истории перевода: ${e.message}")
            }
        }
    }
    
    /**
     * Отправляет оповещение о переводе в игру
     */
    private fun sendPaymentNotification(fromPlayer: String, toPlayer: String, amount: Double) {
        if (conf.paymentBroadcastCommand.isNotEmpty()) {
            val economy = ZTele.economy
            val currencyName = economy?.currencyNamePlural() ?: "монет"
            val message = conf.paymentBroadcastMessage
                .replace("%from_player%", fromPlayer)
                .replace("%to_player%", toPlayer)
                .replace("%amount%", String.format("%.2f", amount))
                .replace("%currency%", currencyName)
            
            Bukkit.getScheduler().runTask(plugin, Runnable {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "${conf.paymentBroadcastCommand} $message")
            })
        }
    }
    
    /**
     * Получает историю переводов для игрока
     * @param playerName Имя игрока
     * @param limit Лимит записей
     * @return Список переводов (от кого, кому, сумма, время)
     */
    fun getPaymentHistory(playerName: String, limit: Int = 20): List<PaymentRecord> {
        if (!conf.databaseEnabled || !ZTele.database.databaseExists()) {
            return emptyList()
        }
        
        return try {
            ZTele.database.executeQuery(
                """
                SELECT from_player, to_player, amount, timestamp 
                FROM payments 
                WHERE from_player = ? OR to_player = ?
                ORDER BY timestamp DESC 
                LIMIT ?
                """,
                listOf(playerName, playerName, limit)
            ) { rs ->
                val history = mutableListOf<PaymentRecord>()
                while (rs.next()) {
                    history.add(
                        PaymentRecord(
                            fromPlayer = rs.getString("from_player"),
                            toPlayer = rs.getString("to_player"),
                            amount = rs.getDouble("amount"),
                            timestamp = rs.getString("timestamp")
                        )
                    )
                }
                history
            } ?: emptyList()
        } catch (e: Exception) {
            plugin.logger.warning("Ошибка получения истории переводов: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Получает статистику переводов для игрока
     */
    fun getPaymentStats(playerName: String): PaymentStats {
        if (!conf.databaseEnabled || !ZTele.database.databaseExists()) {
            return PaymentStats(0.0, 0.0, 0, 0)
        }
        
        return try {
            val sent = ZTele.database.executeQuery(
                "SELECT COALESCE(SUM(amount), 0) as total, COUNT(*) as count FROM payments WHERE from_player = ?",
                listOf(playerName)
            ) { rs ->
                if (rs.next()) {
                    Pair(rs.getDouble("total"), rs.getInt("count"))
                } else {
                    Pair(0.0, 0)
                }
            } ?: Pair(0.0, 0)
            
            val received = ZTele.database.executeQuery(
                "SELECT COALESCE(SUM(amount), 0) as total, COUNT(*) as count FROM payments WHERE to_player = ?",
                listOf(playerName)
            ) { rs ->
                if (rs.next()) {
                    Pair(rs.getDouble("total"), rs.getInt("count"))
                } else {
                    Pair(0.0, 0)
                }
            } ?: Pair(0.0, 0)
            
            PaymentStats(sent.first, received.first, sent.second, received.second)
        } catch (e: Exception) {
            plugin.logger.warning("Ошибка получения статистики переводов: ${e.message}")
            PaymentStats(0.0, 0.0, 0, 0)
        }
    }
    
    /**
     * Запись о переводе
     */
    data class PaymentRecord(
        val fromPlayer: String,
        val toPlayer: String,
        val amount: Double,
        val timestamp: String
    )
    
    /**
     * Статистика переводов
     */
    data class PaymentStats(
        val totalSent: Double,
        val totalReceived: Double,
        val sentCount: Int,
        val receivedCount: Int
    )
}


