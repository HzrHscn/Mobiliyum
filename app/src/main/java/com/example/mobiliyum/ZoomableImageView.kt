package com.example.mobiliyum

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs

class ZoomableImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private var matrixImage = Matrix()
    private var mode = NONE
    private var last = PointF()
    private var start = PointF()
    private var minScale = 1f
    private var maxScale = 4f
    private var saveScale = 1f
    private var right: Float = 0f
    private var bottom: Float = 0f
    private var origWidth: Float = 0f
    private var origHeight: Float = 0f
    private var bmWidth: Float = 0f
    private var bmHeight: Float = 0f

    private val m = FloatArray(9)

    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())

    companion object {
        const val NONE = 0
        const val DRAG = 1
        const val ZOOM = 2
        const val CLICK = 3
    }

    init {
        super.setClickable(true)
        scaleType = ScaleType.MATRIX
        matrixImage.setTranslate(1f, 1f)
        m[Matrix.MSCALE_X] = 1f
        m[Matrix.MSCALE_Y] = 1f
        imageMatrix = matrixImage
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            mode = ZOOM
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            var mScaleFactor = detector.scaleFactor
            val origScale = saveScale
            saveScale *= mScaleFactor
            if (saveScale > maxScale) {
                saveScale = maxScale
                mScaleFactor = maxScale / origScale
            } else if (saveScale < minScale) {
                saveScale = minScale
                mScaleFactor = minScale / origScale
            }

            if (origWidth * saveScale <= width || origHeight * saveScale <= height) {
                matrixImage.postScale(mScaleFactor, mScaleFactor, width / 2f, height / 2f)
            } else {
                matrixImage.postScale(mScaleFactor, mScaleFactor, detector.focusX, detector.focusY)
            }

            fixTrans()
            return true
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        val curr = PointF(event.x, event.y)

        when (event.actionMasked) { // actionMasked kullanmak multi-touch için daha doğru
            MotionEvent.ACTION_DOWN -> {
                last.set(curr)
                start.set(last)
                mode = DRAG
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == DRAG) {
                    val deltaX = curr.x - last.x
                    val deltaY = curr.y - last.y
                    val fixTransX = getFixDragTrans(deltaX, width.toFloat(), origWidth * saveScale)
                    val fixTransY = getFixDragTrans(deltaY, height.toFloat(), origHeight * saveScale)
                    matrixImage.postTranslate(fixTransX, fixTransY)
                    fixTrans()
                    last.set(curr.x, curr.y)
                }
            }
            MotionEvent.ACTION_UP -> {
                mode = NONE
                val xDiff = abs(curr.x - start.x).toInt()
                val yDiff = abs(curr.y - start.y).toInt()
                if (xDiff < CLICK && yDiff < CLICK) {
                    performClick()
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                mode = NONE
            }
        }

        imageMatrix = matrixImage
        invalidate()
        return true
    }

    private fun fixTrans() {
        matrixImage.getValues(m)
        val transX = m[Matrix.MTRANS_X]
        val transY = m[Matrix.MTRANS_Y]

        val fixTransX = getFixTrans(transX, width.toFloat(), origWidth * saveScale)
        val fixTransY = getFixTrans(transY, height.toFloat(), origHeight * saveScale)

        if (fixTransX != 0f || fixTransY != 0f) {
            matrixImage.postTranslate(fixTransX, fixTransY)
        }
    }

    private fun getFixTrans(trans: Float, viewSize: Float, contentSize: Float): Float {
        val minTrans: Float
        val maxTrans: Float

        if (contentSize <= viewSize) {
            minTrans = 0f
            maxTrans = viewSize - contentSize
        } else {
            minTrans = viewSize - contentSize
            maxTrans = 0f
        }

        if (trans < minTrans) return -trans + minTrans
        if (trans > maxTrans) return -trans + maxTrans
        return 0f
    }

    private fun getFixDragTrans(delta: Float, viewSize: Float, contentSize: Float): Float {
        if (contentSize <= viewSize) {
            return 0f
        }
        return delta
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        bmWidth = drawable?.intrinsicWidth?.toFloat() ?: 0f
        bmHeight = drawable?.intrinsicHeight?.toFloat() ?: 0f

        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)

        // Fit to screen.
        val scale: Float
        val scaleX = width.toFloat() / bmWidth
        val scaleY = height.toFloat() / bmHeight

        scale = if (scaleX < scaleY) scaleX else scaleY

        matrixImage.setScale(scale, scale)

        // Center the image
        val redundantYSpace = (height.toFloat() - (scale * bmHeight))
        val redundantXSpace = (width.toFloat() - (scale * bmWidth))

        var ySpace = redundantYSpace / 2f
        var xSpace = redundantXSpace / 2f

        matrixImage.postTranslate(xSpace, ySpace)

        origWidth = width - 2 * xSpace
        origHeight = height - 2 * ySpace

        imageMatrix = matrixImage
    }
}