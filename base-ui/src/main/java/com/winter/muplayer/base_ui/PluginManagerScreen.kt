package com.winter.muplayer.base_ui

import android.os.Process
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.winter.muplayer.plugin.PluginFileManager
import com.winter.muplayer.plugin.PluginManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PluginManagerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loadedPlugins: List<PluginManager.LoadedPlugin> by remember {
        mutableStateOf(PluginManager.getLoadedPlugins())
    }
    var showRestartDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    uris.forEach { uri ->
                        val fileName = uri.lastPathSegment ?: return@forEach
                        val ext = fileName.substringAfterLast('.', "").lowercase()
                        if (ext != "dex" && ext != "json") return@forEach

                        val inputStream = context.contentResolver.openInputStream(uri) ?: return@forEach
                        val tempFile = java.io.File(context.cacheDir, fileName)
                        tempFile.outputStream().use { output ->
                            inputStream.copyTo(output)
                        }
                        PluginFileManager.copyPluginFile(context, tempFile)
                        tempFile.delete()
                    }
                }
                pendingAction = "import"
                showRestartDialog = true
            }
        }
    }

    fun refreshPlugins() {
        loadedPlugins = PluginManager.getLoadedPlugins()
    }

    fun deletePlugin(dexFileName: String) {
        val plugin = PluginManager.getPluginByDexFileName(dexFileName)
        if (plugin != null) {
            plugin.onUnload()
            PluginManager.unregister(plugin)
        }
        PluginFileManager.deletePluginFile(context, dexFileName)
        refreshPlugins()
        pendingAction = "delete"
        showRestartDialog = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("插件管理", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("已加载插件 (${loadedPlugins.size})", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { launcher.launch(arrayOf("*/*")) }) {
                Icon(Icons.Filled.Add, contentDescription = "导入插件")
                Spacer(Modifier.width(4.dp))
                Text("导入")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        if (loadedPlugins.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无插件")
            }
        } else {
            LazyColumn {
                items(items = loadedPlugins, key = { it.plugin.id }) { loaded ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(loaded.plugin.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "ID: ${loaded.plugin.id} | 类型: ${loaded.plugin.type}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "文件: ${loaded.dexFileName}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = { deletePlugin(loaded.dexFileName) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除插件")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(
                    "插件来自外部来源，请确保信任来源后再导入。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text("提示") },
            text = {
                Text(
                    when (pendingAction) {
                        "import" -> "插件已复制到插件目录，重启应用后生效。"
                        "delete" -> "插件已删除，下次启动将不再加载。"
                        else -> "操作需要重启应用。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRestartDialog = false
                    Process.killProcess(Process.myPid())
                }) {
                    Text("立即重启")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) {
                    Text("稍后")
                }
            }
        )
    }
}