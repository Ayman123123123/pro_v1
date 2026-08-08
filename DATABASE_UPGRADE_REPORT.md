# 🗄️ تقرير تطوير قواعد البيانات السيادية الشامل

## الفرع: `arena/019fdfec-pro-v1` | Commit: `d09ea88`

---

## 🏛️ استراتيجية قواعد البيانات — الأفضل والأقوى لكل نوع

| قاعدة البيانات | نوع البيانات | لماذا هذا الخيار؟ |
|---|---|---|
| **PostgreSQL** | علائقية، تحتاج ACID، JOINs، CHECK constraints | مستخدمون، أجهزة، مجموعات، مكالمات، فواتير، خصوصية |
| **MongoDB** | مستندات مرنة، مصفوفات متداخلة، TTL | رسائل مشفرة، قصص (تنتهي)، منشورات، بث مباشر |
| **Redis** | مؤقتة، كاش، عدادات، أنماط Pub/Sub | حالة اتصال، تسلسل، rate limiting، إشعارات مؤقتة |
| **Room (Android)** | محلية، offline-first، Flow/LiveData | نسخة محلية من كل شيء للعمل بدون إنترنت |

---

## 📜 PostgreSQL — 15 migration → 17 جدول

### V14: `Profiles_Privacy_Calls_Notifications_Groups`

| الجدول | الأعمدة | الفهارس | Constraints |
|---|---|---|---|
| `users` (+12 أعمدة) | avatar_media_key, about_text, status_type, status_custom_text, status_visible_to, theme_preference, accent_color, font_scale, chat_bubble_style, language, is_rtl | — | CHECK على status_type (6 حالات)، CHECK على status_visible_to (5 مستويات)، CHECK على theme (8 ثيمات)، CHECK على accent (7 ألوان) |
| `user_privacy_settings` | user_id, last_seen, online_status, profile_photo, about, status, read_receipts, calls, groups_add, live_location | PK: user_id | CHECK على كل 9 أعمدة (5 مستويات خصوصية) |
| `privacy_exceptions` | id, user_id, setting, exception_user_id | user+setting | UNIQUE(user, setting, exception) |
| `call_history` | 22 عمود: caller_id, callee_id/callee_phone, call_type, call_route, direction, status, duration_ms, dinstar_port, signal_strength, max_participants, viewer_count, is_recorded, recording_media_key, 6 timestamps | caller+time, callee+time, type+time, status+time | CHECK على call_type (6 أنواع)، route (2)، direction (2)، status (7) |
| `call_participants` | call_id, user_id, joined_at, left_at, role | PK(call, user) | — |
| `user_notifications` | 15 عمود: user_id, type, title, body, sender_id/name, thread_id, group_id, is_read, priority, action_label, action_data (JSONB), secondary_action_label | user+unread+time, user+type+time, time | CHECK على type (16 نوع)، CHECK على priority (4 مستويات) |
| `notification_preferences` | user_id, 8 قنوات boolean, quiet_hours_enabled/start/end | PK: user_id | — |
| `groups` (+7 أعمدة) | description, avatar_media_key, privacy, created_by_red_id, max_members, is_announcement, updated_at | — | CHECK على privacy (3 مستويات) |
| `group_members` (+4 أعمدة) | custom_title, joined_at, is_muted, is_pinned | — | CHECK على role (4 أدوار: OWNER/ADMIN/MODERATOR/MEMBER) |
| `group_features` | group_id, messages, media, voice_notes, polls, calls, live, links, files | PK: group_id | — |
| `group_invites` | id, group_id, inviter_id, invitee_id, status, expires_at | invitee+status | CHECK على status (4 حالات) |
| `story_viewers` | story_id, viewer_id, viewed_at, reaction | PK(story, viewer), story+time | — |
| `usage_stats` | user_id, stat_date, 12 عداد استخدام | UNIQUE(user, date), user+date | — |

### V15: `Billing_CDR_RateLimit_Encryption`

| الجدول | الأعمدة | الفهارس | Constraints |
|---|---|---|---|
| `dinstar_cdr` | 22 عمود: gateway_id, port_index, call_id, caller/callee_number, direction, call_type, status, duration_seconds, ring_duration_seconds, start/answer/end_time, avg/min_signal_strength, cost_yer, internal_call_id | port+time, caller+time, callee+time | CHECK على direction (2)، status (5) |
| `pstn_tariffs` | 10 أعمدة: name, country_code, prefix_pattern, rate_per_minute_yer, rate_per_sms_yer, billing_increment_seconds | prefix+active | — |
| `user_bills` | 12 عمود: فترة، استخدام، تكاليف (YER)، حالة | user+period | CHECK على status (4) |
| `rate_limit_rules` | endpoint_pattern, limits (minute/hour/day), scope | endpoint+active | CHECK على scope (3) |
| `encryption_sessions` | user_id, remote_user_id, remote_device_id, session_state (BYTEA) | user+lastUsed | UNIQUE(user, remote, device) |
| `sent_prekey_records` | device_id, key_id, key_type, public_key, sent_to_user/device | device+type+consumed | CHECK على key_type (2) |
| `message_delivery_receipts` | message_uuid, recipient_user_id, recipient_device_id, delivery_status, timestamps | recipient+status+time, message_uuid | UNIQUE(msg, recipient, device), CHECK على status (4) |

### بيانات افتراضية
- **4 تعرفة PSTN يمنية**: سبأفون (77), MTN (71), يموبايل (73), HiTel (70)
- **6 قواعد Rate Limit**: login (5/min IP), register (3/min), messages (60/min), calls (10/min), PSTN (5/min), stories (20/min)
- **فهارس بحث**: Arabic tsvector على full_name+username، prefix indexes على red_id و username

---

## 🍃 MongoDB — 12 Collection

| Collection | الوصف | TTL | Compound Index |
|---|---|---|---|
| `messages` | رسائل مشفرة طرفيًا مع مرفقات وتفاعلات | — | `{conversationId: 1, sequenceNumber: -1}` |
| `conversation_sequences` | عداد تسلسلي للمحادثات | — | — |
| `call_history` | سجل المكالمات مع مشاركين | — | `{initiatorId: 1, startedAt: -1}` |
| `stories` | قصص مع خصوصية | **24 ساعة** (expireAfter) | `{ownerId: 1, expiresAt: 1}` |
| `story_views` | مشاهدات القصص | — | — |
| `story_reactions` | تفاعلات القصص | — | — |
| `posts` | منشورات مع استطلاعات | — | — |
| `post_reactions` | تفاعلات المنشورات | — | — |
| `poll_votes` | أصوات الاستطلاعات | — | — |
| `follows` | متابعات | — | — |
| `live_streams` | بث مباشر | — | — |
| `audio_spaces` | غرف صوتية | — | — |
| `group_messages` | رسائل مجموعات مشفرة | — | `{groupId: 1, sequenceNumber: -1}` |
| `notification_archive` | أرشيف إشعارات | — | `{userId: 1, createdAt: -1}` |

**تحسينات عن السابق:**
- `MessageDocument`: أضفف `attachments[]`, `reactions[]`, `replyToMessageUuid`, `forwardedFromConversationId`, `deletedForSenderAt`, `deletedForEveryoneAt`, `editedAt`
- `CallHistoryDocument`: أضفف `durationMs`, `dinstarPort`, `signalStrength`, `viewerCount`, `isRecorded`, `recordingMediaKey`, `participants[]`
- `StoryDocument`: أضفف `backgroundColor`, `visibleTo`, `excludedUsers`, `includedUsers`
- `PostDocument`: أضفف `visibleTo`, `excludedUsers`, `poll.multiChoice`
- **جديد**: `LiveStreamDocument`, `AudioSpaceDocument`, `GroupMessageDocument`, `NotificationArchiveDocument`

---

## 🔴 Redis — 20+ نمط مفاتيح

| النمط | النوع | TTL | الوصف |
|---|---|---|---|
| `red:seq:{convId}` | Counter | ∞ | تسلسل رقمي للمحادثة |
| `red:seq:group:{groupId}` | Counter | ∞ | تسلسل رسائل المجموعة |
| `red:presence:{userId}` | String | 5 دقائق | حالة الاتصال |
| `red:online` | Set | ∞ | المستخدمون المتصلون |
| `red:status:{userId}` | Hash | 24 ساعة | الحالة التفصيلية |
| `red:typing:{convId}:{userId}` | String | 5 ثواني | "يكتب الآن" |
| `red:ratelimit:{scope}:{key}` | Counter | = window | عداد Rate Limit |
| `red:session:{tokenHash}` | String | = session | جلسة Refresh Token |
| `red:otp:{userId}` | String | 5 دقائق | رمز التحقق |
| `red:notify:unread:{userId}` | Counter | ∞ | إشعارات غير مقروءة |
| `red:notify:queue:{userId}` | List (max 100) | ∞ | إشعارات مؤقتة |
| `red:call:signaling:{callId}` | String | 30 دقيقة | إشارات WebRTC |
| `red:dinstar:status:{gwId}` | String | 2 دقائق | حالة البوابة |
| `red:dinstar:ports:{gwId}` | Hash | ∞ | منافذ حالية |
| `red:dinstar:loadbalancer` | Counter | ∞ | Round-robin |
| `red:media:grant:{key}:{userId}` | String | 1 ساعة | صلاحية وسائط |
| `red:search:recent:{userId}` | List (max 20) | ∞ | عمليات بحث أخيرة |
| `red:metrics:realtime` | Hash | ∞ | مقاييس حية |

**دوال RedisManager الجديدة (30+ دالة):**
- Presence: setPresence, getPresence, removePresence, getOnlineUsers, isUserOnline
- Status: setUserStatus, getUserStatus
- Typing: setTyping, isTyping, getTypingUsers
- Rate Limit: checkRateLimit, getRateLimitRemaining
- Sessions: storeRefreshSession, getRefreshSession, revokeRefreshSession
- OTP: storeOtp, verifyOtp
- Notifications: incrementUnreadNotifications, getUnreadNotificationCount, resetUnreadNotifications, pushNotification, getRecentNotifications
- Call Signaling: cacheCallSignal, getCallSignal, removeCallSignal
- Dinstar: cacheDinstarStatus, getDinstarStatus, cacheDinstarPorts, getDinstarPorts, incrementLoadBalancerCounter
- Media: grantMediaAccess, hasMediaAccess, revokeMediaAccess
- Search: addRecentSearch, getRecentSearches, clearRecentSearches
- Metrics: incrementMetric, getMetrics, setMetric
- Cleanup: cleanUserData

---

## 📱 Android Room — 15 كيان + 7 DAO + 4 Migration

### الكيانات (v5)

| الكيان | الجدول | أعمدة جديدة عن v2 |
|---|---|---|
| `MessageEntity` | messages | messageType (8 أنواع), replyToMessageId, forwardedFromId, isDeletedForMe, isDeletedForEveryone, isEdited |
| `ConversationEntity` | conversations | isPinned, isMuted, draftText, yemeniPhoneNumber |
| `CallLogEntity` | call_logs | **جديد** — 6 أنواع مكالمات مع PSTN details |
| `ContactEntity` | contacts | **جديد** — 15 عمود مع حالة وخصوصية |
| `GroupEntity` | groups | **جديد** — privacy, myRole, features |
| `GroupMemberEntity` | group_members | **جديد** — role, customTitle |
| `NotificationEntity` | notifications | **جديد** — 16 نوع مع إجراءات |
| `MediaAttachmentEntity` | media_attachments | **جديد** — download/upload tracking |
| `PrivacySettingsEntity` | privacy_settings | **جديد** — 9 إعدادات |
| `UserProfileEntity` | user_profile | **جديد** — theme + status + language |
| `DraftMessageEntity` | draft_messages | **جديد** — مسودات محادثة |
| `MessageReactionEntity` | reactions | **جديد** — تفاعلات رسائل |

### الـ DAOs (7 واجهات)

| DAO | الدوال الرئيسية |
|---|---|
| `MasterDao` | 30+ دالة: conversations (getAll/getPrivate/getGroup/unread/pin/mute/draft), messages (get/insert/update/status/delete/search), media, reactions, drafts, profile, search |
| `StoryDao` | 8 دوال: getActive/getUser/getMy, insert, incrementViewCount, insertView, getViewCount, deleteExpired |
| `CallLogDao` | 9 دوال: getAll/getByType/getVoip/getPstn/getConference/getMissed/getMissedCount, insert, update, deleteOld |
| `ContactDao` | 12 دالة: getAll/getOnline/search/getByRedId/getByPhone, insert/toggleBlock/updateOnlineStatus/setOffline, getContactCount |
| `GroupDao` | 10 دوال: getAll/get/getMembers/getOwner/getMemberCount, insert/update/insertMember/updateRole/removeMember |
| `NotificationDao` | 9 دوال: getAll/getUnread/getByType/getUnreadCount (Flow), insert/markAsRead/markAllAsRead, deleteOld |
| `PrivacyDao` | 10 دوال: getSettings (Flow), save, update لكل 6 إعدادات فردية |

### الـ Migrations

| Migration | التغييرات |
|---|---|
| 1→2 | إنشاء stories + story_views مع فهارس |
| 2→3 | إنشاء call_logs (6 أنواع) + contacts (15 عمود) |
| 3→4 | إنشاء groups + group_members + notifications |
| 4→5 | إنشاء media_attachments + privacy_settings + user_profile + draft_messages + reactions. ALTER conversations (+3 أعمدة) + stories (+3 أعمدة) |

---

## 📊 ملخص بالأرقام

| المقياس | القيمة |
|---|---|
| PostgreSQL migrations جديدة | **2** (V14 + V15) |
| PostgreSQL جداول جديدة/محدثة | **17** |
| PostgreSQL أعمدة مضافة | **70+** |
| PostgreSQL فهارس | **25+** |
| PostgreSQL CHECK constraints | **15+** |
| PostgreSQL بيانات افتراضية | **4 تعرفة + 6 rate limits** |
| MongoDB collections | **14** |
| MongoDB documents جديدة/محدثة | **8** |
| Redis أنماط مفاتيح | **20+** |
| Redis دوال | **30+** |
| Room entities | **15** |
| Room DAOs | **7** |
| Room دوال DAO | **80+** |
| Room migrations | **4** |
| إجمالي أسطر برمجية | **2,044+** |
