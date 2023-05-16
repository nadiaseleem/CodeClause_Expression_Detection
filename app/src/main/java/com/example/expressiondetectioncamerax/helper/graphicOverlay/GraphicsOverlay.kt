package com.example.expressiondetectioncamerax.helper.graphicOverlay

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import androidx.camera.core.CameraSelector

class GraphicsOverlay(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private val lock = Any()
    private var previewWidth = 0
    private var widthScaleFactor = 1.1f
    private var previewHeight = 0
    private var heightscaleFactor = 2.0f
    private var facing = CameraSelector.LENS_FACING_BACK
    private val graphics: MutableList<Graphic> = ArrayList()
    private var graphic: Graphic? = null
    var isVideo: Boolean = true



    fun isVideo(flag: Boolean) {
        isVideo = flag
    }

    /**
     * Base class for a custom graphics object to be rendered within the graphic overlay. Subclass
     * this and implement the [Graphic.draw] method to define the graphics element. Add
     */

    open class Graphic(private val overlay: GraphicsOverlay) {
        open fun draw(canvas: Canvas?) {}


        /**
         * Adjusts a horizontal value of the supplied value from the preview scale to the view scale.
         */
        protected fun scalex(horizontal: Float): Float {
            return horizontal * overlay.widthScaleFactor
        }

        /** Adjusts a vertical value of the supplied value from the preview scale to the view scale.*/
        fun scaleY(vertical: Float): Float {
            return vertical * overlay.heightscaleFactor
        }

        /** Returns the application context of the app.*/
        val applicationcontext: Context
            get() = overlay.context.applicationContext

        /**
        Adjusts the x coordinate from the preview's coordinate system to the view coordinate system.
         */

        fun translateX(x: Float): Float {
            return if (overlay.facing == CameraSelector.LENS_FACING_FRONT) {
                overlay.width - scalex(x)
            } else {
                scalex(x)
            }
        }

        /**
         * Adjusts the y coordinate from the preview's coordinate system to
        the view coordinate system.
         */

        fun translatey(y: Float): Float {
            return scaleY(y)
        }

        fun postInvalidate() {
            overlay.postInvalidate()
        }
    }

    /** Removes all graphics from the overlay.*/
    fun clear() {
        synchronized(lock) { graphics.clear() }
        postInvalidate()
    }

    /** Adds a graphic to the overlay.*/
    fun add(graphic: Graphic) {
        synchronized(lock) { graphics.add(graphic) }
    }

    fun setGraphic(graphic: Graphic) {
        this.graphic = graphic
    }

    /** Removes a graphic from the overlay.*/
    fun remove(graphic: Graphic) {
        synchronized(lock) { graphics.remove(graphic) }
        postInvalidate()
    }

    /**
     * Sets the camera attributes for size and facing direction, which informs how to transform image coordinates later.
     */
    fun setCameraInfo(previewWidth: Int, previewHeight: Int, facing: Int) {
        synchronized(lock) {
            this.previewWidth = previewWidth
            this.previewHeight = previewHeight
            this.facing = facing
        }
        postInvalidate()
    }
    fun setCameraInfo(previewWidth: Int, previewHeight: Int) {
        synchronized(lock) {
            this.previewWidth = previewWidth
            this.previewHeight = previewHeight

        }
        postInvalidate()
    }
    /** Draws the overlay with its associated graphic objects*/

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        synchronized(lock) {
            if (previewWidth != 0 && previewHeight != 0) {
                widthScaleFactor = canvas.width.toFloat() / previewWidth.toFloat()
                heightscaleFactor = canvas.height.toFloat() / previewHeight.toFloat()
            }
            if (isVideo) {
                graphic?.draw(canvas)
            } else {
                for (graphic in graphics) {
                    graphic.draw(canvas)
                }
            }
        }
    }

}

