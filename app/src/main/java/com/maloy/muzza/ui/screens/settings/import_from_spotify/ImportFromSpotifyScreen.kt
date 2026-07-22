package com.maloy.muzza.ui.screens.settings.import_from_spotify

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.maloy.muzza.R
import com.maloy.muzza.ui.screens.settings.import_from_spotify.model.Playlist
import com.maloy.muzza.ui.utils.backToMain
import com.maloy.muzza.viewmodels.ImportFromSpotifyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportFromSpotifyScreen(
    navController: NavController, 
    scrollBehavior: TopAppBarScrollBehavior
) {
    val viewModel: ImportFromSpotifyViewModel = hiltViewModel()
    val state by viewModel.importFromSpotifyScreenState
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val lazyListState = rememberLazyListState()
    
    var clientId by rememberSaveable { mutableStateOf("") }
    var clientSecret by rememberSaveable { mutableStateOf("") }
    var authCode by rememberSaveable { mutableStateOf("") }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }

    val spotifyLoginLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spDc = result.data?.getStringExtra("SP_DC")
            if (spDc != null) {
                viewModel.fetchPlaylistsWithSpDc(spDc)
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Spotify Sync", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { if (!viewModel.isImportingInProgress.value) navController.navigateUp() }) {
                        Icon(painterResource(R.drawable.arrow_back), null)
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.isObtainingAccessTokenSuccessful) {
                // PANTALLA POST-LOGIN (Lista de Playlists)
                Column(modifier = Modifier.fillMaxSize()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(48.dp).background(Color(0xFF1DB954), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Person, null, tint = Color.White)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(state.userName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${state.totalPlaylistsCount} Playlists found", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        item {
                            ListItem(
                                headlineContent = { Text("Liked Songs", fontWeight = FontWeight.SemiBold) },
                                leadingContent = { 
                                    Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                                    }
                                },
                                trailingContent = {
                                    Checkbox(checked = viewModel.isLikedSongsSelectedForImport.value, onCheckedChange = { viewModel.isLikedSongsSelectedForImport.value = it })
                                },
                                modifier = Modifier.clickable { viewModel.isLikedSongsSelectedForImport.value = !viewModel.isLikedSongsSelectedForImport.value }
                            )
                        }
                        items(state.playlists) { playlist ->
                            val isSelected = viewModel.selectedPlaylists.any { it.id == playlist.playlistId }
                            ListItem(
                                headlineContent = { Text(playlist.playlistName) },
                                leadingContent = {
                                    AsyncImage(
                                        model = playlist.images.firstOrNull()?.url,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                                    )
                                },
                                trailingContent = {
                                    Checkbox(checked = isSelected, onCheckedChange = { checked ->
                                        if (checked) viewModel.selectedPlaylists.add(Playlist(playlist.playlistName, playlist.playlistId))
                                        else viewModel.selectedPlaylists.removeIf { it.id == playlist.playlistId }
                                    })
                                },
                                modifier = Modifier.clickable {
                                    if (isSelected) viewModel.selectedPlaylists.removeIf { it.id == playlist.playlistId }
                                    else viewModel.selectedPlaylists.add(Playlist(playlist.playlistName, playlist.playlistId))
                                }
                            )
                        }
                    }
                }

                Button(
                    onClick = { viewModel.importSelectedItems(true) },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp).height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    enabled = viewModel.selectedPlaylists.isNotEmpty() || viewModel.isLikedSongsSelectedForImport.value
                ) {
                    Icon(Icons.Rounded.CloudDownload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start Importing", fontWeight = FontWeight.Bold)
                }

            } else {
                // PANTALLA DE LOGIN
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.spotify),
                        contentDescription = null,
                        modifier = Modifier.size(80.dp).align(Alignment.CenterHorizontally),
                        tint = Color(0xFF1DB954)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Connect Spotify", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Text("Transfer your music from Spotify", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    
                    Spacer(Modifier.height(32.dp))

                    // MÉTODO RÁPIDO
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1DB954).copy(alpha = 0.1f))
                    ) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Fast Login", style = MaterialTheme.typography.labelLarge, color = Color(0xFF1DB954))
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    val intent = Intent(context, SpotifyLoginActivity::class.java)
                                    spotifyLoginLauncher.launch(intent)
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(28.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954))
                            ) {
                                Text("Login with Spotify", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("No Client ID needed. Just log in.", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(Modifier.height(32.dp))

                    // MÉTODO TRADICIONAL
                    TextButton(
                        onClick = { showAdvanced = !showAdvanced },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(if (showAdvanced) "Hide Advanced Login" else "Use API Credentials (Personal App)")
                        Icon(if (showAdvanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                    }

                    AnimatedVisibility(showAdvanced) {
                        Column {
                            Text(
                                "Note: Each user needs to create their own app in the Spotify Dashboard.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            OutlinedTextField(
                                value = clientId,
                                onValueChange = { clientId = it },
                                label = { Text("Client ID") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = clientSecret,
                                onValueChange = { clientSecret = it },
                                label = { Text("Client Secret") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { uriHandler.openUri("https://accounts.spotify.com/authorize?client_id=${clientId.trim()}&response_type=code&redirect_uri=http://127.0.0.1:45454&scope=user-library-read%20playlist-read-private") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                enabled = clientId.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                            ) {
                                Text("1. Get Auth Code")
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = authCode,
                                onValueChange = { authCode = it.substringAfter("code=").substringBefore("&").trim() },
                                label = { Text("URL / Code") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.loginWithCredentials(clientId.trim(), clientSecret.trim(), authCode.trim()) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                enabled = clientId.isNotBlank() && clientSecret.isNotBlank() && authCode.isNotBlank()
                            ) {
                                Text("2. Sync with Keys")
                            }
                        }
                    }
                }
            }

            // OVERLAY DE IMPORTACIÓN
            if (viewModel.isImportingInProgress.value) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black.copy(alpha = 0.85f)) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF1DB954), strokeWidth = 6.dp, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(32.dp))
                        Text("Syncing your library...", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Don't close the app", color = Color.White.copy(alpha = 0.6f))
                        Spacer(Modifier.height(24.dp))
                        
                        Box(Modifier.fillMaxWidth().height(150.dp).background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp)).padding(12.dp)) {
                            LazyColumn(reverseLayout = true) {
                                items(viewModel.importLogs.asReversed()) { log ->
                                    Text(log, color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(viewModel.isImportingCompleted.value) {
        if (viewModel.isImportingCompleted.value) {
            Toast.makeText(context, "Import successful!", Toast.LENGTH_LONG).show()
            navController.navigateUp()
        }
    }
    
    BackHandler {
        if (!viewModel.isImportingInProgress.value) {
            navController.navigateUp()
        }
    }
}
