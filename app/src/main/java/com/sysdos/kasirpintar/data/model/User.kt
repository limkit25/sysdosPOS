package com.sysdos.kasirpintar.data.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "user_table") // Sebaiknya 'users' biar sama kayak MySQL
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @SerializedName("username") // Kunci untuk kirim ke API
    val username: String,

    @SerializedName("password")
    val password: String,

    @SerializedName("role")
    val role: String, // "admin" atau "cashier"

    // 🔥 WAJIB ADA AGAR FITUR REGISTER LENGKAP JALAN
    @SerializedName("name")
    val name: String = "",

    @SerializedName("phone")
    val phone: String = ""


) : Parcelable