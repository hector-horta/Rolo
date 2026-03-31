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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import com.rolo.app.camera.CameraPreview
import com.rolo.app.data.BusinessCard
import com.rolo.app.ui.MainViewModel
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
                darkTheme -> darkColorScheme()
                else -> lightColorScheme()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoloAppScreen(
    viewModel: MainViewModel,
    onTakePhoto: () -> Unit,
    onExportDb: () -> Unit,
    onCardClick: (BusinessCard) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TopAppBar(
                    title = { Text("Rolo") },
                    actions = {
                        IconButton(onClick = onExportDb) {
                            Icon(Icons.Default.Backup, contentDescription = "Backup Database")
                        }
                    }
                )
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.showPaywallIfLimitReached(force = true) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val progressVal = (state.cardCount / 25f).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = progressVal,
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = if (progressVal >= 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        text = if (state.isPremium) "Premium Unlocked" else "${state.cardCount}/25 Free Cards (Tap for Info)",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        floatingActionButton = {
            if (state.cardCount < 25 || state.isPremium) {
                FloatingActionButton(onClick = onTakePhoto, containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Default.Add, contentDescription = "Scan Card")
                }
            }
        }
    ) { padding ->
        if (state.showPaywall) {
            PaywallScreen(
                onPurchase = { viewModel.purchasePremium(context) },
                onDismiss = { viewModel.dismissPaywall() }
            )
        } else {
            if (state.cards.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No cards yet. Tap + to scan your first business card.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = padding,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.cards) { card ->
                        BusinessCardItem(
                            card = card,
                            onCardClick = { onCardClick(card) },
                            onCall = { 
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${card.phone}"))
                                context.startActivity(intent)
                            },
                            onEmail = {
                                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${card.email}"))
                                context.startActivity(intent)
                            },
                            onNavigate = {
                                val uri = Uri.parse("geo:0,0?q=${Uri.encode(card.address)}")
                                val intent = Intent(Intent.ACTION_VIEW, uri)
                                context.startActivity(intent)
                            },
                            onAddToContacts = {
                                val intent = Intent(Intent.ACTION_INSERT).apply {
                                    type = ContactsContract.RawContacts.CONTENT_TYPE
                                    putExtra(ContactsContract.Intents.Insert.NAME, card.name)
                                    putExtra(ContactsContract.Intents.Insert.PHONE, card.phone)
                                    putExtra(ContactsContract.Intents.Insert.EMAIL, card.email)
                                    putExtra(ContactsContract.Intents.Insert.POSTAL, card.address)
                                }
                                context.startActivity(intent)
                            }
                        )
                    }
                }
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
    val density = LocalDensity.current
    
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
                Text("Tap on the image to call, email, or navigate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                
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
                        ContactInfoRow(icon = Icons.Default.PersonAdd, label = "Name", value = card.name)
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
fun BusinessCardItem(
    card: BusinessCard,
    onCardClick: () -> Unit,
    onCall: () -> Unit, 
    onEmail: () -> Unit, 
    onNavigate: () -> Unit,
    onAddToContacts: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onCardClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (card.imagePath.isNotEmpty() && File(card.imagePath).exists()) {
                Image(
                    painter = rememberAsyncImagePainter(File(card.imagePath)),
                    contentDescription = "Card thumbnail",
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = card.name.ifEmpty { "Unknown" }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (card.phone.isNotEmpty()) {
                    Text(text = card.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (card.phone.isNotEmpty()) {
                    IconButton(onClick = onCall, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Call, "Call", modifier = Modifier.size(18.dp)) }
                }
                if (card.email.isNotEmpty()) {
                    IconButton(onClick = onEmail, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Email, "Email", modifier = Modifier.size(18.dp)) }
                }
            }
        }
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
