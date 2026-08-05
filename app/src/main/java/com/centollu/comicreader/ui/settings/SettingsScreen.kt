package com.centollu.comicreader.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.pm.PackageManager
import android.os.Build
import com.centollu.comicreader.util.AppPrefs
import com.centollu.comicreader.util.ComicExtractor
import com.centollu.comicreader.util.NfsManager
import com.centollu.comicreader.util.NfsServerConfig
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var nfsConfig by remember { mutableStateOf(NfsManager.getConfig(context)) }
    var ipInput by remember { mutableStateOf(nfsConfig.serverIp) }
    var pathInput by remember { mutableStateOf(nfsConfig.exportPath) }
    var nfsEnabled by remember { mutableStateOf(nfsConfig.isEnabled) }
    var showSavedMessage by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var cacheGb by remember {
        mutableStateOf(ComicExtractor.getMaxCacheSize(context) / (1024f * 1024 * 1024))
    }
    var currentUsageBytes by remember { mutableStateOf(0L) }

    var gridColumns by remember {
        mutableStateOf(AppPrefs.getGridColumns(context).toFloat())
    }

    val versionName = remember {
        runCatching {
            val pm = context.packageManager
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            pkgInfo.versionName ?: "Desconocida"
        }.getOrNull() ?: "Desconocida"
    }

    LaunchedEffect(Unit) {
        currentUsageBytes = ComicExtractor.getCurrentCacheSize(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes y Red NFS", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Configuración de Servidor NFS (Red Local)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Habilitar carpetas en red NFS", modifier = Modifier.weight(1f))
                        Switch(
                            checked = nfsEnabled,
                            onCheckedChange = { nfsEnabled = it }
                        )
                    }

                    if (nfsEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = ipInput,
                            onValueChange = { ipInput = it },
                            label = { Text("Dirección IP del servidor/NAS") },
                            placeholder = { Text("192.168.1.100") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = pathInput,
                            onValueChange = { pathInput = it },
                            label = { Text("Ruta del recurso compartido") },
                            placeholder = { Text("/volume1/comics") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val newConfig = NfsServerConfig(
                                serverIp = ipInput.trim(),
                                exportPath = pathInput.trim(),
                                isEnabled = nfsEnabled
                            )
                            NfsManager.saveConfig(context, newConfig)
                            showSavedMessage = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Guardar Configuración NFS")
                    }

                    if (showSavedMessage) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "¡Configuración guardada correctamente!",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Biblioteca",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Elementos por fila: ${gridColumns.roundToInt()}",
                        fontSize = 14.sp
                    )
                    Slider(
                        value = gridColumns,
                        onValueChange = { gridColumns = it },
                        onValueChangeFinished = {
                            AppPrefs.setGridColumns(context, gridColumns.roundToInt())
                        },
                        valueRange = 1f..6f,
                        steps = 4
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Caché de lectura",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Tamaño máximo: ${"%.1f".format(cacheGb)} GB",
                        fontSize = 14.sp
                    )
                    Slider(
                        value = cacheGb,
                        onValueChange = { cacheGb = it },
                        onValueChangeFinished = {
                            val bytes = (cacheGb * 1024 * 1024 * 1024).toLong()
                            ComicExtractor.setMaxCacheSize(context, bytes)
                            scope.launch {
                                ComicExtractor.trimCache(context)
                                currentUsageBytes = ComicExtractor.getCurrentCacheSize(context)
                            }
                        },
                        valueRange = 1f..50f
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Los cómics extraídos se mantienen en caché hasta alcanzar el tamaño máximo. Al llenarse, se eliminan los más antiguos (FIFO) para dejar espacio.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Uso actual de caché: ${"%.2f".format(currentUsageBytes / (1024f * 1024 * 1024))} GB",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Acerca de ComicReader", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Versión: $versionName", fontSize = 12.sp)
                    Text("Motor BD: MongoDB Realm (Local)", fontSize = 12.sp)
                    Text("Formatos soportados: .cbz (ZIP) y .cbr (RAR)", fontSize = 12.sp)
                }
            }
        }
    }
}
