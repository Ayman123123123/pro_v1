/**
 * ══════════════════════════════════════════════════════════════════════
 * قاعدة بيانات التطوير — SQLite حقيقية بلا أي اعتمادية جديدة
 * ══════════════════════════════════════════════════════════════════════
 *
 * تستخدم `node:sqlite` المدمج في Node 22 LTS (المعتمد في DEPENDENCY_POLICY)،
 * فلا تُضاف أي حزمة إلى package.json.
 *
 * الغرض: تشغيل لوحة الإدارة على تخزين دائم فعلي بدل بيانات في الذاكرة،
 * بحيث تبقى الموافقات والحظر والنشر بعد إعادة تشغيل الخادم — تمامًا كما
 * يفعل PostgreSQL في الإنتاج.
 *
 * المخطط مشتق حرفيًا من كيانات JPA الحقيقية:
 *   auth/model/UserAccount.kt · auth/model/UserDevice.kt
 *   admin/model/AdminAnalytics.kt · admin/model/AdminSessions.kt
 *   admin/model/AdminAuditLog.kt · admin/model/ContentModels.kt
 *
 * ⚠️ للتطوير المحلي فقط. الإنتاج يستخدم PostgreSQL + Mongo + Redis عبر
 *    backend-server الحقيقي. ملف قاعدة البيانات مُستبعد من Git.
 */
const { DatabaseSync } = require('node:sqlite');
const crypto = require('node:crypto');
const path = require('node:path');
const fs = require('node:fs');

const DB_PATH = process.env.RED_DEV_DB || path.join(__dirname, 'data', 'red-dev.sqlite');
fs.mkdirSync(path.dirname(DB_PATH), { recursive: true });

// `--reset` أو RED_DEV_DB_RESET=1 → حذف القاعدة والبدء ببيانات أولية نظيفة
if (process.argv.includes('--reset') || process.env.RED_DEV_DB_RESET === '1') {
  for (const suffix of ['', '-wal', '-shm']) {
    try { fs.rmSync(DB_PATH + suffix, { force: true }); } catch { /* غير موجود */ }
  }
  console.log(`[db] أُعيد ضبط قاعدة البيانات: ${DB_PATH}`);
}

const db = new DatabaseSync(DB_PATH);
db.exec('PRAGMA journal_mode = WAL');
db.exec('PRAGMA foreign_keys = ON');

const uuid = () => crypto.randomUUID();
const nowIso = () => new Date().toISOString();
const iso = (daysAgo = 0, hoursAgo = 0) =>
  new Date(Date.now() - daysAgo * 86400000 - hoursAgo * 3600000).toISOString();

// ─────────────────────────────── المخطط ───────────────────────────────
db.exec(`
CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  red_id TEXT NOT NULL UNIQUE,
  username TEXT NOT NULL UNIQUE,
  display_name TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING',
  role TEXT NOT NULL DEFAULT 'USER',
  pstn_enabled INTEGER NOT NULL DEFAULT 0,
  pstn_daily_limit INTEGER NOT NULL DEFAULT 0,
  rejection_reason TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  approved_at TEXT,
  approved_by TEXT,
  last_seen TEXT
);

CREATE TABLE IF NOT EXISTS devices (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device_name TEXT NOT NULL,
  platform TEXT NOT NULL DEFAULT 'ANDROID',
  identity_fingerprint TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING',
  authorization_certificate TEXT,
  certificate_expires_at TEXT,
  created_at TEXT NOT NULL,
  approved_at TEXT,
  revoked_at TEXT
);

CREATE TABLE IF NOT EXISTS refresh_sessions (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device_id TEXT,
  token_hash TEXT NOT NULL,
  created_at TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  revoked_at TEXT
);

CREATE TABLE IF NOT EXISTS audit_log (
  id TEXT PRIMARY KEY,
  admin_id TEXT,
  admin_username TEXT,
  action TEXT NOT NULL,
  category TEXT NOT NULL,
  target_type TEXT,
  target_id TEXT,
  description TEXT,
  metadata TEXT,
  ip_address TEXT,
  user_agent TEXT,
  severity TEXT NOT NULL DEFAULT 'INFO',
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS announcements (
  id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  body TEXT NOT NULL,
  type TEXT NOT NULL DEFAULT 'INFO',
  target_audience TEXT NOT NULL DEFAULT 'ALL',
  priority INTEGER NOT NULL DEFAULT 0,
  is_dismissible INTEGER NOT NULL DEFAULT 1,
  is_published INTEGER NOT NULL DEFAULT 0,
  show_from TEXT,
  show_until TEXT,
  created_by TEXT,
  created_at TEXT NOT NULL,
  published_at TEXT
);

CREATE TABLE IF NOT EXISTS feature_flags (
  id TEXT PRIMARY KEY,
  flag_name TEXT NOT NULL UNIQUE,
  description TEXT,
  enabled INTEGER NOT NULL DEFAULT 0,
  rollout_percentage INTEGER NOT NULL DEFAULT 0,
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS reports (
  id TEXT PRIMARY KEY,
  reporter_id TEXT,
  reporter_username TEXT,
  reported_user_id TEXT,
  reported_username TEXT,
  category TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING',
  description TEXT,
  content_type TEXT,
  content_id TEXT,
  assigned_to TEXT,
  resolution TEXT,
  notes TEXT,
  created_at TEXT NOT NULL,
  resolved_at TEXT
);

CREATE TABLE IF NOT EXISTS backups (
  id TEXT PRIMARY KEY,
  backup_type TEXT NOT NULL,
  status TEXT NOT NULL,
  size_bytes INTEGER NOT NULL DEFAULT 0,
  location TEXT,
  checksum TEXT,
  notes TEXT,
  started_at TEXT NOT NULL,
  completed_at TEXT,
  created_by TEXT
);

CREATE TABLE IF NOT EXISTS admin_sessions (
  id TEXT PRIMARY KEY,
  admin_id TEXT,
  admin_username TEXT,
  ip_address TEXT,
  user_agent TEXT,
  is_current INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL,
  last_activity_at TEXT,
  expires_at TEXT
);

CREATE TABLE IF NOT EXISTS analytics_daily (
  id TEXT PRIMARY KEY,
  stat_date TEXT NOT NULL UNIQUE,
  payload TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS polls (
  id TEXT PRIMARY KEY,
  question TEXT NOT NULL,
  poll_type TEXT NOT NULL DEFAULT 'SINGLE_CHOICE',
  status TEXT NOT NULL DEFAULT 'ACTIVE',
  is_anonymous INTEGER NOT NULL DEFAULT 0,
  allow_add_options INTEGER NOT NULL DEFAULT 0,
  options TEXT NOT NULL DEFAULT '[]',
  total_votes INTEGER NOT NULL DEFAULT 0,
  created_by TEXT,
  created_at TEXT NOT NULL,
  closes_at TEXT
);

CREATE TABLE IF NOT EXISTS events (
  id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  description TEXT,
  event_type TEXT NOT NULL DEFAULT 'MEETING',
  status TEXT NOT NULL DEFAULT 'SCHEDULED',
  visibility TEXT NOT NULL DEFAULT 'PUBLIC',
  rsvp_enabled INTEGER NOT NULL DEFAULT 1,
  attendee_count INTEGER NOT NULL DEFAULT 0,
  starts_at TEXT,
  ends_at TEXT,
  created_by TEXT,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS hashtags (
  id TEXT PRIMARY KEY,
  tag TEXT NOT NULL UNIQUE,
  usage_count INTEGER NOT NULL DEFAULT 0,
  trend_score INTEGER NOT NULL DEFAULT 0,
  is_blocked INTEGER NOT NULL DEFAULT 0,
  last_used_at TEXT,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS sticker_packs (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  description TEXT,
  is_official INTEGER NOT NULL DEFAULT 0,
  is_published INTEGER NOT NULL DEFAULT 0,
  is_free INTEGER NOT NULL DEFAULT 1,
  price_cents INTEGER NOT NULL DEFAULT 0,
  sticker_count INTEGER NOT NULL DEFAULT 0,
  cover_url TEXT,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS notifications (
  id TEXT PRIMARY KEY,
  type TEXT NOT NULL,
  title TEXT NOT NULL,
  body TEXT,
  is_read INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS kv (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);
CREATE INDEX IF NOT EXISTS idx_devices_user ON devices(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_reports_status ON reports(status);
`);

// ───────────────────── سلطة الهوية: مفاتيح ECDSA ─────────────────────
/**
 * تولّد زوج مفاتيح P-256 وتحفظه، مطابقًا لما يتوقعه
 * DeviceCertificateService.kt (SHA256withECDSA على EC/prime256v1).
 * الشهادة تُوقَّع فعليًا وتُتحقَّق فعليًا — ليست نصًا وهميًا.
 */
function identityAuthority() {
  const row = db.prepare('SELECT value FROM kv WHERE key = ?').get('identity_authority');
  if (row) return JSON.parse(row.value);
  const { privateKey, publicKey } = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const material = {
    privateKeyPem: privateKey.export({ type: 'pkcs8', format: 'pem' }).toString(),
    publicKeyPem: publicKey.export({ type: 'spki', format: 'pem' }).toString(),
    publicKeyBase64: publicKey.export({ type: 'spki', format: 'der' }).toString('base64'),
  };
  db.prepare('INSERT INTO kv (key, value) VALUES (?, ?)').run('identity_authority', JSON.stringify(material));
  return material;
}

/** يبني شهادة تفويض جهاز بنفس تنسيق الخادم: base64url(payload).base64url(signature) */
function issueDeviceCertificate(user, device, validDays = 90) {
  const authority = identityAuthority();
  const issuedAt = Math.floor(Date.now() / 1000);
  const expiresAt = issuedAt + validDays * 86400;
  const payload = [
    'v1',
    user.id,
    user.red_id,
    device.id,
    device.identity_fingerprint,
    String(issuedAt),
    String(expiresAt),
  ].join('|');
  const signature = crypto.sign('sha256', Buffer.from(payload, 'utf8'), crypto.createPrivateKey(authority.privateKeyPem));
  return {
    compact: `${Buffer.from(payload, 'utf8').toString('base64url')}.${signature.toString('base64url')}`,
    expiresAt: new Date(expiresAt * 1000).toISOString(),
  };
}

/** تحقق فعلي من التوقيع — تستخدمه شاشة الأمان لإثبات صحة الشهادة. */
function verifyDeviceCertificate(compact) {
  try {
    const [payloadB64, sigB64] = String(compact).split('.');
    if (!payloadB64 || !sigB64) return { valid: false, reason: 'MALFORMED' };
    const payload = Buffer.from(payloadB64, 'base64url').toString('utf8');
    const valid = crypto.verify(
      'sha256',
      Buffer.from(payload, 'utf8'),
      crypto.createPublicKey(identityAuthority().publicKeyPem),
      Buffer.from(sigB64, 'base64url')
    );
    const [, userId, redId, deviceId, fingerprint, issued, expires] = payload.split('|');
    return {
      valid,
      userId,
      redId,
      deviceId,
      fingerprint,
      issuedAt: new Date(Number(issued) * 1000).toISOString(),
      expiresAt: new Date(Number(expires) * 1000).toISOString(),
      expired: Number(expires) * 1000 < Date.now(),
    };
  } catch (e) {
    return { valid: false, reason: e.message };
  }
}

// ─────────────────────────────── التعبئة ───────────────────────────────
function seedIfEmpty() {
  const { c } = db.prepare('SELECT COUNT(*) AS c FROM users').get();
  if (c > 0) return false;

  const insertUser = db.prepare(`INSERT INTO users
    (id,red_id,username,display_name,status,role,pstn_enabled,pstn_daily_limit,created_at,updated_at,approved_at,last_seen)
    VALUES (?,?,?,?,?,?,?,?,?,?,?,?)`);
  const insertDevice = db.prepare(`INSERT INTO devices
    (id,user_id,device_name,platform,identity_fingerprint,status,authorization_certificate,certificate_expires_at,created_at,approved_at)
    VALUES (?,?,?,?,?,?,?,?,?,?)`);

  const people = [
    ['younes_sovereign', 'يونس السيادي', 'APPROVED', 'ADMIN', 1, 100, 0],
    ['ahmed_dev', 'أحمد المطور', 'PENDING', 'USER', 0, 0, 1],
    ['ali_red', 'علي أحمد', 'PENDING', 'USER', 0, 0, 2],
    ['sara_ops', 'سارة العمليات', 'APPROVED', 'USER', 1, 20, 5],
    ['khaled_m', 'خالد محمد', 'APPROVED', 'USER', 0, 0, 9],
    ['noor_a', 'نور عبدالله', 'BANNED', 'USER', 0, 0, 14],
    ['omar_t', 'عمر طارق', 'APPROVED', 'USER', 1, 10, 21],
    ['huda_s', 'هدى سالم', 'REJECTED', 'USER', 0, 0, 27],
    ['fahd_k', 'فهد كمال', 'APPROVED', 'USER', 0, 0, 33],
    ['layla_n', 'ليلى ناصر', 'PENDING', 'USER', 0, 0, 0],
  ];

  people.forEach(([username, displayName, status, role, pstn, limit, daysAgo], i) => {
    const id = uuid();
    const created = iso(daysAgo, i);
    insertUser.run(
      id, `RED-${1000 + i * 7}`, username, displayName, status, role, pstn, limit,
      created, created,
      status === 'APPROVED' ? created : null,
      status === 'APPROVED' ? iso(0, i) : null
    );

    const deviceId = uuid();
    // بصمة مفتاح هوية بصيغة libsignal (60 خانة على مجموعات خماسية)
    const fingerprint = crypto.createHash('sha256').update(username).digest('hex')
      .slice(0, 60).replace(/(.{5})/g, '$1 ').trim();
    const deviceStatus = status === 'APPROVED' ? 'APPROVED' : status === 'PENDING' ? 'PENDING' : 'REVOKED';
    let cert = null;
    let certExp = null;
    if (deviceStatus === 'APPROVED') {
      const issued = issueDeviceCertificate(
        { id, red_id: `RED-${1000 + i * 7}` },
        { id: deviceId, identity_fingerprint: fingerprint }
      );
      cert = issued.compact;
      certExp = issued.expiresAt;
    }
    insertDevice.run(
      deviceId, id, i % 2 === 0 ? 'Samsung Galaxy A54' : 'Xiaomi Redmi Note 12',
      'ANDROID', fingerprint, deviceStatus, cert, certExp, created,
      deviceStatus === 'APPROVED' ? created : null
    );
  });

  const admin = db.prepare("SELECT id FROM users WHERE role='ADMIN'").get();

  const insAudit = db.prepare(`INSERT INTO audit_log
    (id,admin_id,admin_username,action,category,target_type,target_id,description,ip_address,user_agent,severity,created_at)
    VALUES (?,?,?,?,?,?,?,?,?,?,?,?)`);
  [
    ['ACCOUNT_APPROVED', 'USER', 'INFO', 'اعتماد حساب بعد تطابق بصمة المفتاح'],
    ['SESSION_TERMINATED', 'SECURITY', 'WARNING', 'إنهاء جلسة إدارية خاملة'],
    ['FEATURE_FLAG_UPDATED', 'SYSTEM', 'INFO', 'تحديث علم الميزة LIVE_STREAMING'],
    ['KILL_SWITCH_ARMED', 'SECURITY', 'CRITICAL', 'تفعيل مفتاح الإيقاف الطارئ (تجربة)'],
    ['ANNOUNCEMENT_PUBLISHED', 'SYSTEM', 'INFO', 'نشر إعلان الصيانة'],
    ['BACKUP_CREATED', 'SYSTEM', 'INFO', 'إنشاء نسخة احتياطية كاملة'],
  ].forEach(([action, category, severity, description], i) =>
    insAudit.run(uuid(), admin.id, 'red_admin', action, category, 'SYSTEM', null,
      description, '192.168.11.20', 'Mozilla/5.0 (X11; Linux x86_64)', severity, iso(0, i * 3))
  );

  const insAnn = db.prepare(`INSERT INTO announcements
    (id,title,body,type,target_audience,priority,is_dismissible,is_published,show_from,created_by,created_at,published_at)
    VALUES (?,?,?,?,?,?,?,?,?,?,?,?)`);
  [
    ['صيانة مجدولة للبنية التحتية', 'صيانة قاعدة البيانات الليلة 02:00–03:00 بتوقيت عدن.', 'MAINTENANCE', 1, 2],
    ['إطلاق المكالمات الجماعية', 'المؤتمرات الصوتية عبر SFU متاحة الآن لكل الحسابات المعتمدة.', 'FEATURE', 1, 6],
    ['تنبيه أمني', 'فعّلوا التحقق من بصمة المفتاح قبل قبول أي جهاز جديد.', 'WARNING', 0, 1],
  ].forEach(([title, body, type, published, daysAgo]) =>
    insAnn.run(uuid(), title, body, type, 'ALL', type === 'WARNING' ? 2 : 0, 1, published,
      iso(daysAgo), 'red_admin', iso(daysAgo), published ? iso(daysAgo) : null)
  );

  const insFlag = db.prepare('INSERT INTO feature_flags (id,flag_name,description,enabled,rollout_percentage,updated_at) VALUES (?,?,?,?,?,?)');
  [
    ['GROUP_E2EE_SENDER_KEYS', 'توزيع وتدوير مفاتيح المجموعات', 0, 0],
    ['PSTN_DINSTAR_ROUTING', 'توجيه المكالمات عبر بوابة DINSTAR', 1, 100],
    ['STORIES_MEDIA', 'الحالات والوسائط المؤقتة', 1, 100],
    ['LIVE_STREAMING', 'البث المباشر عبر SFU', 0, 25],
    ['LOCAL_FTS_SEARCH', 'بحث محلي مشفر FTS5', 0, 10],
  ].forEach(([name, desc, enabled, rollout]) => insFlag.run(uuid(), name, desc, enabled, rollout, iso(1)));

  const allUsers = db.prepare('SELECT id, username FROM users').all();
  const insRep = db.prepare(`INSERT INTO reports
    (id,reporter_id,reporter_username,reported_user_id,reported_username,category,status,description,content_type,content_id,resolution,created_at,resolved_at)
    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)`);
  [
    ['SPAM', 'PENDING', 'رسائل ترويجية متكررة'],
    ['ABUSE', 'PENDING', 'لغة مسيئة داخل مجموعة'],
    ['IMPERSONATION', 'RESOLVED', 'انتحال هوية حساب إداري'],
    ['OTHER', 'DISMISSED', 'بلاغ غير مكتمل'],
  ].forEach(([category, status, description], i) => {
    const a = allUsers[(i + 3) % allUsers.length];
    const b = allUsers[(i + 5) % allUsers.length];
    insRep.run(uuid(), a.id, a.username, b.id, b.username, category, status, description,
      'MESSAGE', uuid(), status === 'RESOLVED' ? 'تم اتخاذ إجراء' : null, iso(0, i * 5),
      status === 'RESOLVED' ? iso(0, 1) : null);
  });

  const insBackup = db.prepare(`INSERT INTO backups
    (id,backup_type,status,size_bytes,location,checksum,started_at,completed_at,created_by) VALUES (?,?,?,?,?,?,?,?,?)`);
  [
    ['FULL', 'COMPLETED', 4294967296],
    ['INCREMENTAL', 'COMPLETED', 536870912],
    ['CONFIG_ONLY', 'VERIFIED', 12582912],
  ].forEach(([type, status, size], i) =>
    insBackup.run(uuid(), type, status, size, `minio://red-backups/${type.toLowerCase()}-${i}.tar.zst`,
      crypto.randomBytes(16).toString('hex'), iso(i), iso(i), 'red_admin')
  );

  const insSession = db.prepare(`INSERT INTO admin_sessions
    (id,admin_id,admin_username,ip_address,user_agent,is_current,created_at,last_activity_at,expires_at) VALUES (?,?,?,?,?,?,?,?,?)`);
  insSession.run(uuid(), admin.id, 'red_admin', '192.168.11.20', 'Mozilla/5.0 (X11; Linux x86_64)', 1, iso(0, 1), nowIso(), iso(-1));
  insSession.run(uuid(), admin.id, 'ops_admin', '192.168.11.34', 'Mozilla/5.0 (Windows NT 10.0)', 0, iso(0, 5), iso(0, 2), iso(-1));

  const insPoll = db.prepare(`INSERT INTO polls (id,question,poll_type,status,is_anonymous,allow_add_options,options,total_votes,created_by,created_at) VALUES (?,?,?,?,?,?,?,?,?,?)`);
  [
    ['ما أولوية التطوير القادمة؟', 'ACTIVE', 120],
    ['هل تفضل الوضع الليلي؟', 'CLOSED', 75],
  ].forEach(([q, status, votes], i) =>
    insPoll.run(uuid(), q, 'SINGLE_CHOICE', status, 0, 0,
      JSON.stringify([
        { id: uuid(), text: 'المكالمات', votes: Math.round(votes * 0.53) },
        { id: uuid(), text: 'المجموعات', votes: Math.round(votes * 0.47) },
      ]), votes, admin.id, iso(i + 1))
  );

  const insEvent = db.prepare(`INSERT INTO events (id,title,description,event_type,status,visibility,rsvp_enabled,attendee_count,starts_at,ends_at,created_by,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)`);
  [
    ['اجتماع فريق التشغيل', 'SCHEDULED', 'MEETING', 40],
    ['بث مباشر: إطلاق النسخة', 'LIVE', 'BROADCAST', 28],
    ['ورشة الأمان', 'COMPLETED', 'WORKSHOP', 16],
  ].forEach(([title, status, type, count], i) =>
    insEvent.run(uuid(), title, 'حدث داخلي على منصة يونس', type, status, 'PUBLIC', 1, count,
      iso(-i), iso(-i - 1), admin.id, iso(i + 2))
  );

  const insTag = db.prepare('INSERT INTO hashtags (id,tag,usage_count,trend_score,is_blocked,last_used_at,created_at) VALUES (?,?,?,?,?,?,?)');
  ['يونس', 'صنعاء', 'عدن', 'تقنية', 'أمن_المعلومات', 'اليمن', 'برمجة', 'تشفير']
    .forEach((tag, i) => insTag.run(uuid(), tag, 1200 - i * 130, 98 - i * 9, 0, iso(0, i), iso(30 - i)));

  const insPack = db.prepare('INSERT INTO sticker_packs (id,name,description,is_official,is_published,is_free,price_cents,sticker_count,created_at) VALUES (?,?,?,?,?,?,?,?,?)');
  [
    ['حزمة يونس الرسمية', 1, 1, 0],
    ['تعابير يمنية', 0, 1, 0],
    ['حزمة رمضان', 0, 0, 500],
  ].forEach(([name, official, published, price]) =>
    insPack.run(uuid(), name, 'حزمة ملصقات محلية', official, published, price === 0 ? 1 : 0, price, 24, iso(10))
  );

  const insNotif = db.prepare('INSERT INTO notifications (id,type,title,body,is_read,created_at) VALUES (?,?,?,?,?,?)');
  [
    ['APPROVAL', 'طلب موافقة جديد', 'جهاز جديد بانتظار التحقق من البصمة'],
    ['SECURITY', 'تنبيه أمني', 'محاولة دخول فاشلة متكررة'],
    ['SYSTEM', 'اكتملت النسخة الاحتياطية', 'نسخة كاملة بحجم 4 غيغابايت'],
  ].forEach(([type, title, body], i) => insNotif.run(uuid(), type, title, body, 0, iso(0, i * 2)));

  // تحليلات 30 يومًا — الرئيسية ترسم منها المخططات
  const insAna = db.prepare('INSERT INTO analytics_daily (id,stat_date,payload) VALUES (?,?,?)');
  for (let d = 29; d >= 0; d--) {
    const date = new Date(Date.now() - d * 86400000).toISOString().slice(0, 10);
    const i = 29 - d;
    insAna.run(uuid(), date, JSON.stringify({
      statDate: date,
      totalUsers: 1180 + i * 8, newUsers: 8 + (i % 5) * 3,
      activeUsersDau: 420 + i * 4, activeUsersMau: 980 + i * 6,
      pendingApprovals: 3, bannedUsers: 1,
      messagesSent: 9800 + i * 210, messagesDelivered: 9650 + i * 205, messagesRead: 9100 + i * 200,
      voiceMessages: 320 + i * 9, mediaUploads: 210 + i * 6,
      mediaBytesUploaded: (18 + i) * 1073741824,
      callsTotal: 260 + i * 7, callsAudio: 180 + i * 4, callsVideo: 55 + i * 2,
      callsConference: 15 + (i % 4), callsLive: 4, callsPstn: 40 + (i % 9),
      callsDurationSeconds: (3600 + i * 90) * 6, callsFailed: 6, callsMissed: 14,
      dinstarActivePorts: 7, dinstarTotalCalls: 40 + (i % 9),
      dinstarTotalDurationSeconds: 5400 + i * 120,
      dinstarBalanceRemaining: 24500 - i * 180,
      groupsCreated: 3 + (i % 3),
      storageUsedBytes: (240 + i * 2) * 1073741824,
    }));
  }

  return true;
}

const seeded = seedIfEmpty();

// ───────────────────────── أدوات استعلام مختصرة ─────────────────────────
/**
 * `node:sqlite` يرفض `undefined` بخطأ غامض («cannot be bound to SQLite parameter»).
 * الطلبات القادمة من الواجهة كثيرًا ما تُغفل حقولًا اختيارية، فتصل `undefined`
 * بدل `null`. التطبيع هنا يمنع تحوّل حقل ناقص إلى خطأ 500.
 */
const bind = (params) => params.map((v) => (v === undefined ? null : v));

const all = (sql, ...p) => db.prepare(sql).all(...bind(p));
const get = (sql, ...p) => db.prepare(sql).get(...bind(p));
const run = (sql, ...p) => db.prepare(sql).run(...bind(p));

function recordAudit({ adminId = null, adminUsername = 'red_admin', action, category = 'SYSTEM',
  targetType = null, targetId = null, description = null, severity = 'INFO', ip = '127.0.0.1' }) {
  run(`INSERT INTO audit_log (id,admin_id,admin_username,action,category,target_type,target_id,description,ip_address,user_agent,severity,created_at)
       VALUES (?,?,?,?,?,?,?,?,?,?,?,?)`,
    uuid(), adminId, adminUsername, action, category, targetType, targetId, description,
    ip, 'admin-dashboard', severity, nowIso());
}

module.exports = {
  db, DB_PATH, seeded,
  uuid, nowIso, iso,
  all, get, run,
  recordAudit,
  identityAuthority, issueDeviceCertificate, verifyDeviceCertificate,
};
