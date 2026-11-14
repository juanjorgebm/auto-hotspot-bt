package com.rutatv.widget

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.rutatv.R
import com.rutatv.RutaApp
import com.rutatv.data.Item
import com.rutatv.data.Kind
import com.rutatv.data.XtreamApi
import com.rutatv.player.PlayQueue
import com.rutatv.ui.MainActivity
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/** Los tres widgets de la pantalla de inicio. */
enum class WidgetType(val title: String, val empty: String, val grid: Boolean) {
    FAV_CHANNELS("★ Canales favoritos", "Sin canales favoritos.\nMárcalos con la ☆ en la app.", false),
    RECENT_SERIES("Series recientes", "Aún no has visto series.", false),
    NEW_MOVIES("Películas nuevas", "Cargando películas nuevas…", true),
    EVENTS("Eventos deportivos", "Cargando eventos…", false),
}

/** Categorías de TV en vivo que puede mostrar el widget de eventos (se cambia con ⇄). */
val EVENT_CATEGORIES = listOf("DEPORTES DIARIOS VIP", "EVENTOS DEPORTIVOS")

const val ACTION_REFRESH = "com.rutatv.widget.REFRESH"
const val ACTION_SWITCH = "com.rutatv.widget.SWITCH"

object Widgets {
    private val providers = listOf(
        FavChannelsWidget::class.java, RecentSeriesWidget::class.java, NewMoviesWidget::class.java, EventsWidget::class.java,
    )

    fun eventChoice(widgetId: Int) = RutaApp.repo.prefs.int("evt_choice_$widgetId") % EVENT_CATEGORIES.size

    /** Avisa a los widgets que cambiaron favoritos, recientes o el perfil. */
    fun refresh(context: Context) {
        try {
            val m = AppWidgetManager.getInstance(context) ?: return
            providers.forEach { cls ->
                val ids = m.getAppWidgetIds(ComponentName(context, cls))
                if (ids.isNotEmpty()) {
                    m.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
                    m.notifyAppWidgetViewDataChanged(ids, R.id.widget_grid)
                }
            }
        } catch (e: Exception) {
            // Sin widgets o lanzador sin soporte: no pasa nada.
        }
    }

    /** Lista que muestra cada widget. [network] = se permite descargar (solo para películas nuevas). */
    fun loadItems(context: Context, type: WidgetType, network: Boolean, widgetId: Int = 0): List<Item> {
        val repo = RutaApp.repo
        val prefs = repo.prefs
        val profile = repo.active.value ?: repo.lastProfile() ?: return emptyList()
        return when (type) {
            WidgetType.FAV_CHANNELS -> prefs.favorites(profile.id).filter { it.kind == Kind.LIVE }
            WidgetType.RECENT_SERIES -> prefs.recents(profile.id).filter { it.kind == Kind.EPISODE }.take(20)
            WidgetType.NEW_MOVIES -> {
                val server = profile.account.server
                val cached = prefs.newMovies(server)
                val stale = System.currentTimeMillis() - prefs.newMoviesTime(server) > 6 * 3600_000L
                if (network && (cached.isEmpty() || stale)) {
                    try {
                        XtreamApi(profile.account, repo.http).latestMovies(24).also { prefs.setNewMovies(server, it) }
                    } catch (e: Exception) {
                        cached
                    }
                } else cached
            }
            WidgetType.EVENTS -> {
                val cacheKey = "evt_items_$widgetId"
                if (!network) return prefs.items(cacheKey)
                try {
                    val api = XtreamApi(profile.account, repo.http)
                    val wanted = com.rutatv.data.Repo.normalize(EVENT_CATEGORIES[eventChoice(widgetId)])
                    val cat = api.categories(Kind.LIVE).firstOrNull { com.rutatv.data.Repo.normalize(it.name).contains(wanted) }
                        ?: return emptyList<Item>().also { prefs.setItems(cacheKey, it) }
                    api.streams(Kind.LIVE, cat.id).also { prefs.setItems(cacheKey, it) }
                } catch (e: Exception) {
                    prefs.items(cacheKey)
                }
            }
        }
    }

    fun bitmap(context: Context, url: String?, w: Int, h: Int): Bitmap? {
        if (url.isNullOrBlank()) return null
        return try {
            val req = ImageRequest.Builder(context).data(url).size(w, h).allowHardware(false).build()
            val result = runBlocking { context.imageLoader.execute(req) }
            (result as? SuccessResult)?.drawable?.toBitmap(w, h)
        } catch (e: Throwable) {
            null
        }
    }
}

abstract class BaseWidget(private val type: WidgetType) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_list)
            val title = if (type == WidgetType.EVENTS) EVENT_CATEGORIES[Widgets.eventChoice(id)] else type.title
            views.setTextViewText(R.id.widget_title, title)
            val showButtons = type == WidgetType.EVENTS
            views.setViewVisibility(R.id.widget_refresh, if (showButtons) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_switch, if (showButtons) View.VISIBLE else View.GONE)
            if (showButtons) {
                val immutableFlag = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
                fun action(a: String, code: Int) = PendingIntent.getBroadcast(
                    context, code,
                    Intent(context, javaClass).setAction(a).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                    PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag,
                )
                views.setOnClickPendingIntent(R.id.widget_refresh, action(ACTION_REFRESH, id * 10 + 1))
                views.setOnClickPendingIntent(R.id.widget_switch, action(ACTION_SWITCH, id * 10 + 2))
            }
            views.setTextViewText(R.id.widget_empty, type.empty)

            val svc = Intent(context, WidgetService::class.java)
                .putExtra("type", type.name)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            svc.data = Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME))
            val listId = if (type.grid) R.id.widget_grid else R.id.widget_list
            views.setViewVisibility(R.id.widget_grid, if (type.grid) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_list, if (type.grid) View.GONE else View.VISIBLE)
            @Suppress("DEPRECATION")
            views.setRemoteAdapter(listId, svc)
            views.setEmptyView(listId, R.id.widget_empty)

            val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val immutable = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
            val template = PendingIntent.getActivity(
                context, type.ordinal, Intent(context, WidgetLaunchActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or mutable,
            )
            views.setPendingIntentTemplate(listId, template)
            val openApp = PendingIntent.getActivity(
                context, 100 + type.ordinal, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or immutable,
            )
            views.setOnClickPendingIntent(R.id.widget_header, openApp)
            manager.updateAppWidget(id, views)
        }
        // El id real de la lista visible: avisamos a ambos para que recarguen.
        manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_grid)
        manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
    }
}

class FavChannelsWidget : BaseWidget(WidgetType.FAV_CHANNELS)
class RecentSeriesWidget : BaseWidget(WidgetType.RECENT_SERIES)
class NewMoviesWidget : BaseWidget(WidgetType.NEW_MOVIES)

/** Eventos deportivos del día, con botones ⟳ (actualizar) y ⇄ (cambiar de categoría). */
class EventsWidget : BaseWidget(WidgetType.EVENTS) {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID && (intent.action == ACTION_REFRESH || intent.action == ACTION_SWITCH)) {
            val prefs = RutaApp.repo.prefs
            if (intent.action == ACTION_SWITCH) prefs.setInt("evt_choice_$id", prefs.int("evt_choice_$id") + 1)
            val m = AppWidgetManager.getInstance(context)
            onUpdate(context, m, intArrayOf(id))
            return
        }
        super.onReceive(context, intent)
    }
}

class WidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        Factory(
            applicationContext,
            WidgetType.valueOf(intent.getStringExtra("type") ?: WidgetType.FAV_CHANNELS.name),
            intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0),
        )

    private class Factory(val context: Context, val type: WidgetType, val widgetId: Int) : RemoteViewsFactory {
        private var items: List<Item> = emptyList()

        override fun onCreate() {}
        override fun onDataSetChanged() { items = Widgets.loadItems(context, type, network = true, widgetId = widgetId) }
        override fun onDestroy() {}
        override fun getCount() = items.size
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = false

        override fun getViewAt(position: Int): RemoteViews {
            val item = items.getOrNull(position)
            val layout = if (type.grid) R.layout.widget_poster else R.layout.widget_item
            val v = RemoteViews(context.packageName, layout)
            if (item == null) return v
            v.setTextViewText(R.id.item_title, item.name)
            val bmp = if (type.grid) Widgets.bitmap(context, item.icon, 120, 180) else Widgets.bitmap(context, item.icon, 120, 80)
            if (bmp != null) v.setImageViewBitmap(R.id.item_image, bmp) else v.setImageViewResource(R.id.item_image, R.drawable.ic_launcher_foreground)
            v.setOnClickFillInIntent(
                R.id.item_root,
                Intent().putExtra("type", type.name).putExtra("item", item.toJson().toString()).putExtra("wid", widgetId),
            )
            return v
        }
    }
}

/** Recibe el toque en un widget y abre el reproductor (sin pantalla propia). */
class WidgetLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val repo = RutaApp.repo
            if (repo.active.value == null) repo.selectProfile(repo.lastProfile())
            val item = intent.getStringExtra("item")?.let { Item.fromJson(JSONObject(it)) }
            val type = intent.getStringExtra("type")?.let { WidgetType.valueOf(it) }
            if (item != null && type != null && repo.active.value != null) {
                val list = Widgets.loadItems(this, type, network = false, widgetId = intent.getIntExtra("wid", 0)).ifEmpty { listOf(item) }
                val idx = list.indexOfFirst { it.key == item.key }
                if (idx >= 0) PlayQueue.start(this, list, idx) else PlayQueue.start(this, listOf(item), 0)
            } else {
                startActivity(Intent(this, MainActivity::class.java))
            }
        } catch (e: Exception) {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }
}
