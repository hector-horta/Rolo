package com.rolo.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rolo.app.ui.BusinessCard
import com.rolo.app.ui.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Simulación: En la app final se usará CameraX para captura automática
        val takePicture = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            bitmap?.let { viewModel.processImage(it) }
        }

        setContent {
            val context = LocalContext.current
            val darkTheme = isSystemInDarkTheme()
            
            // Soporte para Material You (Colores Dinámicos) con fallback
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
                    RoloAppScreen(
                        viewModel = viewModel,
                        onTakePhoto = { takePicture.launch(null) },
                        onExportDb = { viewModel.exportDatabaseToDrive(this) }
                    )
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
    onExportDb: () -> Unit
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
                // Barra de progreso interactiva (Tap abre el Paywall)
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
            LazyColumn(
                contentPadding = padding,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.cards) { card ->
                    BusinessCardItem(
                        card = card,
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

@Composable
fun BusinessCardItem(
    card: BusinessCard, 
    onCall: () -> Unit, 
    onEmail: () -> Unit, 
    onNavigate: () -> Unit,
    onAddToContacts: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = card.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (card.phone.isNotEmpty()) {
                    IconButton(onClick = onCall) { Icon(Icons.Default.Call, "Call") }
                }
                if (card.email.isNotEmpty()) {
                    IconButton(onClick = onEmail) { Icon(Icons.Default.Email, "Email") }
                }
                if (card.address.isNotEmpty()) {
                    IconButton(onClick = onNavigate) { Icon(Icons.Default.LocationOn, "Address") }
                }
                IconButton(onClick = onAddToContacts) { Icon(Icons.Default.PersonAdd, "Add to Contacts") }
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
