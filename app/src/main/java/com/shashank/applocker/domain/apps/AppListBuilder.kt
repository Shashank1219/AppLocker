package com.shashank.applocker.domain.apps

/** One row in the picker. */
data class AppItem(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val selected: Boolean,
    val role: CriticalRole?,
    /** False for a selected app that is disabled, hidden or gone. It can only be removed. */
    val available: Boolean,
)

/** Pure list logic, kept out of the ViewModel so it is easy to test. */
object AppListBuilder {
    fun build(
        snapshot: InstalledAppsSnapshot,
        selected: Set<String>,
        ownPackage: String,
    ): List<AppItem> {
        val visible = snapshot.apps.filter { it.packageName != ownPackage }
        val known = visible.mapTo(HashSet()) { it.packageName }
        val live = visible.map {
            AppItem(it.packageName, it.label, it.isSystem, it.packageName in selected, snapshot.criticalRoles[it.packageName], true)
        }
        val missing = selected.filter { it !in known && it != ownPackage }.map {
            AppItem(it, it, isSystem = false, selected = true, role = null, available = false)
        }
        return live + missing
    }

    /**
     * Filters by the query (label or package name, case-insensitive) and sorts alphabetically.
     * System apps are hidden unless [showSystem], except ones already selected so a protected
     * app never vanishes from the list.
     */
    fun filterAndSort(items: List<AppItem>, query: String, showSystem: Boolean): List<AppItem> {
        val q = query.trim()
        return items
            .filter { showSystem || !it.isSystem || it.selected }
            .filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
            .sortedWith(compareBy<AppItem, String>(String.CASE_INSENSITIVE_ORDER) { it.label }.thenBy { it.packageName })
    }
}
