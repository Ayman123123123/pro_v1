-- ══════════════════════════════════════════════════════════════════
-- V20: Advanced Content Features (Polls, Stories Reactions, Events)
-- PostgreSQL — ميزات متقدمة للمحتوى والتفاعل
-- ══════════════════════════════════════════════════════════════════

-- ━━━━ Polls (استطلاعات متقدمة) ━━━━
CREATE TABLE IF NOT EXISTS polls (
    id UUID PRIMARY KEY,
    creator_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    question TEXT NOT NULL,
    description TEXT,
    poll_type VARCHAR(20) NOT NULL DEFAULT 'SINGLE_CHOICE', -- SINGLE_CHOICE, MULTIPLE_CHOICE, RANKED
    is_anonymous BOOLEAN NOT NULL DEFAULT FALSE,
    allow_add_options BOOLEAN NOT NULL DEFAULT FALSE,
    -- Results visibility
    show_results_before_vote BOOLEAN NOT NULL DEFAULT FALSE,
    show_results_after_close BOOLEAN NOT NULL DEFAULT TRUE,
    -- Lifecycle
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- DRAFT, ACTIVE, CLOSED, ARCHIVED
    starts_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ends_at TIMESTAMP,
    -- Target
    target_type VARCHAR(20) NOT NULL DEFAULT 'GLOBAL', -- GLOBAL, GROUP, USER
    target_group_id UUID REFERENCES groups(id) ON DELETE CASCADE,
    target_user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    -- Stats
    total_votes INTEGER NOT NULL DEFAULT 0,
    unique_voters INTEGER NOT NULL DEFAULT 0,
    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT polls_type_check CHECK (poll_type IN ('SINGLE_CHOICE','MULTIPLE_CHOICE','RANKED')),
    CONSTRAINT polls_status_check CHECK (status IN ('DRAFT','ACTIVE','CLOSED','ARCHIVED')),
    CONSTRAINT polls_target_check CHECK (
        (target_type = 'GLOBAL') OR
        (target_type = 'GROUP' AND target_group_id IS NOT NULL) OR
        (target_type = 'USER' AND target_user_id IS NOT NULL)
    )
);
CREATE INDEX idx_polls_creator ON polls(creator_id, created_at DESC);
CREATE INDEX idx_polls_status ON polls(status, ends_at);
CREATE INDEX idx_polls_target_group ON polls(target_group_id, status) WHERE target_type = 'GROUP';
CREATE INDEX idx_polls_active ON polls(status, ends_at) WHERE status = 'ACTIVE';

CREATE TABLE IF NOT EXISTS poll_options (
    id UUID PRIMARY KEY,
    poll_id UUID NOT NULL REFERENCES polls(id) ON DELETE CASCADE,
    option_text VARCHAR(200) NOT NULL,
    option_order INTEGER NOT NULL DEFAULT 0,
    vote_count INTEGER NOT NULL DEFAULT 0,
    color VARCHAR(20),  -- للعرض البصري
    image_url VARCHAR(500)
);
CREATE INDEX idx_poll_options_poll ON poll_options(poll_id, option_order);

CREATE TABLE IF NOT EXISTS poll_votes (
    id UUID PRIMARY KEY,
    poll_id UUID NOT NULL REFERENCES polls(id) ON DELETE CASCADE,
    option_id UUID NOT NULL REFERENCES poll_options(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rank INTEGER, -- for RANKED polls (1=first choice, 2=second, etc.)
    voted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(poll_id, user_id, option_id)
);
CREATE INDEX idx_poll_votes_poll ON poll_votes(poll_id);
CREATE INDEX idx_poll_votes_user ON poll_votes(user_id, voted_at DESC);

-- ━━━━ Events (أحداث المجموعات/المستخدمين) ━━━━
CREATE TABLE IF NOT EXISTS events (
    id UUID PRIMARY KEY,
    creator_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    -- Location (for in-person events)
    location_name VARCHAR(200),
    location_address TEXT,
    location_lat DECIMAL(10,7),
    location_lng DECIMAL(10,7),
    location_url VARCHAR(500), -- for online events
    -- Time
    starts_at TIMESTAMP NOT NULL,
    ends_at TIMESTAMP,
    timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Aden',
    -- Type
    event_type VARCHAR(30) NOT NULL DEFAULT 'MEETING', -- MEETING, CONFERENCE, WEBINAR, SOCIAL, CELEBRATION, OTHER
    visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC', -- PUBLIC, PRIVATE, INVITATION_ONLY
    -- Capacity
    max_attendees INTEGER,
    current_attendees INTEGER NOT NULL DEFAULT 0,
    waitlist_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    -- Status
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED', -- DRAFT, SCHEDULED, LIVE, ENDED, CANCELLED
    cancelled_at TIMESTAMP,
    cancellation_reason TEXT,
    -- Media
    cover_image_media_key VARCHAR(200),
    -- RSVP
    rsvp_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    rsvp_deadline TIMESTAMP,
    -- Reminders
    reminder_sent BOOLEAN NOT NULL DEFAULT FALSE,
    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT events_type_check CHECK (event_type IN ('MEETING','CONFERENCE','WEBINAR','SOCIAL','CELEBRATION','OTHER')),
    CONSTRAINT events_visibility_check CHECK (visibility IN ('PUBLIC','PRIVATE','INVITATION_ONLY')),
    CONSTRAINT events_status_check CHECK (status IN ('DRAFT','SCHEDULED','LIVE','ENDED','CANCELLED'))
);
CREATE INDEX idx_events_creator ON events(creator_id, starts_at DESC);
CREATE INDEX idx_events_starts ON events(starts_at, status);
CREATE INDEX idx_events_live ON events(status, starts_at) WHERE status = 'LIVE';
CREATE INDEX idx_events_upcoming ON events(status, starts_at) WHERE status = 'SCHEDULED' AND starts_at > NOW();

CREATE TABLE IF NOT EXISTS event_attendees (
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rsvp_status VARCHAR(20) NOT NULL DEFAULT 'GOING', -- GOING, MAYBE, NOT_GOING, WAITLIST
    rsvp_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    checked_in_at TIMESTAMP,
    -- Role
    role VARCHAR(20) NOT NULL DEFAULT 'ATTENDEE', -- ORGANIZER, SPEAKER, ATTENDEE, VOLUNTEER
    notes TEXT,
    PRIMARY KEY (event_id, user_id),
    CONSTRAINT event_rsvp_check CHECK (rsvp_status IN ('GOING','MAYBE','NOT_GOING','WAITLIST'))
);
CREATE INDEX idx_event_attendees_user ON event_attendees(user_id, rsvp_at DESC);
CREATE INDEX idx_event_attendees_status ON event_attendees(rsvp_status);

-- ━━━━ Stories Enhancements (Highlights, Mentions) ━━━━
CREATE TABLE IF NOT EXISTS story_highlights (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(100) NOT NULL,
    cover_media_key VARCHAR(200) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    -- Privacy
    visibility VARCHAR(20) NOT NULL DEFAULT 'EVERYONE', -- EVERYONE, CONTACTS, NOBODY
    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT highlights_visibility_check CHECK (visibility IN ('EVERYONE','CONTACTS','NOBODY'))
);
CREATE INDEX idx_highlights_user ON story_highlights(user_id, display_order);

CREATE TABLE IF NOT EXISTS story_highlight_items (
    id UUID PRIMARY KEY,
    highlight_id UUID NOT NULL REFERENCES story_highlights(id) ON DELETE CASCADE,
    story_id VARCHAR(40) NOT NULL, -- MongoDB StoryDocument ID
    display_order INTEGER NOT NULL DEFAULT 0,
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(highlight_id, story_id)
);
CREATE INDEX idx_highlight_items_highlight ON story_highlight_items(highlight_id, display_order);

-- ━━━━ Post Reactions (مشاعر المنشورات) ━━━━
CREATE TABLE IF NOT EXISTS post_reactions (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL, -- refers to posts in social module
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reaction_type VARCHAR(20) NOT NULL, -- LIKE, LOVE, LAUGH, WOW, SAD, ANGRY, FIRE, CLAP
    reacted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(post_id, user_id),
    CONSTRAINT post_reactions_type_check CHECK (reaction_type IN (
        'LIKE','LOVE','LAUGH','WOW','SAD','ANGRY','FIRE','CLAP','THINKING','PRAY'
    ))
);
CREATE INDEX idx_post_reactions_post ON post_reactions(post_id, reaction_type);
CREATE INDEX idx_post_reactions_user ON post_reactions(user_id, reacted_at DESC);

-- ━━━━ Hashtags & Trends ━━━━
CREATE TABLE IF NOT EXISTS hashtags (
    id UUID PRIMARY KEY,
    tag_name VARCHAR(100) UNIQUE NOT NULL, -- without the # symbol
    description TEXT,
    category VARCHAR(50), -- TECHNOLOGY, SPORTS, POLITICS, ENTERTAINMENT, etc.
    -- Stats
    usage_count INTEGER NOT NULL DEFAULT 0,
    posts_count INTEGER NOT NULL DEFAULT 0,
    stories_count INTEGER NOT NULL DEFAULT 0,
    unique_users INTEGER NOT NULL DEFAULT 0,
    -- Trending
    trending_score REAL NOT NULL DEFAULT 0,
    is_trending BOOLEAN NOT NULL DEFAULT FALSE,
    trending_since TIMESTAMP,
    -- Moderation
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    blocked_reason TEXT,
    blocked_by UUID REFERENCES users(id),
    -- Locale
    language VARCHAR(10) DEFAULT 'ar',
    -- Timestamps
    first_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_hashtags_trending ON hashtags(trending_score DESC, is_trending) WHERE is_trending = TRUE;
CREATE INDEX idx_hashtags_usage ON hashtags(usage_count DESC);
CREATE INDEX idx_hashtags_name ON hashtags(tag_name);

CREATE TABLE IF NOT EXISTS hashtag_follows (
    hashtag_id UUID NOT NULL REFERENCES hashtags(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    followed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (hashtag_id, user_id)
);
CREATE INDEX idx_hashtag_follows_user ON hashtag_follows(user_id, followed_at DESC);

-- ━━━━ Saved Messages / Bookmarks ━━━━
CREATE TABLE IF NOT EXISTS saved_messages (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message_id UUID NOT NULL, -- references messages
    saved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Categorization
    collection VARCHAR(50) NOT NULL DEFAULT 'DEFAULT', -- DEFAULT, IMPORTANT, WORK, PERSONAL
    notes TEXT,
    UNIQUE(user_id, message_id)
);
CREATE INDEX idx_saved_messages_user ON saved_messages(user_id, saved_at DESC);
CREATE INDEX idx_saved_messages_collection ON saved_messages(user_id, collection, saved_at DESC);

-- ━━━━ Stickers & Reactions Library ━━━━
CREATE TABLE IF NOT EXISTS sticker_packs (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    creator_id UUID REFERENCES users(id) ON DELETE SET NULL, -- NULL for official packs
    is_official BOOLEAN NOT NULL DEFAULT FALSE,
    -- Display
    cover_media_key VARCHAR(200) NOT NULL,
    preview_media_key VARCHAR(200),
    -- Metadata
    sticker_count INTEGER NOT NULL DEFAULT 0,
    total_downloads INTEGER NOT NULL DEFAULT 0,
    -- Pricing
    is_free BOOLEAN NOT NULL DEFAULT TRUE,
    price_cents INTEGER NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    -- Visibility
    is_published BOOLEAN NOT NULL DEFAULT FALSE,
    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_sticker_packs_published ON sticker_packs(is_published, is_official, created_at DESC);

CREATE TABLE IF NOT EXISTS stickers (
    id UUID PRIMARY KEY,
    pack_id UUID NOT NULL REFERENCES sticker_packs(id) ON DELETE CASCADE,
    name VARCHAR(100),
    media_key VARCHAR(200) NOT NULL,
    emoji_tags TEXT[] DEFAULT '{}', -- for search
    display_order INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_stickers_pack ON stickers(pack_id, display_order);

CREATE TABLE IF NOT EXISTS user_sticker_packs (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    pack_id UUID NOT NULL REFERENCES sticker_packs(id) ON DELETE CASCADE,
    installed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_favorite BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_id, pack_id)
);
CREATE INDEX idx_user_sticker_packs_user ON user_sticker_packs(user_id, is_favorite);

-- ━━━━ View for Trending Hashtags ━━━━
CREATE OR REPLACE VIEW v_trending_hashtags AS
SELECT
    id, tag_name, usage_count, posts_count, trending_score,
    is_trending, last_used_at
FROM hashtags
WHERE is_blocked = FALSE
ORDER BY trending_score DESC, usage_count DESC
LIMIT 100;

-- ━━━━ Comments ━━━━
COMMENT ON TABLE polls IS 'استطلاعات متقدمة مع أنواع مختلفة';
COMMENT ON TABLE events IS 'أحداث المجموعات والمستخدمين';
COMMENT ON TABLE story_highlights IS 'إبراز القصص الدائم';
COMMENT ON TABLE post_reactions IS 'مشاعر المنشورات';
COMMENT ON TABLE hashtags IS 'هاشتاجات وترندات';
COMMENT ON TABLE saved_messages IS 'رسائل محفوظة (Bookmark)';
COMMENT ON TABLE sticker_packs IS 'حزم الملصقات';
