package com.daveendev.justdoodles

/**
 * A sheet of blank paper, white or black. There can be many: each keeps its own drawing in
 * the [PageStore] under its [id], so starting a fresh sheet never wipes an old picture.
 */
class Paper(val id: String, val dark: Boolean) {

    val color get() = if (dark) Palette.DARK_PAPER else Palette.PAPER

    companion object {
        /**
         * At most this many sheets with drawings are kept. Then there's no fresh paper in the
         * picker until a grown-up deletes some in the settings; old drawings never vanish by themselves.
         */
        const val MAX_DRAWN = 30

        /** The white sheet from before there could be more than one; its drawing is kept. */
        private const val FIRST = ColoringPage.BLANK

        fun newId() = "paper-" + System.currentTimeMillis()

        /** Writes [papers] as one string for the settings file: "id:0,id:1". */
        fun encode(papers: List<Paper>) = papers.joinToString(",") { "${it.id}:${if (it.dark) 1 else 0}" }

        /** Reads what [encode] wrote. Nothing saved yet means the one original white sheet. */
        fun decode(saved: String?): MutableList<Paper> {
            if (saved == null) return mutableListOf(Paper(FIRST, dark = false))
            return saved.split(",").mapNotNullTo(ArrayList()) { entry ->
                val parts = entry.split(":")
                if (parts.size == 2 && parts[0].isNotEmpty()) Paper(parts[0], parts[1] == "1") else null
            }
        }
    }
}
