package com.example.expressiondetectioncamerax.helper.graphicOverlay;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

public class RectOverlay extends GraphicsOverlay.Graphic {

    private int RECT_COLOR = Color.rgb(139,195,74);
    private float tebalgaris = 10.0f;
    private Paint rectPaint;

    private GraphicsOverlay graphicsOverlay;
    private Rect rect;

    public RectOverlay(GraphicsOverlay graphicsOverlay, Rect rect) {
        super(graphicsOverlay);

        rectPaint  = new Paint();
        rectPaint.setColor(RECT_COLOR);
        rectPaint.setStyle(Paint.Style.STROKE);
        rectPaint.setStrokeWidth(tebalgaris);

        this.graphicsOverlay = graphicsOverlay;
        this.rect = rect;


        postInvalidate();
    }

    @Override
    public void draw(Canvas canvas) {
        RectF rectF = new RectF(rect);
        rectF.left = translateX(rectF.left);
        rectF.right = translateX(rectF.right);
        rectF.top = translatey(rectF.top);
        rectF.bottom = translatey(rectF.bottom);

        canvas.drawRect(rectF,rectPaint );


    }
}

