package com.shashank.applocker.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Package names are not secret (see README), so this table is stored unencrypted. */
@Entity(tableName = "protected_apps")
data class ProtectedAppEntity(
    @PrimaryKey val packageName: String,
    /** False while the user has paused protection for this app. Not an unlock session. */
    val lockActive: Boolean = true,
)
