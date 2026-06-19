package com.winter.muplayer.base_ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.winter.muplayer.plugin.PluginHost
import java.io.File

/**
 * 插件管理界面。
 *
 * 插件是独立 APK，通过系统 PackageInstaller 安装。
 * 宿主通过 [PluginHost.discover] 发现已安装的插件 ContentProvider。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginManagerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 插件列表状态
    var plugins by remember { mutableStateOf<List<PluginHost.PluginInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // APK 文件选择器（用于安装新插件）
    val installLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // 将选中的 APK 复制到缓存目录，然后启动系统安装器
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@rememberLauncherForActivityResult
            val tempFile = File(context.cacheDir, "plugin_install_${System.currentTimeMillis()}.apk")
            tempFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            // 启动系统 PackageInstaller
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(tempFile), "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installIntent)
            Toast.makeText(context, "APK 已导出，请跟随系统提示安装", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "安装失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // 刷新插件列表
    fun refreshPlugins() {
        isLoading = true
        plugins = try {
            PluginHost(context).discover()
        } catch (e: Exception) {
            Toast.makeText(context, "扫描插件失败: ${e.message}", Toast.LENGTH_SHORT).show()
            emptyList()
        }
        isLoading = false
    }

    // 初始加载
    LaunchedEffect(Unit) {
        refreshPlugins()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 顶部标题 + 操作按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("插件管理", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 刷新按钮
                IconButton(onClick = { refreshPlugins() }) {
                    Icon(
                        if (isLoading) Icons.Filled.HourglassTop else Icons.Filled.Refresh,
                        contentDescription = "刷新"
                    )
                }
                // 安装插件按钮
                Button(onClick = {
                    installLauncher.launch(arrayOf("application/vnd.android.package-archive"))
                }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("安装 APK")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 说明文本
        Text(
            "插件是独立安装的 APK 应用。安装后点击刷新即可识别。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 插件列表
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (plugins.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.ExtensionOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("暂无已安装的插件", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "点击「安装 APK」选择插件文件安装",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(items = plugins, key = { it.id }) { plugin ->
                    PluginCard(plugin = plugin, onUninstall = {
                        // 打开系统卸载界面
                        val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                            data = Uri.parse("package:${plugin.packageName}")
                        }
                        context.startActivity(uninstallIntent)
                        Toast.makeText(context, "卸载后将在此消失", Toast.LENGTH_SHORT).show()
                    })
                }
            }
        }
    }
}

@Composable
private fun PluginCard(
    plugin: PluginHost.PluginInfo,
    onUninstall: () -> Unit
) {
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
                // 名称 + 类型
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(plugin.name, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(8.dp))
                    SuggestionChip(
                        onClick = {},
                        label = { Text(plugin.type, style = MaterialTheme.typography.labelSmall) }
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "ID: ${plugin.id} | v${plugin.version}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "包名: ${plugin.packageName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 权限声明
                if (plugin.permissions.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "权限: ${plugin.permissions.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onUninstall) {
                Icon(Icons.Filled.Delete, contentDescription = "卸载插件")
            }
        }
    }
}
