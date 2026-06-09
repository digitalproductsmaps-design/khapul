package com.example.data

import androidx.room.*

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val username: String,
    val passwordHash: String,
    val role: String, // "CUSTOMER", "DRIVER", "ADMIN"
    val phone: String,
    val operatingZones: String, // Comma separated list of zones for drivers
    val documentUrl: String?, // Document path or placeholder string
    val isApproved: Boolean,  // Document verification status
    val rating: Float = 5.0f,
    val ratingCount: Int = 0
)

@Entity(tableName = "tickets")
data class TicketEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val customerUsername: String,
    val type: String, // "RIDE", "ERRAND"
    val zone: String,
    val startLocation: String, // Description or coordinate representation
    val destination: String,
    val status: String, // "OPEN", "NEGOTIATING", "ACTIVE", "CLOSED"
    val createdAt: Long = System.currentTimeMillis(),
    val agreedPrice: Double = 0.0,
    val assignedDriverUsername: String? = null,
    val isPriority: Boolean = false // Urgent requests have 1-day priority
)

@Entity(tableName = "errand_items")
data class ErrandItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ticketId: Int,
    val description: String,
    val isCompleted: Boolean = false,
    val delegatedDriverUsername: String? = null // For driver-led delegation during issues
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ticketId: Int,
    val senderName: String,
    val senderRole: String, // "CUSTOMER", "DRIVER", "ADMIN"
    val messageText: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "support_tickets")
data class SupportTicketEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val messageText: String,
    val replyText: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "ratings")
data class RatingEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ticketId: Int,
    val reviewer: String,
    val reviewee: String,
    val stars: Int,
    val comment: String,
    val timestamp: Long = System.currentTimeMillis()
)
