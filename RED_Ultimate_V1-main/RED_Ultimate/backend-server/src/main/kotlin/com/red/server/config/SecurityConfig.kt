package com.red.server.config

import com.red.server.auth.security.JwtAuthenticationFilter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * YOUNES Sovereign Security Configuration
 * - Argon2id password hashing
 * - JWT authentication filter
 * - CORS configuration
 * - Stateless session management
 */
@Configuration
class SecurityConfig(
    private val jwtFilter: JwtAuthenticationFilter,
    @Value("\${red.security.allowed-origins:http://localhost,http://127.0.0.1}")
    private val configuredAllowedOrigins: String
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling { exceptions ->
                exceptions
                    .authenticationEntryPoint { _, response, _ ->
                        response.status = 401
                        response.contentType = MediaType.APPLICATION_JSON_VALUE
                        response.writer.write("{\"error\":\"AUTHENTICATION_REQUIRED\"}")
                    }
                    .accessDeniedHandler { _, response, _ ->
                        response.status = 403
                        response.contentType = MediaType.APPLICATION_JSON_VALUE
                        response.writer.write("{\"error\":\"ADMIN_ROLE_REQUIRED\"}")
                    }
            }
            .authorizeHttpRequests { auth ->
                auth
                    // Public endpoints
                    .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout", "/api/auth/recover").permitAll()
                    // مسار Asterisk dialplan الداخلي (System curl داخل شبكة Docker
                    // فقط — غير منشور عبر nginx). الحماية الفعلية: X-Internal-Secret
                    // يُتحقق منها في InternalPstnController نفسه.
                    .requestMatchers("/api/internal/pstn/**").permitAll()
                    // سلطة التوقيع وحدها عامة: مفتاح عام يجب أن يصل إلى
                    // العميل قبل أن يملك جلسة، وهو غير حساس بطبيعته.
                    .requestMatchers(HttpMethod.GET, "/api/identity/authority").permitAll()

                    // ⚠️ `/api/identity/directory` **ليست عامة**.
                    //
                    // كانت `permitAll()`، والمسار بلا شرطة مائلة نهائية لا
                    // يطابق `/api/identity/directory/{redId}`، غير أن ترك
                    // القاعدة هنا يوحي بأن الدليل عام ويغري بتوسيعها إلى
                    // `/**`. والمسار يكشف حزم المفاتيح العامة كاملةً
                    // (identityKey، البصمة، شهادة التخويل) لكل جهاز معتمد.
                    //
                    // الأخطر مسار `…/{deviceId}/prekey`: كل نداء **يستهلك**
                    // مفتاحًا لمرة واحدة استهلاكًا ذرّيًا. بلا مصادقة يستطيع
                    // أي طرف استنزاف مخزون مفاتيح أي مستخدم بحلقة بسيطة،
                    // فتتدهور جلسات Signal الجديدة إلى المفتاح الموقّع
                    // وحده — تعطيل خدمة يمسّ سرّية التشفير المستقبلية.
                    //
                    // ومع اختصار معرّف يونس إلى خمسة أرقام صار فضاء
                    // المعرّفات (89,999) قابلًا للتعداد الكامل، فما كان
                    // صعبًا عمليًا صار زحفًا مباشرًا على الدليل كله.
                    .requestMatchers(HttpMethod.GET, "/api/identity/directory/**").authenticated()
                    // التفاصيل الكاملة (قواعد البيانات، Dinstar، المضيف) للمسؤولين فقط؛
                    // المساران العامان أدناه يُبقيان الحالة وحدها.
                    .requestMatchers("/health/detailed").hasRole("ADMIN")
                    .requestMatchers("/health", "/health/live", "/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                    .requestMatchers("/ws/**").permitAll()
                    // ── مشاركة المستخدم في المحتوى ──
                    //
                    // ⚠️ هذه المسارات تقع تحت `/api/admin/content` بحكم
                    // `@RequestMapping` في `ContentController`، لكنها
                    // **أفعال مشارِك لا أفعال مسؤول**: كلٌّ منها يأخذ
                    // `authentication.name` — أي هوية المستدعي نفسه —
                    // ويسجّل صوته أو حضوره به.
                    //
                    // بقاؤها تحت قاعدة `/api/admin/**` كان يعني أن **لا
                    // مستخدم عادي يستطيع التصويت في استطلاع ولا تأكيد
                    // حضور فعالية**: 403 دائمًا. والتطبيق يستدعيها فعلًا
                    // (`EventsApi.rsvp/checkin`, `PollsApi.vote`).
                    //
                    // تسبق قاعدة ADMIN لأن الأسبقية للأول المطابق.
                    .requestMatchers(HttpMethod.POST, "/api/admin/content/polls/*/vote").authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/admin/content/events/*/rsvp").authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/admin/content/events/*/checkin").authenticated()
                    // قراءة المحتوى المنشور متاحة لكل مصادَق؛ الإنشاء
                    // والتعديل والحذف تبقى إدارية عبر القاعدة التالية.
                    .requestMatchers(HttpMethod.GET, "/api/admin/content/polls", "/api/admin/content/polls/active").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/admin/content/polls/*").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/admin/content/events", "/api/admin/content/events/live", "/api/admin/content/events/upcoming").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/admin/content/events/*").authenticated()
                    // الملصقات: استعراض المنشورة + ملصقات الحزمة + المثبّتة (للمستخدم)
                    .requestMatchers(HttpMethod.GET, "/api/admin/content/sticker-packs/published", "/api/admin/content/sticker-packs/installed", "/api/admin/content/sticker-packs/*/stickers").authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/admin/content/sticker-packs/*/install").authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/api/admin/content/sticker-packs/*/install").authenticated()

                    // SMS send/incoming: authenticated() عمداً — التطبيق يستخدمه للمستخدمين.
                    // الإنفاذ الفعلي داخل DinstarSmsController: المستخدم محبوس على شريحته
                    // المربوطة 1:1، والأدمن وحده يتحكم بالمنافذ/البوابات بحرية.
                    // ⚠️ SPRING SECURITY — FIRST MATCH WINS: specific authenticated() exceptions
                    // MUST come BEFORE broader hasRole(ADMIN) for same prefix, else ADMIN dead code.
                    // send/incoming are intentional user-level exceptions (isAdmin branching in controller
                    // — regular user locked to bound SIM, admin free on ports/gateways).
                    .requestMatchers(HttpMethod.POST, "/api/admin/dinstar/sms/send").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/admin/dinstar/sms/incoming").authenticated()
                    // All other Dinstar SMS ops (queue, result, stop, deliver) remain ADMIN-only.
                    // Placed AFTER send/incoming exceptions but BEFORE broad /api/admin/** so it is
                    // explicit and not shadowed/dead (previously at EOF after broad ADMIN → dead code).
                    .requestMatchers("/api/admin/dinstar/sms/**").hasRole("ADMIN")
                    // Admin endpoints (including the legacy live-stream admin namespace)
                    .requestMatchers("/api/admin/**", "/api/master/admin/**", "/api/master/v1/**", "/api/live/admin/**").hasRole("ADMIN")
                    // Social features
                    .requestMatchers(HttpMethod.GET, "/api/social/status/**").authenticated()
                    .requestMatchers(HttpMethod.PUT, "/api/social/status", "/api/social/privacy").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/social/privacy", "/api/social/online-contacts").authenticated()
                    .requestMatchers("/api/notifications/**").authenticated()
                    // Telemetry aggregates are an operations signal, not public user data.
                    // Keep the upload authenticated, but restrict the aggregated admin view.
                    .requestMatchers(HttpMethod.GET, "/api/calls/telemetry/stats").hasRole("ADMIN")
                    .requestMatchers("/api/calls/**", "/api/calls/ice-servers", "/api/calls/telemetry").authenticated()
                    .requestMatchers("/api/recordings/**").authenticated()
                    .requestMatchers("/api/stories/**").authenticated()
                    .requestMatchers("/api/feed/**", "/api/posts/**", "/api/social/**").authenticated()
                    .requestMatchers("/api/groups/**").authenticated()
                    // V26: ميزات جديدة — قنوات، تثبيت، اختفاء مرن
                    .requestMatchers("/api/channels/**").authenticated()
                    .requestMatchers("/api/messages/pins/**").authenticated()
                    .requestMatchers("/api/messages/**", "/api/contacts/**", "/api/devices/**").authenticated()
                    .requestMatchers("/api/pstn/**", "/api/dinstar/**").authenticated()
                    // Note: /api/admin/dinstar/sms/** ADMIN is already handled ABOVE
                    // (after send/incoming exceptions, before broad /api/admin/**).
                    // Removed dead duplicate that was here after broad ADMIN → never matched.
                    .requestMatchers("/api/media/**").authenticated()
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().authenticated()
            }
            // ✅ HttpOnly + SameSite for refresh cookie — CSRF enabled for admin panel
            // Admin dashboard uses Bearer JWT (stateless), but future HttpOnly cookie will set SameSite=Strict
            .headers { headers ->
                headers
                    .xssProtection { it.disable() }
                    .contentSecurityPolicy { it.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; connect-src 'self' ws: wss:; font-src 'self' data:; media-src 'self' blob:; frame-ancestors 'none'") }
                    .referrerPolicy { it.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER) }
            }
            // JWT filter must come before UsernamePasswordAuthenticationFilter (always present in filter chain)
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    /**
     * 🍪 Helper for future HttpOnly refresh cookie (10min SFU ticket already uses short-lived JWT)
     * When migrating from Bearer to Cookie, set:
     *   ResponseCookie.from("refreshToken", token).httpOnly(true).secure(true).sameSite("Strict").path("/api/auth").maxAge(30*24*3600).build()
     */
    fun buildRefreshCookie(token: String, maxAgeDays: Long = 30): String {
        return "refreshToken=$token; Path=/api/auth; Max-Age=${maxAgeDays*24*3600}; HttpOnly; Secure; SameSite=Strict"
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOriginPatterns = configuredAllowedOrigins.split(',').map(String::trim).filter(String::isNotEmpty)
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("Authorization", "Content-Type", "X-Requested-With", "X-Device-Id", "X-RED-Admin-Web", "X-RED-CSRF")
            exposedHeaders = listOf("Location", "X-Total-Count")
            allowCredentials = true
            maxAge = 3600
        }
        return UrlBasedCorsConfigurationSource().also {
            it.registerCorsConfiguration("/**", configuration)
        }
    }
}