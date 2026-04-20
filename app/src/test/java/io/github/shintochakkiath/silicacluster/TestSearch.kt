import io.github.shintochakkiath.silicacluster.WebSearcher
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val results = WebSearcher.search("weather today")
    println(results)
}
