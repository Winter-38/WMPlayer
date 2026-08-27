package com.winter.muplayer.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.winter.muplayer.plugin.PluginException
import com.winter.muplayer.plugin.model.PluginType
import com.winter.muplayer.ui.PluginHost
import com.winter.muplayer.ui.PluginUiHost
import com.winter.muplayer.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 插件管理界面（Material 3）：
 * - 顶部栏（返回）+ 已安装插件列表（点击条目打开插件默认界面，条目右侧加载/卸载）
 * - 右下角 FAB 安装本地插件（SAF 选择 zip，安装并自动加载，结果 Snackbar 提示）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = PluginHost.get(context)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var tick by remember { mutableIntStateOf(0) }
    var installing by remember { mutableStateOf(false) }
    var pendingUninstall by remember { mutableStateOf<String?>(null) }
    val installed = remember(tick) { manager.installedPlugins() }
    fun refresh() { tick++ }

    // SAF 文件选择器（免存储权限）：选择插件 zip 包
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            installing = true
            try {
                // 复制到应用缓存目录（installZip 需要本地 File）
                val zipFile = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "plugin-import").apply { mkdirs() }
                    val target = File(dir, "import-${System.currentTimeMillis()}.zip")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { out -> input.copyTo(out) }
                    } ?: throw PluginException("无法读取所选文件")
                    target
                }
                val plugin = withContext(Dispatchers.IO) { manager.installZip(zipFile) }
                // 安装成功后自动加载（入口执行失败不算安装失败）
                val loadState = try {
                    manager.load(plugin.id)
                    context.getString(R.string.plugin_loaded_ok)
                } catch (_: Throwable) {
                    context.getString(R.string.plugin_loaded_fail)
                }
                // 先刷新列表（第一时间显示新插件），再弹提示（showSnackbar 是挂起等待，不能挡住刷新）
                refresh()
                snackbarHostState.showSnackbar(
                    context.getString(R.string.plugin_installed_result, plugin.id, loadState)
                )
            } catch (t: Throwable) {
                refresh()
                snackbarHostState.showSnackbar(
                    context.getString(R.string.plugin_install_fail, t.message ?: t.javaClass.simpleName)
                )
            } finally {
                installing = false
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plugin_manager)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (!installing) launcher.launch(arrayOf("*/*")) },
            ) {
                Icon(
                    painterResource(R.drawable.ic_playlist_add),
                    contentDescription = stringResource(R.string.plugin_install_local),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (installed.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.plugin_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(installed, key = { it }) { id ->
                    val desc = manager.pluginDescriptor(id)
                    val typeLabel = when (desc?.type) {
                        PluginType.COMPONENT -> stringResource(R.string.plugin_type_component)
                        PluginType.SERVICE -> stringResource(R.string.plugin_type_service)
                        else -> stringResource(R.string.plugin_type_app)
                    }
                    ListItem(
                        headlineContent = { Text(desc?.name ?: id) },
                        supportingContent = { Text("$id · $typeLabel") },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = (desc?.name ?: id).take(1).uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = { pendingUninstall = id }) {
                                Icon(
                                    painterResource(R.drawable.ic_delete),
                                    contentDescription = stringResource(R.string.plugin_unload),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        modifier = Modifier.clickable { PluginUiHost.openDefault(id) },
                    )
                }
            }
        }
    }

    // 卸载确认弹窗
    pendingUninstall?.let { id ->
        val name = manager.pluginDescriptor(id)?.name ?: id
        AlertDialog(
            onDismissRequest = { pendingUninstall = null },
            title = { Text(stringResource(R.string.plugin_uninstall_title)) },
            text = { Text(stringResource(R.string.plugin_uninstall_text, name)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingUninstall = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { manager.uninstall(id) }
                            refresh() // 先移除列表项，再弹提示
                            snackbarHostState.showSnackbar(
                                context.getString(R.string.plugin_uninstalled, name)
                            )
                        } catch (t: Throwable) {
                            snackbarHostState.showSnackbar(
                                context.getString(
                                    R.string.plugin_install_fail,
                                    t.message ?: t.javaClass.simpleName
                                )
                            )
                        } finally {
                            refresh() // 无论成败都强制重读列表
                        }
                    }
                }) {
                    Text(stringResource(R.string.plugin_unload), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUninstall = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
