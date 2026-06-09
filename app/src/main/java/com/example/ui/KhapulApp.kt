package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KhapulApp(viewModel: KhapulViewModel) {
    val currentScreen = viewModel.currentScreen
    val currentUser = viewModel.currentUser

    val feedbackError = viewModel.authFeedback
    val feedbackSuccess = viewModel.successFeedback

    // SnackBar scaffold state
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(feedbackError) {
        if (feedbackError != null) {
            snackbarHostState.showSnackbar(feedbackError)
            viewModel.authFeedback = null
        }
    }

    LaunchedEffect(feedbackSuccess) {
        if (feedbackSuccess != null) {
            snackbarHostState.showSnackbar(feedbackSuccess)
            viewModel.successFeedback = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = "Khapul Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "KHAPUL",
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif,
                            color = MaterialTheme.colorScheme.onBackground,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "COMMUNITY TRANSIT",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    if (currentUser != null) {
                        IconButton(
                            onClick = { viewModel.currentScreen = AppScreen.SupportChat },
                            modifier = Modifier.testTag("action_support_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SupportAgent,
                                contentDescription = "Support Appeal Inbox"
                            )
                        }
                        
                        Text(
                            text = "@${currentUser.username}",
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        IconButton(
                            onClick = { viewModel.logout() },
                            modifier = Modifier.testTag("logout_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExitToApp,
                                contentDescription = "Log Out"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "ScreenTransition"
            ) { screen ->
                when (screen) {
                    is AppScreen.Auth -> AuthScreen(viewModel)
                    is AppScreen.CustomerMain -> CustomerDashboard(viewModel)
                    is AppScreen.DriverMain -> DriverDashboard(viewModel)
                    is AppScreen.AdminMain -> AdminDashboard(viewModel)
                    is AppScreen.SupportChat -> SupportChatScreen(viewModel)
                    is AppScreen.TicketChat -> RealTimeTicketNegotiationChat(viewModel, screen.ticketId)
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// SECURE USER ONBOARDING (SUPABASE STORAGE & WARNINGS MOCKING CONTROLS)
// -----------------------------------------------------------------------------
@Composable
fun AuthScreen(viewModel: KhapulViewModel) {
    var isRegistering by remember { mutableStateOf(false) }
    var isDriverMode by remember { mutableStateOf(false) }

    var usernameInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var phoneInput by remember { mutableStateOf("") }

    // Multi-zone selector list for drivers
    val selectedZones = remember { mutableStateListOf<String>() }

    // Document mock uploading states
    var uploadedIDFilename by remember { mutableStateOf("") }
    var uploadedPermitFilename by remember { mutableStateOf("") }
    var isUploadingDoc by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Security Alert",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "SECURITY WARNING",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = "Registration is free. Never share your OTPs or pay unofficial fees.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isRegistering) "Create Community Account" else "Welcome to Khapul",
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = if (isRegistering) "Safe & Verified localized fulfillment network" else "Connect with verified local drivers instantly",
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            color = TextMuted,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        if (!isRegistering) {
            // LOGIN SCREEN
            OutlinedTextField(
                value = usernameInput,
                onValueChange = { usernameInput = it },
                label = { Text("Enter Username") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("username_input"),
                colors = textFieldColors()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = passwordInput,
                onValueChange = { passwordInput = it },
                label = { Text("Authentication Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("password_input"),
                colors = textFieldColors()
            )
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.login(usernameInput, passwordInput) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("login_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    "LOG IN TO DISPATCH CENTER",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = { isRegistering = true },
                modifier = Modifier.testTag("switch_register_btn")
            ) {
                Text("Don't have an account? Sign Up for Free !", color = MaterialTheme.colorScheme.primary)
            }

        } else {
            // REGISTRATION MULTI-STEP WIZARD
            if (!isDriverMode) {
                // Register type selection or customer info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FilterChip(
                        selected = !isDriverMode,
                        onClick = { isDriverMode = false },
                        label = { Text("Customer Account") },
                        leadingIcon = { if (!isDriverMode) Icon(Icons.Default.Check, contentDescription = null) }
                    )
                    FilterChip(
                        selected = isDriverMode,
                        onClick = { isDriverMode = true },
                        label = { Text("Verify Driver Protocol") },
                        leadingIcon = { if (isDriverMode) Icon(Icons.Default.Check, contentDescription = null) }
                    )
                }

                // CUSTOMER REGISTER FORM
                OutlinedTextField(
                    value = usernameInput,
                    onValueChange = { usernameInput = it },
                    label = { Text("Choose Username") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("Create Private Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = { phoneInput = it },
                    label = { Text("Mobile Contact Phone") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors()
                )

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.registerCustomer(usernameInput, passwordInput, phoneInput) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("submit_customer_reg"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("REGISTER & DISPATCH NOW", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
            } else {
                // DRIVER REGISTRATION PROTOCOL (3 EASY STEPS TO REGISTER)
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Wizard progress visualizer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (step in 1..4) {
                            val active = viewModel.registrationStep >= step
                            val color = if (active) MaterialTheme.colorScheme.primary else TextMuted
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(color),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    step.toString(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) MaterialTheme.colorScheme.onPrimary else Color.White
                                )
                            }
                            if (step < 4) {
                                Box(
                                    modifier = Modifier
                                        .height(2.dp)
                                        .width(36.dp)
                                        .background(if (viewModel.registrationStep > step) MaterialTheme.colorScheme.primary else TextMuted)
                                )
                            }
                        }
                    }

                    Text(
                        text = when (viewModel.registrationStep) {
                            1 -> "Step 1: Account & Operating Areas"
                            2 -> "Step 2: Secure Doc Upload (Supabase storage)"
                            3 -> "Step 3: Verification Waitlist Status"
                            else -> "Step 4: Verification Approved! Start Driving"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    when (viewModel.registrationStep) {
                        1 -> {
                            // Driver Account Setup & Zones Checklist
                            OutlinedTextField(
                                value = usernameInput,
                                onValueChange = { usernameInput = it },
                                label = { Text("Choose Driver Handle (Username)") },
                                leadingIcon = { Icon(Icons.Default.DriveEta, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = textFieldColors()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = { Text("Private Dispatch Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = textFieldColors()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = phoneInput,
                                onValueChange = { phoneInput = it },
                                label = { Text("Active Driver Phone Number") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = textFieldColors()
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Declare Your Operating Zones/Neighborhoods:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Tickets will route to you strictly based on these selected sectors.",
                                fontSize = 11.sp,
                                color = TextMuted,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            viewModel.availableZones.forEach { zone ->
                                val active = selectedZones.contains(zone)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (active) selectedZones.remove(zone) else selectedZones.add(zone)
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = active,
                                        onCheckedChange = {
                                            if (active) selectedZones.remove(zone) else selectedZones.add(zone)
                                        }
                                    )
                                    Text(zone, fontSize = 13.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    viewModel.registerDriverStep1(usernameInput, passwordInput, phoneInput, selectedZones)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("NEXT: UPLOAD DOCUMENTS", fontWeight = FontWeight.Bold)
                            }
                        }
                        2 -> {
                            // Driver Verification Docs Storage Uploading Mock
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "Driver Custody Verification Protocol",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        "To ensure passenger safety, drivers must upload valid verification credentials. Files are stored securely on the Supabase storage vault.",
                                        fontSize = 11.sp,
                                        color = TextMuted,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                                    )

                                    // DOC 1: ID
                                    Text("1. National ID Card / Work Permit document", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Row(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = {
                                                isUploadingDoc = true
                                                uploadedIDFilename = "national_license_${usernameInput}.pdf"
                                                isUploadingDoc = false
                                            }
                                        ) {
                                            Row {
                                                Icon(Icons.Default.CloudUpload, contentDescription = null)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Tap to Attach")
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = if (uploadedIDFilename.isEmpty()) "No file chosen" else uploadedIDFilename,
                                            fontSize = 11.sp,
                                            color = if (uploadedIDFilename.isEmpty()) TextMuted else GreenVerified
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // DOC 2: Work permit
                                    Text("2. Valid Taxi Transport Permit or Driver License", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Row(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = {
                                                isUploadingDoc = true
                                                uploadedPermitFilename = "permit_cert_${usernameInput}.jpg"
                                                isUploadingDoc = false
                                            }
                                        ) {
                                            Row {
                                                Icon(Icons.Default.CloudUpload, contentDescription = null)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Tap to Attach")
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = if (uploadedPermitFilename.isEmpty()) "No file chosen" else uploadedPermitFilename,
                                            fontSize = 11.sp,
                                            color = if (uploadedPermitFilename.isEmpty()) TextMuted else GreenVerified
                                        )
                                    }

                                    if (isUploadingDoc) {
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = {
                                            viewModel.registerDriverStep2(uploadedIDFilename, uploadedPermitFilename)
                                        },
                                        enabled = uploadedIDFilename.isNotEmpty() && uploadedPermitFilename.isNotEmpty(),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("SUBMIT DOCUMENTS TO ADMINS")
                                    }
                                }
                            }
                        }
                        3 -> {
                            // Driver Verification Pending
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.PendingActions,
                                        contentDescription = "Pending Approval",
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "APPLICATION SUBMITTED SUCCESS!",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Your document file chain is locked in sandbox Supabase storage, waiting for active manual review.\n\n" +
                                                "You remain 'PENDING' until approved verbally/digitally. You can inspect the interface now, but cannot lock in tickets yet.",
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Button(
                                        onClick = { viewModel.registerDriverStep3Confirm() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("PROCEED TO DRIVER INSPECT DECK")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = { isRegistering = false },
                modifier = Modifier.testTag("switch_login_btn")
            ) {
                Text("Already registered? Log In Instead", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// -----------------------------------------------------------------------------
// CUSTOMER AREA: ZONE CREATOR, CANVAS OSM-STYLE PIN MAP & CHALLENGE
// -----------------------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomerDashboard(viewModel: KhapulViewModel) {
    val myTickets by viewModel.allTickets.collectAsStateWithLifecycle()
    val username = viewModel.currentUser?.username ?: ""
    val customerTickets = myTickets.filter { it.customerUsername == username }

    var selectedTab by remember { mutableStateOf(0) } // 0: Open Ticket, 1: History

    // Form inputs
    var isErrandMode by remember { mutableStateOf(false) }
    var selectedZone by remember { mutableStateOf("") }
    var startLocationText by remember { mutableStateOf("") }
    var destinationText by remember { mutableStateOf("") }
    var isUrgentPriority by remember { mutableStateOf(false) }

    // Itemized Errand tasks
    var errandItemInputText by remember { mutableStateOf("") }
    val itemizedTaskList = remember { mutableStateListOf<String>() }

    // Auth verification challenge dialog
    var showChallengeDialog by remember { mutableStateOf(false) }
    var challengePasswordState by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dim)
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Open Ticket (Rides/Errands)") },
                icon = { Icon(Icons.Default.AddLocation, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("My Active/Past Jobs") },
                icon = { Icon(Icons.Default.History, contentDescription = null) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedTab == 0) {
            // NEW DISPATCH TICKET FORM
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Button(
                    onClick = { isErrandMode = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isErrandMode) MaterialTheme.colorScheme.primary else SurfaceCard
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mode_ride_btn")
                ) {
                    Icon(Icons.Default.DirectionsCar, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("TAXI RIDE", color = if (!isErrandMode) MaterialTheme.colorScheme.onPrimary else TextLight)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = { isErrandMode = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isErrandMode) MaterialTheme.colorScheme.primary else SurfaceCard
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mode_errand_btn")
                ) {
                    Icon(Icons.Default.ShoppingBag, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ERRAND SERVICE", color = if (isErrandMode) MaterialTheme.colorScheme.onPrimary else TextLight)
                }
            }

            Text(
                "1. SELECT TRIP LOCATION PIN (OSM Leaflet Engine)",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // INTERACTIVE MAP BOX
            InteractiveMapView(
                onLocationSelected = { coordinates, zone, name ->
                    selectedZone = zone
                    if (startLocationText.isEmpty()) {
                        startLocationText = "$name ($coordinates)"
                    } else {
                        destinationText = "$name ($coordinates)"
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Text Inputs
            OutlinedTextField(
                value = startLocationText,
                onValueChange = { startLocationText = it },
                label = { Text("Pickup Location Pin Address") },
                leadingIcon = { Icon(Icons.Default.MyLocation, contentDescription = null, tint = GreenVerified) },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (startLocationText.isNotEmpty()) {
                        IconButton(onClick = { startLocationText = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                colors = textFieldColors()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = destinationText,
                onValueChange = { destinationText = it },
                label = { Text("Destination / Drop-off Pin Address") },
                leadingIcon = { Icon(Icons.Default.PinDrop, contentDescription = null, tint = RedAlert) },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (destinationText.isNotEmpty()) {
                        IconButton(onClick = { destinationText = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                colors = textFieldColors()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Zone Selection readout
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Operational Fulfillment Zone: ", fontSize = 12.sp)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = if (selectedZone.isEmpty()) "Tap Map to Resolve Zone" else selectedZone,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isErrandMode) {
                // ITEMIZED ERRAND & DELEGATION SYSTEM
                Text(
                    "2. ITEMIZED ERRAND TASKING (MANDATORY REQUIREMENT)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                ) {
                    Column(modifier = Modifier.padding(8.dim)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = errandItemInputText,
                                onValueChange = { errandItemInputText = it },
                                label = { Text("Add specific purchase / task item individually") },
                                modifier = Modifier.weight(1f),
                                colors = textFieldColors()
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = {
                                    if (errandItemInputText.isNotBlank()) {
                                        itemizedTaskList.add(errandItemInputText)
                                        errandItemInputText = ""
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Item", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }

                        if (itemizedTaskList.isEmpty()) {
                            Text(
                                "No errand items added yet. You must list each item separately for safe chain of custody.",
                                fontSize = 11.sp,
                                color = TextMuted,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            Text(
                                "List of Errand Items:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            itemizedTaskList.forEachIndexed { index, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Circle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(6.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("${index + 1}. $item", fontSize = 12.sp)
                                    }
                                    IconButton(onClick = { itemizedTaskList.removeAt(index) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = RedAlert, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Errand urgent prioritizing
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("1-Day Urgency Priority?", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Fast-track courier matching. Default is 1 day.", fontSize = 11.sp, color = TextMuted)
                        }
                        Switch(
                            checked = isUrgentPriority,
                            onCheckedChange = { isUrgentPriority = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // CREATION DISPATCH ACTION
            Button(
                onClick = {
                    if (isErrandMode && itemizedTaskList.isEmpty()) {
                        viewModel.authFeedback = "You must list at least one specific errand item before dispatching."
                    } else if (startLocationText.isEmpty() || destinationText.isEmpty() || selectedZone.isEmpty()) {
                        viewModel.authFeedback = "Pins must be resolved into start and destination areas."
                    } else {
                        // Pop authentication challenge
                        showChallengeDialog = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("broadcast_ticket_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Podcasts, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("BROADCAST TICKET TO DRIVERS", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimary)
            }

        } else {
            // MY TICKETS HISTORY VIEW
            if (customerTickets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ConfirmationNumber, contentDescription = null, tint = TextMuted, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No active or historic transit tickets found.", fontSize = 13.sp, color = TextMuted)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 600.dp)
                ) {
                    items(customerTickets) { ticket ->
                        CustomerTicketCard(ticket, viewModel)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        // AUTHENTICATION CHALLENGE DIALOG MODAL
        if (showChallengeDialog) {
            AlertDialog(
                onDismissRequest = { showChallengeDialog = false },
                title = { Text("Private PIN Challenge") },
                text = {
                    Column {
                        Text(
                            text = "To protect your account from accidental or fraudulent trip broadcasts, please verify your chosen password credentials.",
                            fontSize = 11.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        OutlinedTextField(
                            value = challengePasswordState,
                            onValueChange = { challengePasswordState = it },
                            label = { Text("Confirm Sign-In Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = textFieldColors()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showChallengeDialog = false
                            viewModel.createCustomerTicket(
                                type = if (isErrandMode) "ERRAND" else "RIDE",
                                zone = selectedZone,
                                startAddress = startLocationText,
                                destAddress = destinationText,
                                isPriority = isUrgentPriority,
                                errandItemsList = itemizedTaskList.toList(),
                                passConfirm = challengePasswordState,
                                onSuccess = {
                                    // Reset forms
                                    startLocationText = ""
                                    destinationText = ""
                                    selectedZone = ""
                                    itemizedTaskList.clear()
                                    isUrgentPriority = false
                                }
                            )
                            challengePasswordState = ""
                        }
                    ) {
                        Text("AUTHENTICATE & BROADCAST")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showChallengeDialog = false
                        challengePasswordState = ""
                    }) {
                        Text("CANCEL")
                    }
                }
            )
        }
    }
}

@Composable
fun CustomerTicketCard(ticket: TicketEntity, viewModel: KhapulViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (ticket.type == "RIDE") Icons.Default.DirectionsCar else Icons.Default.ShoppingBag,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ticket #${ticket.id} (${ticket.type})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when (ticket.status) {
                            "OPEN" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            "NEGOTIATING" -> AccentOrange.copy(alpha = 0.15f)
                            "ACTIVE" -> GreenVerified.copy(alpha = 0.15f)
                            else -> TextMuted.copy(alpha = 0.15f)
                        }
                    )
                ) {
                    Text(
                        text = ticket.status,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (ticket.status) {
                            "OPEN" -> MaterialTheme.colorScheme.primary
                            "NEGOTIATING" -> AccentOrange
                            "ACTIVE" -> GreenVerified
                            else -> TextMuted
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, tint = GreenVerified, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pin Location: ${ticket.startLocation}", fontSize = 11.sp, color = TextLight)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PinDrop, contentDescription = null, tint = RedAlert, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Destination: ${ticket.destination}", fontSize = 11.sp, color = TextLight)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Public, contentDescription = null, tint = TextMuted, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Zone: ${ticket.zone}", fontSize = 11.sp, color = TextMuted)
                }
            }

            if (ticket.isPriority) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = AccentOrange.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FlashOn, contentDescription = "Urgent Priority", tint = AccentOrange, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("1-Day Urgent Prioritized Request", fontSize = 10.sp, color = AccentOrange, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Price Negotiation Loop view
            if (ticket.status == "OPEN") {
                Text(
                    text = "Broadcasting... Drivers are evaluating in private chat.",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            } else if (ticket.status == "NEGOTIATING") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Driver Bidding Offer: @${ticket.assignedDriverUsername ?: ""}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "$${String.format("%.2f", ticket.agreedPrice)}",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row {
                            Button(
                                onClick = { viewModel.customerAcceptOffer(ticket.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = GreenVerified),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("ACCEPT PRICE OFFER")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.currentScreen = AppScreen.TicketChat(ticket.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("NEGOTIATE CHAT")
                            }
                        }
                    }
                }
            } else if (ticket.status == "ACTIVE") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Assigned: @${ticket.assignedDriverUsername} - Locked at $${String.format("%.2f", ticket.agreedPrice)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = GreenVerified
                    )
                    IconButton(
                        onClick = { viewModel.currentScreen = AppScreen.TicketChat(ticket.id) }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Negotiation chat board", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            } else if (ticket.status == "CLOSED") {
                // RATING PORTAL POST-TRANSACTION
                var starsInput by remember { mutableStateOf(5) }
                var commentInput by remember { mutableStateOf("") }
                var hasSubmittedRating by remember { mutableStateOf(false) }

                if (!hasSubmittedRating) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Rate and Review Driver @${ticket.assignedDriverUsername ?: ""}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Row {
                                for (star in 1..5) {
                                    IconButton(onClick = { starsInput = star }, modifier = Modifier.size(28.dp)) {
                                        Icon(
                                            imageVector = if (starsInput >= star) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = commentInput,
                                    onValueChange = { commentInput = it },
                                    placeholder = { Text("Comment on safety and reliability...") },
                                    modifier = Modifier.weight(1f),
                                    colors = textFieldColors()
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = {
                                        viewModel.rateUser(
                                            ticketId = ticket.id,
                                            reviewer = ticket.customerUsername,
                                            reviewee = ticket.assignedDriverUsername ?: "",
                                            stars = starsInput,
                                            comment = commentInput
                                        )
                                        hasSubmittedRating = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("SUBMIT")
                                }
                            }
                        }
                    }
                } else {
                    Text("Safe Journey Complete! 2-Way Review logged.", fontSize = 11.sp, color = GreenVerified)
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// DRIVER AREA: AREA MATCHER, ACCEPTED TICKETS, ITEM PROGRESS & PEER DELEGATOR
// -----------------------------------------------------------------------------
@Composable
fun DriverDashboard(viewModel: KhapulViewModel) {
    val currentDriver = viewModel.currentUser ?: return
    val allTicketsList by viewModel.allTickets.collectAsStateWithLifecycle()

    // 1. Status limits
    if (!currentDriver.isApproved) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.LockClock, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(80.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "VERIFICATION PENDING REVIEW",
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Your document file chains (License & work permit) have been uploaded to local Supabase secure mocks and are in queue.",
                        fontSize = 12.sp,
                        color = TextLight,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Operating Zones Registered: ${currentDriver.operatingZones}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { viewModel.logout() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("BACK TO SIGN IN SCREEN")
            }
        }
        return
    }

    // Driver is pre-approved! Filter available tickets by zone matching
    val driverZones = currentDriver.operatingZones.split(", ").map { it.trim() }

    // Matches strictly Zone-Based Broadcast matching
    val matchOpenTickets = allTicketsList.filter { ticket ->
        ticket.status == "OPEN" && driverZones.contains(ticket.zone)
    }

    val activeLockedTickets = allTicketsList.filter { ticket ->
        ticket.assignedDriverUsername == currentDriver.username && ticket.status != "CLOSED"
    }

    var selectedTab by remember { mutableStateOf(0) } // 0: Available matching, 1: Active locked

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GreenVerified, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("VERIFIED DRIVER ACTIVE STATUS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = GreenVerified)
                    Text("Operational inside zones: ${currentDriver.operatingZones}", fontSize = 11.sp, color = TextLight)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Zone Broadcasts (${matchOpenTickets.size})") },
                icon = { Icon(Icons.Default.Podcasts, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Active Jobs (${activeLockedTickets.size})") },
                icon = { Icon(Icons.Default.DriveEta, contentDescription = null) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedTab == 0) {
            // MATCHED ZONE BROADCAST TICKETS
            if (matchOpenTickets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Inbox, contentDescription = null, tint = TextMuted, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No open broadcasting tickets inside your declared zones right now.",
                            fontSize = 12.sp,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                matchOpenTickets.forEach { ticket ->
                    DriverBroadcastTicketCard(ticket, viewModel)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        } else {
            // ACTIVE TAKEN JOBS WITH ITEM BREAKDOWNS & PEER DELEGATORS
            if (activeLockedTickets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No active locked transit jobs right now.", fontSize = 12.sp, color = TextMuted)
                }
            } else {
                activeLockedTickets.forEach { ticket ->
                    DriverActiveTaskCard(ticket, viewModel)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun DriverBroadcastTicketCard(ticket: TicketEntity, viewModel: KhapulViewModel) {
    var bidPriceInput by remember { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${ticket.type} Service (Zone matches, Broadcasted!)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (ticket.isPriority) {
                    Card(colors = CardDefaults.cardColors(containerColor = AccentOrange.copy(alpha = 0.2f))) {
                        Text(
                            "PRIORITY",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentOrange,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column {
                Text("Customer Profile: @${ticket.customerUsername}", fontSize = 11.sp, color = TextMuted)
                Text("Retrieve Location: ${ticket.startLocation}", fontSize = 11.sp)
                Text("Deliver Destination: ${ticket.destination}", fontSize = 11.sp)
                Text("Zone Area: ${ticket.zone}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Enter Negotiation Mode directly with client
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = bidPriceInput,
                    onValueChange = { bidPriceInput = it },
                    placeholder = { Text("Price bid ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = textFieldColors()
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val dPrice = bidPriceInput.toDoubleOrNull()
                        if (dPrice != null && dPrice > 0) {
                            viewModel.offerTicketNegotiation(ticket.id, dPrice)
                        } else {
                            viewModel.authFeedback = "Enter valid numeric price bid to open private negotiation chat."
                        }
                    },
                    modifier = Modifier.testTag("submit_bid_btn")
                ) {
                    Text("START BARGAIN")
                }
            }
        }
    }
}

@Composable
fun DriverActiveTaskCard(ticket: TicketEntity, viewModel: KhapulViewModel) {
    val errandItemsFlowByTicket = viewModel.repository.getErrandItemsForTicket(ticket.id).collectAsStateWithLifecycle(emptyList())
    val errandItems = errandItemsFlowByTicket.value

    val otherDrivers by viewModel.allDrivers.collectAsStateWithLifecycle()
    // Peer matching list for delegation
    val peerDriversList = otherDrivers.filter { it.username != viewModel.currentUser?.username && it.isApproved }

    var showDelegationTargetItem by remember { mutableStateOf<ErrandItemEntity?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACTIVE JOB: Ticket #${ticket.id}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = GreenVerified
                )
                Card(colors = CardDefaults.cardColors(containerColor = GreenVerified.copy(alpha = 0.15f))) {
                    Text(
                        text = "Negotiated Price: $${String.format("%.2f", ticket.agreedPrice)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = GreenVerified,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text("Client: @${ticket.customerUsername}", fontSize = 11.sp)
            Text("Pickup Point: ${ticket.startLocation}", fontSize = 11.sp, color = TextLight)
            Text("Deliver Destination: ${ticket.destination}", fontSize = 11.sp, color = TextLight)

            if (ticket.type == "ERRAND") {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Itemized Checklist:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Fulfill items one-by-one. Delegation chain transfer keeps custody safe.",
                    fontSize = 10.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                errandItems.forEach { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = item.isCompleted,
                                    onCheckedChange = { viewModel.toggleErrandItem(item.id, it) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = item.description,
                                        fontSize = 12.sp,
                                        color = if (item.isCompleted) TextMuted else TextLight
                                    )
                                    if (item.delegatedDriverUsername != null) {
                                        Text(
                                            "Delegated custody to verified driver: @${item.delegatedDriverUsername}",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            // Driver-Led Delegation Button
                            if (!item.isCompleted && item.delegatedDriverUsername == null) {
                                IconButton(onClick = { showDelegationTargetItem = item }) {
                                    Icon(
                                        Icons.Default.GroupAdd,
                                        contentDescription = "Delegate item to peer",
                                        tint = AccentOrange,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row {
                Button(
                    onClick = { viewModel.currentScreen = AppScreen.TicketChat(ticket.id) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("CHAT WITH CLIENT", fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Close and Lock complete button
                Button(
                    onClick = { viewModel.driverCloseAndLockJob(ticket.id) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenVerified)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("CLOSE & REALIZE", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }

    // PEER DELEGATION CHOSEN OVERLAY DIALOG MODAL
    if (showDelegationTargetItem != null) {
        val activeItem = showDelegationTargetItem!!
        AlertDialog(
            onDismissRequest = { showDelegationTargetItem = null },
            title = { Text("Delegate Errand Custody") },
            text = {
                Column {
                    Text(
                        text = "CAR PROBLEM RESOLUTION: You can safely delegate specific uncompleted items to verified drivers in your community group. This maintains the custody chain.",
                        fontSize = 11.sp,
                        color = AccentOrange,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text("Item: \"${activeItem.description}\"", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (peerDriversList.isEmpty()) {
                        Text("No other verified peer drivers available in session.", fontSize = 11.sp, color = TextMuted)
                    } else {
                        Text("Select Peer Driver to transfer itemized custody:", fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        peerDriversList.forEach { peer ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.delegateErrandItemWithParams(
                                            ticketId = ticket.id,
                                            itemId = activeItem.id,
                                            peerDriverUsername = peer.username,
                                            itemDescription = activeItem.description
                                        )
                                        showDelegationTargetItem = null
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.DriveEta, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("@${peer.username} (Operating: ${peer.operatingZones})", fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDelegationTargetItem = null }) {
                    Text("CANCEL")
                }
            }
        )
    }
}

// -----------------------------------------------------------------------------
// CHAT NEGOTIATION LOOP WINDOW & SETTLED AGREEMENT
// -----------------------------------------------------------------------------
@Composable
fun RealTimeTicketNegotiationChat(viewModel: KhapulViewModel, ticketId: Int) {
    val liveTicketFlow = viewModel.repository.getTicketByIdFlow(ticketId).collectAsStateWithLifecycle(null)
    val ticket = liveTicketFlow.value ?: return

    val messages by viewModel.repository.getMessagesForTicket(ticketId).collectAsStateWithLifecycle(emptyList())

    var chatMessageInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Ticket Header Area info
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            viewModel.currentScreen = if (viewModel.currentUser?.role == "DRIVER") AppScreen.DriverMain else AppScreen.CustomerMain
                        }
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Return to Dashboard")
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Ticket Negotiation Screen",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("Zones: ${ticket.zone} | Safe & Tracked Grid", fontSize = 10.sp, color = TextMuted)
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = GreenVerified.copy(alpha = 0.15f))
                    ) {
                        Text(
                            text = "$${String.format("%.2f", ticket.agreedPrice)}",
                            modifier = Modifier.padding(8.dp, 4.dp),
                            color = GreenVerified,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(4.dp))

                Text("Start point: ${ticket.startLocation}", fontSize = 11.sp)
                Text("Deliver drop: ${ticket.destination}", fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Message List viewport
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            reverseLayout = false
        ) {
            items(messages) { msg ->
                val bSystem = msg.senderRole == "SYSTEM"
                val bMine = msg.senderName == viewModel.currentUser?.username

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = if (bSystem) Arrangement.Center else if (bMine) Arrangement.End else Arrangement.Start
                ) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (bSystem) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                            else if (bMine) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else SurfaceCard
                        ),
                        border = if (bMine) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) else null,
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            if (!bSystem) {
                                Text(
                                    text = "@${msg.senderName}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                            Text(text = msg.messageText, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Bottom Message Send Input and price actions
        Column {
            if (ticket.status == "NEGOTIATING" && viewModel.currentUser?.role == "CUSTOMER") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Driver Proposed Price Target", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("$${String.format("%.2f", ticket.agreedPrice)}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        }
                        Button(
                            onClick = { viewModel.customerAcceptOffer(ticket.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenVerified)
                        ) {
                            Text("ACCEPT & LOCK TRIP")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = chatMessageInput,
                    onValueChange = { chatMessageInput = it },
                    placeholder = { Text("Negotiate details / ask description...") },
                    modifier = Modifier.weight(1f),
                    colors = textFieldColors()
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (chatMessageInput.isNotBlank()) {
                            viewModel.sendMessageInTicket(ticket.id, chatMessageInput)
                            chatMessageInput = ""
                        }
                    },
                    modifier = Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// COMMUNITY STANDARDS & ADMIN OVERSIGHT (DRIVER REVIEWS, SYS PANEL, SUPPORT CHAT)
// -----------------------------------------------------------------------------
@Composable
fun AdminDashboard(viewModel: KhapulViewModel) {
    val pending by viewModel.pendingDrivers.collectAsStateWithLifecycle()
    val drivers by viewModel.allDrivers.collectAsStateWithLifecycle()
    val tickets by viewModel.allTickets.collectAsStateWithLifecycle()
    val queries by viewModel.supportTickets.collectAsStateWithLifecycle()
    val admins by viewModel.allAdmins.collectAsStateWithLifecycle()

    var selectedAdminSection by remember { mutableStateOf(0) } // 0: Approvals, 1: Support Inbox, 2: Analytics, 3: Configure Admins, 4: My Account

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Khapul Administrative Portal", fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        Text("Keep the verified community network moving safely", fontSize = 12.sp, color = TextMuted)

        Spacer(modifier = Modifier.height(16.dp))

        // Analytics Row Cards
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceCard), modifier = Modifier.weight(1f).padding(4.dp)) {
                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Verified Drivers", fontSize = 10.sp, color = TextMuted)
                    Text(drivers.filter { it.isApproved }.size.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GreenVerified)
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceCard), modifier = Modifier.weight(1f).padding(4.dp)) {
                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Pending Audit", fontSize = 10.sp, color = TextMuted)
                    Text(pending.size.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceCard), modifier = Modifier.weight(1f).padding(4.dp)) {
                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total Tickets", fontSize = 10.sp, color = TextMuted)
                    Text(tickets.size.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ScrollableTabRow(
            selectedTabIndex = selectedAdminSection,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 0.dp
        ) {
            Tab(
                selected = selectedAdminSection == 0,
                onClick = { selectedAdminSection = 0 },
                text = { Text("ID Approvals (${pending.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedAdminSection == 1,
                onClick = { selectedAdminSection = 1 },
                text = { Text("Complaints (${queries.filter { it.replyText == null }.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedAdminSection == 2,
                onClick = { selectedAdminSection = 2 },
                text = { Text("Live Records", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedAdminSection == 3,
                onClick = { selectedAdminSection = 3 },
                text = { Text("Configure Admins (${admins.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedAdminSection == 4,
                onClick = { selectedAdminSection = 4 },
                text = { Text("My Account", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedAdminSection == 0) {
            // DRIVER VERIFICATION DOCUMENT APPROVALS
            if (pending.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("All driver verification queues are fully audited!", fontSize = 12.sp, color = GreenVerified)
                }
            } else {
                pending.forEach { d ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("@${d.username}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Row {
                                    IconButton(onClick = { viewModel.adminApproveDriver(d.username) }) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Approve Doc", tint = GreenVerified, modifier = Modifier.size(28.dp))
                                    }
                                    IconButton(onClick = { viewModel.adminRejectDriver(d.username) }) {
                                        Icon(Icons.Default.Cancel, contentDescription = "Reject", tint = RedAlert, modifier = Modifier.size(28.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Operating Zones: ${d.operatingZones}", fontSize = 11.sp)
                            Text("Mobile: ${d.phone}", fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
                            ) {
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.FilePresent, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text("Supabase Storage Documents Attached:", fontSize = 9.sp, color = TextMuted)
                                        Text(d.documentUrl ?: "No credential string", fontSize = 10.sp, color = TextLight, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (selectedAdminSection == 1) {
            // ADMIN APPEALS & COMPLAINTS WORKSPACE
            if (queries.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No suggests or complaints registered.", fontSize = 12.sp, color = TextMuted)
                }
            } else {
                queries.forEach { q ->
                    var adminResponseText by remember { mutableStateOf("") }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Complainant: @${q.username}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                                Text(SimpleDateFormat("MMM dd HH:mm", Locale.getDefault()).format(Date(q.timestamp)), fontSize = 10.sp, color = TextMuted)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "\"${q.messageText}\"", fontSize = 12.sp, style = MaterialTheme.typography.bodyMedium)

                            Spacer(modifier = Modifier.height(8.dp))
                            if (q.replyText != null) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Official Resolution Reply: ${q.replyText}",
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(8.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = adminResponseText,
                                        onValueChange = { adminResponseText = it },
                                        placeholder = { Text("Official resolution reply...") },
                                        modifier = Modifier.weight(1f),
                                        colors = textFieldColors()
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Button(
                                        onClick = {
                                            viewModel.adminReplySupport(q.id, adminResponseText)
                                            adminResponseText = ""
                                        }
                                    ) {
                                        Text("REPLY")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (selectedAdminSection == 2) {
            // LIVE RECORDS TAB
            var selectedSubSection by remember { mutableStateOf(0) } // 0: All Drivers, 1: All Tickets

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Community Drivers List", "Platform Transit Tickets").forEachIndexed { idx, label ->
                    val isSelected = selectedSubSection == idx
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else SurfaceCard
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedSubSection = idx }
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else TextLight,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            if (selectedSubSection == 0) {
                if (drivers.isEmpty()) {
                    Text("No registered drivers on the platform yet.", fontSize = 12.sp, color = TextMuted)
                } else {
                    drivers.forEach { drv ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("@${drv.username}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        if (drv.isApproved) {
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = GreenVerified.copy(alpha = 0.15f))
                                            ) {
                                                Text(
                                                    "VERIFIED",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = GreenVerified,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        } else {
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = AccentOrange.copy(alpha = 0.15f))
                                            ) {
                                                Text(
                                                    "PENDING ID",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AccentOrange,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("${drv.rating} (${drv.ratingCount})", fontSize = 11.sp, color = TextLight)
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Operating Zones: ${drv.operatingZones.ifBlank { "None Specified" }}", fontSize = 11.sp, color = TextLight)
                                Text("Contact Mobile: ${drv.phone}", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                    }
                }
            } else {
                if (tickets.isEmpty()) {
                    Text("No transit tickets have been initiated yet in eSwatini.", fontSize = 12.sp, color = TextMuted)
                } else {
                    tickets.forEach { ticket ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (ticket.type == "RIDE") Icons.Default.DirectionsCar else Icons.Default.ShoppingBag,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Ticket #${ticket.id}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        if (ticket.isPriority) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = AccentOrange.copy(alpha = 0.2f))
                                            ) {
                                                Text(
                                                    "URGENT",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AccentOrange,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    
                                    val statusColor = when (ticket.status) {
                                        "OPEN" -> MaterialTheme.colorScheme.primary
                                        "NEGOTIATING" -> AccentOrange
                                        "ACTIVE" -> GreenVerified
                                        else -> TextMuted
                                    }
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.15f))
                                    ) {
                                        Text(
                                            ticket.status,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Client: @${ticket.customerUsername}", fontSize = 11.sp, color = TextLight)
                                if (ticket.assignedDriverUsername != null) {
                                    Text("Assigned Driver: @${ticket.assignedDriverUsername}", fontSize = 11.sp, color = GreenVerified)
                                } else {
                                    Text("Assigned Driver: Unassigned (Broadcasting)", fontSize = 11.sp, color = TextMuted)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.MyLocation, contentDescription = null, tint = GreenVerified, modifier = Modifier.size(10.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pickup: ${ticket.startLocation}", fontSize = 11.sp, color = TextLight)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PinDrop, contentDescription = null, tint = RedAlert, modifier = Modifier.size(10.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Dropoff: ${ticket.destination} (${ticket.zone})", fontSize = 11.sp, color = TextLight)
                                }
                                if (ticket.agreedPrice > 0.0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Agreed Rate: E ${String.format("%.2f", ticket.agreedPrice)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        } else if (selectedAdminSection == 3) {
            // CONFIGURE ADMINS TAB
            var adminUserReg by remember { mutableStateOf("") }
            var adminPassReg by remember { mutableStateOf("") }
            var adminPhoneReg by remember { mutableStateOf("") }

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Register New Platform Administrator",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = adminUserReg,
                        onValueChange = { adminUserReg = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Username Icon") },
                        colors = textFieldColors()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = adminPassReg,
                        onValueChange = { adminPassReg = it },
                        label = { Text("Password Hash / Passphrase") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password Icon") },
                        colors = textFieldColors()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = adminPhoneReg,
                        onValueChange = { adminPhoneReg = it },
                        label = { Text("Contact Phone") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = "Phone Icon") },
                        colors = textFieldColors()
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            viewModel.adminAddAdmin(adminUserReg.trim(), adminPassReg.trim(), adminPhoneReg.trim())
                            adminUserReg = ""
                            adminPassReg = ""
                            adminPhoneReg = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("CREATE ADMINISTRATOR ACCOUNT", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Registered System Administrators", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            admins.forEach { adm ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    border = BorderStroke(1.dp, if (adm.username == viewModel.currentUser?.username) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text("@${adm.username}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Contact: ${adm.phone}", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (adm.username == viewModel.currentUser?.username) GreenVerified.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            )
                        ) {
                            Text(
                                if (adm.username == viewModel.currentUser?.username) "YOU (ACTIVE)" else "CO-ADMIN",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (adm.username == viewModel.currentUser?.username) GreenVerified else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // MY ACCOUNT TAB (EDIT CREDENTIALS)
            val currentAdmin = viewModel.currentUser
            var editUser by remember(currentAdmin) { mutableStateOf(currentAdmin?.username ?: "") }
            var editPass by remember(currentAdmin) { mutableStateOf(currentAdmin?.passwordHash ?: "") }
            var editPhone by remember(currentAdmin) { mutableStateOf(currentAdmin?.phone ?: "") }

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Update Administrator Credentials",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "You can change your username, password, or contact details after your first login.",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = editUser,
                        onValueChange = { editUser = it },
                        label = { Text("Profile Username") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        colors = textFieldColors()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = editPass,
                        onValueChange = { editPass = it },
                        label = { Text("Secret Password / Passphrase") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        colors = textFieldColors()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = { Text("Contact Phone") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        colors = textFieldColors()
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            viewModel.updateAdminCredentials(editUser.trim(), editPass.trim(), editPhone.trim())
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenVerified)
                    ) {
                        Text("SAVE CHANGES & UPDATE PROFILE", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// USER OFFICIAL SUPPORT CHAT APPEALS WINDOW
// -----------------------------------------------------------------------------
@Composable
fun SupportChatScreen(viewModel: KhapulViewModel) {
    val myAppeals by viewModel.mySupportTickets.collectAsStateWithLifecycle(emptyList())
    var suggestionInputText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    viewModel.currentScreen = when (viewModel.currentUser?.role) {
                        "CUSTOMER" -> AppScreen.CustomerMain
                        "DRIVER" -> AppScreen.DriverMain
                        "ADMIN" -> AppScreen.AdminMain
                        else -> AppScreen.Auth
                    }
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Return")
                }
                Text("Official Support Desk", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Text(
                    text = "A dedicated Support Admin team is active to manually evaluate complaints, suggestions, or disputes to keep the community moving safely.",
                    fontSize = 11.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Text("Your Historic Complaints & Suggestions:", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))

            if (myAppeals.isEmpty()) {
                Text("No previous queries registered.", fontSize = 11.sp, color = TextMuted)
            } else {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(myAppeals) { appeal ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(appeal.messageText, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                if (appeal.replyText != null) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = GreenVerified.copy(alpha = 0.15f))
                                    ) {
                                        Text(
                                            text = "Admin Resolution: ${appeal.replyText}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GreenVerified,
                                            modifier = Modifier.padding(6.dp)
                                        )
                                    }
                                } else {
                                    Text("Waiting for Admin team evaluation...", fontSize = 10.sp, color = AccentOrange)
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = suggestionInputText,
                onValueChange = { suggestionInputText = it },
                placeholder = { Text("Type dispute Suggestion / Complaint details...") },
                modifier = Modifier.weight(1f),
                colors = textFieldColors()
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = {
                    if (suggestionInputText.isNotBlank()) {
                        viewModel.submitComplainOrSuggestion(suggestionInputText)
                        suggestionInputText = ""
                    }
                },
                modifier = Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Submit", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

// -----------------------------------------------------------------------------
// INTERACTIVE MAP WITH EMBEDDED CANVAS (OPEN STREET MAP PIN RESOLVER SIMULATION)
// -----------------------------------------------------------------------------
@Composable
fun InteractiveMapView(
    onLocationSelected: (coordinates: String, zone: String, name: String) -> Unit
) {
    var selectedOffset by remember { mutableStateOf<Offset?>(null) }
    var zoomLevel by remember { mutableStateOf(1.0f) }
    var activeLayerStyle by remember { mutableStateOf("SLATE STREETS") } // SLATE STREETS, TERRAIN, BOUNDS

    // Operating Zones bounds map areas
    val downtownBounds = Rect(300f, 250f, 700f, 550f)
    val northBounds = Rect(200f, 0f, 800f, 250f)
    val southBounds = Rect(200f, 550f, 800f, 800f)
    val westBounds = Rect(0f, 100f, 300f, 700f)
    val eastBounds = Rect(700f, 100f, 1000f, 700f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .background(Color(0xFF1E293B))
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        selectedOffset = offset

                        // scale offset back to visual proportions (assuming 1000 x 800 layout canvas viewport ratio)
                        val scaledX = (offset.x / size.width) * 1000f
                        val scaledY = (offset.y / size.height) * 800f

                        // Detect which zone bounds it falls in
                        val (resolvedZone, placeName) = when {
                            downtownBounds.contains(Offset(scaledX, scaledY)) ->
                                Pair("Hhohho (Mbabane)", "Mbabane CBD near Allister Miller Street")
                            northBounds.contains(Offset(scaledX, scaledY)) ->
                                Pair("Hhohho (North)", "Piggs Peak Ring Road Junction")
                            southBounds.contains(Offset(scaledX, scaledY)) ->
                                Pair("Shiselweni", "Nhlangano Shopping Center Road")
                            westBounds.contains(Offset(scaledX, scaledY)) ->
                                Pair("Manzini", "Matsapha Industrial Hub Gate 2")
                            eastBounds.contains(Offset(scaledX, scaledY)) ->
                                Pair("Lubombo", "Siteki Main Interchange")
                            else ->
                                Pair("Hhohho (Mbabane)", "Mbabane Bypass Highway")
                        }

                        val lat = -26.3150 + (scaledY - 400) * -0.0010
                        val lon = 31.1350 + (scaledX - 500) * 0.0015
                        val coordinateString = "Lat: ${String.format("%.4f", lat)}, Long: ${String.format("%.4f", lon)}"
                        onLocationSelected(coordinateString, resolvedZone, placeName)
                    }
                }
        ) {
            // Background Canvas OSM tile style
            val canvasColor = if (activeLayerStyle == "SLATE STREETS") Color(0xFF0F172A) else Color(0xFF0D1016)
            drawRect(color = canvasColor)

            // Draw Zone Sector Grid Overlays with soft indicators
            // River
            val riverPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, height * 0.3f)
                cubicTo(width * 0.3f, height * 0.25f, width * 0.4f, height * 0.7f, width, height * 0.8f)
            }
            drawPath(
                path = riverPath,
                color = Color(0xFF1E40AF),
                style = Stroke(width = 16f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f)))
            )

            // Draw neighborhood bounds boxes
            drawRect(
                color = Color(0xFFFCBF49).copy(alpha = 0.03f),
                topLeft = Offset(width * 0.3f, height * 0.3f),
                size = androidx.compose.ui.geometry.Size(width * 0.4f, height * 0.4f)
            )

            // Streets Grid lines
            // Downtown / horizontal
            drawLine(Color(0xFF334155), Offset(0f, height * 0.5f), Offset(width, height * 0.5f), strokeWidth = 6f)
            drawLine(Color(0xFF334155), Offset(0f, height * 0.2f), Offset(width, height * 0.2f), strokeWidth = 4f)
            drawLine(Color(0xFF334155), Offset(0f, height * 0.8f), Offset(width, height * 0.8f), strokeWidth = 4f)
            // Vertical
            drawLine(Color(0xFF334155), Offset(width * 0.3f, 0f), Offset(width * 0.3f, height), strokeWidth = 4f)
            drawLine(Color(0xFF334155), Offset(width * 0.7f, 0f), Offset(width * 0.7f, height), strokeWidth = 4f)

            // City center park
            drawRoundRect(
                color = Color(0xFF065F46).copy(alpha = 0.5f),
                topLeft = Offset(width * 0.45f, height * 0.45f),
                size = androidx.compose.ui.geometry.Size(width * 0.15f, height * 0.12f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
            )

            // Selected Pin placement
            selectedOffset?.let { offset ->
                // Draw ripple circle
                drawCircle(
                    color = Color(0xFFFCBF49).copy(alpha = 0.3f),
                    radius = 28f,
                    center = offset
                )
                // Draw PIN pointer
                drawCircle(
                    color = Color(0xFFF77F00),
                    radius = 11f,
                    center = offset
                )
                drawCircle(
                    color = Color.White,
                    radius = 5f,
                    center = offset
                )
            }
        }

        // Floating OSM style layers readouts & zoom
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceCard.copy(alpha = 0.85f))) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Leaflet.OSM Tile Mode: ", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TextLight)
                    Text(activeLayerStyle, fontSize = 8.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard.copy(alpha = 0.9f)),
                modifier = Modifier.clickable {
                    activeLayerStyle = if (activeLayerStyle == "SLATE STREETS") "TERRAIN PROTO" else "SLATE STREETS"
                }
            ) {
                Text(
                    "Layer Cycle",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceCard.copy(alpha = 0.9f))) {
                Text(
                    "Touch to Place Pin",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TEXTFIELD STYLING HELPER
// -----------------------------------------------------------------------------
@Composable
fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = TextMuted,
    focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
    unfocusedLeadingIconColor = TextMuted,
    focusedTextColor = TextLight,
    unfocusedTextColor = TextLight
)

// Legacy dimension alias helpers to ensure dynamic sizing scaling
val Int.dim get() = this.dp
val Double.dim get() = this.dp
val Float.dim get() = this.dp
