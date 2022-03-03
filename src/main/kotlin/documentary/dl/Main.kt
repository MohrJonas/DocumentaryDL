package documentary.dl

import com.eclipsesource.json.Json
import io.github.reugn.kotlin.backoff.StrategyBackoff
import io.github.reugn.kotlin.backoff.strategy.ConstantStrategy
import io.github.reugn.kotlin.backoff.util.Ok
import io.github.reugn.kotlin.backoff.util.nonFatal
import it.sauronsoftware.cron4j.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.http4k.client.ApacheClient
import org.http4k.core.Method
import org.http4k.core.Request
import java.nio.file.Path

private val client = ApacheClient()
private val request = Request(Method.GET, "https://www.reddit.com/r/Documentaries/top.json?t=day")

fun main() {
    val scheduler = Scheduler()
    scheduler.schedule("32 0 * * *") {
        val backoffStrat = StrategyBackoff<List<String>>(10, ConstantStrategy(300000L), ::nonFatal) {
            it.isNotEmpty()
        }
        runBlocking {
            when (val result = backoffStrat.withRetries { parseLinks() }) {
                is Ok -> {
                    result.value.forEach { downloadVideo(it, Path.of("/home/jonas/Videos")) }
                }
                else -> {
                    System.err.println(result)
                }
            }
        }
    }
    scheduler.start()
}

private fun parseLinks(): List<String> {
    val response = client(request).body.toString()
    val json = Json.parse(response).asObject()
    if (json.names().contains("error")) {
        System.err.print(json.get("message").asString())
        return emptyList()
    }
    return json.get("data").asObject()
        .get("children").asArray()
        .map { it.asObject().get("data").asObject() }
        .filter {
            it.getString("post_hint", String()) == "rich:video" && it.getString("domain", String()) == "youtube.com"
        }
        .map { it.get("url_overridden_by_dest").asString() }
        .take(5)
}

private suspend fun downloadVideo(url: String, folder: Path) {
    val process = withContext(Dispatchers.IO) {
        ProcessBuilder().directory(folder.toFile()).command("yt-dlp", "--remux-video", "mp4", url).inheritIO().start()
            .waitFor()
    }
}