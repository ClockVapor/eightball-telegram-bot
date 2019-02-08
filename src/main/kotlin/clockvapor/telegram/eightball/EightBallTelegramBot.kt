package clockvapor.telegram.eightball

import com.fasterxml.jackson.databind.ObjectMapper
import com.xenomachina.argparser.ArgParser
import me.ivmg.telegram.bot
import me.ivmg.telegram.dispatch
import me.ivmg.telegram.dispatcher.command
import me.ivmg.telegram.entities.ChatAction
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class EightBallTelegramBot(private val token: String,
                           private val dataPath: String,
                           private val defaultAnswersPath: String) {
    fun run() {
        bot {
            logLevel = HttpLoggingInterceptor.Level.NONE
            this.token = this@EightBallTelegramBot.token
            dispatch {
                command("8ball") { bot, update ->
                    val message = update.message!!
                    bot.sendChatAction(message.chat.id, ChatAction.TYPING)
                    val command = message.entities!![0]
                    val question = message.text!!.substring(command.offset + command.length).trim()
                    val replyText = if (question.isBlank()) {
                        "Please ask me a question."
                    } else {
                        tryOrErrorMessage { getRandomAnswer(message.chat.id) }
                    }
                    bot.sendMessage(message.chat.id, replyText, replyToMessageId = message.messageId)
                }
                command("listanswers") { bot, update ->
                    val message = update.message!!
                    bot.sendChatAction(message.chat.id, ChatAction.TYPING)
                    val replyText = tryOrErrorMessage {
                        loadAnswers(message.chat.id).takeIf { it.isNotEmpty() }?.mapIndexed { i, answer ->
                            "${i + 1}. ${sanitizeAnswerForList(answer)}"
                        }?.joinToString("\n") ?: "There are currently no answers in the 8 ball."
                    }
                    bot.sendMessage(message.chat.id, replyText, replyToMessageId = message.messageId)
                }
                command("addanswer") { bot, update ->
                    val message = update.message!!
                    bot.sendChatAction(message.chat.id, ChatAction.TYPING)
                    val command = message.entities!![0]
                    val replyText = tryOrErrorMessage {
                        sanitizeAnswer(message.text!!.substring(command.offset + command.length))
                            .takeIf { it.isNotBlank() }
                            ?.let { newAnswer ->
                                if (addAnswer(message.chat.id, newAnswer)) "Added your new answer."
                                else "That answer isn't new."
                            } ?: "Please provide a new answer to add."
                    }
                    bot.sendMessage(message.chat.id, replyText, replyToMessageId = message.messageId)
                }
                command("removeanswer") { bot, update ->
                    val message = update.message!!
                    bot.sendChatAction(message.chat.id, ChatAction.TYPING)
                    val command = message.entities!![0]
                    val replyText = tryOrErrorMessage {
                        message.text!!.substring(command.offset + command.length).trim().toIntOrNull()?.let { i ->
                            if (removeAnswer(message.chat.id, i - 1)) "Removed that answer."
                            else "Invalid answer number."
                        } ?: "Please provide the number of the answer to remove."
                    }
                    bot.sendMessage(message.chat.id, replyText, replyToMessageId = message.messageId)
                }
            }
        }.startPolling()
    }

    private fun getRandomAnswer(chatId: Long): String =
        loadAnswers(chatId).random()

    private fun addAnswer(chatId: Long, answer: String): Boolean {
        val answers = loadAnswers(chatId)
        return (!answers.contains(answer)).alsoIfTrue {
            answers.add(answer)
            saveAnswers(chatId, answers)
        }
    }

    private fun removeAnswer(chatId: Long, i: Int): Boolean {
        val answers = loadAnswers(chatId)
        return (i in answers.indices).alsoIfTrue {
            answers.removeAt(i)
            saveAnswers(chatId, answers)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadAnswers(chatId: Long): MutableList<String> {
        val answersFile = getAnswersFile(chatId)
        if (!answersFile.exists()) {
            Files.copy(Paths.get(defaultAnswersPath), answersFile.toPath())
        }
        return ObjectMapper().readValue(answersFile, MutableList::class.java) as MutableList<String>
    }

    private fun saveAnswers(chatId: Long, answers: List<String>) {
        ObjectMapper().writeValue(getAnswersFile(chatId), answers)
    }

    private fun getAnswersFile(chatId: Long): File =
        Paths.get(dataPath, "$chatId.json").toFile().apply { parentFile.mkdirs() }

    private fun sanitizeAnswer(answer: String): String =
        answer.trim()

    private fun sanitizeAnswerForList(answer: String): String =
        answer.trim().replace('\n', ' ').let {
            if (it.length > LIST_CLAMP_LENGTH) {
                it.take(LIST_CLAMP_LENGTH - 3) + "..."
            } else {
                it
            }
        }

    private fun tryOrErrorMessage(func: () -> String): String = try {
        func()
    } catch (e: Exception) {
        e.printStackTrace()
        "<an error occurred>"
    }

    class Args(parser: ArgParser) {
        val token by parser.storing("-t", "--token", help = "Telegram bot token")
        val dataPath by parser.storing("-d", "--data", help = "Path to data folder")
        val defaultAnswersPath by parser.storing("-a", "--answers",
            help = "Path to JSON file containing default 8 ball answers.")
    }

    companion object {
        private const val LIST_CLAMP_LENGTH: Int = 40

        @JvmStatic
        fun main(args: Array<String>) {
            val a = ArgParser(args).parseInto(::Args)
            EightBallTelegramBot(a.token, a.dataPath, a.defaultAnswersPath).run()
        }
    }
}
