package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class DatabaseRepository(private val db: AppDatabase) {
    val userDao = db.userDao()
    val ticketDao = db.ticketDao()
    val errandItemDao = db.errandItemDao()
    val messageDao = db.messageDao()
    val supportTicketDao = db.supportTicketDao()
    val ratingDao = db.ratingDao()

    // Query helpers
    fun getPendingDrivers(): Flow<List<UserEntity>> = userDao.getPendingDriversFlow()
    fun getAllDrivers(): Flow<List<UserEntity>> = userDao.getAllDriversFlow()
    fun getAllAdmins(): Flow<List<UserEntity>> = userDao.getAllAdminsFlow()
    fun getTicketsForCustomer(username: String): Flow<List<TicketEntity>> = ticketDao.getTicketsForCustomerFlow(username)
    fun getTicketsForDriver(username: String, zones: List<String>): Flow<List<TicketEntity>> = ticketDao.getTicketsForDriverFlow(username, zones)
    fun getMessagesForTicket(ticketId: Int): Flow<List<MessageEntity>> = messageDao.getMessagesForTicketFlow(ticketId)
    fun getErrandItemsForTicket(ticketId: Int): Flow<List<ErrandItemEntity>> = errandItemDao.getErrandItemsForTicketFlow(ticketId)
    fun getAllSupportTickets(): Flow<List<SupportTicketEntity>> = supportTicketDao.getAllSupportFlow()
    fun getSupportTicketsForUser(username: String): Flow<List<SupportTicketEntity>> = supportTicketDao.getSupportForUserFlow(username)

    suspend fun getUser(username: String): UserEntity? = userDao.getUser(username)
    suspend fun insertUser(user: UserEntity) = userDao.insertUser(user)
    suspend fun deleteUser(username: String) = userDao.deleteUser(username)
    suspend fun approveDriver(username: String) = userDao.updateUserApproval(username, true)
    suspend fun getTicketById(id: Int) = ticketDao.getTicketById(id)
    fun getTicketByIdFlow(id: Int) = ticketDao.getTicketByIdFlow(id)

    suspend fun createTicket(ticket: TicketEntity, errandItems: List<String>): Int {
        val ticketId = ticketDao.insertTicket(ticket).toInt()
        errandItems.forEach { desc ->
            if (desc.isNotBlank()) {
                errandItemDao.insertErrandItem(
                    ErrandItemEntity(ticketId = ticketId, description = desc, isCompleted = false)
                )
            }
        }
        return ticketId
    }

    suspend fun negotiate(ticketId: Int, price: Double, driverUsername: String) {
        ticketDao.negotiateTicket(ticketId, price, driverUsername)
        // System automated log message in chat context
        messageDao.insertMessage(
            MessageEntity(
                ticketId = ticketId,
                senderName = "System",
                senderRole = "SYSTEM",
                messageText = "Driver @$driverUsername proposed a price offer of $${String.format("%.2f", price)}. Use chat to negotiate or tap Accept."
            )
        )
    }

    suspend fun sendMessage(ticketId: Int, senderName: String, senderRole: String, text: String) {
        messageDao.insertMessage(
            MessageEntity(
                ticketId = ticketId,
                senderName = senderName,
                senderRole = senderRole,
                messageText = text
            )
        )
    }

    suspend fun acceptNegotiatedPrice(ticketId: Int) {
        ticketDao.acceptNegotiatedPrice(ticketId)
        val t = ticketDao.getTicketById(ticketId)
        messageDao.insertMessage(
            MessageEntity(
                ticketId = ticketId,
                senderName = "System",
                senderRole = "SYSTEM",
                messageText = "Customer approved the price of $${String.format("%.2f", t?.agreedPrice ?: 0.0)}. The ride/errand is now ACTIVE!"
            )
        )
    }

    suspend fun closeTicket(ticketId: Int, driverUsername: String) {
        ticketDao.closeTicket(ticketId)
        messageDao.insertMessage(
            MessageEntity(
                ticketId = ticketId,
                senderName = "System",
                senderRole = "SYSTEM",
                messageText = "Driver @$driverUsername officially marked this job as COMPLETED & CLOSED."
            )
        )
    }

    suspend fun updateErrandItemCompletion(itemId: Int, isCompleted: Boolean) {
        errandItemDao.updateCompletion(itemId, isCompleted)
    }

    suspend fun delegateErrandItem(itemId: Int, delegatedDriver: String) {
        errandItemDao.delegateItem(itemId, delegatedDriver)
    }

    suspend fun submitSupportTicket(username: String, message: String) {
        supportTicketDao.insertSupport(
            SupportTicketEntity(username = username, messageText = message)
        )
    }

    suspend fun replyToSupportTicket(id: Int, reply: String) {
        supportTicketDao.replyToSupport(id, reply)
    }

    suspend fun submitReview(ticketId: Int, reviewer: String, reviewee: String, stars: Int, comment: String) {
        ratingDao.insertRating(
            RatingEntity(ticketId = ticketId, reviewer = reviewer, reviewee = reviewee, stars = stars, comment = comment)
        )
        // Recalculate average rating
        val ratings = ratingDao.getRatingsForUserFlow(reviewee).firstOrNull() ?: emptyList()
        val count = ratings.size + 1
        val sum = ratings.sumOf { it.stars } + stars
        val newAvg = sum.toFloat() / count
        userDao.updateUserRating(reviewee, newAvg, count)
    }

    suspend fun seedDatabase() {
        // Only seed if Admin doesn't exist
        if (userDao.getUser("admin") == null) {
            // Admin account
            userDao.insertUser(
                UserEntity(
                    username = "admin",
                    passwordHash = "admin",
                    role = "ADMIN",
                    phone = "+268-7602-0000",
                    operatingZones = "All Areas",
                    documentUrl = null,
                    isApproved = true
                )
            )

            // Pre-approved Driver 1
            userDao.insertUser(
                UserEntity(
                    username = "ali_driver",
                    passwordHash = "1234",
                    role = "DRIVER",
                    phone = "+268-7602-5501",
                    operatingZones = "Hhohho (Mbabane), Hhohho (North)",
                    documentUrl = "upload_id_1.png",
                    isApproved = true,
                    rating = 4.8f,
                    ratingCount = 15
                )
            )

            // Pre-approved Driver 2
            userDao.insertUser(
                UserEntity(
                    username = "fatima_driver",
                    passwordHash = "1234",
                    role = "DRIVER",
                    phone = "+268-7602-5502",
                    operatingZones = "Shiselweni, Lubombo",
                    documentUrl = "upload_id_2.png",
                    isApproved = true,
                    rating = 4.9f,
                    ratingCount = 22
                )
            )

            // Pending Driver 3
            userDao.insertUser(
                UserEntity(
                    username = "samir_driver",
                    passwordHash = "1234",
                    role = "DRIVER",
                    phone = "+268-7602-5503",
                    operatingZones = "Manzini, Hhohho (Mbabane)",
                    documentUrl = "work_permit_samir.png",
                    isApproved = false,
                    rating = 5.0f,
                    ratingCount = 0
                )
            )

            // Demo Customer
            userDao.insertUser(
                UserEntity(
                    username = "zainab_customer",
                    passwordHash = "1234",
                    role = "CUSTOMER",
                    phone = "+268-7602-9001",
                    operatingZones = "",
                    documentUrl = null,
                    isApproved = true
                )
            )

            // Create 2 default tickets to show live list right away
            val ticket1Id = createTicket(
                TicketEntity(
                    customerUsername = "zainab_customer",
                    type = "RIDE",
                    zone = "Hhohho (Mbabane)",
                    startLocation = "Mbabane CBD near Allister Miller Street",
                    destination = "Corporate Plaza Mall, Mbabane",
                    status = "OPEN"
                ),
                emptyList()
            )

            val ticket2Id = createTicket(
                TicketEntity(
                    customerUsername = "zainab_customer",
                    type = "ERRAND",
                    zone = "Hhohho (North)",
                    startLocation = "Piggs Peak Central Pharmacy",
                    destination = "Bulembu Junction Road",
                    status = "OPEN",
                    isPriority = true
                ),
                listOf(
                    "2x Box of Cough Lozenges",
                    "1x Vitamin C Effervescent 1000mg",
                    "Pick up laundry from Laundromat (Receipt #404)"
                )
            )

            // Seed a sample rating/review for Ali
            ratingDao.insertRating(
                RatingEntity(
                    ticketId = 100,
                    reviewer = "zainab_customer",
                    reviewee = "ali_driver",
                    stars = 5,
                    comment = "Very polite driver, prompt delivery of grocery packages!",
                    timestamp = System.currentTimeMillis() - 86400000
                )
            )
        }
    }
}
