package com.example.myapplication.demo

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectDragGestures
// Cần import cái này để dùng .asAndroidPath()
import androidx.compose.ui.graphics.asAndroidPath
import com.example.myapplication.R

// --- 1. Định nghĩa các kiểu cọ vẽ (Đã update PATTERN) ---
enum class BrushType1 {
    SOLID,          // Nét liền
    DASHED,         // Nét đứt
    SHADOW_THICK,   // Nét bự có shadow
    PATTERN, // Cọ ngôi sao lấp lánh
}

// Data class đã update để lưu Points thay vì Path nếu là Pattern
data class DrawAction(
    val type: BrushType1,
    val path: Path = Path(), // Dùng cho SOLID, DASHED, SHADOW
    val points: List<Offset> = emptyList(), // Dùng cho PATTERN
    val color: Color = Color.Red // Màu hardcode
)

// --- 2. Giao diện chính ---
@Composable
fun AdvancedDrawingDemoScreen() {
    val context = LocalContext.current

    // State quản lý lịch sử các nét vẽ
    var drawActions by remember { mutableStateOf(emptyList<DrawAction>()) }

    // State nét đang vẽ dở (cho nét line standard)
    var currentStandardPath by remember { mutableStateOf<Path?>(null) }

    // State nét đang vẽ dở (cho nét PATTERN)
    var currentPatternPoints = remember { mutableStateListOf<Offset>() }

    // State chọn kiểu cọ
    var selectedBrush by remember { mutableStateOf(BrushType1.SOLID) }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var pathUpdateTrigger by remember { mutableIntStateOf(0) }

    // **Load ảnh pattern ngôi sao** (đã tạo ở res/drawable/ic_star_pattern.xml)
    //val starBitmap = ImageBitmap.imageResource(id = R.drawable.ic_star_pattern)
    val starBitmap = remember {
        getBitmapFromVectorDrawable(context, R.drawable.ic_star_pattern)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // --- Thanh công cụ (Top Bar) - 4 OPTIONS ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val modifier = Modifier.weight(1f).padding(2.dp)
            val redColors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            val grayColors = ButtonDefaults.buttonColors(containerColor = Color.Gray)

            Button(onClick = { selectedBrush = BrushType1.SOLID }, modifier = modifier,
                colors = if (selectedBrush == BrushType1.SOLID) redColors else grayColors
            ) { Text("Nét\nliền", style = MaterialTheme.typography.bodySmall) }

            Button(onClick = { selectedBrush = BrushType1.DASHED }, modifier = modifier,
                colors = if (selectedBrush == BrushType1.DASHED) redColors else grayColors
            ) { Text("Nét\nđứt", style = MaterialTheme.typography.bodySmall) }

            Button(onClick = { selectedBrush = BrushType1.SHADOW_THICK }, modifier = modifier,
                colors = if (selectedBrush == BrushType1.SHADOW_THICK) redColors else grayColors
            ) { Text("Bự\nBóng", style = MaterialTheme.typography.bodySmall) }

            Button(onClick = { selectedBrush = BrushType1.PATTERN }, modifier = modifier,
                colors = if (selectedBrush == BrushType1.PATTERN) redColors else grayColors
            ) { Text("⭐\nSao", style = MaterialTheme.typography.bodySmall) }
        }

        Button(
            onClick = {
                if (canvasSize.width > 0 && canvasSize.height > 0) {
                    val bitmap = createBitmapFromActions(canvasSize.width, canvasSize.height, drawActions, starBitmap)
                    saveBitmapToGallery1(context, bitmap)
                }
            },
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)
        ) { Text("💾 LƯU ẢNH VÀO MÁY") }

        // --- Bảng Vẽ (Canvas) ---
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.White)
                .onSizeChanged { size -> canvasSize = size }
                .pointerInput(selectedBrush) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (selectedBrush == BrushType1.PATTERN) {
                                currentPatternPoints.clear()
                                currentPatternPoints.add(offset)
                            } else {
                                currentStandardPath = Path().apply { moveTo(offset.x, offset.y) }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (selectedBrush == BrushType1.PATTERN) {
                                // 1. Lấy tọa độ của ngôi sao gần nhất vừa được vẽ
                                val lastPoint = currentPatternPoints.lastOrNull()

                                if (lastPoint != null) {
                                    // 2. Tính khoảng cách giữa điểm hiện tại và ngôi sao trước đó
                                    val distance = (change.position - lastPoint).getDistance()

                                    // 3. Thay đổi con số 80f này để chỉnh độ thưa/dày của các ngôi sao
                                    val spacingThreshold = 80f

                                    if (distance > spacingThreshold) {
                                        currentPatternPoints.add(change.position)
                                    }
                                } else {
                                    currentPatternPoints.add(change.position)
                                }
                            } else {
                                currentStandardPath?.lineTo(change.position.x, change.position.y)
                            }
                            pathUpdateTrigger++
                        },
                        onDragEnd = {
                            if (selectedBrush == BrushType1.PATTERN) {
                                drawActions = drawActions + DrawAction(
                                    type = BrushType1.PATTERN,
                                    points = currentPatternPoints.toList()
                                )
                                currentPatternPoints.clear()
                            } else {
                                currentStandardPath?.let {
                                    drawActions = drawActions + DrawAction(type = selectedBrush, path = it)
                                }
                                currentStandardPath = null
                            }
                        },
                        onDragCancel = {
                            currentStandardPath = null
                            currentPatternPoints.clear()
                        }
                    )
                }
        ) {
            pathUpdateTrigger.hashCode() // Ép recompose

            // --- Hàm vẽ action thực tế (trên UI) ---
            fun drawMyAction(action: DrawAction, activePoints: List<Offset>? = null) {
                when (action.type) {
                    BrushType1.SOLID -> {
                        drawPath(path = action.path, color = action.color,
                            style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                    BrushType1.DASHED -> {
                        drawPath(path = action.path, color = action.color,
                            style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(40f, 40f))
                            )
                        )
                    }
                    BrushType1.SHADOW_THICK -> {
                        drawIntoCanvas { canvas ->
                            val nativePaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.RED
                                style = android.graphics.Paint.Style.STROKE
                                strokeWidth = 35f
                                strokeCap = android.graphics.Paint.Cap.ROUND
                                strokeJoin = android.graphics.Paint.Join.ROUND
                                isAntiAlias = true
                                setShadowLayer(20f, 0f, 15f, android.graphics.Color.argb(100, 0, 0, 0))
                            }
                            canvas.nativeCanvas.drawPath(action.path.asAndroidPath(), nativePaint)
                        }
                    }
                    BrushType1.PATTERN -> {
                        // **Logic vẽ PATTERN:** Lặp qua danh sách điểm và dán ảnh
                        val pointsToDraw = activePoints ?: action.points
                        pointsToDraw.forEach { offset ->
                            // Vẽ ảnh centered tại điểm chạm (offset đi một nửa chiều bự của ảnh)
                            drawImage(
                                image = starBitmap,
                                topLeft = Offset(
                                    offset.x - starBitmap.width / 2f,
                                    offset.y - starBitmap.height / 2f
                                )
                            )
                        }
                    }
                }
            }

            // 1. Vẽ lịch sử
            drawActions.forEach { drawMyAction(it) }

            // 2. Vẽ nét đang thao tác
            currentStandardPath?.let { drawMyAction(DrawAction(selectedBrush, path = it)) }

            // Vẽ nét PATTERN đang thao tác (real-time)
            if (selectedBrush == BrushType1.PATTERN && currentPatternPoints.isNotEmpty()) {
                drawMyAction(DrawAction(BrushType1.PATTERN), activePoints = currentPatternPoints)
            }
        }
    }
}

// --- 3. Hàm xử lý: Re-render lên Bitmap (Đã update PATTERN) ---
fun createBitmapFromActions(width: Int, height: Int, actions: List<DrawAction>, patternBitmap: ImageBitmap): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val nativeCanvas = android.graphics.Canvas(bitmap)

    nativeCanvas.drawColor(android.graphics.Color.WHITE)

    for (action in actions) {
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.RED
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
            isAntiAlias = true
        }

        when (action.type) {
            BrushType1.SOLID -> {
                paint.strokeWidth = 10f
                nativeCanvas.drawPath(action.path.asAndroidPath(), paint)
            }
            BrushType1.DASHED -> {
                paint.strokeWidth = 10f
                paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(40f, 40f), 0f)
                nativeCanvas.drawPath(action.path.asAndroidPath(), paint)
            }
            BrushType1.SHADOW_THICK -> {
                paint.strokeWidth = 35f
                paint.setShadowLayer(20f, 0f, 15f, android.graphics.Color.argb(100, 0, 0, 0))
                nativeCanvas.drawPath(action.path.asAndroidPath(), paint)
            }
            BrushType1.PATTERN -> {
                // **Logic lưu PATTERN vào ảnh:** Chuyển ImageBitmap về Native Bitmap để dán
                val androidBitmap = patternBitmap.asAndroidBitmap()
                action.points.forEach { offset ->
                    nativeCanvas.drawBitmap(
                        androidBitmap,
                        offset.x - androidBitmap.width / 2f,
                        offset.y - androidBitmap.height / 2f,
                        null
                    )
                }
            }
        }
    }
    return bitmap
}

// --- 4. Hàm xử lý: Lưu ảnh (Giữ nguyên) ---
fun saveBitmapToGallery1(context: Context, bitmap: Bitmap) {
    val filename = "DrawingPattern_${System.currentTimeMillis()}.png"

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
            Toast.makeText(context, "Đã lưu ảnh!", Toast.LENGTH_SHORT).show()
        } ?: run { Toast.makeText(context, "Lỗi tạo file!", Toast.LENGTH_SHORT).show() }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_LONG).show()
    }


}

fun getBitmapFromVectorDrawable(context: Context, drawableId: Int): ImageBitmap {
    val drawable = ContextCompat.getDrawable(context, drawableId) ?: return ImageBitmap(1, 1)
    val bitmap = Bitmap.createBitmap(
        drawable.intrinsicWidth,
        drawable.intrinsicHeight,
        Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap.asImageBitmap()
}
