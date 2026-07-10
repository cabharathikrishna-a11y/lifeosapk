@file:Suppress("DEPRECATION")
package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.Contact
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.util.Locale

object GoogleContactsSyncManager {
    private const val TAG = "GoogleContactsSync"
    private const val CONTACTS_SCOPE = "oauth2:https://www.googleapis.com/auth/contacts"

    private val client = OkHttpClient()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    suspend fun getAccessToken(
        context: Context,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): String? = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            var email = prefs.getString("selected_contacts_account", null)
            if (email.isNullOrBlank()) {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                email = account?.email
            }
            if (email.isNullOrBlank()) {
                Log.w(TAG, "No Google account email found.")
                return@withContext null
            }
            GoogleAuthUtil.getToken(context, email, CONTACTS_SCOPE)
        } catch (recoverable: UserRecoverableAuthException) {
            Log.w(TAG, "User recoverable auth exception encountered for Contacts scope.", recoverable)
            recoverable.intent?.let { intent -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onAuthResolutionRequired(intent) } }
            null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error obtaining Google OAuth2 token for Contacts: ${e.message}", e)
            null
        }
    }

    /**
     * Performs a full 2-way sync:
     * 1. Pulls contacts from Google Contacts and updates/creates them locally.
     * 2. Pushes local contacts that are new or updated to Google Contacts.
     */
    suspend fun syncContacts(
        context: Context,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val token = getAccessToken(context, onAuthResolutionRequired)
            ?: return@withContext Pair(false, "Authorization required. Please connect your Google account.")

        try {
            val database = AppDatabase.getInstance(context)
            val contactDao = database.contactDao()
            val localContacts = contactDao.getAllContacts().first()

            // ---- STEP 1: PULL FROM GOOGLE ----
            val googleContacts = fetchGoogleConnections(context, token)
            val googleIdToConnection = googleContacts.associateBy { it.resourceName }

            for (gContact in googleContacts) {
                // Try to find matching local contact by googleContactId or fallback to names
                val matchedLocal = localContacts.find { it.googleContactId == gContact.resourceName }
                    ?: localContacts.find { 
                        gContact.firstName.isNotEmpty() &&
                        it.firstName.lowercase().trim() == gContact.firstName.lowercase().trim() &&
                        it.lastName.lowercase().trim() == gContact.lastName.lowercase().trim()
                    }

                if (matchedLocal != null) {
                    // Update existing local contact
                    val updated = matchedLocal.copy(
                        firstName = if (gContact.firstName.isNotEmpty()) gContact.firstName else matchedLocal.firstName,
                        middleName = if (gContact.middleName.isNotEmpty()) gContact.middleName else matchedLocal.middleName,
                        lastName = if (gContact.lastName.isNotEmpty()) gContact.lastName else matchedLocal.lastName,
                        phone = if (gContact.phone.isNotEmpty()) gContact.phone else matchedLocal.phone,
                        email = if (gContact.email.isNotEmpty()) gContact.email else matchedLocal.email,
                        address = if (gContact.address.isNotEmpty()) gContact.address else matchedLocal.address,
                        jobTitle = if (gContact.jobTitle.isNotEmpty()) gContact.jobTitle else matchedLocal.jobTitle,
                        dobString = if (gContact.dobString.isNotEmpty()) gContact.dobString else matchedLocal.dobString,
                        photoUri = if (!gContact.photoUrl.isNullOrEmpty()) gContact.photoUrl else matchedLocal.photoUri,
                        anniversaryString = if (gContact.anniversaryString.isNotEmpty()) gContact.anniversaryString else matchedLocal.anniversaryString,
                        additionalDatesJson = if (gContact.additionalDatesJson.isNotEmpty()) gContact.additionalDatesJson else matchedLocal.additionalDatesJson,
                        googleContactId = gContact.resourceName
                    )
                    contactDao.updateContact(updated)
                } else {
                    // Create new local contact (including name, phone, email, address, job title, dob, profile pic, and dates)
                    val newContact = Contact(
                        firstName = gContact.firstName,
                        middleName = gContact.middleName,
                        lastName = gContact.lastName,
                        phone = gContact.phone,
                        dobString = gContact.dobString,
                        photoUri = gContact.photoUrl,
                        email = gContact.email,
                        address = gContact.address,
                        jobTitle = gContact.jobTitle,
                        anniversaryString = gContact.anniversaryString,
                        additionalDatesJson = gContact.additionalDatesJson,
                        googleContactId = gContact.resourceName
                    )
                    contactDao.insertContact(newContact)
                }
            }

            // ---- STEP 2: PUSH TO GOOGLE ----
            // Re-fetch local contacts after Pull updates
            val currentLocalContacts = contactDao.getAllContacts().first()

            for (local in currentLocalContacts) {
                if (local.googleContactId != null) {
                    // It was already synced. Let's see if it still exists on Google
                    val existsOnGoogle = googleIdToConnection.containsKey(local.googleContactId)
                    if (existsOnGoogle) {
                        // Let's update Google if local info is different
                        val gContact = googleIdToConnection[local.googleContactId]!!
                        if (local.firstName != gContact.firstName ||
                            local.middleName != gContact.middleName ||
                            local.lastName != gContact.lastName ||
                            local.phone != gContact.phone ||
                            local.email != gContact.email ||
                            local.address != gContact.address ||
                            local.jobTitle != gContact.jobTitle ||
                            local.dobString != gContact.dobString ||
                            local.anniversaryString != gContact.anniversaryString ||
                            local.additionalDatesJson != gContact.additionalDatesJson
                        ) {
                            updateGoogleContact(token, local)
                        }
                    } else {
                        // It was deleted on Google, so we can clear the googleContactId
                        contactDao.updateContact(local.copy(googleContactId = null))
                    }
                } else {
                    // No Google Contact ID -> This is a new local contact! Create on Google.
                    val newGoogleId = createGoogleContact(token, local)
                    if (newGoogleId != null) {
                        val updatedLocal = local.copy(googleContactId = newGoogleId)
                        contactDao.updateContact(updatedLocal)

                        // If local contact has a profile pic, upload it to Google Contacts!
                        if (!local.photoUri.isNullOrEmpty()) {
                            uploadGoogleContactPhoto(context, token, newGoogleId, local.photoUri)
                        }
                    }
                }
            }

            Pair(true, "Successfully completed 2-way sync with Google Contacts (${googleContacts.size} Google contacts synced).")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error performing Google Contacts 2-way sync: ${e.message}", e)
            Pair(false, "Sync Error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    private fun sanitizePhone(phone: String): String {
        return phone.replace(Regex("[^0-9+]"), "")
    }

    private data class GoogleContactDetails(
        val resourceName: String,
        val etag: String,
        val firstName: String,
        val lastName: String,
        val middleName: String,
        val phone: String,
        val dobString: String,
        val photoUrl: String?,
        val email: String,
        val address: String,
        val jobTitle: String,
        val anniversaryString: String,
        val additionalDatesJson: String
    )

    private suspend fun fetchGoogleConnections(context: Context, token: String): List<GoogleContactDetails> {
        val url = "https://people.googleapis.com/v1/people/me/connections?personFields=names,phoneNumbers,birthdays,photos,emailAddresses,addresses,organizations,events&pageSize=1000"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        val list = mutableListOf<GoogleContactDetails>()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                Log.e(TAG, "Failed to fetch connections: code=${response.code}, msg=${response.message}, body=$errBody")
                if (response.code == 401) {
                    try {
                        GoogleAuthUtil.clearToken(context, token)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error clearing cached token: ${e.message}")
                    }
                }
                throw Exception("Failed to fetch Google Contacts: HTTP ${response.code} - ${response.message}. $errBody")
            }
            val bodyStr = response.body?.string() ?: ""
            Log.d(TAG, "fetchGoogleConnections Response JSON: $bodyStr")
            val json = JSONObject(bodyStr)
            val connections = json.optJSONArray("connections") ?: return emptyList()

            for (i in 0 until connections.length()) {
                val conn = connections.getJSONObject(i)
                val resourceName = conn.optString("resourceName")
                val etag = conn.optString("etag")

                // 2. Phone parsing
                var phone = ""
                val phoneNumbers = conn.optJSONArray("phoneNumbers")
                if (phoneNumbers != null && phoneNumbers.length() > 0) {
                    phone = phoneNumbers.getJSONObject(0).optString("value", "")
                }

                // 5. Email parsing
                var email = ""
                val emailAddresses = conn.optJSONArray("emailAddresses")
                if (emailAddresses != null && emailAddresses.length() > 0) {
                    email = emailAddresses.getJSONObject(0).optString("value", "")
                }

                // 1. Name parsing with display name fallback
                var firstName = ""
                var lastName = ""
                var middleName = ""
                val names = conn.optJSONArray("names")
                if (names != null && names.length() > 0) {
                    val nameObj = names.getJSONObject(0)
                    firstName = nameObj.optString("givenName", "").trim()
                    lastName = nameObj.optString("familyName", "").trim()
                    middleName = nameObj.optString("middleName", "").trim()
                    
                    if (firstName.isEmpty() && lastName.isEmpty()) {
                        val displayName = nameObj.optString("displayName", "").trim()
                        if (displayName.isNotEmpty()) {
                            val parts = displayName.split(" ", limit = 2)
                            firstName = parts.first()
                            lastName = parts.getOrNull(1) ?: ""
                        }
                    }
                }

                if (firstName.isEmpty() && lastName.isEmpty()) {
                    if (phone.isNotEmpty()) {
                        firstName = phone
                    } else if (email.isNotEmpty()) {
                        firstName = email.substringBefore("@")
                    } else {
                        firstName = "Unnamed Google Contact"
                    }
                }

                // 3. Birthday parsing with text fallback
                var dobString = ""
                val birthdays = conn.optJSONArray("birthdays")
                if (birthdays != null && birthdays.length() > 0) {
                    val bdayObj = birthdays.getJSONObject(0)
                    val dateObj = bdayObj.optJSONObject("date")
                    if (dateObj != null) {
                        val y = dateObj.optInt("year", 0)
                        val m = dateObj.optInt("month", 0)
                        val d = dateObj.optInt("day", 0)
                        if (y > 0 && m > 0 && d > 0) {
                            dobString = String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
                        } else if (m > 0 && d > 0) {
                            dobString = String.format(Locale.US, "%02d-%02d", m, d)
                        }
                    } else {
                        val text = bdayObj.optString("text", "").trim()
                        if (text.isNotEmpty()) {
                            dobString = text
                        }
                    }
                }

                // 4. Photo parsing (always extract the URL if present)
                var photoUrl: String? = null
                val photos = conn.optJSONArray("photos")
                if (photos != null && photos.length() > 0) {
                    photoUrl = photos.getJSONObject(0).optString("url")
                }

                // 6. Address parsing
                var address = ""
                val addresses = conn.optJSONArray("addresses")
                if (addresses != null && addresses.length() > 0) {
                    address = addresses.getJSONObject(0).optString("formattedValue", "")
                }

                // 7. Job Title parsing
                var jobTitle = ""
                val organizations = conn.optJSONArray("organizations")
                if (organizations != null && organizations.length() > 0) {
                    jobTitle = organizations.getJSONObject(0).optString("title", "")
                }

                // 8. Anniversary and other dates parsing
                var anniversaryString = ""
                val additionalDatesList = mutableListOf<String>()
                val events = conn.optJSONArray("events")
                if (events != null) {
                    for (j in 0 until events.length()) {
                        val eventObj = events.getJSONObject(j)
                        val type = eventObj.optString("type", "")
                        val formattedType = eventObj.optString("formattedType", type.replaceFirstChar { it.uppercase() })
                        val dateObj = eventObj.optJSONObject("date")
                        var dateStr = ""
                        if (dateObj != null) {
                            val y = dateObj.optInt("year", 0)
                            val m = dateObj.optInt("month", 0)
                            val d = dateObj.optInt("day", 0)
                            if (y > 0 && m > 0 && d > 0) {
                                dateStr = String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
                            } else if (m > 0 && d > 0) {
                                dateStr = String.format(Locale.US, "%02d-%02d", m, d)
                            }
                        } else {
                            dateStr = eventObj.optString("text", "").trim()
                        }

                        if (dateStr.isNotEmpty()) {
                            if (type == "anniversary") {
                                anniversaryString = dateStr
                            } else {
                                val label = if (formattedType.isNotEmpty()) formattedType else "Event"
                                additionalDatesList.add("$label:$dateStr")
                            }
                        }
                    }
                }
                val additionalDatesJson = additionalDatesList.joinToString(";")

                if (resourceName.isNotEmpty()) {
                    list.add(
                        GoogleContactDetails(
                            resourceName = resourceName,
                            etag = etag,
                            firstName = firstName,
                            lastName = lastName,
                            middleName = middleName,
                            phone = phone,
                            dobString = dobString,
                            photoUrl = photoUrl,
                            email = email,
                            address = address,
                            jobTitle = jobTitle,
                            anniversaryString = anniversaryString,
                            additionalDatesJson = additionalDatesJson
                        )
                    )
                }
            }
        }
        return list
    }

    private suspend fun createGoogleContact(token: String, contact: Contact): String? {
        val url = "https://people.googleapis.com/v1/people/createContact"
        val payload = buildContactPayload(contact)

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: ""
                val json = JSONObject(bodyStr)
                return json.optString("resourceName")
            } else {
                Log.e(TAG, "Failed to create contact on Google: code=${response.code}, body=${response.body?.string()}")
            }
        }
        return null
    }

    private suspend fun updateGoogleContact(token: String, contact: Contact): Boolean {
        val resourceName = contact.googleContactId ?: return false
        val etag = getEtag(token, resourceName) ?: return false

        val url = "https://people.googleapis.com/v1/$resourceName?updatePersonFields=names,phoneNumbers,birthdays,emailAddresses,addresses,organizations,events"
        val payload = buildContactPayload(contact).apply {
            put("etag", etag)
        }

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .patch(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                return true
            } else {
                Log.e(TAG, "Failed to update contact on Google: code=${response.code}, body=${response.body?.string()}")
            }
        }
        return false
    }

    private suspend fun uploadGoogleContactPhoto(context: Context, token: String, resourceName: String, photoUriStr: String): Boolean {
        try {
            val uri = Uri.parse(photoUriStr)
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val bytes = inputStream.readBytes()
                inputStream.close()
                val base64Str = Base64.encodeToString(bytes, Base64.NO_WRAP)

                val url = "https://people.googleapis.com/v1/$resourceName:updateContactPhoto"
                val payload = JSONObject().apply {
                    put("photoBytes", base64Str)
                }

                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                client.newCall(request).execute().use { response ->
                    return response.isSuccessful
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload contact photo: ${e.message}")
        }
        return false
    }

    private suspend fun getEtag(token: String, resourceName: String): String? {
        val request = Request.Builder()
            .url("https://people.googleapis.com/v1/$resourceName?personFields=metadata")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: ""
                val json = JSONObject(bodyStr)
                return json.optString("etag")
            }
        }
        return null
    }

    private fun buildContactPayload(contact: Contact): JSONObject {
        val payload = JSONObject()

        val namesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("givenName", contact.firstName)
                put("familyName", contact.lastName)
                if (contact.middleName.isNotEmpty()) {
                    put("middleName", contact.middleName)
                }
            })
        }
        payload.put("names", namesArray)

        if (contact.phone.isNotEmpty()) {
            val phoneArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("value", contact.phone)
                    put("type", "mobile")
                })
            }
            payload.put("phoneNumbers", phoneArray)
        }

        if (contact.email.isNotEmpty()) {
            val emailArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("value", contact.email)
                    put("type", "home")
                })
            }
            payload.put("emailAddresses", emailArray)
        }

        if (contact.address.isNotEmpty()) {
            val addressArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("streetAddress", contact.address)
                    put("type", "home")
                })
            }
            payload.put("addresses", addressArray)
        }

        if (contact.jobTitle.isNotEmpty()) {
            val orgArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("title", contact.jobTitle)
                })
            }
            payload.put("organizations", orgArray)
        }

        if (contact.dobString.isNotEmpty()) {
            val dobStr = contact.dobString
            val parts = dobStr.split("-")
            val dateObj = JSONObject()
            if (parts.size == 3) {
                dateObj.put("year", parts[0].toIntOrNull() ?: 0)
                dateObj.put("month", parts[1].toIntOrNull() ?: 0)
                dateObj.put("day", parts[2].toIntOrNull() ?: 0)
            } else if (parts.size == 2) {
                dateObj.put("month", parts[0].toIntOrNull() ?: 0)
                dateObj.put("day", parts[1].toIntOrNull() ?: 0)
            }
            if (dateObj.length() > 0) {
                val birthdaysArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("date", dateObj)
                    })
                }
                payload.put("birthdays", birthdaysArray)
            }
        }

        val eventsArray = JSONArray()
        if (contact.anniversaryString.isNotEmpty()) {
            val annStr = contact.anniversaryString
            val parts = annStr.split("-")
            val dateObj = JSONObject()
            if (parts.size == 3) {
                dateObj.put("year", parts[0].toIntOrNull() ?: 0)
                dateObj.put("month", parts[1].toIntOrNull() ?: 0)
                dateObj.put("day", parts[2].toIntOrNull() ?: 0)
            } else if (parts.size == 2) {
                dateObj.put("month", parts[0].toIntOrNull() ?: 0)
                dateObj.put("day", parts[1].toIntOrNull() ?: 0)
            }
            if (dateObj.length() > 0) {
                eventsArray.put(JSONObject().apply {
                    put("type", "anniversary")
                    put("date", dateObj)
                })
            }
        }

        if (contact.additionalDatesJson.isNotEmpty()) {
            contact.additionalDatesJson.split(";").forEach { pair ->
                val parts = pair.split(":")
                if (parts.size == 2) {
                    val label = parts[0]
                    val dateVal = parts[1]
                    val dateParts = dateVal.split("-")
                    val dateObj = JSONObject()
                    if (dateParts.size == 3) {
                        dateObj.put("year", dateParts[0].toIntOrNull() ?: 0)
                        dateObj.put("month", dateParts[1].toIntOrNull() ?: 0)
                        dateObj.put("day", dateParts[2].toIntOrNull() ?: 0)
                    } else if (dateParts.size == 2) {
                        dateObj.put("month", dateParts[0].toIntOrNull() ?: 0)
                        dateObj.put("day", dateParts[1].toIntOrNull() ?: 0)
                    }
                    if (dateObj.length() > 0) {
                        eventsArray.put(JSONObject().apply {
                            put("type", "other")
                            put("formattedType", label)
                            put("date", dateObj)
                        })
                    }
                }
            }
        }

        if (eventsArray.length() > 0) {
            payload.put("events", eventsArray)
        }

        return payload
    }
}
