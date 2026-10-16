// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.common

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.victorialauncher.media.NotificationCountBus
import dev.victorialauncher.VictoriaApp
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.EntryKeys
import dev.victorialauncher.data.EntryKind
import dev.victorialauncher.data.IconShape
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** Per-app icon overrides are stored either as a gallery `content://` URI or as `pack:<pkg>:<drawable>`. */
const val ICON_PACK_OVERRIDE_PREFIX = "pack:"

fun encodePackOverride(packPackage: String, drawableName: String) =
    "$ICON_PACK_OVERRIDE_PREFIX$packPackage:$drawableName"

/**
 * Icons are rasterised once and cached. They used to be drawn by handing a Drawable to an
 * ImageView through AndroidView, which meant inflating a real Android View per row — far too
 * expensive for a list that rebuilds while scrubbing.
 *
 * The cache is bounded by *bytes*, not entry count. A fixed count is the wrong unit here: the
 * cache key includes the rasterised pixel size, so moving the icon-size slider adds a whole
 * new generation of bitmaps rather than replacing the old one, and at 96dp on a dense screen
 * a single icon is ~450 KB. Sizing by bytes against the heap keeps that bounded no matter how
 * many apps are installed or how large the icons are set.
 */
private object IconCache {
    private val cache = object : LruCache<String, ImageBitmap>(maxSizeKb()) {
        // Reported in KB so the running total cannot overflow an Int.
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            ((value.width.toLong() * value.height.toLong() * 4L) / 1024L).toInt().coerceAtLeast(1)
    }

    private fun maxSizeKb(): Int {
        val heapKb = Runtime.getRuntime().maxMemory() / 1024L
        return (heapKb / 8L).toInt().coerceIn(4 * 1024, 96 * 1024)
    }

    fun get(key: String): ImageBitmap? = cache.get(key)
    fun put(key: String, value: ImageBitmap) = cache.put(key, value)

    /** Dropped wholesale when what every icon is drawn against changes, such as a profile. */
    fun clear() = cache.evictAll()

    /**
     * These packages' bitmaps and no others, when they are added, removed or changed: an app
     * that ships a new icon in an update keeps none of the key's components, so nothing else
     * would invalidate the stale bitmap. That is their apps and the shortcuts they published,
     * and anything drawn from one of them as an icon pack, since a pack's update changes the
     * pictures of apps that are not its own. Returns the keys that went.
     *
     * One snapshot for the whole burst, since each copies every entry in the cache.
     */
    fun evictPackages(packages: Collection<String>): Set<String> {
        if (packages.isEmpty()) return emptySet()
        val stale = HashSet<String>()
        val keys = cache.snapshot().keys
        for (packageName in packages) {
            val asPack = "|$packageName|"
            val asPackOverride = "|$ICON_PACK_OVERRIDE_PREFIX$packageName:"
            keys.filterTo(stale) { key ->
                EntryKeys.belongsToPackage(key, packageName) ||
                    key.contains(asPack) ||
                    key.contains(asPackOverride)
            }
        }
        stale.forEach { cache.remove(it) }
        return stale
    }
}

private val iconCacheGenerationFlow = MutableStateFlow(0)

/**
 * Moves on whenever the cache is emptied, so the warm pass knows to run again. That pass is
 * otherwise keyed on the app list, and a reload that finds the same apps publishes nothing new:
 * after a clear the cache was refilled by the rows themselves, an icon at a time on the main
 * thread, during the very scroll that asked for them (#119).
 */
val iconCacheGeneration: StateFlow<Int> = iconCacheGenerationFlow.asStateFlow()

fun clearIconCache() {
    IconCache.clear()
    iconCacheGenerationFlow.update { it + 1 }
}

/**
 * Not replayed, since a key that went before anyone was listening has nobody to draw it; and a
 * burst lost to a full buffer only leaves its icons to their rows, as any icon not yet warm is.
 */
private val iconEvictionsFlow = MutableSharedFlow<Set<String>>(
    extraBufferCapacity = 16,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
)

/**
 * The cache keys each [evictPackageIcons] threw out, for the warm pass to draw again and nothing
 * else. These do not move [iconCacheGeneration]: that ran the whole pass for one republished
 * shortcut, and with more icons than the cache holds, a pass in list order missed on every one
 * of them and pushed out the very bitmaps the home screen and the list would ask for next.
 */
val iconEvictions: SharedFlow<Set<String>> = iconEvictionsFlow.asSharedFlow()

fun evictPackageIcons(packages: Collection<String>) {
    val evicted = IconCache.evictPackages(packages)
    if (evicted.isNotEmpty()) iconEvictionsFlow.tryEmit(evicted)
}

// The class name is in here for the rows whose key alone cannot tell two pictures apart: the
// private-space row keeps one key across locking and unlocking, and the padlock it draws is
// not the same picture either side of that. For an app it is what the key already says.
private fun iconCacheKey(
    app: AppInfo,
    iconPack: String?,
    override: String?,
    px: Int,
    style: IconStyle,
) = "${app.key}|${app.componentName.className}|$iconPack|$override|$px|${style.shape}|${style.themed}|${style.background}"

/** Everything about how an icon is drawn that is not the icon itself. */
@Immutable
data class IconStyle(
    val shape: IconShape,
    val themed: Boolean,
    val background: Int,
    val foreground: Int,
)

/**
 * Decode every app's icon ahead of time, off the main thread. Without this the first open of
 * the A-Z list rasterises a screenful of icons synchronously during composition, which is
 * what made it take a beat to appear.
 *
 * Work is fanned out over a few threads rather than run one icon at a time, and
 * [priorityKeys] (favorites and folder members) go first so the home screen is covered before
 * the long tail of everything else installed.
 *
 * [only], when given, is the cache keys to draw: the ones [iconEvictions] reported, so a package
 * that changed has its own icons drawn again without every other one being looked over too.
 */
suspend fun warmIconCache(
    context: Context,
    apps: List<AppInfo>,
    iconPack: String?,
    overrides: Map<String, String>,
    px: Int,
    style: IconStyle,
    priorityKeys: Set<String> = emptySet(),
    only: Set<String>? = null,
) {
    if (px <= 0 || apps.isEmpty()) return
    val victoriaApp = context.applicationContext as VictoriaApp

    fun ownStyle(override: String?): IconStyle {
        val styled = override == null && iconPack == null
        return if (styled) style else style.copy(shape = IconShape.SYSTEM, themed = false)
    }

    fun keyOf(app: AppInfo): String {
        val override = overrides[app.key]
        return iconCacheKey(app, iconPack, override, px, ownStyle(override))
    }

    fun warm(app: AppInfo) {
        val override = overrides[app.key]
        val own = ownStyle(override)
        val key = iconCacheKey(app, iconPack, override, px, own)
        if (IconCache.get(key) != null) return
        runCatching {
            val drawable = resolveDrawable(context, victoriaApp, app, iconPack, override)
            val bitmap = renderIcon(drawable, px, own.shape, own.themed, own.background, own.foreground)
            IconCache.put(key, bitmap.asImageBitmap())
        }
    }

    // Explicitly off the caller's dispatcher: this is invoked from a LaunchedEffect, which
    // runs on Main, and coroutineScope/async would inherit it.
    withContext(warmDispatcher) {
        val targets = if (only == null) apps else apps.filter { keyOf(it) in only }
        val (first, rest) = targets.partition { it.key in priorityKeys }
        for (group in listOf(first, rest)) {
            if (group.isEmpty()) continue
            group.chunked(CHUNK_SIZE)
                .map { chunk ->
                    async {
                        // Checked per icon because nothing in warm() suspends: a pass replaced
                        // by a newer one would otherwise hold its threads to the end of every
                        // chunk it had started, and the newer pass would queue behind it.
                        chunk.forEach { app ->
                            ensureActive()
                            warm(app)
                        }
                    }
                }
                .awaitAll()
        }
    }
}

/** Big enough that per-task overhead stays negligible against a drawable decode. */
private const val CHUNK_SIZE = 16

/**
 * Half the cores, and at least one. The warm pass runs while the list is being opened and
 * scrolled, and spread over every core it competed with the main thread and RenderThread for
 * the very frames it is there to keep smooth (#119).
 */
@OptIn(ExperimentalCoroutinesApi::class)
private val warmDispatcher =
    Dispatchers.Default.limitedParallelism((Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1))

/**
 * Icon pack and per-app overrides, provided once for the whole tree. Collecting these flows
 * inside AppIcon meant every visible row started two DataStore collections of its own — with
 * a screenful of rows that is dozens of disk-backed collectors spun up the moment the list
 * appears, each resolving its icon twice (once for the initial value, once for the real one).
 */
@Immutable
data class IconConfig(
    val pack: String?,
    val overrides: Map<String, String>,
    /** False draws no icons at all, for people who want the list to be nothing but names. */
    val showIcons: Boolean,
    /** Draw the monochrome layer, tinted, instead of the app's own colors. */
    val themed: Boolean,
    /** Whether an app showing notifications is badged with how many. */
    val notificationBadges: Boolean,
    val shape: IconShape,
)

val LocalIconConfig = staticCompositionLocalOf {
    IconConfig(
        pack = null,
        overrides = emptyMap(),
        showIcons = true,
        themed = false,
        notificationBadges = false,
        shape = IconShape.SYSTEM,
    )
}

@Composable
fun AppIcon(app: AppInfo, sizeDp: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val victoriaApp = context.applicationContext as VictoriaApp
    val config = LocalIconConfig.current
    // Nothing drawn and no space taken, so rows close up rather than leaving a hole.
    if (!config.showIcons) return
    val iconPackPackage = config.pack
    val overrideValue = config.overrides[app.key]
    val px = with(LocalDensity.current) { sizeDp.dp.roundToPx() }.coerceAtLeast(1)

    // An override or an icon pack is a picture the user chose; neither is an adaptive icon
    // with layers to tint or a safe zone to cut into, so styling only applies to what the app
    // itself supplies.
    val styled = overrideValue == null && iconPackPackage == null
    val scheme = MaterialTheme.colorScheme
    val style = IconStyle(
        shape = if (styled) config.shape else IconShape.SYSTEM,
        themed = styled && config.themed,
        background = scheme.primaryContainer.toArgb(),
        foreground = scheme.onPrimaryContainer.toArgb(),
    )

    val cacheKey = iconCacheKey(app, iconPackPackage, overrideValue, px, style)
    val bitmap: ImageBitmap? = remember(cacheKey) {
        IconCache.get(cacheKey) ?: runCatching {
            val drawable = resolveDrawable(context, victoriaApp, app, iconPackPackage, overrideValue)
            renderIcon(drawable, px, style.shape, style.themed, style.background, style.foreground)
                .asImageBitmap()
                .also { IconCache.put(cacheKey, it) }
        }.getOrNull()
    }

    // Badged rather than drawn into the bitmap: the count changes while the icon does not, and
    // baking it in would throw away the cached icon every time a notification arrived.
    val badge = notificationBadgeCount(app)
    Box(modifier = modifier.size(sizeDp.dp)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = app.label,
                modifier = Modifier.size(sizeDp.dp),
            )
        }
        if (badge > 0) NotificationBadge(badge, sizeDp, Modifier.align(Alignment.TopEnd))
    }
}

/**
 * How many notifications to show on this app, or zero for none.
 *
 * Zero whenever the setting is off or the listener is not connected, so nothing is drawn from
 * a count that is only as current as the last time the service was alive.
 */
@Composable
private fun notificationBadgeCount(app: AppInfo): Int {
    if (!LocalIconConfig.current.notificationBadges) return 0
    val counts by NotificationCountBus.counts.collectAsState()
    return counts[app.componentName.packageName] ?: 0
}

/**
 * The count, in a filled circle at the icon's top corner.
 *
 * Sized from the icon rather than fixed, so it stays in proportion at every icon size the
 * launcher offers. Anything past nine is drawn as 9+: the circle is the signal, and a
 * three-digit number in it is unreadable at any size an icon is drawn at.
 */
@Composable
private fun NotificationBadge(count: Int, iconSizeDp: Int, modifier: Modifier = Modifier) {
    val diameter = (iconSizeDp * 0.42f).dp.coerceIn(14.dp, 22.dp)
    Box(
        modifier = modifier
            .size(diameter)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 9) "9+" else count.toString(),
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = (diameter.value * 0.55f).sp,
            lineHeight = (diameter.value * 0.55f).sp,
            maxLines = 1,
        )
    }
}

/**
 * Resolves an icon override — an icon-pack drawable or a picture from the gallery — for
 * either an app or a folder. Both use the same encoding on purpose, so a folder can take any
 * icon an app can.
 */
internal fun decodeIconOverride(
    context: Context,
    victoriaApp: VictoriaApp,
    override: String?,
): Drawable? = when {
    override == null -> null

    override.startsWith(ICON_PACK_OVERRIDE_PREFIX) -> {
        val body = override.removePrefix(ICON_PACK_OVERRIDE_PREFIX)
        val split = body.lastIndexOf(':')
        if (split <= 0) {
            null
        } else {
            victoriaApp.iconPackRepository.loadPackDrawable(
                body.substring(0, split),
                body.substring(split + 1),
            )
        }
    }

    else -> runCatching {
        val uri = Uri.parse(override)
        context.contentResolver.openInputStream(uri)?.use { stream ->
            Drawable.createFromStream(stream, uri.toString())
        }
    }.getOrNull()
}

private fun resolveDrawable(
    context: Context,
    victoriaApp: VictoriaApp,
    app: AppInfo,
    iconPackPackage: String?,
    overrideValue: String?,
): Drawable =
    decodeIconOverride(context, victoriaApp, overrideValue)
        ?: if (app.kind != EntryKind.APP) {
            // An icon pack is matched by component, and a shortcut carries its publisher's:
            // every bookmark would come back wearing the browser's icon instead of its own,
            // which is the one thing that tells two of them apart.
            victoriaApp.appRepository.loadIcon(app)
        } else {
            victoriaApp.iconPackRepository.getIcon(iconPackPackage, app.componentName) {
                victoriaApp.appRepository.loadIcon(app)
            }
        }