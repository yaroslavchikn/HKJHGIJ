package com.example.calculator.vault

import android.app.Activity
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.calculator.vault.data.MediaItem
import com.example.calculator.vault.data.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun VaultScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        // Можно добавить логирование, если захочется
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is VaultViewModel.VaultEvent.Message -> {
                    Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
                }

                is VaultViewModel.VaultEvent.DeleteOriginals -> {
                    DeleteOriginalHelper.requestDelete(context, event.uris) { sender ->
                        runCatching {
                            val request = IntentSenderRequest.Builder(sender).build()
                            deleteLauncher.launch(request)
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            viewModel.lockIfUnlocked()
        }
    }

    BackHandler {
        when {
            state.pinStage == VaultViewModel.PinStage.UNLOCKED && state.selected != null -> {
                viewModel.open(null)
            }

            state.pinStage == VaultViewModel.PinStage.UNLOCKED -> {
                viewModel.lock()
                onBack()
            }

            else -> {
                onBack()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (state.pinStage) {
            VaultViewModel.PinStage.CREATE -> {
                CreatePinScreen(onCreate = viewModel::createPin)
            }

            VaultViewModel.PinStage.UNLOCK -> {
                UnlockScreen(onUnlock = viewModel::unlock)
            }

            VaultViewModel.PinStage.UNLOCKED -> {
                VaultContent(
                    state = state,
                    viewModel = viewModel,
                    onBack = {
                        viewModel.lock()
                        onBack()
                    }
                )
            }

            null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun CreatePinScreen(onCreate: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }

    val enabled = pin.length >= 4 && pin == repeat

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Новый код доступа",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit) },
            label = { Text("Код") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = repeat,
            onValueChange = { repeat = it.filter(Char::isDigit) },
            label = { Text("Повтор кода") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )

        if (pin.isNotEmpty() && repeat.isNotEmpty() && pin != repeat) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Коды не совпадают",
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { onCreate(pin) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Сохранить")
        }
    }
}

@Composable
private fun UnlockScreen(onUnlock: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Введите код",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit) },
            label = { Text("Код") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (pin.isNotEmpty()) onUnlock(pin)
                }
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { onUnlock(pin) },
            enabled = pin.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Открыть")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultContent(
    state: VaultViewModel.VaultUiState,
    viewModel: VaultViewModel,
    onBack: () -> Unit
) {
    val pickLauncher = rememberLauncherForActivityResult(WriteableOpenDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.import(uris)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Файлы") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleDeleteOriginal() }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            tint = if (state.deleteOriginal) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Gray
                            }
                        )
                    }

                    if (state.importing) {
                        Box(
                            modifier = Modifier.padding(end = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.Lock, contentDescription = null)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                ExtendedFloatingActionButton(
                    onClick = { pickLauncher.launch(arrayOf("image/*")) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Фото") }
                )

                Spacer(Modifier.height(10.dp))

                ExtendedFloatingActionButton(
                    onClick = { pickLauncher.launch(arrayOf("video/*")) },
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    text = { Text("Видео") }
                )
            }
        }
    ) { padding ->
        if (state.items.isEmpty()) {
            EmptyVault(padding)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(12.dp)
            ) {
                items(state.items, key = { it.id }) { item ->
                    MediaThumbnail(
                        item = item,
                        repository = viewModel.repository,
                        onClick = { viewModel.open(item) }
                    )
                }
            }
        }

        state.selected?.let { item ->
            MediaViewer(
                item = item,
                repository = viewModel.repository,
                onClose = { viewModel.open(null) },
                onDelete = {
                    viewModel.delete(item)
                    viewModel.open(null)
                }
            )
        }
    }
}

@Composable
private fun EmptyVault(padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Здесь пока пусто",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Добавьте фото или видео кнопками ниже",
            textAlign = TextAlign.Center,
            color = Color.Gray
        )
    }
}

@Composable
private fun MediaThumbnail(
    item: MediaItem,
    repository: VaultRepository,
    onClick: () -> Unit
) {
    val file by produceState<File?>(initialValue = null, key1 = item.id) {
        value = withContext(Dispatchers.IO) {
            runCatching { repository.getDecryptedFile(item) }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        file?.let { decrypted ->
            AsyncImage(
                model = decrypted,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } ?: Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp
            )
        }

        if (item.mimeType.startsWith("video/")) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f)
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(16.dp),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun MediaViewer(
    item: MediaItem,
    repository: VaultRepository,
    onClose: () -> Unit,
    onDelete: () -> Unit
) {
    val file by produceState<File?>(initialValue = null, key1 = item.id) {
        value = withContext(Dispatchers.IO) {
            runCatching { repository.getDecryptedFile(item) }.getOrNull()
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(Modifier.fillMaxSize()) {
                file?.let { decrypted ->
                    if (item.mimeType.startsWith("video/")) {
                        VideoPlayer(decrypted)
                    } else {
                        AsyncImage(
                            model = decrypted,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                } ?: Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(8.dp)
                ) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }

                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPlayer(file: File) {
    val context = LocalContext.current
    val view = remember { android.widget.VideoView(context) }

    DisposableEffect(file.absolutePath) {
        val controller = android.widget.MediaController(context)
        controller.setAnchorView(view)

        view.setMediaController(controller)
        view.setOnPreparedListener { player ->
            player.start()
        }
        view.setVideoPath(file.absolutePath)

        onDispose {
            view.stopPlayback()
        }
    }

    AndroidView(
        factory = { view },
        modifier = Modifier.fillMaxSize()
    )
}
