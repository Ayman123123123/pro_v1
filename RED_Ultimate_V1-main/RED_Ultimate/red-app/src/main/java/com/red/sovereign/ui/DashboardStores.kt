package com.red.sovereign.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.MessageStore
import com.red.sovereign.core.database.LocalRepository

/**
 * ════════════════════════════════════════════════════════════════════════
 *  DashboardStores — توحيد TokenStore/LocalRepository/MessageStore
 * ════════════════════════════════════════════════════════════════════════
 *
 *  المشكلة: كان `RedDashboard.kt` ينشئ `TokenStore(context)` في 10+ مواضع
 *  (406/428/432/507/1045/1721/2186/3061/3697) و`LocalRepository` و`MessageStore`
 *  كـ `remember { ... }` منفصلة في كل تركيب، و`FeedScreens` يكرر الثلاثي
 *  نفسه 3 مرات (738/782/815). كل نسخة تحمل نفس SecureStore لكن بعمر
 *  مختلف — هدر ذاكرة وخطر عدم اتساق الجلسة بعد logout/login.
 *
 *  الحل (تطوير حقيقي — ممنوع الحذف): ViewModel واحد بعمر الشاشة
 *  + CompositionLocal للتمرير الضمني. السلوك مطابق: نفس الكلاسات، نفس
 *  الـ Context (applicationContext)، فقط المالك واحد.
 *
 *  الاستخدام:
 *  - داخل `RedDashboard`/`ChatHubScreen`: `val stores = rememberDashboardStores()`
 *    ثم `stores.tokenStore` بدل `remember { TokenStore(context) }`.
 *  - داخل شاشات بعيدة (FeedScreens): `rememberDashboardTokenStore()` تفضل
 *    الـ CompositionLocal إن وُجدت، وإلا ViewModel، وإلا نسخة محلية
 *    (لا كسر للمنادين القدامى).
 */
class DashboardStoresViewModel(app: Application) : AndroidViewModel(app) {
    val tokenStore: TokenStore by lazy { TokenStore(getApplication()) }
    val repository: LocalRepository by lazy { LocalRepository(getApplication()) }
    val localMessages: MessageStore by lazy { MessageStore(getApplication()) }
    val apiClient: AuthorizedApiClient by lazy { AuthorizedApiClient(tokenStore) }
}

/** تمرير ضمني للمخازن الموحدة — null تعني «لا موفر أعلى، استخدم ViewModel». */
val LocalDashboardStores = staticCompositionLocalOf<DashboardStoresViewModel?> { null }

/** المالك الوحيد: CompositionLocal إن وُجد، وإلا ViewModel بعمر الشاشة. */
@Composable
fun rememberDashboardStores(): DashboardStoresViewModel {
    LocalDashboardStores.current?.let { return it }
    return viewModel()
}

/** مختصر TokenStore الموحد — نفس المثيل في كل اللوحة. */
@Composable
fun rememberDashboardTokenStore(): TokenStore {
    LocalDashboardStores.current?.let { return it.tokenStore }
    val vm: DashboardStoresViewModel = viewModel()
    return vm.tokenStore
}

/** مختصر LocalRepository الموحد. */
@Composable
fun rememberDashboardRepository(): LocalRepository {
    LocalDashboardStores.current?.let { return it.repository }
    val vm: DashboardStoresViewModel = viewModel()
    return vm.repository
}

/** مختصر MessageStore الموحد. */
@Composable
fun rememberDashboardMessageStore(): MessageStore {
    LocalDashboardStores.current?.let { return it.localMessages }
    val vm: DashboardStoresViewModel = viewModel()
    return vm.localMessages
}

/** مختصر AuthorizedApiClient الموحد (فوق نفس TokenStore). */
@Composable
fun rememberDashboardApiClient(): AuthorizedApiClient {
    LocalDashboardStores.current?.let { return it.apiClient }
    val vm: DashboardStoresViewModel = viewModel()
    return vm.apiClient
}

/**
 * بديل آمن للسياقات غير-Composable (Worker/Service): نسخة واحدة لكل
 * Application عبر holder بسيط — لا يغير سلوك TokenStore نفسه.
 */
object DashboardStoresHolder {
    @Volatile private var appTokenStore: TokenStore? = null
    fun tokenStore(context: android.content.Context): TokenStore {
        val app = context.applicationContext
        return appTokenStore?.takeIf { it.context.applicationContext === app }
            ?: synchronized(this) {
                appTokenStore?.takeIf { it.context.applicationContext === app }
                    ?: TokenStore(app).also { appTokenStore = it }
            }
    }
    /** للاختبارات فقط — يفضل الوصول عبر LocalContext في Compose. */
    fun peekContext(): android.content.Context? = appTokenStore?.context
}
