package io.github.shintochakkiath.silicacluster

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

data class SearchResult(val title: String, val snippet: String, val url: String)

object WebSearcher {
    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 10000
        }
    }
    
    suspend fun getRealTimeContext(query: String): Pair<String, List<String>> = withContext(Dispatchers.IO) {
        var contextText = ""
        val links = mutableListOf<String>()
        
        // 1. Get Location Context
        var city = "Unknown"
        var country = "Unknown"
        try {
            val ipData = client.get("https://ipinfo.io/json").bodyAsText()
            val json = org.json.JSONObject(ipData)
            city = json.optString("city", "Unknown")
            country = json.optString("country", "Unknown")
            contextText += "CURRENT LOCATION OF USER: City: $city, Country: $country. Current Time: ${java.util.Date()}\n"
        } catch (e: Exception) {
            e.printStackTrace()
            contextText += "Current Time: ${java.util.Date()}\n"
        }

        // 2. Weather Specific Override
        if (query.contains("weather", ignoreCase = true) || query.contains("temperature", ignoreCase = true)) {
            try {
                val weatherUrl = if (city != "Unknown" && city.isNotBlank()) "https://wttr.in/${URLEncoder.encode(city, "UTF-8")}?format=j1" else "https://wttr.in/?format=j1"
                val weatherData = client.get(weatherUrl).bodyAsText()
                val wJson = org.json.JSONObject(weatherData)
                val current = wJson.optJSONArray("current_condition")?.optJSONObject(0)
                if (current != null) {
                    val temp = current.optString("temp_C")
                    val desc = current.optJSONArray("weatherDesc")?.optJSONObject(0)?.optString("value")
                    val humidity = current.optString("humidity")
                    contextText += "REAL-TIME WEATHER DATA FOR $city: $temp °C, Condition: $desc, Humidity: $humidity%.\n"
                    links.add("https://wttr.in/${URLEncoder.encode(city, "UTF-8")}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } 
        
        // 3. Web Search Fallbacks (only if link is still null, meaning we didn't just fulfill a weather query)
        if (links.isEmpty()) {
            try {
                val searchResults = search(query).take(3)
                if (searchResults.isNotEmpty()) {
                    searchResults.forEach { links.add(it.url) }
                    val snippets = searchResults.mapIndexed { index, it -> "Source ${index + 1}: ${it.title}\nURL: ${it.url}\nInformation: ${it.snippet}" }.joinToString("\n\n")
                    contextText += "REAL-TIME WEB SEARCH RESULTS:\n$snippets\n\nSYSTEM INSTRUCTION: You MUST format your response as follows:\n1. Open with a brief introductory paragraph summarizing the topic.\n2. Present a bulleted list where each bullet explains one specific piece of information, ending immediately with its inline citation (e.g., [Source 1]).\n3. End with a short concluding paragraph. Do not use run-on sentences."
                } else {
                    // Try Google News RSS for live events, war stats, updates
                    val newsResults = googleNewsSearch(query).take(3)
                    if (newsResults.isNotEmpty()) {
                        newsResults.forEach { links.add(it.url) }
                        val snippets = newsResults.mapIndexed { index, it -> "Source ${index + 1}: ${it.title}\nLink: ${it.url}\nSummary: ${it.snippet}" }.joinToString("\n\n")
                        contextText += "LATEST LIVE NEWS, STATISTICS & INFORMATION:\n$snippets\n\nSYSTEM INSTRUCTION: You MUST format your response as follows:\n1. Open with a brief introductory paragraph summarizing the current news.\n2. Present a bulleted list where each bullet explains one specific piece of news, ending immediately with its inline citation (e.g., [Source 1]).\n3. End with a short concluding paragraph. Do not use run-on sentences."
                    } else {
                        // Try Wikipedia fallback
                        val wikiUrl = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=${URLEncoder.encode(query, "UTF-8")}&utf8=&format=json"
                        val wikiData = client.get(wikiUrl).bodyAsText()
                        val wikiJson = org.json.JSONObject(wikiData)
                        val searchArray = wikiJson.optJSONObject("query")?.optJSONArray("search")
                        if (searchArray != null && searchArray.length() > 0) {
                            val firstTitle = searchArray.getJSONObject(0).optString("title")
                            val firstSnippet = searchArray.getJSONObject(0).optString("snippet").replace(Regex("<[^>]*>"), "")
                            contextText += "WIKIPEDIA SEARCH RESULT:\nTitle: $firstTitle\nSnippet: $firstSnippet\n"
                            links.add("https://en.wikipedia.org/wiki/${URLEncoder.encode(firstTitle.replace(" ", "_"), "UTF-8")}")
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        Pair(contextText, links)
    }

    suspend fun googleNewsSearch(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://news.google.com/rss/search?q=$encodedQuery&hl=en-US&gl=US&ceid=US:en"
            
            val xml = client.get(url).bodyAsText()
            val results = mutableListOf<SearchResult>()
            
            val itemRegex = Regex("""<item>(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
            val items = itemRegex.findAll(xml)
            
            for (item in items) {
                val blockText = item.groupValues[1]
                val title = Regex("""<title>(.*?)</title>""").find(blockText)?.groupValues?.get(1)?.replace(Regex("<[^>]*>"), "")?.trim() ?: ""
                val googleLink = Regex("""<link>(.*?)</link>""").find(blockText)?.groupValues?.get(1)?.trim() ?: ""
                val desc = Regex("""<description>(.*?)</description>""", RegexOption.DOT_MATCHES_ALL).find(blockText)?.groupValues?.get(1)?.replace(Regex("<[^>]*>"), "")?.replace("&nbsp;", " ")?.trim() ?: ""
                val sourceLink = Regex("""<source.*?url="([^"]+)"""").find(blockText)?.groupValues?.get(1)?.trim()
                
                val finalLink = if (!sourceLink.isNullOrBlank() && googleLink.isNotBlank()) {
                    val domain = try { java.net.URI(sourceLink).host.removePrefix("www.") } catch (e: Exception) { "news.google.com" }
                    "$googleLink#silica_domain=$domain"
                } else if (googleLink.isNotBlank()) {
                    googleLink
                } else {
                    sourceLink ?: ""
                }
                if (title.isNotBlank()) {
                    results.add(SearchResult(title, desc, finalLink))
                }
                if (results.size >= 4) break
            }
            results
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://html.duckduckgo.com/html/"
            
            val html = client.post(url) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("q=$encodedQuery")
            }.bodyAsText()

            val results = mutableListOf<SearchResult>()
            
            val blockRegex = Regex("""<div class="[^"]*result[^"]*"(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
            val titleRegex = Regex("""<h2 class="result__title">.*?<a[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
            val snippetRegex = Regex("""<a class="result__snippet[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
            val urlRegex = Regex("""<a class="result__url" href="([^"]+)"""")

            val blocks = blockRegex.findAll(html)
            for (block in blocks) {
                val blockText = block.value
                val titleMatch = titleRegex.find(blockText)
                val snippetMatch = snippetRegex.find(blockText)
                val urlMatch = urlRegex.find(blockText)

                if (titleMatch != null && snippetMatch != null && urlMatch != null) {
                    val rawTitle = titleMatch.groupValues[1].replace(Regex("<[^>]*>"), "").trim()
                    val rawSnippet = snippetMatch.groupValues[1].replace(Regex("<[^>]*>"), "").trim()
                    var rawUrl = urlMatch.groupValues[1]
                    if (rawUrl.startsWith("//duckduckgo.com/l/?uddg=")) {
                        rawUrl = rawUrl.substringAfter("uddg=").substringBefore("&amp;")
                        rawUrl = java.net.URLDecoder.decode(rawUrl, "UTF-8")
                    } else if (!rawUrl.startsWith("http")) {
                        rawUrl = "https://$rawUrl"
                    }
                    
                    if (rawTitle.isNotBlank() && rawSnippet.isNotBlank()) {
                        results.add(SearchResult(rawTitle, rawSnippet, rawUrl))
                    }
                    if (results.size >= 4) break
                }
            }
            results
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
