package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                StickerDemoScreen()
            }
        }
    }
}

// 1. Data model cho Sticker
data class StickerModel(val id: String, val url: String)

// 2. Sample data: Sử dụng một số icon dạng PNG có nền trong suốt để làm sticker
val sampleStickers = listOf(
    StickerModel("1", "https://cdn-icons-png.flaticon.com/512/4305/4305315.png"), // Cute cat
    StickerModel("2", "https://cdn-icons-png.flaticon.com/512/2626/2626284.png"), // Coffee cup
    StickerModel("3", "https://cdn-icons-png.flaticon.com/512/2873/2873111.png"), // Thumbs up
    StickerModel("4", "https://e7.pngegg.com/pngimages/584/916/png-clipart-bright-blue-sky-aqua-blue-button-rounded-blank-download-free-download-png-download-vector-download-svg-download-transparent-thumbnail.png") // Your example
)

// 3. UI Màn hình chính
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerDemoScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isProcessing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Demo WhatsApp Sticker") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(sampleStickers) { sticker ->
                    StickerItem(
                        sticker = sticker,
                        onClickShare = { url ->
                            if (isProcessing) return@StickerItem
                            isProcessing = true

                            coroutineScope.launch(Dispatchers.IO) {
                                shareStickerToWhatsApp(context, url) { message ->
                                    coroutineScope.launch(Dispatchers.Main) {
                                        isProcessing = false
                                        snackbarHostState.showSnackbar(message)
                                    }
                                }
                            }
                        }
                    )
                }
            }

            // Hiển thị loading indicator nếu đang xử lý tải/convert ảnh
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
fun StickerItem(sticker: StickerModel, onClickShare: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            // Load ảnh bằng Coil để hiển thị trên UI
            AsyncImage(
                model = sticker.url,
                contentDescription = "Sticker",
                modifier = Modifier.size(100.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { onClickShare(sticker.url) }) {
                Text("Gửi WhatsApp")
            }
        }
    }
}

// 4. Logic xử lý: Tải, Convert sang WebP và bắn Intent
suspend fun shareStickerToWhatsApp(context: Context, imageUrl: String, onResult: (String) -> Unit) {
    if (!isWhatsAppInstalled(context)) {
        onResult("App WhatsApp chưa được cài đặt trên thiết bị này!")
        return
    }

    try {
        // Tải ảnh qua Coil
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .allowHardware(false) // Cần để thao tác với pixels
            .build()

        val result = loader.execute(request)
        if (result !is SuccessResult) {
            onResult("Tải ảnh thất bại! Vui lòng kiểm tra mạng.")
            return
        }

        val bitmap = (result.drawable as BitmapDrawable).bitmap

        // Tạo thư mục cache và file
        val cachePath = File(context.cacheDir, "stickers")
        if (!cachePath.exists()) cachePath.mkdirs()

        val file = File(cachePath, "shared_sticker_${System.currentTimeMillis()}.webp")

        // Chuyển thao tác ghi file sang IO thread
        withContext(Dispatchers.IO) {
            val stream = FileOutputStream(file)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 100, stream)
            } else {
                @Suppress("DEPRECATION")
                bitmap.compress(Bitmap.CompressFormat.WEBP, 100, stream)
            }
            stream.close()
        }

        // Lấy URI thông qua FileProvider
        val authority = "${context.packageName}.fileprovider"
        val uri: Uri = FileProvider.getUriForFile(context, authority, file)

        // Bắn Intent
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/webp"
            putExtra(Intent.EXTRA_STREAM, uri)
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // Phải chạy activity trên main thread, nếu đang ở IO thì dùng context.startActivity bình thường
        // nhưng an toàn nhất là switch context khi UI components gọi.
        context.startActivity(intent)

        // Không gọi onResult("Thành công") nữa vì app sẽ chuyển sang WhatsApp,
        // nếu hiện Snackbar lúc này user sẽ không kịp thấy.
        withContext(Dispatchers.Main) {
            // Có thể reset trạng thái loading ở đây (đã được handle ở scope cha)
        }

    } catch (e: Exception) {
        e.printStackTrace()
        onResult("Đã xảy ra lỗi: ${e.localizedMessage}")
    }
}

fun isWhatsAppInstalled(context: Context): Boolean {
    val packageManager = context.packageManager
    return try {
        packageManager.getPackageInfo("com.whatsapp", PackageManager.GET_ACTIVITIES)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}