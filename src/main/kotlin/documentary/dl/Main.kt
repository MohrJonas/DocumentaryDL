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
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.system.exitProcess

private val client = ApacheClient()
private val request = Request(Method.GET, "https://www.reddit.com/r/Documentaries/top.json?t=day")

fun main() {
    preflightAssert().let {
        if (it.isPresent) {
            Logger.error("Preflight", "Error in preflight check: ${it.get()}")
            exitProcess(-1)
        } else {
            Logger.info("Preflight", "Preflight check succeeded")
        }
    }
    val scheduler = Scheduler()
    scheduler.schedule("0 0 * * *") {
        val backoffStrat = StrategyBackoff<List<String>>(10, ConstantStrategy(300000L), ::nonFatal) {
            it.isNotEmpty()
        }
        runBlocking {
            when (val result = backoffStrat.withRetries { parseLinks() }) {
                is Ok -> {
                    Logger.info("LinkFetcher", "Fetching links was successfull")
                    result.value.forEach { downloadVideo(it, Path.of(System.getenv("VIDEO_PATH"))) }
                }
                else -> {
                    Logger.info("LinkFetcher", "Fetching links failed")
                    System.err.println(result)
                }
            }
            Logger.info("Scheduler", "Done executing scheduled job. Running again in 24 hours")
        }
    }
    Logger.info("Scheduler", "Starting scheduler")
    scheduler.start()
}

private fun parseLinks(): List<String> {
    val response = client(request).body.toString()
    val json = Json.parse(response).asObject()
    if (json.names().contains("error")) {
        Logger.error("LinkParser", json.get("message").asString())
        return emptyList()
    }
    return json.get("data").asObject()
        .get("children").asArray()
        .map { it.asObject().get("data").asObject() }
        .filter {
            it.getString("post_hint", String()) == "rich:video" && it.getString("domain", String()).contains("youtu")
        }
        .map { it.get("url_overridden_by_dest").asString() }
        .take(5)
}

private fun preflightAssert(): Optional<String> {
    val videoPath = System.getenv("VIDEO_PATH")
    if (videoPath == null || Path.of(videoPath)
            .let { !Files.exists(it) || !Files.isWritable(it) }
    ) return Optional.of("VIDEO_PATH either isn't defined, doesn't exist or isn't writable")
    return (Runtime.getRuntime().exec("yt-dlp --version")
        .waitFor() == 0).let { if (it) Optional.empty() else Optional.of("yt-dlp either isn't installed or not in the PATH") }
}

private suspend fun downloadVideo(url: String, folder: Path) {
    Logger.info("Downloader", "Downloading video from $url to $folder")
    withContext(Dispatchers.IO) {
        ProcessBuilder()
            .directory(folder.toFile())
            .command("yt-dlp", "--remux-video", "mp4", "--format-sort", "res:1080", url)
            .inheritIO()
            .start()
            .waitFor()
    }
}

object Logger {
    fun error(name: String, msg: String) {
        System.err.println("[$name]: $msg")
    }

    fun info(name: String, msg: String) {
        println("[$name]: $msg")
    }
}