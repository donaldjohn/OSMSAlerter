package com.donaldjohn.smsalerter.data
import android.content.Context

class ContactManager private constructor(context: Context) {
    private val sharedPreferences = context.getSharedPreferences("contacts", Context.MODE_PRIVATE)
    private val contactsKey = "monitored_contacts"

    companion object {
        @Volatile
        private var instance: ContactManager? = null

        fun getInstance(context: Context): ContactManager {
            return instance ?: synchronized(this) {
                instance ?: ContactManager(context).also { instance = it }
            }
        }
    }

    fun addContact(contact: String) {
        val contacts = getContacts().toMutableSet()
        contacts.add(contact)
        sharedPreferences.edit().putStringSet(contactsKey, contacts).apply()
    }

    fun removeContact(contact: String) {
        val contacts = getContacts().toMutableSet()
        contacts.remove(contact)
        sharedPreferences.edit().putStringSet(contactsKey, contacts).apply()
    }

    fun getContacts(): Set<String> {
        return sharedPreferences.getStringSet(contactsKey, setOf()) ?: setOf()
    }

    fun isMonitoredContact(contact: String): Boolean {
        return getContacts().contains(contact)
    }
} 