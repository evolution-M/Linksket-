package com.example.data.model

enum class MediaType(val label: String) {
    VIDEO("Video"),
    IMAGE("Image"),
    AUDIO("Audio"),
    DOCUMENT("Document"),
    OTHER("File");

    companion object {
        fun fromMimeAndUrl(mimeType: String?, urlOrName: String): MediaType {
            val mime = mimeType?.lowercase().orEmpty()
            val lower = urlOrName.lowercase().substringBefore('?').substringBefore('#')
            return when {
                mime.startsWith("video/") -> VIDEO
                mime.startsWith("image/") -> IMAGE
                mime.startsWith("audio/") -> AUDIO
                mime == "application/pdf" ||
                    mime.startsWith("text/") ||
                    mime.contains("document") ||
                    mime.contains("msword") ||
                    mime.contains("spreadsheet") -> DOCUMENT
                lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".webm") ||
                    lower.endsWith(".mkv") || lower.endsWith(".m4v") || lower.endsWith(".3gp") -> VIDEO
                lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
                    lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".bmp") ||
                    lower.endsWith(".heic") -> IMAGE
                lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".aac") ||
                    lower.endsWith(".wav") || lower.endsWith(".ogg") || lower.endsWith(".flac") -> AUDIO
                lower.endsWith(".pdf") || lower.endsWith(".txt") || lower.endsWith(".md") ||
                    lower.endsWith(".epub") || lower.endsWith(".csv") || lower.endsWith(".doc") ||
                    lower.endsWith(".docx") -> DOCUMENT
                else -> OTHER
            }
        }
    }
}

enum class DownloadStatus {
    QUEUED,
    RESOLVING,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

data class MediaCandidate(
    val url: String,
    val title: String,
    val mediaType: MediaType,
    val mimeType: String?,
    val estimatedBytes: Long = -1L
)

data class LinkInspectionResult(
    val originalUrl: String,
    val pageTitle: String,
    val domain: String,
    val candidates: List<MediaCandidate>
)
