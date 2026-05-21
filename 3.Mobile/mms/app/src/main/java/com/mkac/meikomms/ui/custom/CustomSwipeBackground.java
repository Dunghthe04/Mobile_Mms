package com.mkac.meikomms.ui.custom;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

public class CustomSwipeBackground extends View {

    private Paint paint;
    private Path path;

    public CustomSwipeBackground(Context context) {
        super(context);
        init();
    }

    public CustomSwipeBackground(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CustomSwipeBackground(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(0xFFFF0000); // Red color
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);

        path = new Path();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        int cornerRadius = 50; // Set the corner radius for rounded effect
        int depressionDepth = 20; // Depth of the depression

        path.reset();

        // Start from top-left corner
        path.moveTo(0, cornerRadius);
        path.quadTo(0, 0, cornerRadius, 0); // Top-left rounded corner

        // Create a concave curve on the top-left
        path.lineTo(cornerRadius + depressionDepth, 0);
        path.quadTo(cornerRadius, depressionDepth, cornerRadius + 2 * depressionDepth, cornerRadius);

        // Right side
        path.lineTo(width - cornerRadius, 0);
        path.quadTo(width, 0, width, cornerRadius); // Top-right rounded corner

        path.lineTo(width, height - cornerRadius);
        path.quadTo(width, height, width - cornerRadius, height); // Bottom-right rounded corner

        // Create a concave curve on the bottom-left
        path.lineTo(cornerRadius + 2 * depressionDepth, height);
        path.quadTo(cornerRadius, height - depressionDepth, cornerRadius + depressionDepth, height - cornerRadius);

        // Finish back to the starting point
        path.lineTo(cornerRadius, height);
        path.quadTo(0, height, 0, height - cornerRadius);

        canvas.drawPath(path, paint); // Draw the path with red paint
    }
}
