package com.winter.muplayer.base_ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.winter.muplayer.base_ui.ui.theme.itemBorderColor
import com.winter.muplayer.plugin_manager.ShadowPluginHost
import com.winter.muplayer.plugin_manager.PluginInstallInfo

/**
 * 插件管理界面～（Shadow 架构版）
 *
 * 基于 [ShadowPluginHost] 进行插件的安装、卸载、加载、卸载操作。
 * 插件以 APK 文件形式存放于应用私有目录，通过 DexClassLoader 动态加载。
 *
 * Shadow 架构下的变更：
 * - 插件无需预先系统安装，通过 PluginManager.install() 存入私有目录
 * - 加载通过 DexClassLoader 同进程加载，零 IPC 开销
 * - 支持热插拔：安装/卸载不需要重启 App
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginManagerScreen(
    shadowPluginHost: ShadowPluginHost,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current

    // 从 StateFlow 收集安装信息
    val installedPlugins by shadowPluginHost.installedPlugins.collectAsState()
    var isLoading by remember { mutableStateOf(false) }

    // 文件选择器——选一个 APK 文件来安装
    val installLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // 通过 Shadow 宿主安装插件 APK
        shadowPluginHost.install(uri, autoLoad = true)
        Toast.makeText(context, "插件安装中...", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // 顶部栏：标题 + 操作按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        painterResource(R.drawable.ic_arrow_back),
                        contentDescription = "返回"
                    )
                }
                Text("插件管理", style = MaterialTheme.typography.headlineSmall)
            }
            Row(
                modifier = Modifier.padding(end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 刷新按钮
                IconButton(onClick = {
                    isLoading = true
                    shadowPluginHost.refresh()
                    isLoading = false
                }) {
                    Icon(
                        if (isLoading) painterResource(R.drawable.ic_hourglass_top)
                        else painterResource(R.drawable.ic_refresh),
                        contentDescription = "刷新"
                    )
                }
                // 安装 APK 按钮
                Button(onClick = {
                    installLauncher.launch(arrayOf("application/vnd.android.package-archive"))
                }) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("安装 APK")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 说明小提示
        Text(
            "插件通过 Shadow 框架动态加载，无需系统安装。支持热插拔。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 48.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 插件列表区域
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (installedPlugins.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(R.drawable.ic_extendsion_off),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("暂无已安装的插件", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "点击「安装 APK」从文件管理器选择插件文件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(items = installedPlugins, key = { it.metadata.id }) { info ->
                    PluginCard(
                        info = info,
                        onUninstall = {
                            shadowPluginHost.uninstall(info.metadata.id)
                            Toast.makeText(context, "插件已卸载", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

/**
 * 一个插件的信息卡片～
 * 展示插件的名字、类型、版本，以及卸载按钮。
 */
@Composable
private fun PluginCard(
    info: PluginInstallInfo,
    onUninstall: () -> Unit
) {
    val metadata = info.metadata
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧图标
            Icon(
                painter = painterResource(R.drawable.ic_extendsion),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))

            // 中间信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = metadata.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${metadata.type} · v${metadata.version}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (metadata.permissions.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "权限: ${metadata.permissions.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // 卸载按钮
            IconButton(onClick = onUninstall) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = "卸载",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
