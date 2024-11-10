package com.donaldjohn.smsalerter.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.donaldjohn.smsalerter.R

class ContactsAdapter(
    private var contacts: List<String>,
    private val onDeleteClick: (String) -> Unit
) : RecyclerView.Adapter<ContactsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvPhone: TextView = view.findViewById(R.id.tvPhone)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.contact_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.tvPhone.text = contact
        holder.btnDelete.setOnClickListener { onDeleteClick(contact) }
    }

    override fun getItemCount() = contacts.size

    fun updateContacts(newContacts: List<String>) {
        contacts = newContacts
        notifyDataSetChanged()
    }
} 