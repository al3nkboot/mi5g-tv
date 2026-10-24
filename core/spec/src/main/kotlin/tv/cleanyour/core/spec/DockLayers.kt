package tv.cleanyour.core.spec

import tv.cleanyour.core.model.PackageName

// The dock and the rows under it, as the launcher builds them (AppAdapter.reload and MainActivity
// at upstream main): every app is sorted by its place in dock_apps first, the grid has `columns`
// tiles per row and its first row is the dock, and rows_below_dock grid rows stay in view under
// it. So the phone keeps one ordered list and cuts it into rows of `columns`: the first row is the
// dock, the next are Row 2 and Row 3. A row only ever holds apps once the rows above it are full,
// because a gap would make the launcher slide the next apps up into it; the cut list cannot have
// one. The phone offers at most MAX_ROWS rows (the dock and two under it).
//
// On the TV, an app in dock_apps that is not there (uninstalled, switched off, no TV launcher
// activity) is skipped and every later app moves up a slot. `decode` applies the same rule, so
// the sheet shows what the TV shows.
object DockLayers {
    const val MAX_ROWS = 3
    const val DEFAULT_COLUMNS = 4

    // Tiles per row for a theme's doc, as the launcher reads it (1 to 8, else the default 4).
    fun columnsOf(doc: LauncherConfigDoc?): Int = doc?.columns?.takeIf { it in 1..8 } ?: DEFAULT_COLUMNS

    fun capacity(columns: Int): Int = columns.coerceAtLeast(1) * MAX_ROWS

    // The list the phone keeps: no repeats, at most MAX_ROWS rows.
    fun trim(apps: List<PackageName>, columns: Int): List<PackageName> = apps.distinct().take(capacity(columns))

    // The list cut into rows: [dock, row 2, row 3], only the rows that hold an app.
    fun <T> rows(apps: List<T>, columns: Int): List<List<T>> = apps.take(capacity(columns)).chunked(columns.coerceAtLeast(1))

    // How many rows under the dock hold an app: the rows_below_dock the phone writes.
    fun rowsBelowDock(count: Int, columns: Int): Int {
        val c = columns.coerceAtLeast(1)
        val n = count.coerceIn(0, capacity(c))
        return if (n == 0) 0 else (n + c - 1) / c - 1
    }

    // The doc the phone pushes: the theme's doc with dock_apps the trimmed list and rows_below_dock
    // the rows under the dock that hold an app. An empty list leaves dock_apps out, so the launcher
    // keeps its own dock, and leaves the theme's rows_below_dock as it is.
    //
    // Owner decision D1 (launcher v2.4.0): with three rows (the dock and two under it) dock_title
    // is written false. Launcher 2.4 fits the rows to the screen, and the focused app's name above
    // the dock would push most of Row 3 below the edge of a 1080p screen; without it all three
    // rows fit. One or two rows leave the theme's dock_title as it is. Launcher 2.0 ignores the key.
    fun applyTo(doc: LauncherConfigDoc, apps: List<PackageName>): LauncherConfigDoc {
        val columns = columnsOf(doc)
        val dock = trim(apps, columns)
        if (dock.isEmpty()) return doc.copy(dockApps = null)
        val below = rowsBelowDock(dock.size, columns)
        val title = if (below == MAX_ROWS - 1) false else doc.dockTitle
        return doc.copy(dockApps = dock, rowsBelowDock = below, dockTitle = title)
    }

    // What the TV shows for a dock_apps list: the apps it can show, in order, cut to MAX_ROWS rows.
    fun decode(dockApps: List<PackageName>, shown: (PackageName) -> Boolean, columns: Int): List<PackageName> =
        trim(dockApps.filter(shown), columns)
}
