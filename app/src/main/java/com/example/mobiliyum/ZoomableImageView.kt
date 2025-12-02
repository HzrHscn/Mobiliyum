package com.example.mobiliyum

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

class ZoomableImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private var matrixImage = Matrix()
    private var mode = 0 // 0: NONE, 1: DRAG, 2: ZOOM
    private var last = PointF()
    private var start = PointF()
    private var minScale = 1f
    private var maxScale = 4f
    private var mScaleFactor = 1f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scale = detector.scaleFactor
            val origScale = mScaleFactor
            mScaleFactor *= scale
            if (mScaleFactor > maxScale) {
                mScaleFactor = maxScale
            } else if (mScaleFactor < minScale) {
                mScaleFactor = minScale
            }

            if (origScale * scale <= maxScale && origScale * scale >= minScale) {
                matrixImage.postScale(scale, scale, detector.focusX, detector.focusY)
            }
            return true
        }
    })

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        val curr = PointF(event.x, event.y)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                last.set(curr)
                start.set(last)
                mode = 1 // DRAG
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == 1) {
                    val deltaX = curr.x - last.x
                    val deltaY = curr.y - last.y
                    matrixImage.postTranslate(deltaX, deltaY)
                    last.set(curr.x, curr.y)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                mode = 0
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                last.set(event.getX(1), event.getY(1))
                start.set(last)
                mode = 2 // ZOOM
            }
        }

        imageMatrix = matrixImage
        return true
    }
}