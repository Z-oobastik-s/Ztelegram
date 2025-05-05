package org.zoobastiks.ztelegram

import org.bukkit.ChatColor

object ColorUtils {
    /**
     * Translates color codes and hex colors in a string
     * Supports:
     * - Standard color codes with &/§ (e.g. &a)
     * - Hex colors with &#RRGGBB or #RRGGBB
     * - Newlines with \n
     *
     * @param altColorChar The character used for color codes (usually & or §)
     * @param textToTranslate The text to translate color codes for
     * @return The text with translated color codes
     */
    fun translateAlternateColorCodesAndHexes(altColorChar: Char, textToTranslate: String): String {
        // Сначала заменяем \n на настоящие переносы строк
        val processedText = textToTranslate.replace("\\n", "\n")
        
        val b = StringBuilder()
        val mess = processedText.toCharArray()
        var color = false
        var hashtag = false
        var doubleTag = false
        var tmp: Char // Used in loops

        for (i in mess.indices) {
            val c = mess[i]

            if (doubleTag) {
                doubleTag = false
                val max = i + 6

                if (max <= mess.size) {
                    var match = true

                    for (n in i until max) {
                        tmp = mess[n]
                        if (!((tmp in '0'..'9') || (tmp in 'a'..'f') || (tmp in 'A'..'F'))) {
                            match = false
                            break
                        }
                    }

                    if (match) {
                        b.append(ChatColor.COLOR_CHAR)
                        b.append('x')

                        for (j in i until max) {
                            tmp = mess[j]
                            b.append(ChatColor.COLOR_CHAR)
                            b.append(tmp)
                        }
                        continue
                    }
                }
                b.append('#')
            }

            if (hashtag) {
                hashtag = false
                val max = i + 6

                if (max <= mess.size) {
                    var match = true

                    for (n in i until max) {
                        tmp = mess[n]
                        if (!((tmp in '0'..'9') || (tmp in 'a'..'f') || (tmp in 'A'..'F'))) {
                            match = false
                            break
                        }
                    }

                    if (match) {
                        b.append(ChatColor.COLOR_CHAR)
                        b.append('x')

                        for (j in i until max) {
                            b.append(ChatColor.COLOR_CHAR)
                            b.append(mess[j])
                        }
                        continue
                    }
                }
                b.append(altColorChar)
                b.append('#')
            }

            if (color) {
                color = false

                if (c == '#') {
                    hashtag = true
                    continue
                }

                if ((c in '0'..'9') || (c in 'a'..'f') || c == 'r' || (c in 'k'..'o') || (c in 'A'..'F') || c == 'R' || (c in 'K'..'O')) {
                    b.append(ChatColor.COLOR_CHAR)
                    b.append(c)
                    continue
                }

                b.append(altColorChar)
            }

            if (c == altColorChar) {
                color = true
                continue
            }

            if (c == '#') {
                doubleTag = true
                continue
            }

            b.append(c)
        }

        if (color) b.append(altColorChar)
        else if (hashtag) {
            b.append(altColorChar)
            b.append('#')
        } else if (doubleTag) {
            b.append('#')
        }

        return b.toString()
    }
    
    /**
     * Translates all color codes and hex colors in a string using '&' as the color code character
     *
     * @param text The text to translate
     * @return The text with translated color codes
     */
    fun translateColorCodes(text: String): String {
        return translateAlternateColorCodesAndHexes('&', text)
    }
    
    /**
     * Strips all Minecraft color codes from a string
     *
     * @param text The text to strip color codes from
     * @return The text without color codes
     */
    fun stripColorCodes(text: String): String {
        val safeText = text ?: ""
        // Заменяем \n на настоящие переносы строк
        val processedText = safeText.replace("\\n", "\n")
        
        // Удаляем цветовые коды и теги MiniMessage
        val strippedText = ChatColor.stripColor(processedText.replace("&[0-9a-fk-orA-FK-OR]".toRegex(), ""))
        
        // Удаляем MiniMessage теги
        return strippedText?.replace("<[^>]*>".toRegex(), "") ?: processedText
    }
}