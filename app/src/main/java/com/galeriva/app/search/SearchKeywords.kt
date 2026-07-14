package com.galeriva.app.search

/**
 * Maps Indonesian search keywords to English ML Kit label terms.
 * A single Indonesian word can expand to several related labels so that
 * "rapat" also matches whiteboard/presentation/crowd photos.
 */
object SearchKeywords {

    private val keywordMap: Map<String, List<String>> = mapOf(
        // Kegiatan
        "rapat" to listOf("meeting", "whiteboard", "presentation", "crowd", "suit", "computer", "paper", "team", "conference"),
        "meeting" to listOf("meeting", "whiteboard", "presentation", "crowd", "conference"),
        "kerja" to listOf("computer", "desk", "office", "paper", "laptop"),
        "presentasi" to listOf("presentation", "whiteboard", "screen", "projector"),
        "pesta" to listOf("party", "balloon", "cake", "crowd", "event"),
        "pernikahan" to listOf("wedding", "bride", "dress", "flower", "ceremony"),
        "wisuda" to listOf("graduation", "crowd", "ceremony"),
        "olahraga" to listOf("sports", "ball", "running", "gym", "bicycle", "football", "swimming"),
        "liburan" to listOf("beach", "sea", "mountain", "vacation", "landscape", "sky", "travel"),

        // Orang
        "orang" to listOf("person", "people", "crowd", "smile", "selfie"),
        "selfie" to listOf("selfie", "person", "smile", "face"),
        "keluarga" to listOf("people", "person", "crowd", "smile", "child"),
        "anak" to listOf("child", "baby", "toddler", "person"),
        "bayi" to listOf("baby", "toddler", "child"),

        // Makanan & minuman
        "makanan" to listOf("food", "dish", "cuisine", "dessert", "snack", "fruit", "cooking"),
        "makan" to listOf("food", "dish", "cuisine", "restaurant"),
        "minuman" to listOf("drink", "coffee", "juice", "cup", "bottle"),
        "kopi" to listOf("coffee", "cup", "drink"),
        "kue" to listOf("cake", "dessert", "pastry"),
        "buah" to listOf("fruit", "food"),

        // Alam & tempat
        "pantai" to listOf("beach", "sea", "ocean", "sand", "coast"),
        "gunung" to listOf("mountain", "hill", "landscape", "hiking"),
        "laut" to listOf("sea", "ocean", "beach", "water"),
        "pemandangan" to listOf("landscape", "mountain", "sky", "nature", "sunset"),
        "langit" to listOf("sky", "cloud", "sunset", "sunrise"),
        "matahari" to listOf("sun", "sunset", "sunrise", "sky"),
        "bunga" to listOf("flower", "plant", "petal", "garden"),
        "pohon" to listOf("tree", "plant", "forest", "nature"),
        "hujan" to listOf("rain", "umbrella", "cloud"),
        "kota" to listOf("city", "building", "skyscraper", "street", "urban"),
        "gedung" to listOf("building", "architecture", "skyscraper", "tower"),
        "jalan" to listOf("road", "street", "highway", "traffic"),
        "masjid" to listOf("mosque", "dome", "architecture", "temple"),

        // Benda
        "mobil" to listOf("car", "vehicle", "wheel", "traffic"),
        "motor" to listOf("motorcycle", "vehicle", "wheel", "bicycle"),
        "hewan" to listOf("animal", "dog", "cat", "bird", "pet"),
        "kucing" to listOf("cat", "animal", "pet", "kitten"),
        "anjing" to listOf("dog", "animal", "pet", "puppy"),
        "burung" to listOf("bird", "animal"),
        "dokumen" to listOf("paper", "document", "text", "receipt", "menu"),
        "struk" to listOf("receipt", "paper", "text", "document"),
        "tulisan" to listOf("text", "paper", "document", "handwriting"),
        "buku" to listOf("book", "paper", "text"),
        "uang" to listOf("money", "cash", "paper", "currency"),
        "baju" to listOf("clothing", "dress", "shirt", "fashion", "jeans"),
        "sepatu" to listOf("shoe", "footwear", "sneakers"),
        "komputer" to listOf("computer", "laptop", "screen", "keyboard"),
        "hp" to listOf("phone", "mobile phone", "gadget", "screen"),
        "musik" to listOf("music", "guitar", "piano", "concert", "musical instrument"),

        // Waktu
        "malam" to listOf("night", "dark", "moon", "fireworks", "lights"),
        "senja" to listOf("sunset", "dusk", "sky", "twilight")
    )

    /**
     * Expands a raw user query into a set of lowercase label terms to match.
     * Unrecognized words are kept as-is so English queries still work.
     */
    fun expand(query: String): Set<String> {
        val terms = mutableSetOf<String>()
        query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { word ->
            terms += word
            keywordMap[word]?.let { terms += it }
        }
        return terms
    }
}
