package com.maloy.muzza.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.maloy.muzza.LocalPlayerAwareWindowInsets
import com.maloy.muzza.R
import com.maloy.muzza.ui.component.IconButton
import com.maloy.muzza.ui.component.PreferenceEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val shimmerBrush = shimmerEffect()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            )
        )
        Spacer(Modifier.height(48.dp))
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(NavigationBarDefaults.Elevation))
        ) {
            Image(
                painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.padding(16.dp)
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(shimmerBrush)
            )
        }

        Row(
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = "ReTune",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        Spacer(Modifier.height(25.dp))
        PreferenceEntry(
            title = { Text(stringResource(R.string.appearance)) },
            icon = { Icon(painterResource(R.drawable.palette), null) },
            onClick = { navController.navigate("settings/appearance") }
        )
        PreferenceEntry(
            title = { Text(stringResource(R.string.account)) },
            icon = { Icon(painterResource(R.drawable.person), null) },
            onClick = { navController.navigate("settings/account") }
        )
        PreferenceEntry(
            title = { Text(stringResource(R.string.content)) },
            icon = { Icon(painterResource(R.drawable.language), null) },
            onClick = { navController.navigate("settings/content") }
        )
        PreferenceEntry(
            title = { Text(stringResource(R.string.player_and_audio)) },
            icon = { Icon(painterResource(R.drawable.play), null) },
            onClick = { navController.navigate("settings/player") }
        )
        PreferenceEntry(
            title = { Text(stringResource(R.string.storage)) },
            icon = { Icon(painterResource(R.drawable.storage), null) },
            onClick = { navController.navigate("settings/storage") }
        )
        PreferenceEntry(
            title = { Text(stringResource(R.string.privacy)) },
            icon = { Icon(painterResource(R.drawable.security), null) },
            onClick = { navController.navigate("settings/privacy") }
        )
        PreferenceEntry(
            title = { Text(stringResource(R.string.backup_restore)) },
            icon = { Icon(painterResource(R.drawable.restore), null) },
            onClick = { navController.navigate("settings/backup_restore") }
        )
        PreferenceEntry(
            title = { Text("Buscar actualizaciones") },
            description = "Versión instalada: 0.8.4",
            icon = { Icon(painterResource(R.drawable.cached), null) },
            onClick = { 
                // Aquí se disparará el Updater que configuramos antes
            }
        )
        PreferenceEntry(
            title = { Text(stringResource(R.string.about)) },
            icon = { Icon(painterResource(R.drawable.info), null) },
            onClick = { navController.navigate("settings/about") }
        )

        Spacer(Modifier.height(48.dp))
    }
    CenterAlignedTopAppBar(
        title = { Text(stringResource(R.string.settings)) }, navigationIcon = {
            IconButton(
                onClick = { navController.navigate("home") },
                onLongClick = { navController.navigate("home") },
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back), contentDescription = null
                )
            }
        }, scrollBehavior = scrollBehavior
    )
}
