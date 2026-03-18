package com.rolo.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
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
            // Material You - Colores Dinámicos
            MaterialTheme(colorScheme = dynamicLightColorScheme(context)) {
                RoloAppScreen(
                    viewModel = viewModel,
                    onTakePhoto = { takePicture.launch(null) },
                    onExportDb = { viewModel.exportDatabaseToDrive(this) }
                )
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
                    val progress = (state.cardCount / 25f).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = if (progress >= 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
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
                            val intent = Intent(Intent.ACTION.DIAL, Uri.parse("tel:${card.phone}"))
                            context.startActivity(intent)
                        },
                        onEmail = {
                            val intent = Intent(Intent.ACTION.SENDTO, Uri.parse("mailto:${card.email}"))
                            context.startActivity(intent)
                        },
                        onNavigate = {
                            val intent = Intent(Intent.ACTION.VIEW, Uri.parse("geo:0,0?q=${Uri.encode(card.address)}"))
                            intent.setPackage("com.google.android.apps.maps")
                            if(intent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(intent)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun BusinessCardItem(card: BusinessCard, onCall: () -> Unit, onEmail: () -> Unit, onNavigate: () -> Unit) {
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
