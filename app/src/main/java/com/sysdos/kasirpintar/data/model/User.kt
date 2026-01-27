package com.sysdos.kasirpintar.data.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "user_table")
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @SerializedName("username")
    val username: String,

    @SerializedName("password")
    val password: String,

    @SerializedName("role")
    val role: String,

    @SerializedName("name")
    val name: String = "",

    @SerializedName("phone")
    val phone: String = "",

    @SerializedName("branch_id")
    val branchId: Int? = null

    // ❌ DO NOT PUT 'branch' HERE

) : Parcelable {

    // ✅ PUT 'branch' HERE (INSIDE THE CLASS BODY)
    @Ignore
    @SerializedName("branch")
    var branch: Branch? = null
}