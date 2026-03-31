package com.rolo.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import com.rolo.app.camera.CameraPreview
import com.rolo.app.data.BusinessCard
import com.rolo.app.ui.MainViewModel
import com.rolo.app.ui.UiState
import java.io.File

sealed class Screen {
    object List : Screen()
    data class Detail(val card: BusinessCard) : Screen()
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var currentScreen by mutableStateOf<Screen>(Screen.List)
    private var selectedCard by mutableStateOf<BusinessCard?>(null)

    private val cameraPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showCamera = true
        } else {
            Toast.makeText(this, "Camera permission is required to scan cards", Toast.LENGTH_LONG).show()
        }
    }

    private var showCamera by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            val context = LocalContext.current
            val darkTheme = isSystemInDarkTheme()
            
            val colorScheme = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }
                darkTheme -> darkColorScheme(
                    primary = Color.Black,
                    background = Color(0xFFFCF9F8),
                    surface = Color.White
                )
                else -> lightColorScheme(
                    primary = Color.Black,
                    background = Color(0xFFFCF9F8),
                    surface = Color.White
                )
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (val screen = currentScreen) {
                        is Screen.List -> {
                            if (showCamera) {
                                CameraPreview(
                                    onImageCaptured = { bitmap ->
                                        viewModel.processImage(bitmap)
                                        showCamera = false
                                    },
                                    onError = { e ->
                                        Toast.makeText(context, "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    },
                                    onClose = { showCamera = false }
                                )
                            } else {
                                RoloAppScreen(
                                    viewModel = viewModel,
                                    onTakePhoto = {
                                        val permission = Manifest.permission.CAMERA
                                        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                                            showCamera = true
                                        } else {
                                            cameraPermissionLauncher.launch(permission)
                                        }
                                    },
                                    onExportDb = { viewModel.exportDatabaseToDrive(this) },
                                    onCardClick = { card ->
                                        selectedCard = card
                                        currentScreen = Screen.Detail(card)
                                    }
                                )
                            }
                        }
                        is Screen.Detail -> {
                            CardDetailScreen(
                                card = screen.card,
                                onBack = { currentScreen = Screen.List },
                                onCall = {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${screen.card.phone}"))
                                    context.startActivity(intent)
                                },
                                onEmail = {
                                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${screen.card.email}"))
                                    context.startActivity(intent)
                                },
                                onNavigate = {
                                    val uri = Uri.parse("geo:0,0?q=${Uri.encode(screen.card.address)}")
                                    val intent = Intent(Intent.ACTION_VIEW, uri)
                                    context.startActivity(intent)
                                },
                                onAddToContacts = {
                                    val intent = Intent(Intent.ACTION_INSERT).apply {
                                        type = ContactsContract.RawContacts.CONTENT_TYPE
                                        putExtra(ContactsContract.Intents.Insert.NAME, screen.card.name)
                                        putExtra(ContactsContract.Intents.Insert.PHONE, screen.card.phone)
                                        putExtra(ContactsContract.Intents.Insert.EMAIL, screen.card.email)
                                        putExtra(ContactsContract.Intents.Insert.POSTAL, screen.card.address)
                                    }
                                    context.startActivity(intent)
                                },
                                onDelete = {
                                    viewModel.deleteCard(screen.card.id)
                                    currentScreen = Screen.List
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RoloAppScreen(
    viewModel: MainViewModel,
    onTakePhoto: () -> Unit,
    onExportDb: () -> Unit,
    onCardClick: (BusinessCard) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    RoloAppContent(
        state = state,
        onTakePhoto = onTakePhoto,
        onCardClick = onCardClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoloAppContent(
    state: UiState,
    onTakePhoto: () -> Unit = {},
    onCardClick: (BusinessCard) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("Rolo", fontWeight = FontWeight.Bold) 
                },
                navigationIcon = {
                    IconButton(onClick = { /* Menu */ }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = { /* Profile */ }) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = "Profile", modifier = Modifier.size(20.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.GridView, contentDescription = "Gallery") },
                    selected = true,
                    onClick = { }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    selected = false,
                    onClick = { }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    selected = false,
                    onClick = { }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onTakePhoto,
                containerColor = Color.Black,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Card")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            // Search Bar
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .clip(RoundedCornerShape(24.dp)),
                placeholder = { Text("Search cards...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF1F1F1),
                    unfocusedContainerColor = Color(0xFFF1F1F1),
                    disabledContainerColor = Color(0xFFF1F1F1),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                singleLine = true
            )

            // Gallery Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "Gallery",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = "${state.cardCount} total",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Gray
                    )
                }
                
                AssistChip(
                    onClick = { /* Filter */ },
                    label = { Text("RECENT", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    border = null,
                    colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFF1F1F1))
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Grid List
            if (state.cards.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("No cards found", color = Color.Gray)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(state.cards) { card ->
                        ModernBusinessCardItem(
                            card = card,
                            onClick = { onCardClick(card) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModernBusinessCardItem(
    card: BusinessCard,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.LightGray)
            ) {
                if (card.imagePath.isNotEmpty() && File(card.imagePath).exists()) {
                    Image(
                        painter = rememberAsyncImagePainter(File(card.imagePath)),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                
                IconButton(
                    onClick = onClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp)
                ) {
                    Icon(
                        Icons.Default.NorthEast, 
                        contentDescription = "Detail", 
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = card.name.ifEmpty { "Unknown" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                    maxLines = 1
                )
                Text(
                    text = "CREATIVE DIRECTOR • STUDIO MONO",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    card: BusinessCard,
    onBack: () -> Unit,
    onCall: () -> Unit,
    onEmail: () -> Unit,
    onNavigate: () -> Unit,
    onAddToContacts: () -> Unit,
    onDelete: () -> Unit
) {
    var imageSize by remember { mutableStateOf(Pair(0, 0)) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(card.name.ifEmpty { "Card Details" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (card.imagePath.isNotEmpty() && File(card.imagePath).exists()) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
                    .clip(RoundedCornerShape(12.dp))
                    .onGloballyPositioned { coordinates ->
                        imageSize = Pair(coordinates.size.width, coordinates.size.height)
                    }
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(File(card.imagePath)),
                        contentDescription = "Business Card",
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { onCall() },
                        contentScale = ContentScale.Crop
                    )
                    
                    TapZoneOverlay(
                        imageSize = imageSize,
                        card = card,
                        onCall = onCall,
                        onEmail = onEmail,
                        onNavigate = onNavigate
                    )
                }
            }
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (card.name.isNotEmpty()) {
                        ContactInfoRow(icon = Icons.Default.Person, label = "Name", value = card.name)
                    }
                    if (card.phone.isNotEmpty()) {
                        ContactInfoRow(icon = Icons.Default.Call, label = "Phone", value = card.phone, onClick = onCall)
                    }
                    if (card.email.isNotEmpty()) {
                        ContactInfoRow(icon = Icons.Default.Email, label = "Email", value = card.email, onClick = onEmail)
                    }
                    if (card.address.isNotEmpty()) {
                        ContactInfoRow(icon = Icons.Default.LocationOn, label = "Address", value = card.address, onClick = onNavigate)
                    }
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (card.phone.isNotEmpty()) {
                    ActionButton(icon = Icons.Default.Call, label = "Call", onClick = onCall)
                }
                if (card.email.isNotEmpty()) {
                    ActionButton(icon = Icons.Default.Email, label = "Email", onClick = onEmail)
                }
                if (card.address.isNotEmpty()) {
                    ActionButton(icon = Icons.Default.LocationOn, label = "Navigate", onClick = onNavigate)
                }
                ActionButton(icon = Icons.Default.PersonAdd, label = "Save", onClick = onAddToContacts)
            }
        }
    }
}

@Composable
fun TapZoneOverlay(
    imageSize: Pair<Int, Int>,
    card: BusinessCard,
    onCall: () -> Unit,
    onEmail: () -> Unit,
    onNavigate: () -> Unit
) {
    val density = LocalDensity.current
    val (width, height) = imageSize
    
    if (width == 0 || height == 0) return
    
    Box(modifier = Modifier.fillMaxSize()) {
        card.phoneBounds?.let { bounds ->
            if (card.phone.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (bounds.left * width).toInt(),
                                (bounds.top * height).toInt()
                            )
                        }
                        .size(
                            with(density) { ((bounds.right - bounds.left) * width).toDp() },
                            with(density) { ((bounds.bottom - bounds.top) * height).toDp() }
                        )
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            RoundedCornerShape(4.dp)
                        )
                        .clickable { onCall() }
                )
            }
        }
        
        card.emailBounds?.let { bounds ->
            if (card.email.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (bounds.left * width).toInt(),
                                (bounds.top * height).toInt()
                            )
                        }
                        .size(
                            with(density) { ((bounds.right - bounds.left) * width).toDp() },
                            with(density) { ((bounds.bottom - bounds.top) * height).toDp() }
                        )
                        .background(
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                            RoundedCornerShape(4.dp)
                        )
                        .clickable { onEmail() }
                )
            }
        }
        
        card.addressBounds?.let { bounds ->
            if (card.address.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (bounds.left * width).toInt(),
                                (bounds.top * height).toInt()
                            )
                        }
                        .size(
                            with(density) { ((bounds.right - bounds.left) * width).toDp() },
                            with(density) { ((bounds.bottom - bounds.top) * height).toDp() }
                        )
                        .background(
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f),
                            RoundedCornerShape(4.dp)
                        )
                        .clickable { onNavigate() }
                )
            }
        }
    }
}

@Composable
fun ContactInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun PaywallScreen(onPurchase: () -> Unit, onDismiss: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Text("Unlock Unlimited Cards", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Pay a one-time fee of $1.99 to keep storing your business cards forever.", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onPurchase, modifier = Modifier.fillMaxWidth()) {
                Text("Upgrade to Premium ($1.99)")
            }
            TextButton(onClick = onDismiss) {
                Text("Not Now")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RoloAppPreview() {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color.Black,
            background = Color(0xFFFCF9F8),
            surface = Color.White
        )
    ) {
        RoloAppContent(
            state = UiState(
                cardCount = 2,
                cards = listOf(
                    BusinessCard(name = "Alexander Vance"),
                    BusinessCard(name = "Jane Doe")
                )
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ModernBusinessCardItemPreview() {
    MaterialTheme {
        ModernBusinessCardItem(
            card = BusinessCard(name = "Alexander Vance"),
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun CardDetailScreenPreview() {
    MaterialTheme {
        CardDetailScreen(
            card = BusinessCard(name = "Alexander Vance", phone = "123456789", email = "alex@studio.mono", address = "123 Street"),
            onBack = {},
            onCall = {},
            onEmail = {},
            onNavigate = {},
            onAddToContacts = {},
            onDelete = {}
        )
    }
}