package com.example.yumoflatimagemanager.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ScaleGestureDetector
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toRectF
import com.bumptech.glide.Glide
import com.example.yumoflatimagemanager.R
import com.example.yumoflatimagemanager.data.WatermarkState
import com.example.yumoflatimagemanager.data.WatermarkType
import com.example.yumoflatimagemanager.data.ImageWatermarkParams
import com.github.chrisbanes.photoview.PhotoView
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class WatermarkPreviewActivity : AppCompatActivity() {
    
    private lateinit var photoView: PhotoView
    private lateinit var watermarkOverlay: WatermarkOverlayView
    private lateinit var confirmButton: android.widget.Button
    private lateinit var prevButton: android.widget.ImageButton
    private lateinit var nextButton: android.widget.ImageButton
    private lateinit var imageCounter: android.widget.TextView
    
    // 图片列表和当前索引
    private lateinit var imageUris: List<Uri>
    private var currentIndex: Int = 0
    
    // 水印参数
    private var watermarkText: String = ""
    private var watermarkSize: Float = 48f
    private var watermarkAlpha: Int = 200
    
    // 水印预设（支持文字和图片水印）
    private var watermarkPreset: com.example.yumoflatimagemanager.data.WatermarkPreset? = null
    
    // 每张图片的水印参数
    private val imageWatermarkParams = mutableMapOf<String, ImageWatermarkParams>()
    
    companion object {
        const val EXTRA_IMAGE_URIS = "image_uris"
        const val EXTRA_CURRENT_INDEX = "current_index"
        const val EXTRA_WATERMARK_TEXT = "watermark_text"
        const val EXTRA_WATERMARK_SIZE = "watermark_size"
        const val EXTRA_WATERMARK_ALPHA = "watermark_alpha"
        const val EXTRA_WATERMARK_X = "watermark_x"
        const val EXTRA_WATERMARK_Y = "watermark_y"
        const val EXTRA_WATERMARK_PRESET = "watermark_preset"
        const val RESULT_WATERMARK_PARAMS = "result_watermark_params"
        
        fun start(
            context: Context,
            imageUris: List<Uri>,
            currentIndex: Int = 0,
            watermarkText: String,
            watermarkSize: Float,
            watermarkAlpha: Int,
            watermarkX: Float,
            watermarkY: Float,
            preset: com.example.yumoflatimagemanager.data.WatermarkPreset? = null,
            launcher: ActivityResultLauncher<Intent>
        ) {
            val intent = Intent(context, WatermarkPreviewActivity::class.java).apply {
                putStringArrayListExtra(EXTRA_IMAGE_URIS, ArrayList(imageUris.map { it.toString() }))
                putExtra(EXTRA_CURRENT_INDEX, currentIndex)
                putExtra(EXTRA_WATERMARK_TEXT, watermarkText)
                putExtra(EXTRA_WATERMARK_SIZE, watermarkSize)
                putExtra(EXTRA_WATERMARK_ALPHA, watermarkAlpha)
                putExtra(EXTRA_WATERMARK_X, watermarkX)
                putExtra(EXTRA_WATERMARK_Y, watermarkY)
                preset?.let { putExtra(EXTRA_WATERMARK_PRESET, it) }
            }
            launcher.launch(intent)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_watermark_preview)
        
        // 解析传入参数
        val imageUriStrings = intent.getStringArrayListExtra(EXTRA_IMAGE_URIS) ?: emptyList()
        imageUris = imageUriStrings.map { Uri.parse(it) }
        currentIndex = intent.getIntExtra(EXTRA_CURRENT_INDEX, 0)
        watermarkText = intent.getStringExtra(EXTRA_WATERMARK_TEXT) ?: ""
        watermarkSize = intent.getFloatExtra(EXTRA_WATERMARK_SIZE, 48f)
        watermarkAlpha = intent.getIntExtra(EXTRA_WATERMARK_ALPHA, 200)
        watermarkPreset = intent.getParcelableExtra(EXTRA_WATERMARK_PRESET)
        
        // 初始化每张图片的默认水印参数
        val defaultX = intent.getFloatExtra(EXTRA_WATERMARK_X, 0.9f)
        val defaultY = intent.getFloatExtra(EXTRA_WATERMARK_Y, 0.9f)
        imageUris.forEach { uri ->
            imageWatermarkParams[uri.toString()] = ImageWatermarkParams(
                imageUri = uri.toString(),
                watermarkX = defaultX,
                watermarkY = defaultY,
                watermarkScale = 1.0f,
                watermarkRotation = 0f,
                watermarkAlpha = watermarkAlpha
            )
        }
        
        initViews()
        setupPhotoView()
        setupWatermarkOverlay()
        setupControls()
        updateImageCounter()
    }
    
    private fun initViews() {
        photoView = findViewById(R.id.photo_view)
        watermarkOverlay = findViewById(R.id.watermark_overlay)
        confirmButton = findViewById(R.id.confirm_button)
        prevButton = findViewById(R.id.prev_button)
        nextButton = findViewById(R.id.next_button)
        imageCounter = findViewById(R.id.image_counter)
        
        findViewById<android.widget.ImageButton>(R.id.close_button).setOnClickListener {
            finish()
        }
        
        prevButton.setOnClickListener {
            switchToImage(currentIndex - 1)
        }
        
        nextButton.setOnClickListener {
            switchToImage(currentIndex + 1)
        }
    }
    
    private fun setupPhotoView() {
        loadCurrentImage()
        
        // 配置PhotoView
        photoView.maximumScale = 5.0f
        photoView.mediumScale = 2.5f
        photoView.minimumScale = 1.0f
        
        // 监听PhotoView的矩阵变化，同步更新水印位置
        photoView.setOnMatrixChangeListener {
            watermarkOverlay.updatePhotoViewMatrix(photoView.displayRect)
        }
    }
    
    private fun loadCurrentImage() {
        if (imageUris.isNotEmpty() && currentIndex in imageUris.indices) {
            val currentUri = imageUris[currentIndex]
            Glide.with(this)
                .load(currentUri)
                .into(photoView)
        }
    }
    
    private fun setupWatermarkOverlay() {
        // 设置水印覆盖层
        val currentParams = getCurrentImageParams()
        watermarkOverlay.setWatermarkParamsWithPreset(
            watermarkPreset,
            watermarkText, 
            watermarkSize, 
            currentParams.watermarkAlpha, 
            currentParams.watermarkX, 
            currentParams.watermarkY, 
            currentParams.watermarkRotation,
            currentParams.watermarkScale
        )
        watermarkOverlay.setPhotoView(photoView)
        
        // 监听水印选中状态变化，控制PhotoView的缩放
        watermarkOverlay.setOnSelectionChangeListener { isSelected ->
            // PhotoView的isZoomEnabled是只读的，我们需要通过其他方式控制
            // 这里暂时不实现，因为PhotoView没有直接的API来禁用缩放
        }
        
        // 监听水印拖动
        watermarkOverlay.setOnWatermarkDragListener { newX, newY ->
            // 更新参数
            updateCurrentImageParams { it.copy(watermarkX = newX, watermarkY = newY) }
            // 直接更新WatermarkOverlayView的内部状态
            watermarkOverlay.watermarkX = newX
            watermarkOverlay.watermarkY = newY
            watermarkOverlay.invalidate()
        }
        
        // 监听水印缩放
        watermarkOverlay.setOnWatermarkScaleListener { newScale ->
            updateCurrentImageParams { it.copy(watermarkScale = newScale) }
        }
        
        // 监听水印旋转
        watermarkOverlay.setOnWatermarkRotationListener { newRotation ->
            updateCurrentImageParams { it.copy(watermarkRotation = newRotation) }
        }
    }
    
    private fun setupControls() {
        // 确认按钮
        confirmButton.setOnClickListener {
            // 保存当前图片的水印参数
            saveCurrentImageParams()
            
            // 按照图片顺序构建参数列表（关键修复！确保顺序一致）
            val orderedParams = imageUris.map { uri ->
                imageWatermarkParams[uri.toString()] ?: ImageWatermarkParams(
                    imageUri = uri.toString(),
                    watermarkX = 0.9f,
                    watermarkY = 0.9f,
                    watermarkScale = 1.0f,
                    watermarkRotation = 0f,
                    watermarkAlpha = watermarkAlpha
                )
            }
            
            // 添加调试日志
            android.util.Log.d("WatermarkPreview", "返回 ${orderedParams.size} 个参数")
            orderedParams.forEachIndexed { index, params ->
                android.util.Log.d("WatermarkPreview", 
                    "图片$index: uri=${params.imageUri}, " +
                    "x=${params.watermarkX}, y=${params.watermarkY}, " +
                    "rotation=${params.watermarkRotation}, scale=${params.watermarkScale}"
                )
            }
            
            val resultIntent = Intent().apply {
                putParcelableArrayListExtra(RESULT_WATERMARK_PARAMS, ArrayList(orderedParams))
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }
    
    private fun switchToImage(newIndex: Int) {
        if (newIndex in imageUris.indices) {
            // 保存当前图片的水印参数
            saveCurrentImageParams()
            
            // 切换到新图片
            currentIndex = newIndex
            loadCurrentImage()
            
            // 加载新图片的水印参数
            val newParams = getCurrentImageParams()
            watermarkOverlay.setWatermarkParams(
                watermarkText,
                watermarkSize,
                newParams.watermarkAlpha,
                newParams.watermarkX,
                newParams.watermarkY,
                newParams.watermarkRotation,
                newParams.watermarkScale
            )
            
            updateImageCounter()
            updateNavigationButtons()
        }
    }
    
    private fun getCurrentImageParams(): ImageWatermarkParams {
        val currentUri = imageUris[currentIndex].toString()
        return imageWatermarkParams[currentUri] ?: ImageWatermarkParams(
            imageUri = currentUri,
            watermarkX = 0.9f,
            watermarkY = 0.9f,
            watermarkScale = 1.0f,
            watermarkRotation = 0f,
            watermarkAlpha = watermarkAlpha
        )
    }
    
    private fun updateCurrentImageParams(update: (ImageWatermarkParams) -> ImageWatermarkParams) {
        val currentUri = imageUris[currentIndex].toString()
        val currentParams = getCurrentImageParams()
        imageWatermarkParams[currentUri] = update(currentParams)
    }
    
    private fun saveCurrentImageParams() {
        val currentUri = imageUris[currentIndex].toString()
        // 从WatermarkOverlayView获取最新的参数值
        val overlayParams = watermarkOverlay.getCurrentWatermarkParams()
        val currentParams = overlayParams.copy(imageUri = currentUri)
        imageWatermarkParams[currentUri] = currentParams
    }
    
    private fun updateImageCounter() {
        imageCounter.text = "${currentIndex + 1}/${imageUris.size}"
    }
    
    private fun updateNavigationButtons() {
        prevButton.isEnabled = currentIndex > 0
        nextButton.isEnabled = currentIndex < imageUris.size - 1
    }
}

// 自定义水印覆盖层View - Procreate风格
class WatermarkOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    // 水印参数
    private var watermarkText: String = ""
    private var watermarkSize: Float = 48f
    private var watermarkAlpha: Int = 200
    var watermarkX: Float = 0.9f
    var watermarkY: Float = 0.9f
    private var watermarkRotation: Float = 0f
    private var watermarkScale: Float = 1.0f
    
    // 水印预设和图片
    private var watermarkPreset: com.example.yumoflatimagemanager.data.WatermarkPreset? = null
    private var watermarkBitmap: android.graphics.Bitmap? = null
    
    // 视图引用
    private var photoView: PhotoView? = null
    private var displayRect: RectF? = null
    
    // 选择状态
    private var isWatermarkSelected: Boolean = false
    private var watermarkBounds: RectF = RectF()
    private var rotateButtonCenter: PointF = PointF()
    private var rotateButtonRadius: Float = 30f
    
    // 设置水印选中状态并控制动画
    private fun setWatermarkSelected(selected: Boolean) {
        if (isWatermarkSelected != selected) {
            isWatermarkSelected = selected
            if (selected) {
                dashAnimator.start()
            } else {
                dashAnimator.cancel()
            }
            onSelectionChangeListener?.invoke(selected)
            invalidate()
        }
    }
    
    // 触摸状态
    private var isDragging: Boolean = false
    private var isRotating: Boolean = false
    private var isScaling: Boolean = false
    private var isCornerScaling: Boolean = false  // 角落缩放
    private var scaleCornerIndex: Int = -1  // 记录哪个角
    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f
    private var initialRotation: Float = 0f
    private var initialScale: Float = 1.0f
    private var initialDistance: Float = 0f
    private var initialScaleDistance: Float = 0f  // 角落缩放的初始距离
    
    // 角点位置（用于触摸检测）
    private val cornerPoints = mutableListOf<PointF>()
    
    // 画笔
    private val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rotateButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // 虚线流动动画
    private var dashPhase = 0f
    private val dashAnimator = android.animation.ValueAnimator.ofFloat(0f, 15f).apply {
        duration = 1000
        repeatCount = android.animation.ValueAnimator.INFINITE
        repeatMode = android.animation.ValueAnimator.RESTART
        interpolator = android.view.animation.LinearInterpolator()
        addUpdateListener { animation ->
            dashPhase = animation.animatedValue as Float
            if (isWatermarkSelected) {
                invalidate()
            }
        }
    }
    
    // 手势检测器
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    
    // 监听器
    private var onDragListener: ((Float, Float) -> Unit)? = null
    private var onScaleListener: ((Float) -> Unit)? = null
    private var onRotationListener: ((Float) -> Unit)? = null
    private var onSelectionChangeListener: ((Boolean) -> Unit)? = null
    
    init {
        setupPaints()
        setupGestureDetector()
    }
    
    private fun setupPaints() {
        // 水印文字画笔
        watermarkPaint.apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        
        // 选择框画笔
        selectionPaint.apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
            // PathEffect 将在绘制时动态设置以实现流动效果
            isAntiAlias = true
        }
        
        // 旋转按钮画笔
        rotateButtonPaint.apply {
            color = Color.GREEN
            style = Paint.Style.FILL
            isAntiAlias = true
        }
    }
    
    private fun setupGestureDetector() {
        scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                // 只在水印选中时缩放水印
                if (isWatermarkSelected) {
                    val scaleFactor = detector.scaleFactor
                    val newScale = (watermarkScale * scaleFactor).coerceIn(0.1f, 10.0f)
                    watermarkScale = newScale
                    onScaleListener?.invoke(newScale)
                    invalidate()
                    return true
                }
                return false
            }
        })
    }
    
    fun setWatermarkParams(text: String, size: Float, alpha: Int, x: Float, y: Float, rotation: Float, scale: Float = 1.0f) {
        watermarkText = text
        watermarkSize = size
        watermarkAlpha = alpha
        watermarkX = x
        watermarkY = y
        watermarkRotation = rotation
        watermarkScale = scale
        invalidate()
    }
    
    // 新增：支持 WatermarkPreset 的版本
    fun setWatermarkParamsWithPreset(
        preset: com.example.yumoflatimagemanager.data.WatermarkPreset?,
        text: String, 
        size: Float, 
        alpha: Int, 
        x: Float, 
        y: Float, 
        rotation: Float, 
        scale: Float = 1.0f
    ) {
        this.watermarkPreset = preset
        watermarkText = text
        watermarkSize = size
        watermarkAlpha = alpha
        watermarkX = x
        watermarkY = y
        watermarkRotation = rotation
        watermarkScale = scale
        
        // 如果是图片水印，加载图片
        if (preset != null && preset.type == com.example.yumoflatimagemanager.data.WatermarkType.IMAGE && preset.imageUri != null) {
            loadWatermarkImage(preset.imageUri)
        } else {
            watermarkBitmap = null
        }
        
        invalidate()
    }
    
    private fun loadWatermarkImage(uriString: String) {
        try {
            val uri = Uri.parse(uriString)
            val inputStream = context.contentResolver.openInputStream(uri)
            watermarkBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
        } catch (e: Exception) {
            e.printStackTrace()
            watermarkBitmap = null
        }
    }
    
    fun setPhotoView(view: PhotoView) {
        photoView = view
        updatePhotoViewMatrix(view.displayRect)
    }
    
    fun updatePhotoViewMatrix(rect: RectF?) {
        displayRect = rect
        invalidate()
    }
    
    fun setOnWatermarkDragListener(listener: (Float, Float) -> Unit) {
        onDragListener = listener
    }
    
    fun setOnWatermarkScaleListener(listener: (Float) -> Unit) {
        onScaleListener = listener
    }
    
    fun setOnWatermarkRotationListener(listener: (Float) -> Unit) {
        onRotationListener = listener
    }
    
    fun setOnSelectionChangeListener(listener: (Boolean) -> Unit) {
        onSelectionChangeListener = listener
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val rect = displayRect ?: return
        
        // 计算水印在屏幕上的实际位置
        val actualX = rect.left + rect.width() * watermarkX
        val actualY = rect.top + rect.height() * watermarkY
        
        // 获取PhotoView的缩放比例
        val photoView = photoView
        val photoViewScale = photoView?.scale ?: 1.0f
        
        // 获取图片实际宽度（加载后的bitmap宽度）
        val drawable = photoView?.drawable
        val imageWidth = drawable?.intrinsicWidth ?: rect.width().toInt()
        val displayWidth = rect.width()
        
        // 计算图片实际显示的缩放比例
        val imageToDisplayScale = if (imageWidth > 0) displayWidth / imageWidth else 1.0f
        
        // 调试日志
        android.util.Log.d("WatermarkPreview", 
            "绘制水印 - imageWidth=$imageWidth, displayWidth=$displayWidth, " +
            "imageToDisplayScale=$imageToDisplayScale, photoViewScale=$photoViewScale, " +
            "watermarkSize=$watermarkSize, watermarkScale=$watermarkScale"
        )
        
        // 绘制水印
        canvas.save()
        canvas.translate(actualX, actualY)
        canvas.rotate(watermarkRotation)
        
        // 应用水印缩放和PhotoView缩放，让水印随图片缩放
        val totalScale = watermarkScale * photoViewScale
        canvas.scale(totalScale, totalScale)
        
        // 根据水印类型绘制
        if (watermarkPreset != null && watermarkPreset!!.type == com.example.yumoflatimagemanager.data.WatermarkType.IMAGE && watermarkBitmap != null) {
            // 绘制图片水印
            val bitmap = watermarkBitmap!!
            val preset = watermarkPreset!!
            
            // 使用图片原始宽度作为基准（不包含PhotoView缩放）
            val drawable = photoView?.drawable
            val imageWidth = drawable?.intrinsicWidth ?: rect.width().toInt()
            
            // 计算基础水印尺寸（不包含PhotoView缩放）
            val baseTargetWidth = (imageWidth * preset.imageScale).toInt()
            val scaleFactor = baseTargetWidth.toFloat() / bitmap.width
            val baseTargetHeight = (bitmap.height * scaleFactor).toInt()
            
            val watermarkPaintBitmap = Paint().apply { alpha = watermarkAlpha }
            canvas.drawBitmap(
                bitmap,
                null,
                android.graphics.RectF(
                    -baseTargetWidth / 2f,
                    -baseTargetHeight / 2f,
                    baseTargetWidth / 2f,
                    baseTargetHeight / 2f
                ),
                watermarkPaintBitmap
            )
            // 通过 canvas.scale(totalScale, totalScale) 统一缩放
        } else {
            // 绘制文字水印
            // 使用原始水印大小，缩放通过 canvas.scale 统一处理
            watermarkPaint.apply {
                textSize = watermarkSize
                color = Color.WHITE
                alpha = watermarkAlpha
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(watermarkText, 0f, 0f, watermarkPaint)
        }
        
        canvas.restore()
        
        // 如果选中，绘制选择框和旋转按钮
        if (isWatermarkSelected) {
            drawSelectionBox(canvas, actualX, actualY, rect)
        }
    }
    
    private fun drawSelectionBox(canvas: Canvas, centerX: Float, centerY: Float, rect: RectF) {
        // 获取PhotoView的缩放比例
        val photoView = photoView
        val photoViewScale = photoView?.scale ?: 1.0f
        
        // 计算水印边界 - 根据类型
        val scaledWidth: Float
        val scaledHeight: Float
        val totalScale = watermarkScale * photoViewScale
        
        if (watermarkPreset != null && watermarkPreset!!.type == com.example.yumoflatimagemanager.data.WatermarkType.IMAGE && watermarkBitmap != null) {
            // 图片水印边界
            val bitmap = watermarkBitmap!!
            val preset = watermarkPreset!!
            
            // 使用图片原始宽度作为基准（不包含PhotoView缩放）
            val drawable = photoView?.drawable
            val imageWidth = drawable?.intrinsicWidth ?: rect.width().toInt()
            
            val baseTargetWidth = (imageWidth * preset.imageScale).toInt()
            val scaleFactor = baseTargetWidth.toFloat() / bitmap.width
            val baseTargetHeight = (bitmap.height * scaleFactor).toInt()
            
            scaledWidth = baseTargetWidth * totalScale
            scaledHeight = baseTargetHeight * totalScale
        } else {
            // 文字水印边界
            watermarkPaint.textSize = watermarkSize
            val textWidth = watermarkPaint.measureText(watermarkText)
            val textHeight = watermarkSize
            
            scaledWidth = textWidth * totalScale
            scaledHeight = textHeight * totalScale
        }
        
        // 计算旋转后的边界框
        val cos = cos(Math.toRadians(watermarkRotation.toDouble())).toFloat()
        val sin = sin(Math.toRadians(watermarkRotation.toDouble())).toFloat()
        
        val halfWidth = scaledWidth / 2
        val halfHeight = scaledHeight / 2
        
        // 根据PhotoView缩放调整边距
        val margin = 20f * photoViewScale
        
        // 四个角的坐标（旋转前，包含边距）
        val corners = arrayOf(
            PointF(-halfWidth - margin, -halfHeight - margin),
            PointF(halfWidth + margin, -halfHeight - margin),
            PointF(halfWidth + margin, halfHeight + margin),
            PointF(-halfWidth - margin, halfHeight + margin)
        )
        
        // 旋转四个角
        val rotatedCorners = corners.map { corner ->
            PointF(
                centerX + (corner.x * cos - corner.y * sin),
                centerY + (corner.x * sin + corner.y * cos)
            )
        }
        
        // 保存角点位置供触摸检测使用
        cornerPoints.clear()
        cornerPoints.addAll(rotatedCorners)
        
        // 使用 Path 绘制旋转的矩形框，而不是 AABB
        val path = Path().apply {
            moveTo(rotatedCorners[0].x, rotatedCorners[0].y)
            lineTo(rotatedCorners[1].x, rotatedCorners[1].y)
            lineTo(rotatedCorners[2].x, rotatedCorners[2].y)
            lineTo(rotatedCorners[3].x, rotatedCorners[3].y)
            close()
        }
        
        // 绘制前设置流动虚线效果
        selectionPaint.pathEffect = android.graphics.DashPathEffect(
            floatArrayOf(10f, 5f),
            dashPhase
        )
        canvas.drawPath(path, selectionPaint)
        
        // 绘制四个角的控制点
        val cornerRadius = 8f * photoViewScale
        rotatedCorners.forEach { corner ->
            canvas.drawCircle(corner.x, corner.y, cornerRadius, selectionPaint)
        }
        
        // 绘制旋转按钮（在右上角延长线上，跟随水印旋转）
        val rotateButtonDistance = 30f * photoViewScale
        val rightCorner = rotatedCorners[1] // 右上角
        val topCorner = rotatedCorners[0]   // 左上角
        
        // 计算从右上角到左上角的方向向量
        val directionX = topCorner.x - rightCorner.x
        val directionY = topCorner.y - rightCorner.y
        val length = sqrt(directionX * directionX + directionY * directionY)
        
        // 归一化方向向量
        val normalizedX = if (length > 0) directionX / length else 0f
        val normalizedY = if (length > 0) directionY / length else -1f
        
        // 在右上角延长线上放置旋转按钮
        val rotateButtonX = rightCorner.x + normalizedX * rotateButtonDistance
        val rotateButtonY = rightCorner.y + normalizedY * rotateButtonDistance
        rotateButtonCenter.set(rotateButtonX, rotateButtonY)
        rotateButtonRadius = 30f * photoViewScale
        
        canvas.drawCircle(rotateButtonX, rotateButtonY, rotateButtonRadius, rotateButtonPaint)
        
        // 绘制旋转图标（圆形箭头）
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f * photoViewScale
        }
        
        val iconRadius = 8f * photoViewScale
        val centerRadius = 3f * photoViewScale
        
        // 绘制外圆
        canvas.drawCircle(rotateButtonX, rotateButtonY, iconRadius, iconPaint)
        
        // 绘制内圆
        canvas.drawCircle(rotateButtonX, rotateButtonY, centerRadius, iconPaint)
        
        // 绘制旋转箭头
        val arrowLength = 6f * photoViewScale
        val arrowAngle = 45f // 45度角
        
        val arrowX1 = rotateButtonX + iconRadius * cos(Math.toRadians(arrowAngle.toDouble())).toFloat()
        val arrowY1 = rotateButtonY + iconRadius * sin(Math.toRadians(arrowAngle.toDouble())).toFloat()
        val arrowX2 = rotateButtonX + (iconRadius + arrowLength) * cos(Math.toRadians(arrowAngle.toDouble())).toFloat()
        val arrowY2 = rotateButtonY + (iconRadius + arrowLength) * sin(Math.toRadians(arrowAngle.toDouble())).toFloat()
        
        canvas.drawLine(arrowX1, arrowY1, arrowX2, arrowY2, iconPaint)
        
        // 绘制箭头头部
        val headLength = 3f * photoViewScale
        val headAngle1 = arrowAngle + 30f
        val headAngle2 = arrowAngle - 30f
        
        val headX1 = arrowX2 + headLength * cos(Math.toRadians(headAngle1.toDouble())).toFloat()
        val headY1 = arrowY2 + headLength * sin(Math.toRadians(headAngle1.toDouble())).toFloat()
        val headX2 = arrowX2 + headLength * cos(Math.toRadians(headAngle2.toDouble())).toFloat()
        val headY2 = arrowY2 + headLength * sin(Math.toRadians(headAngle2.toDouble())).toFloat()
        
        canvas.drawLine(arrowX2, arrowY2, headX1, headY1, iconPaint)
        canvas.drawLine(arrowX2, arrowY2, headX2, headY2, iconPaint)
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val rect = displayRect ?: return false
        
        // 处理多指手势
        if (event.pointerCount >= 2) {
            // 检查是否有任何手指在水印上
            val actualX = rect.left + rect.width() * watermarkX
            val actualY = rect.top + rect.height() * watermarkY
            
            val anyFingerOnWatermark = (0 until event.pointerCount).any { index ->
                isTouchingWatermark(event.getX(index), event.getY(index), actualX, actualY)
            }
            
            if (anyFingerOnWatermark) {
                // 有手指在水印上，缩放水印
                if (!isWatermarkSelected) {
                    setWatermarkSelected(true)
                }
                // 让ScaleGestureDetector处理缩放
                scaleGestureDetector.onTouchEvent(event)
                return true  // 拦截事件，缩放水印
            } else {
                // 都不在水印上，不拦截，让PhotoView处理图片缩放
                return false
            }
        }
        
        // 单指手势，让ScaleGestureDetector处理（虽然单指不会触发）
        scaleGestureDetector.onTouchEvent(event)
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val actualX = rect.left + rect.width() * watermarkX
                val actualY = rect.top + rect.height() * watermarkY
                
                // 检查是否点击在角落（优先级最高）
                if (isWatermarkSelected) {
                    val cornerIndex = isTouchingCorner(event.x, event.y)
                    if (cornerIndex >= 0) {
                        isCornerScaling = true
                        scaleCornerIndex = cornerIndex
                        initialScaleDistance = sqrt(
                            (event.x - actualX).pow(2) + (event.y - actualY).pow(2)
                        )
                        lastTouchX = event.x
                        lastTouchY = event.y
                        return true
                    }
                }
                
                // 检查是否点击在旋转按钮上
                if (isWatermarkSelected && isTouchingRotateButton(event.x, event.y)) {
                    isRotating = true
                    lastTouchX = event.x
                    lastTouchY = event.y
                    return true
                }
                
                // 检查是否点击在水印上
                if (isTouchingWatermark(event.x, event.y, actualX, actualY)) {
                    // 选中水印
                    setWatermarkSelected(true)
                    // 开始拖动
                    isDragging = true
                    lastTouchX = event.x
                    lastTouchY = event.y
                    return true
                } else if (isWatermarkSelected) {
                    // 水印已选中，点击空白区域取消选择
                    setWatermarkSelected(false)
                    return true
                } else {
                    // 水印未选中，点击空白区域不做处理
                    return false
                }
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (isCornerScaling && isWatermarkSelected) {
                    // 角落缩放
                    val actualX = rect.left + rect.width() * watermarkX
                    val actualY = rect.top + rect.height() * watermarkY
                    val currentDistance = sqrt(
                        (event.x - actualX).pow(2) + (event.y - actualY).pow(2)
                    )
                    
                    val scaleFactor = currentDistance / initialScaleDistance
                    val newScale = (watermarkScale * scaleFactor).coerceIn(0.1f, 10.0f)
                    watermarkScale = newScale
                    onScaleListener?.invoke(newScale)
                    
                    initialScaleDistance = currentDistance
                    invalidate()
                    return true
                } else if (isDragging && isWatermarkSelected) {
                    // 拖动水印
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    
                    val newX = (watermarkX + dx / rect.width()).coerceIn(0f, 1f)
                    val newY = (watermarkY + dy / rect.height()).coerceIn(0f, 1f)
                    
                    onDragListener?.invoke(newX, newY)
                    
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                    return true
                } else if (isRotating && isWatermarkSelected) {
                    // 旋转水印
                    val centerX = rect.left + rect.width() * watermarkX
                    val centerY = rect.top + rect.height() * watermarkY
                    
                    val angle = atan2((event.y - centerY).toDouble(), (event.x - centerX).toDouble())
                    val currentAngle = Math.toDegrees(angle).toFloat()
                    
                    // 计算相对于起始角度的变化
                    val lastAngle = atan2((lastTouchY - centerY).toDouble(), (lastTouchX - centerX).toDouble())
                    val lastAngleDeg = Math.toDegrees(lastAngle).toFloat()
                    val deltaAngle = currentAngle - lastAngleDeg
                    
                    val newRotation = watermarkRotation + deltaAngle
                    val normalizedRotation = ((newRotation % 360f) + 360f) % 360f
                    watermarkRotation = normalizedRotation
                    onRotationListener?.invoke(normalizedRotation)
                    
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                    return true
                }
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging || isRotating || isCornerScaling) {
                    isDragging = false
                    isRotating = false
                    isCornerScaling = false
                    scaleCornerIndex = -1
                    invalidate()
                    return true
                }
            }
        }
        
        // 如果水印未选中，不拦截单指触摸事件
        if (!isWatermarkSelected) {
            return false
        }
        
        // 水印已选中但不在处理特定手势，拦截触摸事件
        return true
    }
    
    private fun isTouchingWatermark(touchX: Float, touchY: Float, centerX: Float, centerY: Float): Boolean {
        val distance = sqrt((touchX - centerX).pow(2) + (touchY - centerY).pow(2))
        // 使用更大的触摸范围，确保容易点击
        val touchRadius = 150f
        return distance < touchRadius
    }
    
    private fun isTouchingRotateButton(touchX: Float, touchY: Float): Boolean {
        // 确保旋转按钮位置已设置
        if (rotateButtonCenter.x == 0f && rotateButtonCenter.y == 0f) {
            return false
        }
        
        val distance = sqrt(
            (touchX - rotateButtonCenter.x).pow(2) + (touchY - rotateButtonCenter.y).pow(2)
        )
        return distance < rotateButtonRadius
    }
    
    private fun isTouchingCorner(touchX: Float, touchY: Float): Int {
        if (cornerPoints.isEmpty()) return -1
        
        val photoView = photoView
        val photoViewScale = photoView?.scale ?: 1.0f
        val touchRadius = 30f * photoViewScale
        
        cornerPoints.forEachIndexed { index, corner ->
            val distance = sqrt(
                (touchX - corner.x).pow(2) + (touchY - corner.y).pow(2)
            )
            if (distance < touchRadius) {
                return index
            }
        }
        return -1
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        dashAnimator.cancel()
    }
    
    // 获取当前水印参数
    fun getCurrentWatermarkParams(): ImageWatermarkParams {
        // 获取预览时图片的实际宽度
        val drawable = photoView?.drawable
        val previewWidth = drawable?.intrinsicWidth ?: 0
        
        return ImageWatermarkParams(
            imageUri = "", // 这个会在调用时设置
            watermarkX = watermarkX,
            watermarkY = watermarkY,
            watermarkScale = watermarkScale,
            watermarkRotation = watermarkRotation,
            watermarkAlpha = watermarkAlpha,
            previewImageWidth = previewWidth
        )
    }
}
