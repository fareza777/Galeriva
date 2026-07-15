package com.galeriva.app.search

/**
 * Maps Indonesian search keywords to English ML Kit label terms.
 *
 * Precision first: every mapping contains only labels that are strong,
 * specific evidence for the keyword. Generic labels (paper, computer,
 * screen, text, ...) are deliberately NOT used as expansions because they
 * match receipts, screenshots, and documents and pollute the results —
 * they only appear under keywords that genuinely mean those things
 * (e.g. "dokumen" → paper/receipt).
 */
object SearchKeywords {

    private val keywordMap: Map<String, List<String>> = mapOf(
        // Kegiatan
        "rapat" to listOf("meeting", "whiteboard", "presentation", "conference", "seminar", "classroom", "lecture", "conference room"),
        "meeting" to listOf("meeting", "whiteboard", "presentation", "conference", "seminar", "conference room"),
        "presentasi" to listOf("presentation", "whiteboard", "projector", "seminar", "lecture"),
        "kerja" to listOf("office", "desk", "laptop", "meeting", "whiteboard"),
        "kantor" to listOf("office", "desk", "meeting", "conference room"),
        "pesta" to listOf("party", "balloon", "cake", "event", "banquet", "fireworks"),
        "pernikahan" to listOf("wedding", "bride", "bridegroom", "ceremony", "veil"),
        "wisuda" to listOf("graduation", "ceremony", "academic dress"),
        "olahraga" to listOf("sports", "ball", "running", "gym", "bicycle", "football", "basketball", "swimming", "badminton", "stadium"),
        "liburan" to listOf("beach", "sea", "mountain", "vacation", "travel", "resort", "tourism"),
        "konser" to listOf("concert", "stage", "crowd", "music", "musician"),

        // Orang
        "orang" to listOf("person", "people", "smile", "selfie", "face", "crowd"),
        "selfie" to listOf("selfie", "smile", "face"),
        "keluarga" to listOf("people", "smile", "child", "family"),
        "anak" to listOf("child", "toddler", "baby"),
        "bayi" to listOf("baby", "toddler"),

        // Makanan & minuman
        "makanan" to listOf("food", "dish", "cuisine", "dessert", "snack", "fruit", "cooking", "breakfast", "lunch", "dinner", "salad", "soup"),
        "makan" to listOf("food", "dish", "cuisine", "restaurant", "eating"),
        "minuman" to listOf("drink", "coffee", "juice", "cocktail", "tea", "wine"),
        "kopi" to listOf("coffee", "espresso", "cappuccino"),
        "kue" to listOf("cake", "dessert", "pastry", "cookie", "bread"),
        "buah" to listOf("fruit", "apple", "banana", "orange", "strawberry", "watermelon"),

        // Alam & tempat
        "pantai" to listOf("beach", "sea", "ocean", "sand", "coast", "surfing"),
        "gunung" to listOf("mountain", "hill", "volcano", "hiking", "cliff"),
        "laut" to listOf("sea", "ocean", "beach", "underwater", "boat"),
        "pemandangan" to listOf("landscape", "mountain", "nature", "sunset", "valley", "waterfall", "lake"),
        "langit" to listOf("sky", "cloud", "sunset", "sunrise", "rainbow", "moon", "star"),
        "matahari" to listOf("sun", "sunset", "sunrise"),
        "bunga" to listOf("flower", "petal", "rose", "garden", "tulip"),
        "pohon" to listOf("tree", "forest", "jungle", "leaf"),
        "hujan" to listOf("rain", "umbrella"),
        "salju" to listOf("snow", "ice", "winter"),
        "kota" to listOf("city", "skyscraper", "skyline", "downtown", "urban"),
        "gedung" to listOf("building", "architecture", "skyscraper", "tower"),
        "jalan" to listOf("road", "street", "highway", "traffic", "bridge"),
        "masjid" to listOf("mosque", "dome", "temple", "cathedral"),
        "sawah" to listOf("rice field", "farm", "field", "agriculture"),

        // Benda
        "mobil" to listOf("car", "sports car", "truck", "van"),
        "motor" to listOf("motorcycle", "scooter"),
        "sepeda" to listOf("bicycle", "cycling"),
        "pesawat" to listOf("airplane", "aircraft", "airport"),
        "hewan" to listOf("animal", "dog", "cat", "bird", "pet", "fish", "horse", "butterfly"),
        "kucing" to listOf("cat", "kitten"),
        "anjing" to listOf("dog", "puppy"),
        "burung" to listOf("bird"),
        "ikan" to listOf("fish", "aquarium", "underwater"),
        "dokumen" to listOf("paper", "document", "text", "receipt", "menu", "handwriting"),
        "struk" to listOf("receipt", "paper", "document"),
        "tulisan" to listOf("text", "handwriting", "document", "paper"),
        "buku" to listOf("book", "bookcase", "reading"),
        "uang" to listOf("money", "cash", "currency", "coin"),
        "baju" to listOf("clothing", "dress", "shirt", "fashion", "jeans", "jacket"),
        "sepatu" to listOf("shoe", "footwear", "sneakers", "sandal"),
        "komputer" to listOf("computer", "laptop", "keyboard", "monitor"),
        "laptop" to listOf("laptop", "computer"),
        "hp" to listOf("mobile phone", "smartphone", "gadget"),
        "musik" to listOf("guitar", "piano", "concert", "musical instrument", "violin", "drum"),
        "layar" to listOf("screen", "monitor", "television"),

        // Waktu
        "malam" to listOf("night", "moon", "fireworks", "midnight"),
        "senja" to listOf("sunset", "dusk", "twilight")
    )

    /**
     * Expands a raw user query into a set of lowercase label terms.
     * Words in the dictionary expand to their precise labels; unrecognized
     * words are kept as-is so direct English label queries still work.
     */
    fun expand(query: String): Set<String> {
        val terms = mutableSetOf<String>()
        query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { word ->
            val mapped = keywordMap[word]
            if (mapped != null) terms += mapped else terms += word
        }
        return terms
    }
}
