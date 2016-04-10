package pw.mzn.uptimebot

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

data class Site(var userId: String, var chatId: String, var url: String)

class SiteManager() {
    val sites: MutableList<Site> = LinkedList()

    fun load() {
        if (!File("sites").exists()) {
            return
        }

        var joiner = StringJoiner("")
        Files.readAllLines(Paths.get("sites")).forEach { e -> joiner.add(e) } // rip google commons

        JSONArray(joiner.toString()).forEach { e -> run() {
            if (e is JSONObject) {
                sites.add(Site(e.getString("user_id"), e.getString("chat_id"), e.getString("url")))
            }
        }} // all of this for @zackpollard bb. no gson4u
    }

    fun save() {
        var json = JSONArray()

        sites.forEach { e -> json.put(JSONObject().put("user_id", e.userId)
                    .put("chat_id", e.chatId)
                    .put("url", e.url)) }

        Files.write(Paths.get("sites"), json.toString().toByteArray())
    }
}