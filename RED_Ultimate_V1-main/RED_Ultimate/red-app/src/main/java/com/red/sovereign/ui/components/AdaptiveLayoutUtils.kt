package com.red.sovereign.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * تصنيف حجم الشاشة — يحكم كل الأبعاد التكيّفية في الواجهة.
 */
enum class ScreenSizeCategory {
    /** أقل من 360dp — هواتف صغيرة أو قديمة. */
    COMPACT,

    /** من 360dp إلى 600dp — الهواتف الحديثة القياسية. */
    STANDARD,

    /** أكبر من 600dp — الهواتف القابلة للطي واللوحيات. */
    EXPANDED
}

/**
 * حزمة الأبعاد التكيّفية المشتقّة من عرض الشاشة.
 *
 * الغرض: منع تناثر أرقام `dp` السحرية في الشاشات. أي مكوّن يحتاج مقاسًا
 * يعتمد على حجم الجهاز يأخذه من هنا عبر [rememberAdaptiveDimens].
 */
data class AdaptiveDimens(
    val category: ScreenSizeCategory,
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val isTabletOrFoldable: Boolean,
    val contentHorizontalPadding: Dp,
    val headerVerticalPadding: Dp,
    val avatarSize: Dp,
    val primaryIconSize: Dp,
    val secondaryIconSize: Dp,
    val bottomBarHeight: Dp,
    val fabSize: Dp,
    val titleFontSize: TextUnit,
    val subtitleFontSize: TextUnit,
    val bodyFontSize: TextUnit
)

/**
 * يحسب [AdaptiveDimens] من إعدادات الجهاز الحالية، ويعيد الحساب فقط عند
 * تغيّر العرض أو الارتفاع (دوران الشاشة، فتح جهاز قابل للطي).
 */
@Composable
fun rememberAdaptiveDimens(): AdaptiveDimens {
    val configuration = LocalConfiguration.current
    val width = configuration.screenWidthDp
    val height = configuration.screenHeightDp

    return remember(width, height) {
        when {
            width < 360 -> AdaptiveDimens(
                category = ScreenSizeCategory.COMPACT,
                screenWidthDp = width,
                screenHeightDp = height,
                isTabletOrFoldable = false,
                contentHorizontalPadding = 10.dp,
                headerVerticalPadding = 6.dp,
                avatarSize = 34.dp,
                primaryIconSize = 22.dp,
                secondaryIconSize = 18.dp,
                bottomBarHeight = 64.dp,
                fabSize = 50.dp,
                titleFontSize = 13.sp,
                subtitleFontSize = 10.sp,
                bodyFontSize = 12.sp
            )

            width > 600 -> AdaptiveDimens(
                category = ScreenSizeCategory.EXPANDED,
                screenWidthDp = width,
                screenHeightDp = height,
                isTabletOrFoldable = true,
                contentHorizontalPadding = 24.dp,
                headerVerticalPadding = 14.dp,
                avatarSize = 46.dp,
                primaryIconSize = 28.dp,
                secondaryIconSize = 24.dp,
                bottomBarHeight = 76.dp,
                fabSize = 64.dp,
                titleFontSize = 16.sp,
                subtitleFontSize = 12.sp,
                bodyFontSize = 15.sp
            )

            else -> AdaptiveDimens(
                category = ScreenSizeCategory.STANDARD,
                screenWidthDp = width,
                screenHeightDp = height,
                isTabletOrFoldable = false,
                contentHorizontalPadding = 16.dp,
                headerVerticalPadding = 10.dp,
                avatarSize = 40.dp,
                primaryIconSize = 24.dp,
                secondaryIconSize = 20.dp,
                bottomBarHeight = 72.dp,
                fabSize = 56.dp,
                titleFontSize = 14.sp,
                subtitleFontSize = 11.sp,
                bodyFontSize = 14.sp
            )
        }
    }
}
