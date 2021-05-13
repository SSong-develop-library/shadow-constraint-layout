package com.kr.hkslibrary.shadowconstraintlayout.shadowconstraint

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.use
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.hk.customcardview.util.dpToPixelFloat
import com.kr.hkslibrary.shadowconstraintlayout.R
import com.kr.hkslibrary.shadowconstraintlayout.util.OnChangeProp

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
 */
class ShadowConstraintLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {
    private val shadowPaint = Paint()
    private var borderPaint = Paint()
    private val rectPaint = Paint()

    private val shadowPath = Path()
    private val rectBackgroundPath = Path()

    private val borderRectF = RectF()
    private val rectBackgroundRectF = RectF()
    private val shadowRectF = RectF()

    private val porterDuffXfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)

    private var shadowColor = Color.GRAY

    private var shadowStrokeWidth = 4.dpToPixelFloat

    // values & Offset & width & height
    private var blurRadius = 16.dpToPixelFloat
    private var shadowEndOffset = 1.dpToPixelFloat
    private var shadowStartOffset = 1.dpToPixelFloat
    private var shadowTopOffset = 1.dpToPixelFloat
    private var shadowBottomOffset = 1.dpToPixelFloat
    private var enableShadow = true
    private var enableBorder = true
    private var borderColor = Color.BLACK

    private val blurMaskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)

    // background Corner Radius
    var cornerRadius by OnChangeProp(16.dpToPixelFloat){
        updateBackground()
    }

    var cardBackgroundColor by OnChangeProp(Color.WHITE){
        updateBackground()
    }

    init {
        if (attrs != null) {
            getStyleableAttrs(attrs)
        }
        updateBackground()
    }

    private fun getStyleableAttrs(attrs: AttributeSet) {
        context.theme.obtainStyledAttributes(attrs, R.styleable.ShadowConstraintLayout, 0, 0).use {
            shadowTopOffset = it.getDimension(
                R.styleable.ShadowConstraintLayout_shadow_top_offset,
                1.dpToPixelFloat
            )
            shadowBottomOffset = it.getDimension(
                R.styleable.ShadowConstraintLayout_shadow_bottom_offset,
                1.dpToPixelFloat
            )
            shadowStartOffset = it.getDimension(
                R.styleable.ShadowConstraintLayout_shadow_start_offset,
                1.dpToPixelFloat
            )
            shadowEndOffset = it.getDimension(
                R.styleable.ShadowConstraintLayout_shadow_end_offset,
                1.dpToPixelFloat
            )
            shadowColor = it.getColor(
                R.styleable.ShadowConstraintLayout_shadow_color,
                Color.BLACK
            )
            shadowStrokeWidth = it.getDimension(
                R.styleable.ShadowConstraintLayout_shadow_stroke_width,
                1.dpToPixelFloat
            )
            cornerRadius = it.getDimension(
                R.styleable.ShadowConstraintLayout_corner_radius,
                3.dpToPixelFloat
            )
            blurRadius =
                it.getDimension(R.styleable.ShadowConstraintLayout_blur_radius, 50.dpToPixelFloat)
            borderColor = it.getColor(
                R.styleable.ShadowConstraintLayout_border_color,
                Color.BLACK
            )
            enableShadow = it.getBoolean(R.styleable.ShadowConstraintLayout_enable_shadow, true)
            enableBorder = it.getBoolean(R.styleable.ShadowConstraintLayout_enable_border, false)
            cardBackgroundColor = it.getColor(R.styleable.ShadowConstraintLayout_card_background_color,Color.WHITE)
        }
    }

    private fun updateBackground(){
        background = MaterialShapeDrawable(ShapeAppearanceModel().withCornerSize(cornerRadius)).apply {
            fillColor = ColorStateList.valueOf(cardBackgroundColor)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (enableShadow) {
            drawShadow(canvas)
        }
        if (enableBorder) {
            drawBorder(canvas)
        }
        drawRectBackground(canvas)
        /*super.onDraw(canvas)*/
    }

    private fun drawShadow(canvas: Canvas) {
        canvas.save()
        shadowPaint.apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            color = shadowColor
            strokeWidth = shadowStrokeWidth
            xfermode = porterDuffXfermode
            maskFilter = blurMaskFilter
        }

       /* shadowPath.apply {
            reset()
            moveTo((width + (shadowEndOffset)), shadowStartY + shadowTopOffset) // StartPosition
            lineTo((shadowStartOffset), shadowStartY + shadowTopOffset) // TR -> TL
            lineTo((shadowStartOffset), (height + shadowBottomOffset)) // TL -> BL
            lineTo((width + shadowEndOffset), (height + shadowBottomOffset)) // BL -> BR
            lineTo((width + shadowEndOffset), shadowStartY + shadowTopOffset) // BR -> TR
        }*/
        /*canvas.drawPath(shadowPath, shadowPaint)*/

        shadowRectF.apply {
            top = 0f - shadowTopOffset
            left = 0f - shadowStartOffset
            right = width - shadowEndOffset
            bottom = height - shadowBottomOffset
        }
        canvas.drawRoundRect(shadowRectF,cornerRadius,cornerRadius,shadowPaint)
        canvas.restore()
    }

    private fun drawBorder(canvas: Canvas) {
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

    private fun drawRectBackground(canvas: Canvas) {
        rectPaint.apply {
            style = Paint.Style.FILL
            color = cardBackgroundColor
            xfermode = porterDuffXfermode
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