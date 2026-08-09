-- 1. جدول المستخدمين وحالة الموافقة الإدارية
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    full_name VARCHAR(100),
    status VARCHAR(20) DEFAULT 'PENDING', -- PENDING, APPROVED, BANNED
    role VARCHAR(20) DEFAULT 'USER',      -- ADMIN, USER
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. جدول المجموعات والأدوار
CREATE TABLE IF NOT EXISTS groups (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    owner_id UUID REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS group_members (
    group_id UUID REFERENCES groups(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) DEFAULT 'MEMBER', -- OWNER, ADMIN, MEMBER
    PRIMARY KEY (group_id, user_id)
);

-- 3. جدول مراقبة DINSTAR (UC2000-VE-8G)
CREATE TABLE IF NOT EXISTS dinstar_slots (
    slot_index INT PRIMARY KEY,
    operator VARCHAR(50), -- Yemen Mobile, Sabafon, etc.
    status VARCHAR(20),   -- IDLE, CALLING, ERROR
    signal_strength INT,
    balance DECIMAL(10,2)
);
