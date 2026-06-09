package com.example.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class AppScreen {
    object Auth : AppScreen()
    object CustomerMain : AppScreen()
    object DriverMain : AppScreen()
    object AdminMain : AppScreen()
    object SupportChat : AppScreen()
    data class TicketChat(val ticketId: Int) : AppScreen()
}

class KhapulViewModel(val repository: DatabaseRepository) : ViewModel() {

    // UI state
    var currentUser by mutableStateOf<UserEntity?>(null)
        private set

    var currentScreen by mutableStateOf<AppScreen>(AppScreen.Auth)

    var authFeedback by mutableStateOf<String?>(null)
    var successFeedback by mutableStateOf<String?>(null)
    var registrationStep by mutableStateOf(1) // Wizard steps 1 to 4

    // Available Zones
    val availableZones = listOf(
        "Hhohho (Mbabane)",
        "Hhohho (North)",
        "Shiselweni",
        "Manzini",
        "Lubombo"
    )

    // Flows derived dynamically from repository
    val allTickets = repository.ticketDao.getAllTicketsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingDrivers = repository.getPendingDrivers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDrivers = repository.getAllDrivers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAdmins = repository.getAllAdmins()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val supportTickets = repository.getAllSupportTickets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active screen support flow for current user
    val mySupportTickets: StateFlow<List<SupportTicketEntity>> = flow {
        while (true) {
            val username = currentUser?.username ?: ""
            emitAll(repository.getSupportTicketsForUser(username))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.seedDatabase()
        }
    }

    // AUTH ACTIONS
    fun login(username: String, sandBoxPasswordHash: String) {
        viewModelScope.launch {
            authFeedback = null
            successFeedback = null
            val user = repository.getUser(username)
            if (user != null && user.passwordHash == sandBoxPasswordHash) {
                if (user.role == "DRIVER" && !user.isApproved) {
                    authFeedback = "Your driver account is registered but Document Verification is PENDING approval by admins."
                    currentUser = user // Allow limited pending view
                    currentScreen = AppScreen.DriverMain
                } else {
                    currentUser = user
                    successFeedback = "Welcome back, @$username!"
                    when (user.role) {
                        "CUSTOMER" -> currentScreen = AppScreen.CustomerMain
                        "DRIVER" -> currentScreen = AppScreen.DriverMain
                        "ADMIN" -> currentScreen = AppScreen.AdminMain
                    }
                }
            } else {
                authFeedback = "Invalid username or password. Free onboarding is open!"
            }
        }
    }

    fun registerCustomer(username: String, pass: String, phone: String) {
        viewModelScope.launch {
            authFeedback = null
            successFeedback = null
            if (username.isBlank() || pass.isBlank() || phone.isBlank()) {
                authFeedback = "All registration fields are required."
                return@launch
            }
            if (repository.getUser(username) != null) {
                authFeedback = "Username '@$username' already exists."
                return@launch
            }

            val newUser = UserEntity(
                username = username,
                passwordHash = pass,
                role = "CUSTOMER",
                phone = phone,
                operatingZones = "",
                documentUrl = null,
                isApproved = true
            )
            repository.insertUser(newUser)
            currentUser = newUser
            successFeedback = "Account Created! Welcome to Khapul."
            currentScreen = AppScreen.CustomerMain
        }
    }

    fun registerDriverStep1(username: String, pass: String, phone: String, zones: List<String>) {
        viewModelScope.launch {
            authFeedback = null
            if (username.isBlank() || pass.isBlank() || phone.isBlank() || zones.isEmpty()) {
                authFeedback = "Please fill credentials, phone, and select operating zones."
                return@launch
            }
            if (repository.getUser(username) != null) {
                authFeedback = "Driver username already exists."
                return@launch
            }
            // Temporarily store in flow states by creating user entity with step 2 requirements
            val zonesString = zones.joinToString(", ")
            val draftUser = UserEntity(
                username = username,
                passwordHash = pass,
                role = "DRIVER",
                phone = phone,
                operatingZones = zonesString,
                documentUrl = "",
                isApproved = false
            )
            currentUser = draftUser
            registrationStep = 2 // Move to upload docs
        }
    }

    fun registerDriverStep2(licFilename: String, permitFilename: String) {
        // Step 2: Documents upload simulation
        if (licFilename.isBlank() || permitFilename.isBlank()) {
            authFeedback = "Please mock upload BOTH items: national ID/License and Work Permit as requested."
            return
        }
        val currentDraft = currentUser ?: return
        currentUser = currentDraft.copy(documentUrl = "$licFilename | $permitFilename")
        registrationStep = 3 // Move to pending approval warning
        
        // Save to database as pending
        viewModelScope.launch {
            currentUser?.let { repository.insertUser(it) }
        }
    }

    fun registerDriverStep3Confirm() {
        registrationStep = 4 // Status complete
        currentScreen = AppScreen.DriverMain
    }

    fun logout() {
        currentUser = null
        currentScreen = AppScreen.Auth
        registrationStep = 1
        authFeedback = null
        successFeedback = null
    }

    // ADMIN ACTIONS
    fun adminApproveDriver(driverUsername: String) {
        viewModelScope.launch {
            repository.approveDriver(driverUsername)
            successFeedback = "Driver @$driverUsername successfully approved & verified! They can now accept rides."
        }
    }

    fun adminRejectDriver(driverUsername: String) {
        // Simple mock deletion or disapproval reset
        viewModelScope.launch {
            // Un-approve
            val u = repository.getUser(driverUsername)
            if (u != null) {
                repository.insertUser(u.copy(isApproved = false, documentUrl = null))
            }
        }
    }

    fun adminReplySupport(supportTicketId: Int, replyText: String) {
        viewModelScope.launch {
            if (replyText.isNotBlank()) {
                repository.replyToSupportTicket(supportTicketId, replyText)
            }
        }
    }

    // Create another Administrator account
    fun adminAddAdmin(username: String, pass: String, phone: String) {
        viewModelScope.launch {
            authFeedback = null
            successFeedback = null
            if (username.isBlank() || pass.isBlank() || phone.isBlank()) {
                authFeedback = "All admin creation fields are required."
                return@launch
            }
            if (repository.getUser(username) != null) {
                authFeedback = "Username '@$username' already exists."
                return@launch
            }
            val newAdmin = UserEntity(
                username = username,
                passwordHash = pass,
                role = "ADMIN",
                phone = phone,
                operatingZones = "All Areas",
                documentUrl = null,
                isApproved = true
            )
            repository.insertUser(newAdmin)
            successFeedback = "New Admin @$username has been successfully added to the system."
        }
    }

    // Change current administrator credentials
    fun updateAdminCredentials(newUsername: String, pass: String, phone: String) {
        viewModelScope.launch {
            authFeedback = null
            successFeedback = null
            val current = currentUser ?: return@launch
            if (newUsername.isBlank() || pass.isBlank() || phone.isBlank()) {
                authFeedback = "All credential fields are required."
                return@launch
            }
            // If username is changing, make sure the new one isn't taken
            if (newUsername != current.username && repository.getUser(newUsername) != null) {
                authFeedback = "Username '@$newUsername' is already taken."
                return@launch
            }

            val updatedAdmin = current.copy(
                username = newUsername,
                passwordHash = pass,
                phone = phone
            )

            if (newUsername != current.username) {
                // Since username is primary key, delete the old first
                repository.deleteUser(current.username)
            }
            repository.insertUser(updatedAdmin)
            currentUser = updatedAdmin
            successFeedback = "Your administrator credentials have been successfully updated!"
        }
    }

    // TICKET CREATION & ACTION PROCESS
    fun createCustomerTicket(
        type: String, // "RIDE" or "ERRAND"
        zone: String,
        startAddress: String,
        destAddress: String,
        isPriority: Boolean,
        errandItemsList: List<String>,
        passConfirm: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            authFeedback = null
            val customer = currentUser ?: return@launch
            if (customer.passwordHash != passConfirm) {
                authFeedback = "AUTHENTICATION CHALLENGE FAILED: Account password incorrect. Ticket locked for security."
                return@launch
            }
            if (startAddress.isBlank() || destAddress.isBlank() || zone.isBlank()) {
                authFeedback = "Please specify locations and corresponding operating zone."
                return@launch
            }

            val newTicket = TicketEntity(
                customerUsername = customer.username,
                type = type,
                zone = zone,
                startLocation = startAddress,
                destination = destAddress,
                status = "OPEN",
                isPriority = isPriority
            )

            val newId = repository.createTicket(newTicket, errandItemsList)
            
            // Send automated systems notification
            repository.sendMessage(
                ticketId = newId,
                senderName = "System",
                senderRole = "SYSTEM",
                text = "Ticket broadcasted to all verified drivers in zone: **$zone**. Status: OPEN."
            )

            successFeedback = "Ticket #$newId successfully created and broadcasted!"
            onSuccess()
        }
    }

    // NEGOTIATION ACTIONS
    fun offerTicketNegotiation(ticketId: Int, proposedPrice: Double) {
        val driver = currentUser ?: return
        viewModelScope.launch {
            repository.negotiate(ticketId, proposedPrice, driver.username)
            currentScreen = AppScreen.TicketChat(ticketId)
        }
    }

    fun customerAcceptOffer(ticketId: Int) {
        viewModelScope.launch {
            repository.acceptNegotiatedPrice(ticketId)
        }
    }

    fun sendMessageInTicket(ticketId: Int, text: String) {
        val sender = currentUser ?: return
        viewModelScope.launch {
            if (text.isNotBlank()) {
                repository.sendMessage(ticketId, sender.username, sender.role, text)
            }
        }
    }

    fun driverCloseAndLockJob(ticketId: Int) {
        val driver = currentUser ?: return
        viewModelScope.launch {
            repository.closeTicket(ticketId, driver.username)
        }
    }

    // ERRAND STEPS INTERACTIVE STATE
    fun toggleErrandItem(itemId: Int, isCompleted: Boolean) {
        viewModelScope.launch {
            repository.updateErrandItemCompletion(itemId, isCompleted)
        }
    }

    fun delegateErrandItemToPeer(itemId: Int, peerDriverUsername: String) {
        viewModelScope.launch {
            repository.delegateErrandItem(itemId, peerDriverUsername)
        }
    }
    
    fun delegateErrandItemWithParams(ticketId: Int, itemId: Int, peerDriverUsername: String, itemDescription: String) {
        viewModelScope.launch {
            repository.delegateErrandItem(itemId, peerDriverUsername)
            repository.sendMessage(
                ticketId = ticketId,
                senderName = "System",
                senderRole = "SYSTEM",
                text = "CAR PROBLEM RESOLUTION: Item \"$itemDescription\" delegated to @$peerDriverUsername (Safe & Verified Chain of Custody maintained)."
            )
        }
    }

    // RATINGS
    fun rateUser(ticketId: Int, reviewer: String, reviewee: String, stars: Int, comment: String) {
        viewModelScope.launch {
            repository.submitReview(ticketId, reviewer, reviewee, stars, comment)
            successFeedback = "Rating submitted! Thank you for keeping Khapul safe and reliable."
        }
    }

    // SUPPORT
    fun submitComplainOrSuggestion(message: String) {
        val user = currentUser ?: return
        viewModelScope.launch {
            if (message.isNotBlank()) {
                repository.submitSupportTicket(user.username, message)
                successFeedback = "Your appeal was submitted! The dedicated Admin team will evaluate soon."
            }
        }
    }
}
