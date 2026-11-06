package com.astra.cloud

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Navy = Color(0xFF071426)
private val Panel = Color(0xFF10243D)
private val Blue = Color(0xFF38BDF8)
private val Muted = Color(0xFF91A7C2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AstraCloudScreen() }
    }
}

data class VaultFile(val file: File) {
    val displayName: String get() = file.name.removeSuffix(".acrypt")
    val size: Long get() = file.length()
}

@Composable
private fun AstraCloudScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vault = remember { File(context.filesDir, "vault").apply { mkdirs() } }
    var files by remember { mutableStateOf<List<VaultFile>>(vault.listFiles()?.filter { it.isFile && it.extension == "acrypt" }?.map { VaultFile(it) } ?: emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Локальный сейф. Облачное подключение готовится.") }
    var selectedForShare by remember { mutableStateOf<VaultFile?>(null) }
    var transferPassword by remember { mutableStateOf("") }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var transferDialog by remember { mutableStateOf(false) }

    fun refresh() {
        files = vault.listFiles()?.filter { it.isFile && it.extension == "acrypt" }?.map { VaultFile(it) } ?: emptyList()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                busy = true
                message = "Шифрую файл…"
                try {
                    withContext(Dispatchers.IO) {
                        val name = queryDisplayName(context, uri)
                        val target = File(vault, safeName(name) + ".acrypt")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
                            FileOutputStream(target).use { output ->
                                output.write(cipher.iv)
                                val encrypted = cipher.doFinal(input.readBytes())
                                output.write(encrypted)
                            }
                        } ?: error("Не удалось открыть файл")
                    }
                    refresh()
                    message = "Файл сохранён в зашифрованном сейфе"
                } catch (e: Exception) {
                    message = "Ошибка: ${e.localizedMessage ?: "не удалось зашифровать"}"
                }
                busy = false
            }
        }
    }

    fun shareFile(item: VaultFile) {
        selectedForShare = item
        pendingImport = null
        transferPassword = ""
        transferDialog = true
    }

    val portablePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            pendingImport = uri
            selectedForShare = null
            transferPassword = ""
            transferDialog = true
        }
    }

    fun performTransfer() {
        val password = transferPassword
        if (password.length < 12) { message = "Пароль должен содержать не менее 12 символов"; return }
        val sharing = selectedForShare
        val importing = pendingImport
        transferDialog = false
        scope.launch {
            busy = true
            message = if (sharing != null) "Создаю зашифрованный файл…" else "Проверяю и расшифровываю файл…"
            try {
                if (sharing != null) {
                    val exported = withContext(Dispatchers.IO) {
                        val raw = sharing.file.readBytes()
                        require(raw.size > 12) { "Повреждён файл сейфа" }
                        val iv = raw.copyOfRange(0, 12)
                        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
                        val plain = cipher.doFinal(raw.copyOfRange(12, raw.size))
                        val out = File(context.cacheDir, "shared").apply { mkdirs() }
                        val file = File(out, safeName(sharing.displayName) + ".astra")
                        encryptPortable(plain, password, file)
                        file
                    }
                    val uri = FileProvider.getUriForFile(context, "com.astra.cloud.fileprovider", exported)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/octet-stream"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "Зашифрованный файл Astra Cloud")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Отправить зашифрованный файл"))
                    message = "Отправляется файл .astra. Передай получателю пароль отдельно."
                } else if (importing != null) {
                    withContext(Dispatchers.IO) {
                        val plain = context.contentResolver.openInputStream(importing)?.use { decryptPortable(it.readBytes(), password) }
                            ?: error("Не удалось открыть файл")
                        val name = queryDisplayName(context, importing).removeSuffix(".astra")
                        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
                        val target = File(vault, safeName(name) + ".acrypt")
                        FileOutputStream(target).use { output -> output.write(cipher.iv); output.write(cipher.doFinal(plain)) }
                    }
                    refresh()
                    message = "Файл расшифрован и сохранён в локальный сейф"
                }
            } catch (e: Exception) {
                message = if (sharing != null) "Не удалось создать защищённую копию: ${e.localizedMessage ?: "ошибка"}" else "Неверный пароль или повреждённый файл"
            }
            busy = false
            selectedForShare = null
            pendingImport = null
        }
    }

    val used: Long = files.fold(0L) { total, item -> total + item.size }
    val usedMb = used / (1024f * 1024f)

    MaterialTheme(colorScheme = darkColorScheme(
        background = Navy, surface = Panel, primary = Blue,
        onBackground = Color.White, onSurface = Color.White,
        secondary = Color(0xFF7C8FA8)
    )) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1E35), Navy)))) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).background(Brush.linearGradient(listOf(Blue, Color(0xFF2563EB))), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Cloud, null, tint = Color.White, modifier = Modifier.size(27.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("ASTRA CLOUD", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                        Text("Личное защищённое хранилище", color = Muted, fontSize = 11.sp)
                    }
                    Box(Modifier.size(38.dp).background(Color(0xFF142B46), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Shield, null, tint = Blue)
                    }
                }

                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF102A49)), shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("МОЁ ХРАНИЛИЩЕ", color = Muted, fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                Text("${"%.1f".format(usedMb)} МБ", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                                Text("${files.size} файлов в сейфе", color = Muted, fontSize = 12.sp)
                            }
                            Box(Modifier.size(76.dp).background(Color(0xFF173B5D), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Lock, null, tint = Blue, modifier = Modifier.size(34.dp))
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        LinearProgressIndicator(progress = { (usedMb / 1024f).coerceIn(0.015f, 1f) }, modifier = Modifier.fillMaxWidth().height(7.dp), color = Blue, trackColor = Color(0xFF24415F))
                        Spacer(Modifier.height(9.dp))
                        Text("Локальный зашифрованный сейф • облако подключим следующим этапом", color = Muted, fontSize = 11.sp)
                    }
                }

                Spacer(Modifier.height(20.dp))
                Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Blue, contentColor = Navy)) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Добавить файл в сейф", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { portablePicker.launch(arrayOf("application/octet-stream", "*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Default.LockOpen, null); Spacer(Modifier.width(8.dp)); Text("Получить зашифрованный файл")
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    FeatureChip(Icons.Default.EnhancedEncryption, "AES-256", Modifier.weight(1f))
                    FeatureChip(Icons.Default.PhoneAndroid, "Только это устройство", Modifier.weight(1f))
                }
                Spacer(Modifier.height(23.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Мои файлы", fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { refresh() }) { Icon(Icons.Default.Refresh, null, tint = Blue); Text("Обновить", color = Blue) }
                }
                if (files.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.FolderOpen, null, tint = Muted, modifier = Modifier.size(54.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Здесь пока пусто", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Добавь первый файл — он будет зашифрован", color = Muted, fontSize = 12.sp)
                        }
                    }
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                        items(files, key = { it.file.name }) { item ->
                            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(17.dp)) {
                                Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(42.dp).background(Color(0xFF183653), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.InsertDriveFile, null, tint = Blue)
                                    }
                                    Spacer(Modifier.width(11.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(item.displayName, maxLines = 1, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(formatSize(item.size), color = Muted, fontSize = 11.sp)
                                    }
                                    IconButton(onClick = { shareFile(item) }) { Icon(Icons.Default.Share, "Поделиться", tint = Blue) }
                                    IconButton(onClick = { item.file.delete(); refresh() }) { Icon(Icons.Default.DeleteOutline, "Удалить", tint = Muted) }
                                }
                            }
                        }
                    }
                }
                if (busy || message.isNotBlank()) {
                    Text(message, color = if (busy) Blue else Muted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, null, tint = Color(0xFF34D399), modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Твои файлы. Твои ключи. Твой контроль.", color = Muted, fontSize = 10.sp)
                }
            }
        }
        if (transferDialog) {
            AlertDialog(
                onDismissRequest = { transferDialog = false; selectedForShare = null; pendingImport = null },
                title = { Text(if (selectedForShare != null) "Защищённая отправка" else "Открыть файл Astra") },
                text = {
                    Column {
                        Text(if (selectedForShare != null) "Файл будет заново зашифрован отдельным паролем. Отправляй пароль получателю другим способом." else "Введи пароль, который отправитель передал отдельно.")
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(value = transferPassword, onValueChange = { transferPassword = it }, label = { Text("Пароль (минимум 12 символов)") }, singleLine = true)
                    }
                },
                confirmButton = { TextButton(onClick = { performTransfer() }) { Text(if (selectedForShare != null) "Зашифровать и отправить" else "Расшифровать") } },
                dismissButton = { TextButton(onClick = { transferDialog = false; selectedForShare = null; pendingImport = null }) { Text("Отмена") } }
            )
        }
    }
}

@Composable
private fun FeatureChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier = Modifier) {
    Row(modifier.background(Color(0xFF112944), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = Blue, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color(0xFFBDD0E4), fontSize = 10.sp, maxLines = 1)
    }
}

private fun getOrCreateKey(): SecretKey {
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (store.getKey("astra_cloud_vault", null) as? SecretKey)?.let { return it }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    generator.init(KeyGenParameterSpec.Builder("astra_cloud_vault", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .setRandomizedEncryptionRequired(true)
        .build())
    return generator.generateKey()
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use { if (it.moveToFirst()) {
        val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (index >= 0) return it.getString(index) ?: "file"
    } }
    return "file"
}
private fun safeName(value: String) = value.replace(Regex("[^a-zA-Z0-9._ -]"), "_").take(100).ifBlank { "file" }
private fun formatSize(size: Long): String = if (size < 1024) "$size Б" else if (size < 1024 * 1024) "${size / 1024} КБ" else "${"%.1f".format(size / 1048576f)} МБ"


private fun encryptPortable(plain: ByteArray, password: String, target: File) {
    val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
    val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
    val spec = PBEKeySpec(password.toCharArray(), salt, 600_000, 256)
    val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    spec.clearPassword()
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
    target.outputStream().use { out ->
        out.write("ASTRAC01".toByteArray(Charsets.US_ASCII))
        out.write(salt); out.write(iv); out.write(cipher.doFinal(plain))
    }
    keyBytes.fill(0)
}

private fun decryptPortable(data: ByteArray, password: String): ByteArray {
    require(data.size >= 8 + 16 + 12 + 16) { "Файл слишком короткий" }
    require(String(data.copyOfRange(0, 8), Charsets.US_ASCII) == "ASTRAC01") { "Неподдерживаемый формат Astra" }
    val salt = data.copyOfRange(8, 24)
    val iv = data.copyOfRange(24, 36)
    val spec = PBEKeySpec(password.toCharArray(), salt, 600_000, 256)
    val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    spec.clearPassword()
    return try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        cipher.doFinal(data.copyOfRange(36, data.size))
    } finally { keyBytes.fill(0) }
}
