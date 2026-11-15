package com.example.data.download

import com.example.data.model.LinkInspectionResult
import com.example.data.model.MediaCandidate
import com.example.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.net.URLDecoder
import java.util.Locale

class LinkResolver(private val client: OkHttpClient) {

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

        private val URL_REGEX = Regex("""https?://[^\s<>"'\)\]]+""", RegexOption.IGNORE_CASE)

        private val META_TAG_REGEX = Regex(
            """<meta\s+([^>]*?)/?>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val ATTR_REGEX = Regex(
            """([a-zA-Z0-9_:-]+)\s*=\s*["']([^"']*)["']""",
            RegexOption.IGNORE_CASE
        )
        private val TITLE_REGEX = Regex(
            """<title[^>]*>(.*?)</title>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val MEDIA_SRC_REGEX = Regex(
            """<(?:video|source|audio|img)[^>]+src\s*=\s*["']([^"']+)["'][^>]*>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val DIRECT_LINK_REGEX = Regex(
            """href\s*=\s*["']([^"']+\.(?:mp4|webm|mov|mkv|mp3|m4a|wav|pdf|jpg|jpeg|png|webp|gif)(?:\?[^"']*)?)["']""",
            RegexOption.IGNORE_CASE
        )
    }

    fun extractFirstUrl(rawText: String): String? {
        val trimmed = rawText.trim()
        val match = URL_REGEX.find(trimmed) ?: return null
        return match.value.trimEnd('.', ',', ';', '!', '?')
    }

    fun extractDomain(url: String): String {
        return runCatching {
            val host = URI(url).host ?: return "web"
            host.removePrefix("www.")
        }.getOrDefault("web")
    }

    suspend fun inspectLink(rawUrl: String): Result<LinkInspectionResult> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = extractFirstUrl(rawUrl)
                ?: throw IllegalArgumentException("Please enter a valid http or https link.")
            val domain = extractDomain(cleanUrl)

            val request = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Server returned HTTP ${response.code}")
                }

                val finalUrl = response.request.url.toString()
                val contentType = response.header("Content-Type")
                    ?.substringBefore(';')
                    ?.trim()
                    ?.lowercase(Locale.ROOT)
                val contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
                val dispositionName = parseContentDispositionFilename(response.header("Content-Disposition"))

                val isHtml = contentType == "text/html" || contentType == "application/xhtml+xml"

                if (!isHtml) {
                    val mediaType = MediaType.fromMimeAndUrl(contentType, finalUrl)
                    val derivedTitle = dispositionName ?: deriveTitleFromUrl(finalUrl, domain)
                    val candidate = MediaCandidate(
                        url = finalUrl,
                        title = derivedTitle,
                        mediaType = mediaType,
                        mimeType = contentType,
                        estimatedBytes = contentLength
                    )
                    return@runCatching LinkInspectionResult(
                        originalUrl = cleanUrl,
                        pageTitle = derivedTitle,
                        domain = domain,
                        candidates = listOf(candidate)
                    )
                }

                // Read up to 512 KB of HTML safely without downloading huge pages
                val body = response.body
                val htmlText = body?.source()?.let { source ->
                    source.request(512 * 1024L)
                    val bytesToRead = minOf(source.buffer.size, 512 * 1024L)
                    source.buffer.clone().readString(bytesToRead, Charsets.UTF_8)
                }.orEmpty()

                val parsed = parseHtmlCandidates(finalUrl, domain, htmlText)
                if (parsed.candidates.isEmpty()) {
                    // Fallback: allow saving the page HTML itself or report none
                    throw IllegalStateException("No downloadable video, image, or audio found on this page.")
                }
                parsed
            }
        }
    }

    private fun parseHtmlCandidates(
        baseUrl: String,
        domain: String,
        html: String
    ): LinkInspectionResult {
        var ogTitle: String? = null
        val rawTitle = TITLE_REGEX.find(html)?.groupValues?.getOrNull(1)?.let(::decodeHtmlEntities)?.trim()

        val foundUrls = LinkedHashSet<String>()
        val candidates = mutableListOf<MediaCandidate>()

        fun addCandidate(rawCandidateUrl: String?, explicitType: MediaType? = null, labelSuffix: String = "") {
            val decoded = rawCandidateUrl?.let(::decodeHtmlEntities)?.trim() ?: return
            if (decoded.isBlank() || decoded.startsWith("data:") || decoded.startsWith("javascript:")) return
            val resolved = resolveUrl(baseUrl, decoded) ?: return
            if (!foundUrls.add(resolved)) return

            val inferredType = explicitType ?: MediaType.fromMimeAndUrl(null, resolved)
            if (inferredType == MediaType.OTHER && explicitType == null) return

            val baseTitle = ogTitle ?: rawTitle ?: deriveTitleFromUrl(resolved, domain)
            val title = if (labelSuffix.isNotEmpty() && candidates.isNotEmpty()) {
                "$baseTitle ($labelSuffix)"
            } else {
                baseTitle
            }
            candidates.add(
                MediaCandidate(
                    url = resolved,
                    title = title.take(100),
                    mediaType = inferredType,
                    mimeType = null
                )
            )
        }

        // First pass: Meta tags (OpenGraph & Twitter)
        val metaMatches = META_TAG_REGEX.findAll(html).toList()
        for (match in metaMatches) {
            val attrs = parseAttributes(match.groupValues[1])
            val key = (attrs["property"] ?: attrs["name"] ?: "").lowercase(Locale.ROOT)
            val content = attrs["content"] ?: continue
            if (key == "og:title" || key == "twitter:title") {
                if (ogTitle.isNullOrBlank()) ogTitle = decodeHtmlEntities(content).trim()
            }
        }

        // Prioritize video meta tags first
        for (match in metaMatches) {
            val attrs = parseAttributes(match.groupValues[1])
            val key = (attrs["property"] ?: attrs["name"] ?: "").lowercase(Locale.ROOT)
            val content = attrs["content"] ?: continue
            when (key) {
                "og:video:secure_url", "og:video:url", "og:video", "twitter:player:stream" -> {
                    addCandidate(content, MediaType.VIDEO, "Video")
                }
                "og:audio:secure_url", "og:audio:url", "og:audio" -> {
                    addCandidate(content, MediaType.AUDIO, "Audio")
                }
            }
        }

        // HTML5 <video>, <source>, <audio> tags
        for (match in MEDIA_SRC_REGEX.findAll(html)) {
            val tag = match.value.lowercase(Locale.ROOT)
            val src = match.groupValues[1]
            when {
                tag.startsWith("<video") || tag.startsWith("<source") -> {
                    val guessed = MediaType.fromMimeAndUrl(null, src)
                    if (guessed == MediaType.VIDEO || guessed == MediaType.AUDIO || tag.startsWith("<video")) {
                        addCandidate(src, if (guessed == MediaType.AUDIO) MediaType.AUDIO else MediaType.VIDEO)
                    }
                }
                tag.startsWith("<audio") -> addCandidate(src, MediaType.AUDIO)
            }
        }

        // Direct downloadable href links (.mp4, .pdf, .mp3, etc.)
        for (match in DIRECT_LINK_REGEX.findAll(html)) {
            addCandidate(match.groupValues[1])
        }

        // OpenGraph / Twitter images
        for (match in metaMatches) {
            val attrs = parseAttributes(match.groupValues[1])
            val key = (attrs["property"] ?: attrs["name"] ?: "").lowercase(Locale.ROOT)
            val content = attrs["content"] ?: continue
            if (key == "og:image:secure_url" || key == "og:image:url" || key == "og:image" || key == "twitter:image") {
                addCandidate(content, MediaType.IMAGE, "Cover Image")
            }
        }

        // Fallback HTML <img> tags (skip tiny icons/trackers)
        var imgCount = 0
        for (match in MEDIA_SRC_REGEX.findAll(html)) {
            if (imgCount >= 8) break
            val tag = match.value.lowercase(Locale.ROOT)
            if (tag.startsWith("<img")) {
                val src = match.groupValues[1]
                val lower = src.lowercase(Locale.ROOT)
                if (!lower.contains("favicon") && !lower.contains("pixel") && !lower.contains("spacer") && !lower.endsWith(".svg")) {
                    addCandidate(src, MediaType.IMAGE, "Image ${imgCount + 1}")
                    imgCount++
                }
            }
        }

        val pageTitle = ogTitle ?: rawTitle ?: domain
        return LinkInspectionResult(
            originalUrl = baseUrl,
            pageTitle = pageTitle.take(100),
            domain = domain,
            candidates = candidates
        )
    }

    private fun parseAttributes(raw: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (m in ATTR_REGEX.findAll(raw)) {
            map[m.groupValues[1].lowercase(Locale.ROOT)] = m.groupValues[2]
        }
        return map
    }

    private fun resolveUrl(base: String, target: String): String? {
        return runCatching {
            val resolved = URI(base).resolve(target).toString()
            if (resolved.startsWith("http://") || resolved.startsWith("https://")) resolved else null
        }.getOrNull()
    }

    private fun parseContentDispositionFilename(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val match = Regex("""filename\*?=(?:UTF-8''|")?([^";\r\n]+)"?""", RegexOption.IGNORE_CASE)
            .find(header) ?: return null
        return runCatching { URLDecoder.decode(match.groupValues[1], "UTF-8") }.getOrNull()
    }

    fun deriveTitleFromUrl(url: String, fallbackDomain: String): String {
        return runCatching {
            val path = URI(url).path.orEmpty().trim('/')
            val lastSegment = path.substringAfterLast('/', "").substringBefore('?')
            if (lastSegment.isNotBlank()) {
                URLDecoder.decode(lastSegment, "UTF-8")
                    .substringBeforeLast('.')
                    .replace('_', ' ')
                    .replace('-', ' ')
                    .ifBlank { fallbackDomain }
            } else {
                fallbackDomain
            }
        }.getOrDefault(fallbackDomain)
    }

    private fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#x2F;", "/")
    }
}
