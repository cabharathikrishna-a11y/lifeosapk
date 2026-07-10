package com.example.util

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.RawContacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Photo
import com.example.data.Contact

object SystemContactSyncHelper {

    fun getContactPhotoBytes(context: Context, photoUriStr: String): ByteArray? {
        try {
            if (photoUriStr.startsWith("http")) {
                var connection: java.net.HttpURLConnection? = null
                try {
                    val url = java.net.URL(photoUriStr)
                    connection = url.openConnection() as java.net.HttpURLConnection
                    connection.doInput = true
                    connection.connect()
                    val input = connection.inputStream
                    val bytes = input.readBytes()
                    input.close()
                    return bytes
                } finally {
                    connection?.disconnect()
                }
            } else {
                val file = java.io.File(photoUriStr)
                if (file.exists()) {
                    return file.readBytes()
                }
                val uri = Uri.parse(photoUriStr)
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    return inputStream.readBytes()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun insertSystemContact(context: Context, contact: Contact): Long? {
        val hasName = contact.firstName.isNotEmpty() || contact.lastName.isNotEmpty()
        val hasPhone = contact.phone.isNotEmpty()
        if (!hasName || !hasPhone) return null

        val resolver = context.contentResolver
        val ops = arrayListOf<ContentProviderOperation>()

        // 1. Raw Contact insertion
        val rawContactOpIndex = ops.size
        ops.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
            .withValue(RawContacts.ACCOUNT_TYPE, null)
            .withValue(RawContacts.ACCOUNT_NAME, null)
            .build())

        // 2. Name insertion
        ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
            .withValueBackReference(Data.RAW_CONTACT_ID, rawContactOpIndex)
            .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
            .withValue(StructuredName.DISPLAY_NAME, "${contact.firstName} ${contact.lastName}".trim())
            .build())

        // 3. Phone insertion
        if (contact.phone.isNotEmpty()) {
            ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, rawContactOpIndex)
                .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                .withValue(Phone.NUMBER, contact.phone)
                .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                .build())
        }

        // 4. Photo insertion
        if (!contact.photoUri.isNullOrEmpty()) {
            val imageBytes = getContactPhotoBytes(context, contact.photoUri)
            if (imageBytes != null) {
                ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, rawContactOpIndex)
                    .withValue(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
                    .withValue(Photo.PHOTO, imageBytes)
                    .build())
            }
        }

        return try {
            val results = resolver.applyBatch(ContactsContract.AUTHORITY, ops)
            if (results.isNotEmpty()) {
                val rawContactUri = results[0].uri
                if (rawContactUri != null) {
                    ContentUris.parseId(rawContactUri)
                } else null
            } else null
        } catch (e: SecurityException) {
            android.util.Log.e("SystemContactSync", "Permission missing for contact insertion: ${e.message}")
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun updateSystemContact(context: Context, contact: Contact): Long? {
        val hasName = contact.firstName.isNotEmpty() || contact.lastName.isNotEmpty()
        val hasPhone = contact.phone.isNotEmpty()
        if (!hasName || !hasPhone) {
            // Requirement: If it doesn't have both name and phone number, don't sync
            return contact.systemContactId
        }

        val systemId = contact.systemContactId ?: return insertSystemContact(context, contact)
        val resolver = context.contentResolver

        // Check if raw contact actually exists on system
        val rawUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, systemId)
        val cursor = try {
            resolver.query(rawUri, arrayOf(RawContacts._ID), null, null, null)
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val exists = cursor?.use { it.moveToFirst() } ?: false
        if (!exists) {
            return insertSystemContact(context, contact)
        }

        val ops = arrayListOf<ContentProviderOperation>()

        // Update StructuredName
        ops.add(ContentProviderOperation.newUpdate(Data.CONTENT_URI)
            .withSelection(
                "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?",
                arrayOf(systemId.toString(), StructuredName.CONTENT_ITEM_TYPE)
            )
            .withValue(StructuredName.DISPLAY_NAME, "${contact.firstName} ${contact.lastName}".trim())
            .build())

        // Delete old phone, photo so we can overwrite cleanly
        ops.add(ContentProviderOperation.newDelete(Data.CONTENT_URI)
            .withSelection(
                "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} IN (?, ?)",
                arrayOf(
                    systemId.toString(),
                    Phone.CONTENT_ITEM_TYPE,
                    Photo.CONTENT_ITEM_TYPE
                )
            )
            .build())

        // Re-insert phone
        if (contact.phone.isNotEmpty()) {
            ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValue(Data.RAW_CONTACT_ID, systemId)
                .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                .withValue(Phone.NUMBER, contact.phone)
                .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                .build())
        }

        // Re-insert photo
        if (!contact.photoUri.isNullOrEmpty()) {
            val imageBytes = getContactPhotoBytes(context, contact.photoUri)
            if (imageBytes != null) {
                ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValue(Data.RAW_CONTACT_ID, systemId)
                    .withValue(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
                    .withValue(Photo.PHOTO, imageBytes)
                    .build())
            }
        }

        return try {
            resolver.applyBatch(ContactsContract.AUTHORITY, ops)
            systemId
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            systemId
        }
    }

    fun deleteSystemContact(context: Context, contact: Contact) {
        val systemId = contact.systemContactId ?: return
        val resolver = context.contentResolver
        val rawUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, systemId)
        try {
            resolver.delete(rawUri, null, null)
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
