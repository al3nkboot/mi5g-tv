package tv.cleanyour.ui.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import kotlinx.coroutines.Job
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tv.cleanyour.ui.design.R
import tv.cleanyour.ui.design.icon.CytIcons
import tv.cleanyour.ui.design.theme.CytTheme
import tv.cleanyour.ui.design.theme.glass

// An app the dock editor can place: a stable key (the package name) and the name to show.
@Immutable
data class DockApp(val key: String, val name: String)

// The rows as they were before "Remove row", for its Undo.
@Immutable
class DockSnapshot internal constructor(internal val apps: List<DockApp>, internal val extraRow: Boolean)

// The dock sheet (S3b) as one ordered list cut into rows of `columns`: the first row is the dock,
// the next are Row 2 and Row 3 under it on the TV's home screen. The launcher sorts its grid by
// the same list, so a row can only hold apps once the rows above it are full; the list cannot
// have a gap, and taking an app out pulls the next one up, as the TV would. A row under the dock
// opens only when the person asks for it ("Add a row under the dock") and the rows above are full,
// and goes again with "Remove row" (the dock itself is never removed).
@Stable
class DockEditorState(initial: List<DockApp>, columns: Int, val maxRows: Int = MAX_ROWS) {
    val columns: Int = columns.coerceAtLeast(1)
    val capacity: Int get() = columns * maxRows

    var apps: List<DockApp> by mutableStateOf(initial.distinctBy { it.key }.take(this.columns * maxRows))
        private set

    // "Add a row under the dock" was tapped and the new row is still empty.
    private var extraRow by mutableStateOf(false)

    // The rows on screen: the ones that hold an app, and the empty one just added.
    val rowCount: Int get() = if (apps.isEmpty()) 0 else (apps.size + columns - 1) / columns + if (extraRow) 1 else 0

    // The rows that hold an app (what rows_below_dock counts, plus the dock).
    val filledRows: Int get() = (apps.size + columns - 1) / columns

    val hasRoom: Boolean get() = apps.size < maxOf(rowCount, 1) * columns

    val canAddRow: Boolean get() = apps.isNotEmpty() && !extraRow && apps.size % columns == 0 && apps.size < capacity

    fun rowOf(index: Int): Int = index / columns

    fun rowApps(row: Int): List<DockApp> = apps.drop(row * columns).take(columns)

    fun add(app: DockApp) {
        if (!hasRoom || apps.any { it.key == app.key }) return
        apps = apps + app
        extraRow = false
    }

    fun remove(key: String) {
        apps = apps.filterNot { it.key == key }
        extraRow = false
    }

    fun move(from: Int, to: Int) {
        if (from !in apps.indices) return
        val target = to.coerceIn(apps.indices)
        if (target == from) return
        apps = apps.toMutableList().apply { add(target, removeAt(from)) }
    }

    // To the end of a row above, or the start of a row below (TalkBack's "Move to row 2").
    fun moveToRow(index: Int, row: Int) {
        val current = rowOf(index)
        if (row == current || row !in 0 until filledRows) return
        move(index, if (row < current) (row + 1) * columns - 1 else row * columns)
    }

    fun addRow() {
        if (canAddRow) extraRow = true
    }

    // Takes a row under the dock away (never the dock): its apps go back to the other apps and any
    // row under it moves up, so the rows stay without a gap, as on the TV. Returns what to put
    // back for Undo, or null when there is no such row.
    fun removeRow(row: Int): DockSnapshot? {
        if (row <= 0 || row >= rowCount) return null
        val before = DockSnapshot(apps, extraRow)
        apps = apps.filterIndexed { i, _ -> rowOf(i) != row }
        extraRow = false
        return before
    }

    fun restore(snapshot: DockSnapshot) {
        apps = snapshot.apps
        extraRow = snapshot.extraRow
    }

    // The app being dragged and where its lifted copy is, in the editor's own coordinates.
    var dragging: String? by mutableStateOf(null)
        internal set
    internal var ghostTop by mutableFloatStateOf(Float.NaN)
    internal var ghostHeight by mutableIntStateOf(0)
    internal var settling by mutableStateOf(false)

    // Screenshot tests: shows the app at `from` lifted over place `to`, `nudgePx` past its slot.
    internal fun previewDrag(from: Int, to: Int, nudgePx: Float) {
        val key = apps.getOrNull(from)?.key ?: return
        move(from, to)
        dragging = key
        ghostTop = Float.NaN
        previewNudge = nudgePx
    }
    internal var previewNudge: Float = 0f

    companion object {
        const val MAX_ROWS = 3
    }
}

// Where each row and its grip sit, for the drag and for the spring that moves the others out of
// the way: layout positions in the editor's coordinates (outside each row's translation), the
// position each app had before the last change, and the offset each app springs back from.
@Stable
private class DockMotion {
    var editor: LayoutCoordinates? = null
    val coords = HashMap<String, LayoutCoordinates>()
    val grips = HashMap<String, LayoutCoordinates>()
    val lastTop = HashMap<String, Float>()
    val offsets = HashMap<String, Animatable<Float, AnimationVector1D>>()
    var awaitingLayout = false
    var autoScroll: Job? = null
    var fingerY = 0f   // the finger, in the editor's coordinates

    fun top(key: String): Float? {
        val e = editor?.takeIf { it.isAttached } ?: return null
        val c = coords[key]?.takeIf { it.isAttached } ?: return null
        return e.localPositionOf(c, Offset.Zero).y
    }

    fun height(key: String): Int = coords[key]?.takeIf { it.isAttached }?.size?.height ?: 0

    // The app whose grip is under the finger, if any.
    fun gripAt(at: Offset, keys: List<String>): String? {
        val e = editor?.takeIf { it.isAttached } ?: return null
        return keys.firstOrNull { k -> grips[k]?.takeIf { it.isAttached }?.let { e.localBoundingBoxOf(it, clipBounds = false).contains(at) } == true }
    }

    // The app whose row is under the finger, if any.
    fun rowAt(at: Offset, keys: List<String>): String? = keys.firstOrNull { k ->
        val t = top(k) ?: return@firstOrNull false
        at.y >= t && at.y < t + height(k)
    }

    // Before a change: where every app is now, so each one that moves can spring from there.
    fun capture(keys: List<String>) {
        for (k in keys) top(k)?.let { lastTop[k] = it }
    }

    fun placed(key: String, c: LayoutCoordinates, animate: Boolean, scope: CoroutineScope) {
        coords[key] = c
        awaitingLayout = false
        val now = top(key) ?: return
        val before = lastTop.put(key, now)
        if (before == null || before == now) return
        val a = offsets.getOrPut(key) { Animatable(0f) }
        scope.launch {
            if (!animate) { a.snapTo(0f); return@launch }
            a.snapTo(a.value + before - now)
            a.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
        }
    }

    fun offset(key: String): Float = offsets[key]?.value ?: 0f

    // A preview lift (screenshot tests) waits for the first layout to know where its slot is.
    fun seedGhost(state: DockEditorState) {
        val key = state.dragging ?: return
        if (state.ghostTop.isNaN()) top(key)?.let { state.ghostTop = it + state.previewNudge; state.ghostHeight = height(key) }
    }
}

// The dock, its rows and the other apps (S3b). Each app has a grip at the end of its row: touch
// it and move, and the app lifts and follows the finger, in its row or into another one, while
// the others make room; a swipe anywhere else scrolls the sheet as usual (a long press on the
// row also lifts it). Near the sheet's top or bottom edge the sheet scrolls on its own when
// `scroll` is the sheet's scroll state. TalkBack and switch access get "Move up", "Move down",
// "Move to the dock", "Move to row n" and "Take out" on every app instead of dragging. Under
// reduced motion nothing lifts or springs: the apps change places at once. Rows under the dock
// have "Remove row"; with `undo`, a snackbar offers to put the row back.
@Composable
fun DockEditor(
    state: DockEditorState,
    apps: List<DockApp>,
    modifier: Modifier = Modifier,
    haptics: Boolean = true,
    scroll: ScrollState? = null,
    undo: UndoSnackbarState? = null,
) {
    val reduced = CytTheme.reducedMotion
    val feel = rememberCytHaptics(haptics)
    val scope = rememberCoroutineScope()
    val motion = remember(state) { DockMotion() }
    val edge = with(LocalDensity.current) { EDGE.toPx() }
    val speed = with(LocalDensity.current) { SCROLL_PER_FRAME.toPx() }
    fun edit(change: () -> Unit) {
        motion.capture(state.apps.map { it.key })
        change()
    }
    val removedText = stringResource(R.string.cyt_dock_row_removed)
    Column(modifier.fillMaxWidth()) {
        // An empty dock has no "Dock" heading over nothing (round 3); the apps to add follow.
        if (state.apps.isNotEmpty()) {
            Box(
                Modifier.fillMaxWidth()
                    .onGloballyPositioned { motion.editor = it; motion.seedGhost(state) }
                    .pointerInput(state, motion, feel, reduced, scroll) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (state.settling || state.dragging != null) return@awaitEachGesture
                            val keys = state.apps.map { it.key }
                            val grip = motion.gripAt(down.position, keys)
                            // The grip lifts at once; the rest of a row only after a long press, so
                            // a swipe there still scrolls the sheet.
                            var key = grip
                            if (key == null) {
                                val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                                key = motion.rowAt(held.position, keys) ?: return@awaitEachGesture
                            }
                            if (grip != null) down.consume()
                            if (!startDrag(state, motion, key)) return@awaitEachGesture
                            feel.light()
                            motion.fingerY = down.position.y
                            if (scroll != null) motion.autoScroll = scope.launch { autoScroll(state, motion, scroll, edge, speed) }
                            val id = down.id
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == id } ?: break
                                if (!change.pressed) { change.consume(); break }
                                val dy = change.positionChange().y
                                change.consume()
                                motion.fingerY = change.position.y
                                dragBy(state, motion, dy)
                            }
                            motion.autoScroll?.cancel()
                            motion.autoScroll = null
                            endDrag(state, motion, reduced, scope) { feel.light() }
                        }
                    },
            ) {
                Column {
                    for (row in 0 until state.rowCount) {
                        RowHeader(row) {
                            edit {
                                val before = state.removeRow(row) ?: return@edit
                                undo?.show(String.format(removedText, row + 1)) { edit { state.restore(before) } }
                            }
                        }
                        ListGroup {
                            val inRow = state.rowApps(row)
                            if (inRow.isEmpty()) {
                                Text(
                                    stringResource(R.string.cyt_dock_row_empty), Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(16.dp),
                                    color = CytTheme.colors.textSecondary, style = CytTheme.type.bodyMedium,
                                )
                            }
                            inRow.forEachIndexed { j, app ->
                                key(app.key) {
                                    if (j > 0) ListDivider()
                                    DockItem(state, motion, row * state.columns + j, app, reduced, scope, ::edit)
                                }
                            }
                        }
                    }
                }
                DragGhost(state, reduced)
            }
            val note = when {
                state.hasRoom && state.apps.size % state.columns != 0 -> stringResource(R.string.cyt_dock_gaps)
                state.hasRoom -> null
                state.canAddRow && state.rowCount == 1 -> stringResource(R.string.cyt_dock_full, state.columns)
                state.canAddRow -> stringResource(R.string.cyt_dock_row_full)
                else -> stringResource(R.string.cyt_dock_all_full)
            }
            if (note != null) {
                Text(note, Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp), color = CytTheme.colors.textSecondary, style = CytTheme.type.bodyMedium)
            }
            if (state.canAddRow) {
                TertiaryButton(stringResource(R.string.cyt_dock_add_row), { edit { state.addRow() } }, Modifier.padding(top = 4.dp))
            }
        }
        val others = apps.filter { a -> state.apps.none { it.key == a.key } }
        if (others.isNotEmpty()) {
            Overline(stringResource(R.string.cyt_dock_others), Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp))
            ListGroup {
                val room = state.hasRoom
                val targetRow = state.rowOf(state.apps.size)
                others.forEachIndexed { i, app ->
                    if (i > 0) ListDivider()
                    val cd = when {
                        !room -> stringResource(R.string.cyt_dock_item_out_cd, app.name)
                        targetRow == 0 -> stringResource(R.string.cyt_dock_add_dock_cd, app.name)
                        else -> stringResource(R.string.cyt_dock_add_row_cd, app.name, targetRow + 1)
                    }
                    DockRow(
                        app.name, Modifier.semantics { contentDescription = cd },
                        controls = if (room) ({ CytIcon(CytIcons.Plus, null, tint = CytTheme.colors.accent) }) else null,
                        onClick = if (room) ({ edit { state.add(app) } }) else null,
                    )
                }
            }
        }
    }
}

private val EDGE = 56.dp
private val SCROLL_PER_FRAME = 6.dp

// "Dock", or "Row 2" with "Remove row" at its end (rows under the dock only).
@Composable
private fun RowHeader(row: Int, onRemove: () -> Unit) {
    val title = if (row == 0) stringResource(R.string.cyt_dock_row_dock) else stringResource(R.string.cyt_dock_row_n, row + 1)
    if (row == 0) {
        Overline(title, Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp))
        return
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Overline(title, Modifier.weight(1f).padding(start = 4.dp))
        val cd = stringResource(R.string.cyt_dock_remove_row_cd, row + 1)
        TertiaryButton(stringResource(R.string.cyt_dock_remove_row), onRemove, Modifier.semantics { contentDescription = cd }, quiet = true)
    }
}

// One app in a row: the tile and name, a small take out button and the grip, and the TalkBack
// actions that stand in for dragging.
@Composable
private fun DockItem(
    state: DockEditorState,
    motion: DockMotion,
    index: Int,
    app: DockApp,
    reduced: Boolean,
    scope: CoroutineScope,
    edit: (() -> Unit) -> Unit,
) {
    val row = state.rowOf(index)
    val place = index % state.columns + 1
    val last = state.apps.size - 1
    val cd = if (row == 0) stringResource(R.string.cyt_dock_item_dock_cd, app.name, place)
    else stringResource(R.string.cyt_dock_item_row_cd, app.name, row + 1, place)
    val up = stringResource(R.string.cyt_dock_action_up)
    val down = stringResource(R.string.cyt_dock_action_down)
    val out = stringResource(R.string.cyt_dock_action_remove)
    val toDock = stringResource(R.string.cyt_dock_action_to_dock)
    val toRows = (1 until state.filledRows).associateWith { stringResource(R.string.cyt_dock_action_to_row, it + 1) }
    val actions = buildList {
        if (index > 0) add(CustomAccessibilityAction(up) { edit { state.move(index, index - 1) }; true })
        if (index < last) add(CustomAccessibilityAction(down) { edit { state.move(index, index + 1) }; true })
        for (r in 0 until state.filledRows) {
            if (r == row) continue
            add(CustomAccessibilityAction(if (r == 0) toDock else toRows.getValue(r)) { edit { state.moveToRow(index, r) }; true })
        }
        add(CustomAccessibilityAction(out) { edit { state.remove(app.key) }; true })
    }
    DockRow(
        app.name,
        Modifier
            .onGloballyPositioned {
                motion.placed(app.key, it, animate = !reduced && state.dragging != app.key, scope)
                motion.seedGhost(state)
            }
            .graphicsLayer {
                translationY = motion.offset(app.key)
                alpha = if (state.dragging == app.key) 0f else 1f
            }
            .semantics { contentDescription = cd; customActions = actions },
        controls = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TakeOutButton(stringResource(R.string.cyt_dock_remove_cd, app.name)) { edit { state.remove(app.key) } }
                Grip(Modifier.onGloballyPositioned { motion.grips[app.key] = it })
            }
        },
    )
}

// Take out: a small minus in a quiet disc, with the full 48 dp touch target.
@Composable
private fun TakeOutButton(contentDescription: String, onClick: () -> Unit) {
    val c = CytTheme.colors
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(c.surface3), contentAlignment = Alignment.Center) {
            Icon(CytIcons.Minus, contentDescription, Modifier.size(16.dp), tint = c.textPrimary)
        }
    }
}

// The grip at the end of each row: six dots in a 48 dp target. Touch and move lifts the app. Not a
// TalkBack stop of its own (the row's actions move the app).
@Composable
private fun Grip(modifier: Modifier = Modifier) {
    Box(modifier.size(48.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        CytIcon(CytIcons.Grip, null, tint = CytTheme.colors.textSecondary, size = 24.dp)
    }
}

// The lifted copy of the app being dragged: the glass highlight on a solid surface, a soft shadow
// and a 3% lift. Hidden from TalkBack, which moves apps with its actions instead.
@Composable
private fun DragGhost(state: DockEditorState, reduced: Boolean) {
    val key = state.dragging ?: return
    val app = state.apps.firstOrNull { it.key == key } ?: return
    val c = CytTheme.colors
    val shape = CytTheme.shapes.medium
    val lift = remember(key) { Animatable(1f) }
    val lifted = !state.settling && !reduced
    LaunchedEffect(key, lifted) { lift.animateTo(if (lifted) 1.03f else 1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)) }
    val height = with(LocalDensity.current) { state.ghostHeight.toDp() }
    Box(
        Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, if (state.ghostTop.isNaN()) 0 else state.ghostTop.roundToInt()) }
            .zIndex(1f)
            .graphicsLayer { scaleX = lift.value; scaleY = lift.value; alpha = if (state.ghostTop.isNaN()) 0f else 1f }
            .shadow(if (reduced) 4.dp else 12.dp, shape, clip = false)
            .background(c.surface2, shape)
            .glass(shape, CytTheme.highlight)
            .clearAndSetSemantics {},
    ) {
        DockRow(app.name, Modifier.heightIn(min = height), controls = { Grip() })
    }
}

// Lifts the app; false when it is not where the finger is any more.
private fun startDrag(state: DockEditorState, motion: DockMotion, key: String): Boolean {
    if (state.settling || state.dragging != null) return false
    val top = motion.top(key) ?: return false
    motion.capture(state.apps.map { it.key })
    state.previewNudge = 0f
    state.ghostTop = top
    state.ghostHeight = motion.height(key)
    state.dragging = key
    return true
}

// Moves the lifted copy with the finger and the app to the place nearest its middle.
private fun dragBy(state: DockEditorState, motion: DockMotion, dy: Float) {
    val key = state.dragging ?: return
    if (state.settling) return
    val h = motion.height(key).toFloat()
    val first = motion.top(state.apps.first().key) ?: return
    val lastTop = motion.top(state.apps.last().key) ?: return
    state.ghostTop = (state.ghostTop + dy).coerceIn(first - h / 2, lastTop + h / 2)
    if (motion.awaitingLayout) return
    val from = state.apps.indexOfFirst { it.key == key }
    val middle = state.ghostTop + h / 2
    val to = state.apps.indices.minByOrNull { i ->
        val k = state.apps[i].key
        val top = motion.top(k) ?: return@minByOrNull Float.MAX_VALUE
        abs(top + motion.height(k) / 2f - middle)
    } ?: return
    if (to != from) {
        motion.capture(state.apps.map { it.key })
        state.move(from, to)
        motion.awaitingLayout = true
    }
}

// While an app is lifted and the finger is near the visible top or bottom of the sheet, the sheet
// scrolls a little every frame, and the lifted copy moves with the finger over the new place.
private suspend fun autoScroll(state: DockEditorState, motion: DockMotion, scroll: ScrollState, edge: Float, speed: Float) {
    while (state.dragging != null && !state.settling) {
        withFrameNanos { }
        val e = motion.editor?.takeIf { it.isAttached } ?: continue
        val visible = e.boundsInRoot()
        val finger = e.localToRoot(Offset(0f, motion.fingerY)).y
        val step = when {
            finger > visible.bottom - edge && e.localToRoot(Offset(0f, e.size.height.toFloat())).y > visible.bottom + 1f -> speed
            finger < visible.top + edge && e.localToRoot(Offset.Zero).y < visible.top - 1f -> -speed
            else -> 0f
        }
        if (step == 0f) continue
        val moved = scroll.scrollBy(step)
        if (moved != 0f) {
            motion.fingerY += moved
            dragBy(state, motion, moved)
        }
    }
}

// Drops the app: the lifted copy settles into its place on a spring (at once under reduced motion).
private fun endDrag(state: DockEditorState, motion: DockMotion, reduced: Boolean, scope: CoroutineScope, tick: () -> Unit) {
    val key = state.dragging ?: return
    if (state.settling) return
    tick()
    if (reduced) {
        state.dragging = null
        return
    }
    state.settling = true
    scope.launch {
        withFrameNanos { }   // the last move's layout, so the place is where the app now is
        val target = motion.top(key) ?: state.ghostTop
        animate(state.ghostTop, target, animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)) { v, _ -> state.ghostTop = v }
        state.dragging = null
        state.settling = false
    }
}
