package com.yuafeng.videoswiper

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * 正方形 FrameLayout，用于网格项
 * 强制高度 = 宽度，避免 RecyclerView 中尺寸不一致
 */
class SquareFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 强制高度 = 宽度
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}
