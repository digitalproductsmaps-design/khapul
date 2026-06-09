package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE username = :username")
    suspend fun getUser(username: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("DELETE FROM users WHERE username = :username")
    suspend fun deleteUser(username: String)

    @Query("UPDATE users SET isApproved = :approved WHERE username = :username")
    suspend fun updateUserApproval(username: String, approved: Boolean)

    @Query("SELECT * FROM users WHERE role = 'DRIVER' AND isApproved = 0")
    fun getPendingDriversFlow(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE role = 'DRIVER'")
    fun getAllDriversFlow(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE role = 'ADMIN'")
    fun getAllAdminsFlow(): Flow<List<UserEntity>>

    @Query("UPDATE users SET rating = :rating, ratingCount = :count WHERE username = :username")
    suspend fun updateUserRating(username: String, rating: Float, count: Int)
}

@Dao
interface TicketDao {
    @Query("SELECT * FROM tickets ORDER BY id DESC")
    fun getAllTicketsFlow(): Flow<List<TicketEntity>>

    @Query("SELECT * FROM tickets WHERE id = :id")
    suspend fun getTicketById(id: Int): TicketEntity?

    @Query("SELECT * FROM tickets WHERE id = :id")
    fun getTicketByIdFlow(id: Int): Flow<TicketEntity?>

    @Query("SELECT * FROM tickets WHERE customerUsername = :customerUsername ORDER BY id DESC")
    fun getTicketsForCustomerFlow(customerUsername: String): Flow<List<TicketEntity>>

    @Query("SELECT * FROM tickets WHERE status = 'OPEN' AND zone = :zone ORDER BY id DESC")
    fun getOpenTicketsForZoneFlow(zone: String): Flow<List<TicketEntity>>

    @Query("SELECT * FROM tickets WHERE assignedDriverUsername = :driverUsername OR (status = 'OPEN' AND zone IN (:zones)) ORDER BY id DESC")
    fun getTicketsForDriverFlow(driverUsername: String, zones: List<String>): Flow<List<TicketEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTicket(ticket: TicketEntity): Long

    @Query("UPDATE tickets SET status = :status WHERE id = :id")
    suspend fun updateTicketStatus(id: Int, status: String)

    @Query("UPDATE tickets SET agreedPrice = :price, assignedDriverUsername = :driverUsername, status = 'NEGOTIATING' WHERE id = :id")
    suspend fun negotiateTicket(id: Int, price: Double, driverUsername: String)

    @Query("UPDATE tickets SET status = 'ACTIVE' WHERE id = :id")
    suspend fun acceptNegotiatedPrice(id: Int)
    
    @Query("UPDATE tickets SET status = 'CLOSED' WHERE id = :id")
    suspend fun closeTicket(id: Int)
}

@Dao
interface ErrandItemDao {
    @Query("SELECT * FROM errand_items WHERE ticketId = :ticketId ORDER BY id ASC")
    fun getErrandItemsForTicketFlow(ticketId: Int): Flow<List<ErrandItemEntity>>

    @Query("SELECT * FROM errand_items WHERE ticketId = :ticketId ORDER BY id ASC")
    suspend fun getErrandItemsForTicket(ticketId: Int): List<ErrandItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertErrandItem(item: ErrandItemEntity)

    @Query("UPDATE errand_items SET isCompleted = :completed WHERE id = :id")
    suspend fun updateCompletion(id: Int, completed: Boolean)

    @Query("UPDATE errand_items SET delegatedDriverUsername = :delegatedDriver WHERE id = :id")
    suspend fun delegateItem(id: Int, delegatedDriver: String?)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE ticketId = :ticketId ORDER BY timestamp ASC")
    fun getMessagesForTicketFlow(ticketId: Int): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)
}

@Dao
interface SupportTicketDao {
    @Query("SELECT * FROM support_tickets ORDER BY timestamp DESC")
    fun getAllSupportFlow(): Flow<List<SupportTicketEntity>>

    @Query("SELECT * FROM support_tickets WHERE username = :username ORDER BY timestamp DESC")
    fun getSupportForUserFlow(username: String): Flow<List<SupportTicketEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupport(ticket: SupportTicketEntity)

    @Query("UPDATE support_tickets SET replyText = :reply WHERE id = :id")
    suspend fun replyToSupport(id: Int, reply: String)
}

@Dao
interface RatingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRating(rating: RatingEntity)

    @Query("SELECT * FROM ratings WHERE reviewee = :username ORDER BY timestamp DESC")
    fun getRatingsForUserFlow(username: String): Flow<List<RatingEntity>>
}
