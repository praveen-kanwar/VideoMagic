package com.praveen.android.videomagic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Created by Praveen Kanwar on 06/06/17.
 * Copyright (c) 2017 .
 */
public class OverlayView extends View
    {

        PointF[] points;
        private Paint paint;

        public OverlayView(Context context)
            {
                super(context);
                init();
            }

        public OverlayView(Context context, AttributeSet attrs)
            {
                super(context, attrs);
                init();
            }

        public OverlayView(Context context, AttributeSet attrs, int defStyleAttr)
            {
                super(context, attrs, defStyleAttr);
                init();
            }

        private void init()
            {
                paint = new Paint();
                paint.setColor(Color.YELLOW);
                paint.setStyle(Paint.Style.FILL);
            }

        public void setPoints(PointF[] points)
            {
                this.points = points;
                invalidate();
            }

        @Override
        public void draw(Canvas canvas)
            {
                super.draw(canvas);
                if (points != null)
                    {
                        for (PointF pointF : points)
                            {
                                canvas.drawCircle(pointF.x, pointF.y, 10, paint);
                            }
                    }
            }
    }
