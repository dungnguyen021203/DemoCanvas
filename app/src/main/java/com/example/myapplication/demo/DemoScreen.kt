package com.example.myapplication.demo

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectDragGestures

// --- 1. Định nghĩa các kiểu cọ vẽ ---
enum class BrushType {
    SOLID,          // Nét liền
    DASHED,         // Nét đứt
    SHADOW_THICK    // Nét bự có shadow
}

data class DrawPath(
    val path: Path,
    val type: BrushType
)

// --- 2. Giao diện chính ---
@Composable
fun DrawingDemoScreen() {
    val context = LocalContext.current

    // State quản lý danh sách các nét đã vẽ
    var paths by remember { mutableStateOf(emptyList<DrawPath>()) }
    // State lưu nét đang vẽ (real-time)
    var currentPath by remember { mutableStateOf<Path?>(null) }

    // State chọn kiểu cọ vẽ (mặc định là Nét liền)
    var selectedBrush by remember { mutableStateOf(BrushType.SOLID) }

    // Kích thước của Canvas (để xuất ảnh Bitmap cho chuẩn)
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Hack nhỏ: Compose Path không trigger recomposition khi thay đổi nội dung,
    // nên ta dùng một biến counter để ép UI vẽ lại khi ngón tay di chuyển.
    var pathUpdateTrigger by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        // --- Thanh công cụ (Top Bar) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { selectedBrush = BrushType.SOLID },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedBrush == BrushType.SOLID) Color.Red else Color.Gray
                )
            ) { Text("Nét liền") }

            Button(
                onClick = { selectedBrush = BrushType.DASHED },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedBrush == BrushType.DASHED) Color.Red else Color.Gray
                )
            ) { Text("Nét đứt") }

            Button(
                onClick = { selectedBrush = BrushType.SHADOW_THICK },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedBrush == BrushType.SHADOW_THICK) Color.Red else Color.Gray
                )
            ) { Text("Bự + Bóng") }
        }

        Button(
            onClick = {
                // Gọi hàm tạo Bitmap và lưu vào Gallery
                if (canvasSize.width > 0 && canvasSize.height > 0) {
                    val bitmap = createBitmapFromPaths(canvasSize.width, canvasSize.height, paths)
                    saveBitmapToGallery(context, bitmap)
                }
            },
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)
        ) {
            Text("💾 LƯU ẢNH VÀO MÁY")
        }

        // --- Bảng Vẽ (Canvas) ---
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.White) // Background mặc định là Trắng
                .onSizeChanged { size -> canvasSize = size } // Bắt kích thước thực tế
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            // Khởi tạo nét vẽ mới tại điểm chạm
                            currentPath = Path().apply { moveTo(offset.x, offset.y) }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            // Kéo nối điểm
                            currentPath?.lineTo(change.position.x, change.position.y)
                            pathUpdateTrigger++ // Ép recompose
                        },
                        onDragEnd = {
                            // Lưu nét vừa vẽ vào danh sách
                            currentPath?.let {
                                paths = paths + DrawPath(it, selectedBrush)
                            }
                            currentPath = null
                        },
                        onDragCancel = { currentPath = null }
                    )
                }
        ) {
            // Đọc biến này để Compose biết cần vẽ lại mỗi khi ngón tay di chuyển
            pathUpdateTrigger.hashCode()

            // Hàm vẽ 1 Path lên Compose Canvas
            fun drawMyPath(drawPath: Path, type: BrushType) {
                val color = Color.Red // Màu đỏ hardcode theo yêu cầu
                when (type) {
                    BrushType.SOLID -> {
                        drawPath(
                            path = drawPath, color = color,
                            style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                    BrushType.DASHED -> {
                        drawPath(
                            path = drawPath, color = color,
                            style = Stroke(
                                width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(40f, 40f))
                            )
                        )
                    }
                    BrushType.SHADOW_THICK -> {
                        // Dùng drawIntoCanvas để xài Native Paint tạo Shadow thật
                        drawIntoCanvas { canvas ->
                            val nativePaint = android.graphics.Paint().apply {
                                this.color = android.graphics.Color.RED
                                this.style = android.graphics.Paint.Style.STROKE
                                this.strokeWidth = 35f // Nét bự
                                this.strokeCap = android.graphics.Paint.Cap.ROUND
                                this.strokeJoin = android.graphics.Paint.Join.ROUND
                                this.isAntiAlias = true
                                // Cài đặt đổ bóng (Bán kính mờ, x, y, màu bóng)
                                this.setShadowLayer(20f, 0f, 15f, android.graphics.Color.argb(100, 0, 0, 0))
                            }
                            // Ép kiểu Compose Path về Native Path để vẽ
                            canvas.nativeCanvas.drawPath(drawPath.asAndroidPath(), nativePaint)
                        }
                    }
                }
            }

            // 1. Vẽ lại lịch sử nét vẽ
            paths.forEach { drawMyPath(it.path, it.type) }

            // 2. Vẽ nét đang thao tác
            currentPath?.let { drawMyPath(it, selectedBrush) }
        }
    }
}

// --- 3. Hàm xử lý: Re-render các nét vẽ lên một Bitmap ---
fun createBitmapFromPaths(width: Int, height: Int, paths: List<DrawPath>): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val nativeCanvas = android.graphics.Canvas(bitmap)

    // Tô nền trắng chuẩn
    nativeCanvas.drawColor(android.graphics.Color.WHITE)

    for (drawPath in paths) {
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.RED
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
            isAntiAlias = true
        }

        when (drawPath.type) {
            BrushType.SOLID -> {
                paint.strokeWidth = 10f
            }
            BrushType.DASHED -> {
                paint.strokeWidth = 10f
                paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(40f, 40f), 0f)
            }
            BrushType.SHADOW_THICK -> {
                paint.strokeWidth = 35f
                paint.setShadowLayer(20f, 0f, 15f, android.graphics.Color.argb(100, 0, 0, 0))
            }
        }
        nativeCanvas.drawPath(drawPath.path.asAndroidPath(), paint)
    }
    return bitmap
}

// --- 4. Hàm xử lý: Lưu Bitmap vào Bộ sưu tập của máy ---
fun saveBitmapToGallery(context: Context, bitmap: Bitmap) {
    val filename = "DrawingDemo_${System.currentTimeMillis()}.png"

    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }
    }

    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

    try {
        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            Toast.makeText(context, "Đã lưu ảnh thành công vào Thư viện!", Toast.LENGTH_SHORT).show()
        } ?: run {
            Toast.makeText(context, "Không thể tạo file ảnh!", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Lỗi khi lưu ảnh: ${e.message}", Toast.LENGTH_LONG).show()
    }
}