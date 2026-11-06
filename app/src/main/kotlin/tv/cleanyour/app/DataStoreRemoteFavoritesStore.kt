package tv.cleanyour.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import tv.cleanyour.core.flows.RemoteFavoritesStore
import tv.cleanyour.core.model.TvId

class DataStoreRemoteFavoritesStore(context: Context) : RemoteFavoritesStore {
    private val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("remote-favorites") })
    private fun key(tvId: TvId) = stringSetPreferencesKey("tv_${tvId.value}")
    override fun load(tvId: TvId): Set<String> = runBlocking { store.data.first()[key(tvId)].orEmpty() }
    override suspend fun save(tvId: TvId, favorites: Set<String>) { store.edit { it[key(tvId)] = favorites } }
}
