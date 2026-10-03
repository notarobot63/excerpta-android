package xyz.notarobot.excerpta

/**
 * Liens de la file hors-ligne, présentés comme des liens de la liste : sans
 * cela, un lien partagé sans réseau n'apparaissait nulle part après le toast.
 */
object PendingDisplay {

    /**
     * Ids négatifs : jamais en collision avec un id serveur, pour que le diff
     * de la liste ne confonde pas une carte en attente avec un vrai lien.
     */
    fun idOf(index: Int): Int = -(index + 1)

    fun indexOf(id: Int): Int? = if (id < 0) -id - 1 else null

    /**
     * Applique les filtres de la liste, sinon un lien en attente s'afficherait
     * sous un tag ou un dossier qu'il ne porte pas. La recherche exige chaque
     * mot, sans casse, dans le titre, l'URL, l'extrait, la note ou les tags.
     */
    fun items(
        pending: List<PendingLink>,
        query: String = "",
        tag: String? = null,
        groupId: Int? = null,
    ): List<ApiClient.LinkItem> {
        val terms = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        return pending.mapIndexedNotNull { index, link ->
            if (tag != null && link.tags.none { it.equals(tag, ignoreCase = true) }) return@mapIndexedNotNull null
            if (groupId != null && link.folderId != groupId) return@mapIndexedNotNull null
            val haystack = listOf(link.title, link.url, link.description, link.note, link.tags.joinToString(" "))
                .joinToString(" ")
                .lowercase()
            if (terms.any { it !in haystack }) return@mapIndexedNotNull null
            ApiClient.LinkItem(
                id = idOf(index),
                url = link.url,
                title = link.title,
                description = link.description,
                faviconUrl = "",
                thumbnailUrl = "",
                tags = link.tags,
                createdAt = "",
                isPublic = link.isPublic,
                note = link.note,
                isPending = true,
            )
        }
    }
}
