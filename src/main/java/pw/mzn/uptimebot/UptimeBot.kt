package pw.mzn.uptimebot

import com.mashape.unirest.http.Unirest
import com.mashape.unirest.http.exceptions.UnirestException
import pro.zackpollard.telegrambot.api.TelegramBot
import pro.zackpollard.telegrambot.api.chat.IndividualChat
import pro.zackpollard.telegrambot.api.chat.message.send.ParseMode
import pro.zackpollard.telegrambot.api.chat.message.send.SendableTextMessage
import pro.zackpollard.telegrambot.api.event.Listener
import pro.zackpollard.telegrambot.api.event.chat.message.CommandMessageReceivedEvent
import java.net.URL
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.properties.Delegates

class UptimeBot(val key: String) {
    val interval: Long = TimeUnit.MINUTES.toMillis(1L)
    val timer: Timer = Timer()
    var bot: TelegramBot by Delegates.notNull()
    var siteManager: SiteManager = SiteManager()

    fun init() {
        bot = TelegramBot.login(key)
        bot.eventsManager.register(UptimeBotListener(this))
        bot.startUpdates(false)
        siteManager.load()
        timer.scheduleAtFixedRate(UptimeMasterTask(this), 0L, interval)
        println("Started!")
    }

    fun isOnline(url: String): Boolean {
        try {
            var contents = Unirest.get(url).asString()

            if (contents.body.contains(Matches.cloudFlareMatch)) {
                return false
            }

            if (contents.status != 200 && contents.status != 404) { // accept 404s, it's online technically
                return false
            }

            return true
        } catch (ex: UnirestException) {
            return false
        }
    }
}

class UptimeMasterTask(val instance: UptimeBot): TimerTask() {
    override fun run() {
        var sites = instance.siteManager.sites

        if (sites.size == 0) {
            // let's not divide by zero
            return
        }

        var interval = instance.interval / sites.size
        var counter = sites.size

        sites.forEach { e -> instance.timer.schedule(UptimeMinorTask(e, instance), (interval * --counter) + 1) }
    }
}

class UptimeMinorTask(val site: Site, val instance: UptimeBot): TimerTask() {
    override fun run() {
        if (!instance.isOnline(site.url)) {
            return // welp
        }

        var chat = TelegramBot.getChat(site.chatId)
        var niceUrl = URL(site.url).host
        var messageBuilder = StringBuilder("$niceUrl is online! \n")

        if (chat !is IndividualChat) {
            messageBuilder.append("*Submitted by* @${site.userId}\n")
        }

        messageBuilder.append("[Click here to go on it!](${site.url})")
        instance.bot.sendMessage(chat, SendableTextMessage.builder()
                .parseMode(ParseMode.MARKDOWN)
                .message(messageBuilder.toString())
                .build())
        instance.siteManager.sites.remove(site)
        instance.siteManager.save()
    }
}

class UptimeBotListener(val instance: UptimeBot) : Listener {
    override fun onCommandMessageReceived(event: CommandMessageReceivedEvent?) {
        if (event!!.command.equals("register")) {
            register(event)
        }
    }

    fun register(event: CommandMessageReceivedEvent) {
        var args = event.args

        if (args.size == 0) {
            event.chat.sendMessage("Provide a URL for the bot to check for", instance.bot)
            return
        }

        var url = args[0]

        if (!Matches.testUrl(url)) {
            event.chat.sendMessage("Please provide a valid URL", instance.bot)
            return
        }

        if (instance.isOnline(url)) {
            event.chat.sendMessage("That site is online!", instance.bot)
            return
        }

        var site = Site(event.message.sender.username, event.chat.id, url)
        instance.siteManager.sites.add(site)
        instance.siteManager.save()
        event.chat.sendMessage("Alright, I'll let you know when ${URL(url).host} is back online", instance.bot)
    }
}

private object Matches {
    val cloudFlareMatch = "<h2 class=\"cf-subheadline\" data-translate=\"error_desc\">Web server is down</h2>"

    fun testUrl(link: String): Boolean {
        try {
            URL(link)
            return true
        } catch (e: Exception) {
            return false
        }
    }
}

