package com.hk.customcardview.shadowconstraint

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.hk.customcardview.util.dpToPixelFloat
import com.kr.hkslibrary.shadowconstraintlayout.R
import com.kr.hkslibrary.shadowconstraintlayout.util.OnChangeProp
import java.lang.Float.MIN_VALUE

/**
 * Shadow ConstraintLayout
 *
 * Layout 그려지는 과정 (측정과 순서의 과정을 거친다)
 * measure() -> onMeasure() -> layout() -> onLayout() -> dispatchDraw() -> draw() -> onDraw()
 *
 * measure(widthMeasureSpec : Int , heightMeasureSpec : Int) : 뷰의 크기를 알아내기 위해 호출 , 실제 크기 측정을 위해 onMeasure()을 호출
 *
 * onMeasure() : 실제 뷰의 크기를 측정
 *
 * layout(left : Int , top : Int , right : Int , bottom : Int) : 뷰의 위치를 할당하기 위해 호출 , 실제 할당을 위해 onLayout호출
 *
 * onLayout() : 실제 뷰의 할당
 *
 * Custom View Lifecycle
 * Constructor -> onAttachedToWindow -> measure -> onMeasure -> layout -> onLayout -> dispatchDraw -> draw -> onDraw
 *
 * Constructor
 * 1. CustomView(context : Context) -> 코드로 생성하면 호출
 *    CustomView(context : Context , attributeSet : AttributeSet) -> xml로 생성하면 호출
 *
 * 2. onAttachedToWindow : Parent View가 addView를 호출하면서 해당 View가 Window에 연결됩니다.
 *
 * 3. onMeasure : View의 사이즈 측정
 *
 * 4. onLayout : 개별 child View들의 사이즈와 위치 할당
 *
 * 5. onDraw : view그리는 작업
 *
 * View UpDate
 * invalidate : view에 변화가 생겨서 다시 그릴 때
 * requestLayout : View를 처음부터 그려야 할 떄
 *
 * 그래픽 그릴 때 필요한거
 * 1. 캔버스 : 뷰의 표면에 직접 그릴 수 있는 객체
 * 2. 페인트 : 그래픽 색상과 속성을 담고 있다. / 속성은 Style.FILL , Style.STROKE 각각 색을 채울 때, 선을 그릴 때 사용
 *  - 투명도 조절 : ARGB
 *  - 부드러운 선 : AntiAlias = true
 * 3. 비트맵
 * 4. Drawable
 * 5. Path : 안드로이드에선 기본적으로 원 , 사각형 , 등 기본적인 도형은 사용할 수 있도록 하는 이녀석을 사용하면 어려운 도형을 만들 수 있음.
 * 6. Cliping : 그리기 연산이 일어날 영역을 뜻함
 *
 * 그리는 순서
 * 1. Cliping으로 그리고자 하는 영역을 설정
 * 2. paint에 색 담아놓고
 * 3. path를 사용한다면 Path line을 설정해놓는다.
 * 4. 모든걸 그렸다면 canvas에 적용시키면 그려진다.
 *
 * 이거 같은 경우엔 코너가 살짝 둥근 사각형이라 CornerRoundRect로 만들었고
 * shadow , border , 안에 들어갈 view영역 이렇게 3개를 그렸다.
 *
 * 사용법
 * : 기존 elevation을 보여주듯이 보여주면 되는데 clipChildren 과 clipToPadding 을 false로 적용해줘야 합니다.
 * : 그 후 뭐.....원하는 cornerRadius나 blurRadius를 처리하면 끝!
 *
 * background 그리는걸 MaterialShapeAppearance로 처리를 한다면 어떨까라는 생각으로 시작합니다.
 *
 * app :background를 바꿔줄 수 있는 xml 속성을 해놔야 할 거 같습니다.
 */
class ShadowConstraintLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {
    // Paint
    private val shadowPaint = Paint()
    private var borderPaint = Paint()
    private var rectPaint = Paint()

    // To give a path to shadow and that is around the view like a RECTANGLE
    private val shadowPath = Path()
    private var rectBackgroundPath = Path()
    private var clipPath = Path()

    // RectF
    private var borderRectF = RectF()
    private var clipRectF = RectF()
    private var rectBackgroundRectF = RectF()

    // The Shadow should not overlap the content of your constraint layout
    // That's why we used PorterDuffXfermode
    private val porterDuffXfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)

    private var shadowColor = Color.GRAY

    // stroke Width
    private var shadowStrokeWidth = 15.toFloat()

    // values & Offset & width & height
    private var blurRadius = 40.toFloat() // 50 is pixel
    private var shadowStartY = MIN_VALUE
    private var shadowEndOffset = 0f
    private var shadowStartOffset = 0f
    private var shadowTopOffset = 0f
    private var shadowBottomOffset = 0f
    private var enableShadow = true
    private var enableBorder = true
    private var borderHeight = 0f

    // background Corner Radius
    var cornerRadius by OnChangeProp(16.dpToPixelFloat){
        updateBackground()
    }

    // blurMask
    // that's why we have used blurMaskFilter
    private val blurMaskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)

    init {
        setBackgroundColor(Color.WHITE)
        if (attrs != null) {
            getStyleableAttrs(attrs)
        }
    }

    private fun getStyleableAttrs(attrs: AttributeSet) {
        // XML상에서 app:shadowTopOffset같은걸 지정해주기 위해서 사용
        context.theme.obtainStyledAttributes(attrs, R.styleable.ShadowConstraintLayout, 0, 0).use {
            shadowTopOffset = it.getDimension(
                R.styleable.ShadowConstraintLayout_shadowTopOffset,
                0.dpToPixelFloat
            )
            shadowBottomOffset = it.getDimension(
                R.styleable.ShadowConstraintLayout_shadowBottomOffset,
                0.dpToPixelFloat
            )
            shadowStartOffset = it.getDimension(
                R.styleable.ShadowConstraintLayout_shadowStartOffset,
                0.dpToPixelFloat
            )
            shadowEndOffset = it.getDimension(
                R.styleable.ShadowConstraintLayout_shadowEndOffset,
                0.dpToPixelFloat
            )
            shadowStartY = it.getDimension(
                R.styleable.ShadowConstraintLayout_shadowStartY,
                MIN_VALUE
            )
            shadowColor = it.getColor(
                R.styleable.ShadowConstraintLayout_shadowColor,
                Color.BLACK
            )
            shadowStrokeWidth = it.getDimension(
                R.styleable.ShadowConstraintLayout_shadowStrokeWidth,
                1.dpToPixelFloat
            )
            cornerRadius = it.getDimension(
                R.styleable.ShadowConstraintLayout_cornerRadius,
                3.dpToPixelFloat
            )
            blurRadius =
                it.getDimension(R.styleable.ShadowConstraintLayout_blurRadius, 50.dpToPixelFloat)
            enableShadow = it.getBoolean(R.styleable.ShadowConstraintLayout_enableShadow, true)
            enableBorder = it.getBoolean(R.styleable.ShadowConstraintLayout_enableBorder, false)
            borderHeight = it.getDimension(R.styleable.ShadowConstraintLayout_borderHeight, 0f)
        }
    }

    private fun updateBackground(){
        background = MaterialShapeDrawable(ShapeAppearanceModel().withCornerSize(cornerRadius))
    }

    override fun dispatchDraw(canvas: Canvas) {
        clipRoundCorners(canvas)
        super.dispatchDraw(canvas)
    }

    override fun onDraw(canvas: Canvas) {
        if (enableShadow) {
            drawShadow(canvas)
        }
        drawRectBackground(canvas)
        if (enableBorder) {
            drawBorder(canvas)
        }
        super.onDraw(canvas)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN)
            Toast.makeText(context, "i'm touched....", Toast.LENGTH_SHORT).show()
        return super.onTouchEvent(event)
    }

    /**
     * Draw shadow around your view
     */
    private fun drawShadow(canvas: Canvas) {
        canvas.save()
        // setup Paint
        shadowPaint.apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            color = shadowColor
            strokeWidth = shadowStrokeWidth
            xfermode = porterDuffXfermode
            maskFilter = blurMaskFilter
        }

        shadowTopOffset = 6.dpToPixelFloat
        shadowBottomOffset = (-2).dpToPixelFloat
        shadowStartOffset = 2.dpToPixelFloat
        shadowEndOffset = (-2).dpToPixelFloat

        // setup Path
        shadowPath.apply {
            reset()
            moveTo((width + (shadowEndOffset)), shadowStartY + shadowTopOffset) // StartPosition
            lineTo((shadowStartOffset), shadowStartY + shadowTopOffset) // TR -> TL
            lineTo((shadowStartOffset), (height + shadowBottomOffset)) // TL -> BL
            lineTo((width + shadowEndOffset), (height + shadowBottomOffset)) // BL -> BR
            lineTo((width + shadowEndOffset), shadowStartY + shadowTopOffset) // BR -> TR
        }
        canvas.drawPath(shadowPath, shadowPaint)
        canvas.restore()
        // It means
        // 1. Goto start Point
        // Make a rectangle from there and at last come back from where you started
    }

    /**
     * Draw a border around the view
     */
    private fun drawBorder(canvas: Canvas) {
        // setup Paint , need to change
        val borderColor = Color.BLACK

        borderPaint.apply {
            style = Paint.Style.STROKE
            color = borderColor
            strokeWidth = 0.5f
        }

        // provide a rect
        borderRectF.apply {
            top = 0f
            left = 0f
            right = width.toFloat()
            bottom = height.toFloat()
        }
        // RoundedRectangle
        canvas.drawRoundRect(borderRectF, cornerRadius, cornerRadius, borderPaint)
    }

    /**
     * Clip the view with RoundCorners
     *
     * 기본적으로 안드로이드의 모든 뷰는 물리적으로 차지하는 영역 만큼만 그릴 수 있다.
     * 이는 모든 view가 부모 view의 canvas를 넘겨받고, 그 canvas를 자신의 영역만큼만 clip해서 사용하기 때문
     * 하지만 자신의 영역 밖에서 그려야 할 상황이 있다. 자신의 영역을 넘어서는 애니메이션을 돌려야 할 때 이를 가능하게 해주는게 clipChildren
     */
    private fun clipRoundCorners(canvas: Canvas) {
        clipPath.reset()

        clipRectF.apply {
            top = 0f
            left = 0f
            right = canvas.width.toFloat()
            bottom = canvas.height.toFloat()
        }
        clipPath.addRoundRect(clipRectF, cornerRadius, cornerRadius, Path.Direction.CW)

        // this means we want to clip this part(defined via clipPath)of canvas
        // And our future drawing commands must happen within the bounds of this clipPath
        canvas.clipPath(clipPath)
    }

    /**
     * Fill your view with white color
     */
    private fun drawRectBackground(canvas: Canvas) {
        rectPaint.apply {
            style = Paint.Style.FILL
            color = ContextCompat.getColor(context,R.color.real_white)
            xfermode = porterDuffXfermode // To ensure the paint should come on top of shadow
        }

        rectBackgroundRectF.apply {
            top = 0f
            left = 0f
            right = width.toFloat()
            bottom = height.toFloat()
        }

        rectBackgroundPath.apply {
            reset()
            addRect(rectBackgroundRectF, Path.Direction.CW)
        }

        canvas.drawRoundRect(rectBackgroundRectF, cornerRadius, cornerRadius, rectPaint)
    }

}