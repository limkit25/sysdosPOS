package com.sysdos.kasirpintar.viewmodel

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sysdos.kasirpintar.data.model.User

class UserAdapter(
    private val onClick: (User) -> Unit,       // UNTUK EDIT
    private val onLongClick: (User) -> Unit    // UNTUK HAPUS
) : ListAdapter<User, UserAdapter.UserViewHolder>(DiffCallback()) {

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text1: TextView = itemView.findViewById(android.R.id.text1)
        val text2: TextView = itemView.findViewById(android.R.id.text2)

        fun bind(user: User, onClick: (User) -> Unit, onLongClick: (User) -> Unit) {
            text1.text = user.username
            text1.textSize = 18f

            // Tampilkan Role di baris kedua
            text2.text = "Role: ${user.role}  |  Password: ••••••"

            // --- GANTI BAGIAN IKON INI ---
            val icon = when (user.role) {
                "admin" -> android.R.drawable.ic_secure        // Ikon Gembok
                "manager" -> android.R.drawable.ic_menu_agenda // Ikon Buku/Agenda
                else -> android.R.drawable.ic_menu_myplaces    // Ikon Orang (Kasir)
            }

            text1.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0)
            text1.compoundDrawablePadding = 24

            // KLIK BIASA -> EDIT
            itemView.setOnClickListener { onClick(user) }

            // KLIK TAHAN -> HAPUS
            itemView.setOnLongClickListener {
                onLongClick(user)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(getItem(position), onClick, onLongClick)
    }

    class DiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(old: User, new: User) = old.id == new.id
        override fun areContentsTheSame(old: User, new: User) = old == new
    }
}