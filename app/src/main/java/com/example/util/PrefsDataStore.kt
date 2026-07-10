package com.example.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lifeos_settings")

object PrefsDataStore {
    suspend fun getString(context: Context, key: String, defaultValue: String? = null): String? {
        val prefKey = stringPreferencesKey(key)
        return context.dataStore.data.map { preferences ->
            preferences[prefKey] ?: defaultValue
        }.first()
    }

    suspend fun putString(context: Context, key: String, value: String?) {
        val prefKey = stringPreferencesKey(key)
        context.dataStore.edit { preferences ->
            if (value != null) {
                preferences[prefKey] = value
            } else {
                preferences.remove(prefKey)
            }
        }
    }

    fun getStringBlocking(context: Context, key: String, defaultValue: String? = null): String? {
        return runBlocking { getString(context, key, defaultValue) }
    }

    fun putStringBlocking(context: Context, key: String, value: String?) {
        runBlocking { putString(context, key, value) }
    }
}
