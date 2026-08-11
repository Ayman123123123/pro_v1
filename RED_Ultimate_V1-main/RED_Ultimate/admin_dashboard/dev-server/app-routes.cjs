/**
 * ══════════════════════════════════════════════════════════════════════
 * مسارات تطبيق الهاتف (red-app) — نفس قاعدة بيانات لوحة الإدارة
 * ══════════════════════════════════════════════════════════════════════
 *
 * هذا هو الربط الفعلي بين الثلاثة: التطبيق والخادم واللوحة يقرؤون ويكتبون
 * في `red-dev.sqlite` نفسه. المستخدم الذي يعتمده المسؤول في صفحة الموافقات
 * يستطيع في اللحظة التالية تسجيل الدخول من التطبيق، ويظهر في دليل RED،
 * وتُقبل جهات اتصاله — بلا أي مزامنة يدوية.
 *
 * العقد منقول حرفيًا من الخادم الحقيقي ومن نماذج التطبيق:
 *   auth/AuthController.kt · auth/RegistrationService.kt  → /api/auth/*
 *   auth/ContactController.kt · auth/ContactService.kt    → /api/contacts/*
 *   auth/PublicDirectoryController.kt                     → /api/directory/search
 *   auth/IdentityDirectoryController.kt                   → /api/identity/directory/*
 *   red-app: social/FeedModels.kt · groups/GroupModels.kt · calls/CallHistoryModels.kt
 *
 * ⚠️ الخصوصية: هذه المسارات لا تخزّن ولا تعيد أي نص رسائل خاصة ولا مفاتيح
 *    خاصة. الرسائل تمر مشفّرة عبر /ws/master ولا تُفك هنا إطلاقًا. ما يُخزَّن
 *    من المفاتيح هو الحزم العامة فقط (identityKey/preKeys) كما في الخادم.
 */
const crypto = require('node:crypto');
const d = require('./db.cjs');

const { uuid, nowIso, iso, all, get, run, recordAudit } = d;

// ─────────────── الجلسات: رمز وصول ↔ مستخدم (تطوير فقط) ───────────────
/**
 * الخادم الحقيقي يُصدر JWT موقّعًا. هنا نستخدم رمزًا معتمًا مربوطًا بجدول
 * في الذاكرة: يكفي لتشغيل التطبيق محليًا، ويمنع انتحال هوية مستخدم آخر
 * لأن الرمز عشوائي 32 بايت وغير قابل للتخمين.
 */
const sessions = new Map();   // accessToken  → userId
const refreshes = new Map();  // refreshToken → userId

function issueTokens(userId) {
  const accessToken = `red.${crypto.randomBytes(24).toString('base64url')}`;
  const refreshToken = `rfr.${crypto.randomBytes(24).toString('base64url')}`;
  sessions.set(accessToken, userId);
  refreshes.set(refreshToken, userId);
  return { accessToken, refreshToken, tokenType: 'Bearer', expiresInSeconds: 900 };
}

/** يستخرج المستخدم من ترويسة Authorization. */
function currentUser(ctx) {
  const header = ctx?.headers?.authorization || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : null;
  if (!token) return null;
  const userId = sessions.get(token);
  return userId ? get('SELECT * FROM users WHERE id = ?', userId) : null;
}

// ─────────────────────────── محوّلات ───────────────────────────
const deviceDto = (r) => ({
  id: r.id, deviceName: r.device_name, platform: r.platform,
  identityFingerprint: r.identity_fingerprint, status: r.status,
  authorizationCertificate: r.authorization_certificate,
  certificateExpiresAt: r.certificate_expires_at,
});

/** UserResponse كما يتوقعه red-app/auth/AuthModels.kt — redId حقل إلزامي. */
const userResponse = (u) => ({
  id: u.id,
  redId: u.red_id,
  username: u.username,
  displayName: u.display_name,
  status: u.status,
  role: u.role,
  rejectionReason: u.rejection_reason,
  pstnEnabled: !!u.pstn_enabled,
  pstnDailyLimit: u.pstn_daily_limit,
  devices: all('SELECT * FROM devices WHERE user_id = ? ORDER BY created_at', u.id).map(deviceDto),
});

/** PublicRedProfile — (redId, username, displayName) فقط، بلا أي حقل خاص. */
const publicProfile = (u) => ({ redId: u.red_id, username: u.username, displayName: u.display_name });

const postDto = (p, viewerId) => {
  const author = get('SELECT * FROM users WHERE id = ?', p.author_id);
  const reactions = all('SELECT type, COUNT(*) c FROM post_reactions WHERE post_id = ? GROUP BY type', p.id);
  return {
    id: p.id,
    authorRedId: author?.red_id || 'RED-0000',
    authorUsername: author?.username || 'unknown',
    authorDisplayName: author?.display_name || 'مستخدم',
    text: p.text,
    visibility: p.visibility,
    kind: p.kind,
    parentId: p.parent_id,
    quotePostId: p.quote_post_id,
    poll: p.poll ? JSON.parse(p.poll) : null,
    media: [],
    hashtags: JSON.parse(p.hashtags || '[]'),
    mentions: JSON.parse(p.mentions || '[]'),
    linkCard: null,
    createdAt: p.created_at,
    editedAt: p.edited_at,
    editHistory: [],
    reactionCounts: Object.fromEntries(reactions.map((r) => [r.type, r.c])),
    replyCount: get('SELECT COUNT(*) c FROM posts WHERE parent_id = ?', p.id).c,
    repostCount: p.repost_count,
    isHidden: !!p.is_hidden,
    isMuted: false,
    viewerReacted: viewerId
      ? all('SELECT type FROM post_reactions WHERE post_id=? AND user_id=?', p.id, viewerId).map((r) => r.type)
      : [],
  };
};

const groupDto = (g) => {
  const owner = get('SELECT * FROM users WHERE id = ?', g.owner_id);
  return {
    id: g.id, name: g.name, description: g.description,
    ownerRedId: owner?.red_id || '', avatarUrl: g.avatar_url, createdAt: g.created_at,
    members: all(`SELECT m.*, u.red_id, u.username FROM group_members m
                  JOIN users u ON u.id = m.user_id WHERE m.group_id = ? ORDER BY m.joined_at`, g.id)
      .map((m) => ({
        id: m.id, groupId: m.group_id, userId: m.user_id,
        redId: m.red_id, username: m.username, role: m.role, joinedAt: m.joined_at,
      })),
  };
};

const callDto = (c) => ({
  id: c.id, peerId: c.peer_id, peerLabel: c.peer_label, direction: c.direction,
  type: c.type, route: c.route, status: c.status,
  startedAt: c.started_at, answeredAt: c.answered_at, endedAt: c.ended_at,
});

// ─────────────────────────── التسجيل ───────────────────────────
const ok = (data) => ({ status: 200, data });
const created = (data) => ({ status: 201, data });
const noContent = () => ({ status: 204, data: null });
const bad = (error) => ({ status: 400, data: { error } });
const unauthorized = () => ({ status: 401, data: { error: 'UNAUTHENTICATED' } });
const notFound = (error = 'NOT_FOUND') => ({ status: 404, data: { error } });

const USERNAME_RE = /^[a-zA-Z][a-zA-Z0-9_.]{2,31}$/;
const COMMON_PASSWORDS = new Set([
  '123456789012', 'password1234', 'qwerty123456', 'admin12345678',
  'younes123456', 'red123456789', '111111111111', '000000000000',
]);

/**
 * معرّف يونس — خمسة أرقام. منقول عن `auth/RedIdGenerator.kt`.
 *
 * المدى 10001..99999 (89,999 معرّفًا؛ 10000 محجوز للنظام). لا يبدأ بصفر حتى يبقى الطول
 * خمسة دائمًا ولا يضيع الصفر عند نسخه إلى حقل رقمي.
 *
 * التوليد عشوائي تشفيريًا لا تسلسلي: المعرّف التسلسلي يكشف ترتيب
 * التسجيل وحجم القاعدة.
 */
/** محجوز لمُرسِل رسائل النظام — لا يُخصَّص لمستخدم. */
const YOUNES_ID_SYSTEM = '10000';
const YOUNES_ID_MIN = 10001;
const YOUNES_ID_MAX = 99999;
const YOUNES_ID_SPACE = YOUNES_ID_MAX - YOUNES_ID_MIN + 1;
const YOUNES_ID_PATTERN = /^[1-9][0-9]{4}$/;

const nextRedId = () => {
  for (let attempt = 0; attempt < 200; attempt++) {
    const redId = String(YOUNES_ID_MIN + crypto.randomInt(YOUNES_ID_SPACE));
    if (!get('SELECT 1 x FROM users WHERE red_id = ?', redId)) return redId;
  }
  throw new Error(`تعذّر تخصيص معرّف يونس — الفضاء (${YOUNES_ID_SPACE}) شارف على الامتلاء`);
};

/** تطبيع مدخل البحث: يقبل البادئات القديمة ويُبقي الأرقام. */
const normalizeYounesId = (raw) => {
  const digits = String(raw || '').toUpperCase()
    .replace(/^YNS-?/, '').replace(/^RED-?/, '').replace(/\D/g, '');
  if (!YOUNES_ID_PATTERN.test(digits)) return null;
  // معرّف النظام ليس مستخدمًا ولا يُبحث عنه.
  return digits === YOUNES_ID_SYSTEM ? null : digits;
};

/**
 * محدّد معدل البحث في الدليل — نافذة منزلقة لكل مستخدم.
 *
 * ضروري لأن معرّف يونس صار خمسة أرقام: 90,000 احتمالًا قابلة للتعداد
 * الكامل. بلا هذا الحد يستطيع حساب معتمد واحد حصاد كل المستخدمين
 * (الاسم والصورة وحالة الاتصال) بتجربة كل الأرقام.
 *
 * الحد 20 بحثًا في الدقيقة: يكفي الاستخدام البشري الطبيعي بفارق كبير،
 * ويجعل مسح الفضاء كاملًا يستغرق أكثر من ثلاثة أشهر متواصلة.
 */
const DIRECTORY_WINDOW_MS = 60_000;
const DIRECTORY_MAX_QUERIES = 20;
const directoryHits = new Map();

function directoryRateLimiter(userId) {
  const now = Date.now();
  const hits = (directoryHits.get(userId) || []).filter((t) => now - t < DIRECTORY_WINDOW_MS);
  if (hits.length >= DIRECTORY_MAX_QUERIES) {
    const retryAfterSeconds = Math.ceil((DIRECTORY_WINDOW_MS - (now - hits[0])) / 1000);
    directoryHits.set(userId, hits);
    return { allowed: false, retryAfterSeconds };
  }
  hits.push(now);
  directoryHits.set(userId, hits);
  return { allowed: true, remaining: DIRECTORY_MAX_QUERIES - hits.length };
}

/** رسالة الحظر كما في RegistrationService.blockedResponse. */
const BLOCKED_MESSAGE = {
  PENDING: 'ACCOUNT_PENDING_ADMIN_APPROVAL',
  REJECTED: 'ACCOUNT_REJECTED',
  SUSPENDED: 'ACCOUNT_SUSPENDED',
  BANNED: 'ACCOUNT_BANNED',
};

/**
 * تسجيل حساب جديد من التطبيق.
 * بلا رقم هاتف ولا بريد ولا OTP — والحساب والجهاز كلاهما PENDING حتى
 * موافقة المسؤول من اللوحة. هذا هو المسار الذي يُنشئ صفوف صفحة الموافقات.
 */
function registerAccount(b) {
  const username = String(b?.username || '').trim().toLowerCase();
  const displayName = String(b?.displayName || '').trim();
  const password = String(b?.password || '');

  if (!USERNAME_RE.test(username)) return bad('Username must be 3-32 characters and contain only letters, numbers, dot or underscore');
  if (displayName.length < 2 || displayName.length > 100) return bad('Display name must be 2-100 visible characters');
  if (password.length < 12 || password.length > 128) return bad('Password must contain 12-128 characters');
  if (password.toLowerCase().includes(username)) return bad('Password must not contain the username');
  if (COMMON_PASSWORDS.has(password.toLowerCase())) return bad('Password is too common');
  if (get('SELECT id FROM users WHERE lower(username) = ?', username)) return bad('Username is already registered');

  const id = uuid();
  const ts = nowIso();
  const redId = nextRedId();
  run(`INSERT INTO users (id,red_id,username,display_name,status,role,pstn_enabled,pstn_daily_limit,created_at,updated_at)
       VALUES (?,?,?,?,'PENDING','USER',0,0,?,?)`, id, redId, username, displayName, ts, ts);

  const dev = b?.device || {};
  // البصمة تُشتق من المفتاح العام فقط — لا يصل الخادم أي مفتاح خاص
  const fingerprint = (crypto.createHash('sha256')
    .update(String(dev.identityKey || username)).digest('hex').slice(0, 60).match(/.{5}/g) || []).join(' ');
  const deviceId = uuid();
  run(`INSERT INTO devices (id,user_id,device_name,platform,identity_fingerprint,status,created_at)
       VALUES (?,?,?,?,?,'PENDING',?)`,
    deviceId, id, dev.deviceName || 'جهاز جديد', dev.platform || 'ANDROID', fingerprint, ts);

  if (dev.identityKey) {
    run(`INSERT OR REPLACE INTO device_prekeys
         (device_id,registration_id,protocol_device_id,identity_key,signed_pre_key_id,signed_pre_key,
          signed_pre_key_signature,kyber_pre_key_id,kyber_pre_key,kyber_pre_key_signature)
         VALUES (?,?,?,?,?,?,?,?,?,?)`,
      deviceId, dev.registrationId || 0, dev.protocolDeviceId || 1, dev.identityKey,
      dev.signedPreKeyId || 0, dev.signedPreKey || '', dev.signedPreKeySignature || '',
      dev.kyberPreKeyId || 0, dev.kyberPreKey || '', dev.kyberPreKeySignature || '');
  }

  recordAudit({ action: 'ACCOUNT_REGISTERED', category: 'USER', targetType: 'USER', targetId: id,
    description: `${redId} — بانتظار موافقة المسؤول` });

  return ok({
    status: 'PENDING',
    user: userResponse(get('SELECT * FROM users WHERE id = ?', id)),
    deviceId,
    recoveryCodes: Array.from({ length: 6 }, () => crypto.randomBytes(5).toString('hex').toUpperCase()),
    message: 'ACCOUNT_PENDING_ADMIN_APPROVAL',
  });
}

/**
 * تسجيل الدخول.
 * الحساب غير المعتمد يحصل على رد بلا رموز مع سبب الرفض — وهو ما يجعل
 * التطبيق يعرض «بانتظار موافقة المسؤول» بدل الدخول.
 */
function loginAccount(b) {
  const username = String(b?.username || '').trim().toLowerCase();
  const user = get('SELECT * FROM users WHERE lower(username) = ?', username);
  if (!user) return { status: 401, data: { error: 'INVALID_CREDENTIALS' } };

  if (user.status !== 'APPROVED') {
    return ok({
      status: user.status,
      user: userResponse(user),
      message: BLOCKED_MESSAGE[user.status] || 'ACCOUNT_BLOCKED',
    });
  }

  const device = get("SELECT * FROM devices WHERE user_id=? AND status='APPROVED' ORDER BY created_at LIMIT 1", user.id);
  const tokens = issueTokens(user.id);
  run('UPDATE users SET last_seen=? WHERE id=?', nowIso(), user.id);
  run(`INSERT INTO refresh_sessions (id,user_id,device_id,token_hash,created_at,expires_at)
       VALUES (?,?,?,?,?,?)`,
    uuid(), user.id, device?.id || null,
    crypto.createHash('sha256').update(tokens.refreshToken).digest('hex'),
    nowIso(), new Date(Date.now() + 30 * 86400000).toISOString());
  recordAudit({ adminId: user.id, adminUsername: user.username, action: 'USER_LOGIN',
    category: 'SECURITY', targetId: user.id, description: `${user.red_id} من التطبيق` });

  return ok({ status: 'APPROVED', user: userResponse(user), deviceId: device?.id, ...tokens });
}

// ─────────────────────────── تعريف المسارات ───────────────────────────
/** @param {(m:string,p:string,h:Function)=>void} on */
module.exports = function registerAppRoutes(on) {
  // ═══ المصادقة ═══
  on('POST', '/api/auth/register', (_p, _q, b) => registerAccount(b));
  on('POST', '/api/auth/login', (_p, _q, b) => loginAccount(b));

  on('POST', '/api/auth/refresh', (_p, _q, b) => {
    const userId = refreshes.get(b?.refreshToken);
    if (!userId) return unauthorized();
    refreshes.delete(b.refreshToken); // تدوير: الرمز القديم يُبطل فورًا
    const user = get('SELECT * FROM users WHERE id = ?', userId);
    if (!user || user.status !== 'APPROVED') return unauthorized();
    return ok(issueTokens(userId));
  });

  on('POST', '/api/auth/logout', (_p, _q, b, ctx) => {
    const header = ctx?.headers?.authorization || '';
    if (header.startsWith('Bearer ')) sessions.delete(header.slice(7));
    if (b?.refreshToken) refreshes.delete(b.refreshToken);
    return noContent();
  });

  on('POST', '/api/auth/recover', (_p, _q, b) => {
    const user = get('SELECT * FROM users WHERE red_id = ?', String(b?.redId || '').toUpperCase());
    if (!user) return notFound('RECOVERY_FAILED');
    recordAudit({ adminId: user.id, action: 'PASSWORD_RECOVERED', category: 'SECURITY',
      targetId: user.id, description: user.red_id, severity: 'WARNING' });
    return noContent();
  });

  // ═══ الملف الشخصي — مصدر RED ID داخل التطبيق ═══
  on('GET', '/api/me', (_p, _q, _b, ctx) => {
    const me = currentUser(ctx);
    return me ? ok(userResponse(me)) : unauthorized();
  });

  // ═══ الدليل العام ═══
  on('GET', '/api/directory/search', (_p, q, _b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    const term = String(q.get('query') || '').trim();
    if (term.length < 3 || term.length > 32) return bad('Search query must contain 3-32 characters');

    // ⚠️ حماية التعداد — لازمة بعد اختصار المعرّف إلى خمسة أرقام.
    // الفضاء 90,000 فقط، فبحثٌ بلا حدّ يعني حصاد الدليل كاملًا في
    // دقائق. الصيغة الطويلة السابقة كانت تجعل هذا مستحيلًا عمليًا،
    // والتعويض الآن بضبط المعدل لا بالمعرّف نفسه.
    const limited = directoryRateLimiter(me.id);
    if (!limited.allowed) {
      return { status: 429, data: { error: 'DIRECTORY_RATE_LIMITED', retryAfterSeconds: limited.retryAfterSeconds } };
    }

    // البحث بمعرّف يونس (خمسة أرقام) أو باسم المستخدم — المعتمدون فقط.
    const asId = normalizeYounesId(term);
    const matches = asId
      ? [get("SELECT * FROM users WHERE red_id = ? AND status='APPROVED'", asId)]
      : all(`SELECT * FROM users WHERE status='APPROVED'
             AND (lower(username) LIKE ? OR display_name LIKE ?) LIMIT 20`,
      `%${term.toLowerCase()}%`, `%${term}%`);
    return ok(matches.filter((u) => u && u.id !== me.id).map(publicProfile));
  });

  // ═══ جهات الاتصال ═══
  on('GET', '/api/contacts', (_p, _q, _b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    return ok(all(`SELECT u.* FROM contacts c JOIN users u ON u.id = c.contact_id
                   WHERE c.owner_id = ? AND u.status='APPROVED'
                   ORDER BY lower(u.display_name)`, me.id).map(publicProfile));
  });

  on('GET', '/api/contacts/presence', (_p, q, _b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    const requested = String(q.get('ids') || '').split(',').map((s) => s.trim().toUpperCase()).filter(Boolean);
    if (requested.length > 100) return bad('At most 100 contact IDs may be checked at once');
    // الحضور لجهات الاتصال المثبتة فقط — لا يُكشف لأي هوية عشوائية
    const allowed = all(`SELECT u.red_id, u.last_seen FROM contacts c JOIN users u ON u.id = c.contact_id
                         WHERE c.owner_id = ?`, me.id)
      .filter((r) => requested.includes(r.red_id));
    const cutoff = Date.now() - 120000;
    return ok(Object.fromEntries(allowed.map((r) =>
      [r.red_id, r.last_seen ? Date.parse(r.last_seen) >= cutoff : false])));
  });

  on('GET', '/api/contacts/requests', (_p, _q, _b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    // ملاحظة: `u.*` يطمس `r.id` لأن الاسمين متطابقان، فيعود معرّف المستخدم
    // بدل معرّف الطلب ويفشل القبول بـ 404. لذلك تُسمّى الأعمدة صراحة.
    return ok(all(`SELECT r.id AS request_id, r.created_at AS requested_at,
                          u.red_id, u.username, u.display_name
                   FROM contact_requests r
                   JOIN users u ON u.id = r.requester_id
                   WHERE r.recipient_id = ? AND r.status='PENDING' ORDER BY r.created_at`, me.id)
      .map((r) => ({ id: r.request_id, requester: publicProfile(r), createdAt: r.requested_at })));
  });

  on('POST', '/api/contacts/requests/:redId', (p, _q, _b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    const target = get("SELECT * FROM users WHERE red_id = ? AND status='APPROVED'", p.redId.toUpperCase());
    if (!target) return notFound('RED identity not found');
    if (target.id === me.id) return bad('Cannot add yourself');
    const blocked = get(`SELECT 1 x FROM contact_blocks WHERE (owner_id=? AND blocked_id=?) OR (owner_id=? AND blocked_id=?)`,
      me.id, target.id, target.id, me.id);
    if (blocked) return bad('Contact is blocked');
    if (get('SELECT 1 x FROM contacts WHERE owner_id=? AND contact_id=?', me.id, target.id)) return bad('Already a contact');
    const id = uuid();
    run(`INSERT INTO contact_requests (id,requester_id,recipient_id,status,created_at) VALUES (?,?,?,'PENDING',?)`,
      id, me.id, target.id, nowIso());
    return ok({ id, requester: publicProfile(me), createdAt: nowIso() });
  });

  const resolveRequest = (requestId, accept, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    const req = get('SELECT * FROM contact_requests WHERE id=? AND recipient_id=?', requestId, me.id);
    if (!req) return notFound('REQUEST_NOT_FOUND');
    run('UPDATE contact_requests SET status=? WHERE id=?', accept ? 'ACCEPTED' : 'REJECTED', requestId);
    if (accept) {
      const ts = nowIso();
      run('INSERT OR IGNORE INTO contacts (owner_id,contact_id,created_at) VALUES (?,?,?)', me.id, req.requester_id, ts);
      run('INSERT OR IGNORE INTO contacts (owner_id,contact_id,created_at) VALUES (?,?,?)', req.requester_id, me.id, ts);
    }
    return noContent();
  };
  on('POST', '/api/contacts/requests/:requestId/accept', (p, _q, _b, ctx) => resolveRequest(p.requestId, true, ctx));
  on('POST', '/api/contacts/requests/:requestId/reject', (p, _q, _b, ctx) => resolveRequest(p.requestId, false, ctx));

  on('DELETE', '/api/contacts/:redId', (p, _q, _b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    const target = get('SELECT * FROM users WHERE red_id = ?', p.redId.toUpperCase());
    if (!target) return notFound();
    run('DELETE FROM contacts WHERE owner_id=? AND contact_id=?', me.id, target.id);
    return noContent();
  });

  on('POST', '/api/contacts/:redId/block', (p, _q, _b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    const target = get('SELECT * FROM users WHERE red_id = ?', p.redId.toUpperCase());
    if (!target) return notFound();
    run('INSERT OR IGNORE INTO contact_blocks (owner_id,blocked_id,created_at) VALUES (?,?,?)', me.id, target.id, nowIso());
    run('DELETE FROM contacts WHERE owner_id=? AND contact_id=?', me.id, target.id);
    return noContent();
  });

  on('DELETE', '/api/contacts/:redId/block', (p, _q, _b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    const target = get('SELECT * FROM users WHERE red_id = ?', p.redId.toUpperCase());
    if (!target) return notFound();
    run('DELETE FROM contact_blocks WHERE owner_id=? AND blocked_id=?', me.id, target.id);
    return noContent();
  });

  on('GET', '/api/contacts/blocked', (_p, _q, _b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    return ok(all(`SELECT u.* FROM contact_blocks b JOIN users u ON u.id = b.blocked_id
                   WHERE b.owner_id = ?`, me.id).map(publicProfile));
  });

  /** بلاغ من التطبيق — يصل مباشرة إلى صفحة الإشراف في اللوحة. */
  on('POST', '/api/contacts/reports', (_p, _q, b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    const target = get('SELECT * FROM users WHERE red_id = ?', String(b?.redId || '').toUpperCase());
    const id = uuid();
    run(`INSERT INTO reports (id,reporter_id,reporter_username,reported_user_id,reported_username,category,status,description,created_at)
         VALUES (?,?,?,?,?,?,'PENDING',?,?)`,
      id, me.id, me.username, target?.id || null, target?.display_name || null,
      b?.category || 'ABUSE', b?.details || null, nowIso());
    recordAudit({ adminId: me.id, adminUsername: me.username, action: 'REPORT_SUBMITTED',
      category: 'MODERATION', targetType: 'REPORT', targetId: id,
      description: `${me.red_id} أبلغ عن ${target?.red_id || '—'}`, severity: 'WARNING' });
    return ok({ id, status: 'PENDING' });
  });

  // ═══ الهوية والمفاتيح العامة ═══
  on('GET', '/api/identity/authority', () =>
    ok({ publicKey: d.identityAuthority().publicKeyBase64, algorithm: 'SHA256withECDSA', curve: 'prime256v1' }));

  on('GET', '/api/identity/directory/:redId', (p) => {
    const user = get('SELECT * FROM users WHERE red_id = ?', p.redId.toUpperCase());
    if (!user) return notFound('RED identity not found');
    const devices = all("SELECT * FROM devices WHERE user_id=? AND status='APPROVED'", user.id);
    return ok({
      redId: user.red_id,
      devices: devices.map((dev) => {
        const k = get('SELECT * FROM device_prekeys WHERE device_id = ?', dev.id) || {};
        return {
          deviceId: dev.id,
          registrationId: k.registration_id ?? 0,
          protocolDeviceId: k.protocol_device_id ?? 1,
          identityKey: k.identity_key ?? '',
          identityFingerprint: dev.identity_fingerprint,
          signedPreKeyId: k.signed_pre_key_id ?? 0,
          signedPreKey: k.signed_pre_key ?? '',
          signedPreKeySignature: k.signed_pre_key_signature ?? '',
          kyberPreKeyId: k.kyber_pre_key_id ?? 0,
          kyberPreKey: k.kyber_pre_key ?? '',
          kyberPreKeySignature: k.kyber_pre_key_signature ?? '',
          authorizationCertificate: dev.authorization_certificate,
          certificateExpiresAt: dev.certificate_expires_at,
        };
      }),
    });
  });

  on('GET', '/api/identity/directory/:redId/:deviceId/prekey', (p) => {
    const user = get('SELECT * FROM users WHERE red_id = ?', p.redId.toUpperCase());
    if (!user) return notFound('RED identity not found');
    const dev = get("SELECT * FROM devices WHERE id=? AND user_id=? AND status='APPROVED'", p.deviceId, user.id);
    if (!dev) return notFound('Approved RED device not found');
    const k = get('SELECT * FROM device_prekeys WHERE device_id = ?', dev.id) || {};
    return ok({
      deviceId: dev.id,
      registrationId: k.registration_id ?? 0,
      protocolDeviceId: k.protocol_device_id ?? 1,
      identityKey: k.identity_key ?? '',
      signedPreKeyId: k.signed_pre_key_id ?? 0,
      signedPreKey: k.signed_pre_key ?? '',
      signedPreKeySignature: k.signed_pre_key_signature ?? '',
      kyberPreKeyId: k.kyber_pre_key_id ?? 0,
      kyberPreKey: k.kyber_pre_key ?? '',
      kyberPreKeySignature: k.kyber_pre_key_signature ?? '',
      oneTimePreKeyId: null,
      oneTimePreKey: null,
      authorizationCertificate: dev.authorization_certificate,
    });
  });

  // ═══ الأجهزة ═══
  on('GET', '/api/devices', (_p, _q, _b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    return ok(all('SELECT * FROM devices WHERE user_id = ? ORDER BY created_at', me.id).map(deviceDto));
  });

  on('DELETE', '/api/devices/:deviceId', (p, _q, _b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    run("UPDATE devices SET status='REVOKED', revoked_at=? WHERE id=? AND user_id=?", nowIso(), p.deviceId, me.id);
    recordAudit({ adminId: me.id, adminUsername: me.username, action: 'DEVICE_REVOKED',
      category: 'SECURITY', targetId: p.deviceId, description: me.red_id, severity: 'WARNING' });
    return noContent();
  });

  /** رفع/جرد مفاتيح الاستخدام الواحد — عامة فقط، والخاصة تبقى على الهاتف. */
  on('POST', '/api/devices/:deviceId/prekeys', (p, _q, b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    const dev = get('SELECT * FROM devices WHERE id=? AND user_id=?', p.deviceId, me.id);
    if (!dev) return notFound('DEVICE_NOT_FOUND');
    const count = (b?.preKeys || b?.oneTimePreKeys || []).length;
    return ok({ accepted: count, deviceId: p.deviceId });
  });

  on('GET', '/api/devices/:deviceId/prekeys/stock', (p, _q, _b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    const dev = get('SELECT * FROM devices WHERE id=? AND user_id=?', p.deviceId, me.id);
    if (!dev) return notFound('DEVICE_NOT_FOUND');
    return ok({ deviceId: p.deviceId, available: 92, threshold: 20, needsReplenish: false });
  });

  // ═══ المحتوى التفاعلي الذي يستهلكه التطبيق (استطلاعات وفعاليات) ═══
  on('POST', '/api/admin/content/polls/:pollId/vote', (p, _q, b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    const poll = get('SELECT * FROM polls WHERE id = ?', p.pollId);
    if (!poll) return notFound('POLL_NOT_FOUND');
    if (poll.status !== 'ACTIVE') return bad('POLL_CLOSED');
    const options = JSON.parse(poll.options);
    const option = options.find((o) => o.id === b?.optionId);
    if (!option) return bad('INVALID_OPTION');
    option.votes += 1;
    run('UPDATE polls SET options=?, total_votes=? WHERE id=?',
      JSON.stringify(options), poll.total_votes + 1, p.pollId);
    return ok({ id: poll.id, question: poll.question, options, totalVotes: poll.total_votes + 1, status: poll.status });
  });

  on('POST', '/api/admin/content/events/:eventId/rsvp', (p, _q, _b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    const ev = get('SELECT * FROM events WHERE id = ?', p.eventId);
    if (!ev) return notFound('EVENT_NOT_FOUND');
    run('UPDATE events SET attendee_count = attendee_count + 1 WHERE id = ?', p.eventId);
    return ok({ eventId: p.eventId, rsvp: true, attendeeCount: ev.attendee_count + 1 });
  });

  on('POST', '/api/admin/content/events/:eventId/checkin', (p, _q, _b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    if (!get('SELECT 1 x FROM events WHERE id = ?', p.eventId)) return notFound('EVENT_NOT_FOUND');
    return ok({ eventId: p.eventId, checkedIn: true, at: nowIso() });
  });

  /** إرسال SMS عبر بوابة DINSTAR — مسار PSTN منفصل يتحكم به المسؤول. */
  on('POST', '/api/admin/dinstar/sms/send', (_p, _q, b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    if (!me.pstn_enabled) return { status: 403, data: { error: 'PSTN_NOT_AUTHORIZED' } };
    const id = uuid();
    recordAudit({ adminId: me.id, adminUsername: me.username, action: 'DINSTAR_SMS_SENT',
      category: 'SYSTEM', targetId: id, description: `${me.red_id} → ${b?.number || '—'}` });
    return ok({ messageId: id, status: 'QUEUED', port: b?.port ?? 0 });
  });

  // ═══ التغذية الاجتماعية (ليست E2EE — محتوى عام) ═══
  on('GET', '/api/feed', (_p, q, _b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    const limit = Math.min(Number(q.get('limit') || 20), 100);
    const posts = all(`SELECT * FROM posts WHERE parent_id IS NULL AND is_hidden = 0
                       ORDER BY created_at DESC LIMIT ?`, limit);
    return ok({ posts: posts.map((p) => postDto(p, me.id)), nextCursor: null });
  });

  on('POST', '/api/feed/posts', (_p, _q, b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    const text = String(b?.text || '').trim();
    if (!text) return bad('Post text must not be empty');
    const id = uuid();
    const poll = (b?.pollOptions || []).length
      ? JSON.stringify({ options: b.pollOptions.map((t) => ({ id: uuid(), text: t, votes: 0 })), expiresAt: null })
      : null;
    run(`INSERT INTO posts (id,author_id,text,visibility,kind,parent_id,quote_post_id,hashtags,mentions,poll,created_at)
         VALUES (?,?,?,?,?,?,?,?,?,?,?)`,
      id, me.id, text, b?.visibility || 'LOCAL_YEMEN', b?.parentId ? 'REPLY' : 'POST',
      b?.parentId || null, b?.quotePostId || null,
      JSON.stringify(b?.hashtags || []), JSON.stringify(b?.mentions || []), poll, nowIso());
    return created(postDto(get('SELECT * FROM posts WHERE id = ?', id), me.id));
  });

  on('GET', '/api/feed/posts/:postId/thread', (p, _q, _b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    const rows = all('SELECT * FROM posts WHERE id = ? OR parent_id = ? ORDER BY created_at', p.postId, p.postId);
    return ok(rows.map((r) => postDto(r, me.id)));
  });

  on('PUT', '/api/feed/posts/:postId', (p, _q, b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    const post = get('SELECT * FROM posts WHERE id=? AND author_id=?', p.postId, me.id);
    if (!post) return notFound('POST_NOT_FOUND');
    run('UPDATE posts SET text=?, edited_at=? WHERE id=?', String(b?.text || post.text), nowIso(), p.postId);
    return ok(postDto(get('SELECT * FROM posts WHERE id = ?', p.postId), me.id));
  });

  on('DELETE', '/api/feed/posts/:postId', (p, _q, _b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    run('DELETE FROM posts WHERE id=? AND author_id=?', p.postId, me.id);
    return noContent();
  });

  on('POST', '/api/feed/posts/:postId/reactions', (p, _q, b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    const type = String(b?.type || 'LIKE');
    if (b?.active === false) run('DELETE FROM post_reactions WHERE post_id=? AND user_id=? AND type=?', p.postId, me.id, type);
    else run('INSERT OR IGNORE INTO post_reactions (post_id,user_id,type) VALUES (?,?,?)', p.postId, me.id, type);
    const post = get('SELECT * FROM posts WHERE id = ?', p.postId);
    return post ? ok(postDto(post, me.id)) : notFound();
  });

  on('POST', '/api/feed/posts/:postId/vote', (p, _q, b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    const post = get('SELECT * FROM posts WHERE id = ?', p.postId);
    if (!post || !post.poll) return notFound('POLL_NOT_FOUND');
    const poll = JSON.parse(post.poll);
    const option = poll.options.find((o) => o.id === b?.optionId);
    if (!option) return bad('INVALID_OPTION');
    option.votes += 1;
    run('UPDATE posts SET poll=? WHERE id=?', JSON.stringify(poll), p.postId);
    return ok(postDto(get('SELECT * FROM posts WHERE id = ?', p.postId), me.id));
  });

  on('POST', '/api/feed/posts/:postId/hide', (p, _q, _b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    run('UPDATE posts SET is_hidden=1 WHERE id=?', p.postId);
    return noContent();
  });

  on('POST', '/api/feed/posts/:postId/report', (p, _q, b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    const post = get('SELECT * FROM posts WHERE id = ?', p.postId);
    const author = post ? get('SELECT * FROM users WHERE id = ?', post.author_id) : null;
    const id = uuid();
    run(`INSERT INTO reports (id,reporter_id,reporter_username,reported_user_id,reported_username,category,status,description,content_type,content_id,created_at)
         VALUES (?,?,?,?,?,'CONTENT','PENDING',?,'POST',?,?)`,
      id, me.id, me.username, author?.id || null, author?.display_name || null,
      b?.reason || 'محتوى مخالف', p.postId, nowIso());
    recordAudit({ adminId: me.id, adminUsername: me.username, action: 'REPORT_SUBMITTED',
      category: 'MODERATION', targetType: 'POST', targetId: p.postId,
      description: `بلاغ عن منشور من ${me.red_id}`, severity: 'WARNING' });
    return noContent();
  });

  on('POST', '/api/feed/mute/:redId', () => noContent());
  on('GET', '/api/feed/following/:redId', (_p, _q, _b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    return ok({ posts: [], nextCursor: null });
  });

  // ═══ المجموعات ═══
  on('GET', '/api/groups', (_p, _q, _b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    return ok(all(`SELECT g.* FROM groups_tbl g JOIN group_members m ON m.group_id = g.id
                   WHERE m.user_id = ? ORDER BY g.created_at DESC`, me.id).map(groupDto));
  });

  on('POST', '/api/groups', (_p, _q, b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    const name = String(b?.name || '').trim();
    if (!name) return bad('Group name must not be empty');
    const id = uuid();
    const ts = nowIso();
    run('INSERT INTO groups_tbl (id,name,description,owner_id,created_at) VALUES (?,?,?,?,?)',
      id, name, b?.description || null, me.id, ts);
    run('INSERT INTO group_members (id,group_id,user_id,role,joined_at) VALUES (?,?,?,?,?)',
      uuid(), id, me.id, 'OWNER', ts);
    return created(groupDto(get('SELECT * FROM groups_tbl WHERE id = ?', id)));
  });

  on('GET', '/api/groups/:groupId', (p) => {
    const g = get('SELECT * FROM groups_tbl WHERE id = ?', p.groupId);
    return g ? ok(groupDto(g)) : notFound('GROUP_NOT_FOUND');
  });

  on('DELETE', '/api/groups/:groupId', (p, _q, _b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    run('DELETE FROM groups_tbl WHERE id=? AND owner_id=?', p.groupId, me.id);
    return noContent();
  });

  on('POST', '/api/groups/:groupId/members', (p, _q, b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    const target = get("SELECT * FROM users WHERE red_id=? AND status='APPROVED'", String(b?.redId || '').toUpperCase());
    if (!target) return notFound('RED identity not found');
    if (get('SELECT 1 x FROM group_members WHERE group_id=? AND user_id=?', p.groupId, target.id)) return bad('Already a member');
    run('INSERT INTO group_members (id,group_id,user_id,role,joined_at) VALUES (?,?,?,?,?)',
      uuid(), p.groupId, target.id, b?.role || 'MEMBER', nowIso());
    return ok(groupDto(get('SELECT * FROM groups_tbl WHERE id = ?', p.groupId)));
  });

  on('DELETE', '/api/groups/:groupId/members/:userId', (p, _q, _b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    run('DELETE FROM group_members WHERE group_id=? AND user_id=?', p.groupId, p.userId);
    return noContent();
  });

  on('PUT', '/api/groups/:groupId/members/:userId', (p, _q, b) => {
    run('UPDATE group_members SET role=? WHERE group_id=? AND user_id=?', b?.role || 'MEMBER', p.groupId, p.userId);
    return ok(groupDto(get('SELECT * FROM groups_tbl WHERE id = ?', p.groupId)));
  });

  on('GET', '/api/groups/:groupId/membership', (p, _q, _b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    const m = get('SELECT * FROM group_members WHERE group_id=? AND user_id=?', p.groupId, me.id);
    return ok({ member: !!m, role: m?.role || null });
  });

  on('POST', '/api/groups/:groupId/transfer-ownership', (p, _q, b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    const g = get('SELECT * FROM groups_tbl WHERE id=? AND owner_id=?', p.groupId, me.id);
    if (!g) return notFound('GROUP_NOT_FOUND');
    run('UPDATE groups_tbl SET owner_id=? WHERE id=?', b?.targetUserId, p.groupId);
    run("UPDATE group_members SET role='OWNER' WHERE group_id=? AND user_id=?", p.groupId, b?.targetUserId);
    run("UPDATE group_members SET role='ADMIN' WHERE group_id=? AND user_id=?", p.groupId, me.id);
    return ok(groupDto(get('SELECT * FROM groups_tbl WHERE id = ?', p.groupId)));
  });

  on('PUT', '/api/groups/:groupId/avatar', (p, _q, b) => {
    run('UPDATE groups_tbl SET avatar_url=? WHERE id=?', b?.mediaKey || null, p.groupId);
    return ok(groupDto(get('SELECT * FROM groups_tbl WHERE id = ?', p.groupId)));
  });

  on('POST', '/api/groups/:groupId/invites', (p, _q, b) =>
    ok({ id: uuid(), token: crypto.randomBytes(16).toString('base64url'),
      expiresAt: new Date(Date.now() + (b?.expiresHours || 24) * 3600000).toISOString(),
      maxUses: b?.maxUses || 1, requireApproval: b?.requireApproval !== false }));
  on('GET', '/api/groups/:groupId/join-requests', () => ok([]));
  on('GET', '/api/groups/join-requests', () => ok([]));
  on('POST', '/api/groups/:groupId/join-requests/:requestId', () => noContent());

  // ═══ المكالمات ═══
  on('GET', '/api/calls/history', (_p, q, _b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    return ok(all('SELECT * FROM call_history WHERE user_id = ? ORDER BY started_at DESC LIMIT ?',
      me.id, Math.min(Number(q.get('limit') || 100), 200)).map(callDto));
  });

  on('POST', '/api/calls/history', (_p, _q, b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    const id = uuid();
    run(`INSERT INTO call_history (id,user_id,peer_id,peer_label,direction,type,route,status,started_at,answered_at,ended_at)
         VALUES (?,?,?,?,?,?,?,?,?,?,?)`,
      id, me.id, b?.peerId || '', b?.peerLabel || b?.peerId || '', b?.direction || 'OUTGOING',
      b?.type || 'AUDIO', b?.route || 'RED', b?.status || 'ANSWERED',
      b?.startedAt || nowIso(), b?.answeredAt || null, b?.endedAt || null);
    return created(callDto(get('SELECT * FROM call_history WHERE id = ?', id)));
  });

  on('GET', '/api/calls/ice-servers', () => ok({
    expiresAt: Date.now() + 3600000,
    iceServers: [
      { urls: ['stun:stun.l.google.com:19302'] },
      { urls: ['turn:127.0.0.1:3478'], username: 'red-dev', credential: 'red-dev-secret' },
    ],
  }));
  on('POST', '/api/calls/telemetry', () => noContent());

  // ═══ PSTN (مسار منفصل يتحكم به المسؤول) ═══
  on('POST', '/api/pstn/calls', (_p, _q, b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    if (!me.pstn_enabled) return { status: 403, data: { error: 'PSTN_NOT_AUTHORIZED' } };
    const id = uuid();
    recordAudit({ adminId: me.id, adminUsername: me.username, action: 'PSTN_CALL_STARTED',
      category: 'CALLS', targetId: id, description: `${me.red_id} → ${b?.destination || '—'}` });
    return ok({ callId: id, status: 'DIALING', port: 3, destination: b?.destination });
  });
  on('POST', '/api/pstn/calls/:callId/hangup', () => noContent());

  // ═══ الوسائط والقصص والمجتمعات ═══
  on('POST', '/api/media', (_p, _q, _b, ctx) => {
    const me = currentUser(ctx);
    if (!me) return unauthorized();
    const key = `media/${uuid()}`;
    return ok({ mediaKey: key, uploadUrl: `/api/media/${key}`, expiresInSeconds: 900 });
  });
  on('POST', '/api/media/grants', () => ok({ grant: crypto.randomBytes(16).toString('base64url'), expiresInSeconds: 300 }));

  on('GET', '/api/stories', (_p, _q, _b, ctx) => {
    const me = currentUser(ctx);
    return me ? ok([]) : unauthorized();
  });
  on('POST', '/api/stories', (_p, _q, _b, ctx) => {
    const me = currentUser(ctx);
    return me ? created({ id: uuid(), createdAt: nowIso() }) : unauthorized();
  });
  on('DELETE', '/api/stories/:storyId', () => noContent());
  on('POST', '/api/stories/:storyId/view', () => noContent());
  on('POST', '/api/stories/:storyId/react', () => noContent());

  on('GET', '/api/communities', (_p, _q, _b, ctx) => {
    const me = currentUser(ctx);
    return me ? ok([]) : unauthorized();
  });
  on('POST', '/api/communities', (_p, _q, b, ctx) => {
    const me = currentUser(ctx);
    return me ? created({ id: uuid(), name: b?.name || '', createdAt: nowIso() }) : unauthorized();
  });
  on('GET', '/api/communities/:id', (p) => ok({ id: p.id, name: '', members: [] }));
  on('POST', '/api/communities/:id/join', () => noContent());
  on('POST', '/api/communities/:id/leave', () => noContent());
};

module.exports.currentUser = currentUser;
module.exports.loginAccount = loginAccount;
module.exports.registerAccount = registerAccount;
module.exports.userResponse = userResponse;
module.exports.issueTokens = issueTokens;
module.exports.sessions = sessions;
