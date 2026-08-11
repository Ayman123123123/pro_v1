-- ══════════════════════════════════════════════════════════════════
-- 🏛️ YOUNES Sovereign Master Schema (V23 Sovereign Ultimate Edition)
-- PostgreSQL + MongoDB + Redis — المخطط الرئيسي الكامل
-- ══════════════════════════════════════════════════════════════════
--
-- قواعد البيانات:
-- ┌────────────┬──────────────────────────────────────────────────────┐
-- │ PostgreSQL  │ البيانات العلائقية: users, devices, groups, calls,  │
-- │             │ contacts, audit, billing, media_grants, privacy,    │
-- │             │ call_qoe_telemetry, group_invite_links,             │
-- │             │ channel_subscriber_preferences, session_fingerprints │
-- ├────────────┼──────────────────────────────────────────────────────┤
-- │ MongoDB     │ المستندات: messages, stories, posts, call_history,  │
-- │             │ group_messages, live_streams, audio_spaces           │
-- ├────────────┼──────────────────────────────────────────────────────┤
-- │ Redis       │ المؤقتات: presence, status, typing, sessions,       │
-- │             │ rate_limits, sequences, notifications, signaling    │
-- └────────────┴──────────────────────────────────────────────────────┘

-- ══════════════════════════════════════════
-- PostgreSQL Tables (Relational / ACID)
-- ══════════════════════════════════════════

-- 👤 المستخدمون
-- users: id, email, password_hash, full_name, red_id, username, status, role,
--        avatar_media_key, about_text, status_type, status_custom_text, status_visible_to,
--        theme_preference, accent_color, font_scale, chat_bubble_style, language, is_rtl,
--        pstn_enabled, pstn_daily_limit, last_seen, approved_at, approved_by,
--        rejection_reason, created_at, updated_at

-- 📱 الأجهزة
-- user_devices: id, user_id, device_name, platform, identity_key, signed_pre_key,
--   kyber_pre_key, signatures, identity_fingerprint, status, authorization_certificate,
--   certificate_expires_at, registration_id, protocol_device_id, key IDs, timestamps

-- 🔑 المفاتيح أحادية الاستخدام
-- one_time_ec_prekeys: device_id, key_id, public_key, created_at, consumed_at
-- one_time_kyber_prekeys: device_id, key_id, public_key, signature, created_at, consumed_at

-- 🔄 جلسات التحديث وبصمات الأجهزة
-- refresh_sessions: id, user_id, device_id, token_hash, created_at, expires_at, revoked_at
-- device_session_fingerprints: id, user_id, device_id, ip_address, user_agent, location_country,
--   is_suspicious, last_active_at, created_at

-- 🔐 رموز الاسترداد
-- recovery_codes: id, user_id, code_hash, created_at, used_at

-- 👥 المجموعات والقنوات (SQL للعلاقات)
-- groups: id, name, description, owner_id, avatar_media_key, privacy,
--   created_by_red_id, max_members, is_announcement, created_at, updated_at
-- group_members: group_id, user_id, role, custom_title, is_muted, is_pinned, joined_at
-- group_features: group_id, messages, media, voice_notes, polls, calls, live, links, files
-- group_invites: id, group_id, inviter_id, invitee_id, status, expires_at, timestamps
-- group_invite_links: id, group_id, creator_id, token_hash, max_uses, uses_count, expires_at, is_revoked, created_at
-- channel_subscriber_preferences: id, community_id, user_id, notifications_enabled, is_muted, last_read_post_id, joined_at

-- 📞 سجل المكالمات وجودة الصوت والمرئيات
-- call_history: id, caller_id, callee_id, callee_phone, call_type, call_route,
--   direction, status, duration_ms, dinstar_port, signal_strength, max_participants,
--   viewer_count, is_recorded, recording_media_key, timestamps
-- call_participants: call_id, user_id, role, joined_at, left_at
-- call_qoe_telemetry: id, call_id, user_id, route, duration_seconds, audio_bitrate_kbps,
--   video_bitrate_kbps, packet_loss_percent, jitter_ms, rtt_ms, mos_score, created_at

-- 🔔 الإشعارات
-- user_notifications: id, user_id, type, title, body, sender_id, sender_name,
--   thread_id, group_id, is_read, priority, action_label, action_data, timestamps
-- notification_preferences: user_id, messages, calls, groups, stories, live,
--   system, dinstar, security, quiet_hours_enabled, quiet_hours_start, quiet_hours_end

-- 🔒 الخصوصية
-- user_privacy_settings: user_id, last_seen, online_status, profile_photo, about,
--   status, read_receipts, calls, groups_add, live_location
-- privacy_exceptions: id, user_id, setting, exception_user_id

-- 👥 جهات الاتصال
-- contact_requests: id, requester_id, recipient_id, status, timestamps
-- red_contacts: owner_id, contact_id, created_at
-- user_blocks: blocker_id, blocked_id, created_at
-- user_reports: id, reporter_id, reported_id, category, details, status, timestamps

-- 📡 البوابات
-- telecom_gateways: id, name, vendor, model, host, scheme, api_port, enabled,
--   capabilities_json, last_seen_at, timestamps
-- gateway_port_snapshots: gateway_id, port_index, radio_type, registration_state,
--   call_state, signal_*, sim_number_masked, imsi_masked, iccid_masked
-- gateway_operations: id, gateway_id, actor_id, operation, target_port, status, details

-- 💰 الفواتير والتعرفة
-- dinstar_cdr: id, gateway_id, port_index, call_id, caller_number, callee_number,
--   direction, call_type, status, duration_seconds, ring_duration_seconds,
--   start_time, answer_time, end_time, signal_quality, cost_yer, internal_call_id
-- pstn_tariffs: id, name, country_code, prefix_pattern, rate_per_minute_yer,
--   rate_per_sms_yer, billing_increment_seconds, is_active, effective_from/until
-- user_bills: id, user_id, period, usage_stats, costs, status

-- 🚦 Rate Limiting
-- rate_limit_rules: id, endpoint_pattern, limits_per_minute/hour/day, scope

-- 🔐 التشفير
-- encryption_sessions: id, user_id, remote_user_id, remote_device_id, session_state
-- sent_prekey_records: id, device_id, key_id, key_type, public_key, sent_to/from
-- message_delivery_receipts: id, message_uuid, recipient_user_id, recipient_device_id,
--   delivery_status, timestamps

-- 🖼️ الوسائط
-- media_grants: object_key, owner_id, grantee_id, created_at, expires_at

-- 📊 الإحصائيات
-- usage_stats: id, user_id, stat_date, messages_sent/received, calls_*, pstn_*,
--   stories_posted/viewed, media_uploaded, media_bytes_uploaded

-- 🔍 التدقيق
-- audit_events: id, actor_id, action, target_id, details_json, created_at

-- ⚙️ النظام
-- system_settings: setting_key, setting_value, updated_at

-- ══════════════════════════════════════════
-- MongoDB Collections (Documents / Flexible)
-- ══════════════════════════════════════════

-- 💬 الرسائل (مشفرة طرفيًا)
-- messages: { uuid, conversationId, senderId, senderDeviceId, receiverId,
--   receiverDeviceId, payload (encrypted), messageType, ciphertextType,
--   sequenceNumber, status, attachments[{mediaKey, mimeType, encryptedKey,
--   digest, sizeBytes, width, height, durationMs, caption, fileName}],
--   replyToMessageUuid, forwardedFromConversationId,
--   reactions[{userId, emoji, addedAt}], deletedFor*, createdAt, deliveredAt, readAt }

-- conversation_sequences: { conversationId, sequence }

-- 📞 سجل المكالمات (سريع التغير)
-- call_history: { id, initiatorId, targetId, targetLabel, type, route, status,
--   durationMs, dinstarPort, signalStrength, viewerCount, isRecorded,
--   participants[{userId, role, joinedAt, leftAt}], startedAt, answeredAt, endedAt }

-- 📖 القصص (TTL 24 ساعة)
-- stories: { id, ownerId, ownerRedId, ownerUsername, ownerDisplayName,
--   mediaKey, mediaType, caption, backgroundColor, visibleTo, excludedUsers,
--   createdAt, expiresAt (TTL), deletedAt }
-- story_views: { id, storyId, viewerId, viewedAt, reaction }
-- story_reactions: { id, storyId, userId, emoji, createdAt }

-- 📝 المنشورات
-- posts: { id, authorId, authorRedId, authorUsername, authorDisplayName, text,
--   visibility, kind, parentId, quotePostId, poll{options[], expiresAt, multiChoice},
--   media[{objectKey, mimeType, width, height}], visibleTo, excludedUsers,
--   reactionCounts, repostCount, createdAt, editedAt, deletedAt }
-- post_reactions: { id, postId, userId, type, createdAt }
-- poll_votes: { id, postId, userId, optionId, createdAt }
-- follows: { id, followerId, followedId, createdAt }

-- 📡 البث المباشر
-- live_streams: { id, hostId, hostRedId, hostUsername, title, category,
--   status, viewerCount, maxViewers, mediaKey, startedAt, endedAt }
-- audio_spaces: { id, hostId, hostRedId, title, status,
--   speakers[{userId, username, role, joinedAt}], listenerCount, startedAt, endedAt }

-- 👥 رسائل المجموعات (مشفرة)
-- group_messages: { uuid, groupId, senderId, senderDeviceId, payload,
--   messageType, ciphertextType, sequenceNumber, status, attachments[],
--   replyToMessageUuid, reactions[], deletedFor*, createdAt, deliveredAt, readAt }

-- 🔔 أرشيف الإشعارات
-- notification_archive: { id, userId, type, title, body, senderId, senderName,
--   threadId, priority, actionLabel, actionData, isRead, createdAt, readAt }

-- ══════════════════════════════════════════
-- Redis Key Patterns (Cache / Ephemeral)
-- ══════════════════════════════════════════

-- red:seq:{conversationId}        — Counter: sequence number
-- red:seq:group:{groupId}         — Counter: group message sequence
-- red:presence:{userId}           — String: ONLINE/OFFLINE/BUSY (TTL 5min)
-- red:online                      — Set: online user IDs
-- red:status:{userId}             — Hash: {type, customText, visibleTo, updatedAt}
-- red:typing:{convId}:{userId}    — String: "1" (TTL 5s)
-- red:ratelimit:{scope}:{key}     — Counter: rate limit (TTL = window)
-- red:session:{tokenHash}         — String: userId:deviceId (TTL = session)
-- red:otp:{userId}                — String: code (TTL 5min)
-- red:notify:unread:{userId}      — Counter: unread notification count
-- red:notify:queue:{userId}       — List: recent notifications (max 100)
-- red:call:signaling:{callId}     — String: WebRTC signal JSON (TTL 30min)
-- red:dinstar:status:{gwId}       — String: status JSON (TTL 2min)
-- red:dinstar:ports:{gwId}        — Hash: port_index → port JSON
-- red:dinstar:loadbalancer        — Counter: round-robin port selection
-- red:media:grant:{key}:{userId}  — String: "1" (TTL 1h)
-- red:search:recent:{userId}      — List: recent search queries (max 20)
-- red:metrics:realtime            — Hash: {metric → value}
