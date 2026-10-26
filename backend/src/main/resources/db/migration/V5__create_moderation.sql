ALTER TABLE listings ADD COLUMN moderation_reason VARCHAR(500);

CREATE TABLE content_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id UUID NOT NULL,
    target_type VARCHAR(8) NOT NULL,
    target_listing_id UUID,
    target_user_id UUID,
    reason VARCHAR(16) NOT NULL,
    details VARCHAR(1000),
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    moderator_id UUID,
    resolution_note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMPTZ,
    CONSTRAINT reports_reporter_fk FOREIGN KEY (reporter_id) REFERENCES users (id),
    CONSTRAINT reports_listing_fk FOREIGN KEY (target_listing_id) REFERENCES listings (id),
    CONSTRAINT reports_user_fk FOREIGN KEY (target_user_id) REFERENCES users (id),
    CONSTRAINT reports_moderator_fk FOREIGN KEY (moderator_id) REFERENCES users (id),
    CONSTRAINT reports_target_matches_type CHECK (
        (target_type = 'LISTING' AND target_listing_id IS NOT NULL AND target_user_id IS NULL)
        OR (target_type = 'USER' AND target_user_id IS NOT NULL AND target_listing_id IS NULL)
    ),
    CONSTRAINT reports_reason_allowed CHECK (reason IN ('SCAM', 'DANGEROUS', 'INAPPROPRIATE', 'SPAM', 'OTHER')),
    CONSTRAINT reports_status_allowed CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED')),
    CONSTRAINT reports_resolution_complete CHECK (
        (status = 'OPEN' AND moderator_id IS NULL AND resolved_at IS NULL)
        OR (status <> 'OPEN' AND moderator_id IS NOT NULL AND resolved_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_reports_open_listing ON content_reports (reporter_id, target_listing_id)
    WHERE status = 'OPEN' AND target_listing_id IS NOT NULL;
CREATE UNIQUE INDEX uq_reports_open_user ON content_reports (reporter_id, target_user_id)
    WHERE status = 'OPEN' AND target_user_id IS NOT NULL;
CREATE INDEX idx_reports_status_created ON content_reports (status, created_at);
CREATE INDEX idx_reports_target_listing ON content_reports (target_listing_id) WHERE target_listing_id IS NOT NULL;
CREATE INDEX idx_reports_target_user ON content_reports (target_user_id) WHERE target_user_id IS NOT NULL;

CREATE TABLE moderation_actions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    moderator_id UUID NOT NULL,
    action VARCHAR(24) NOT NULL,
    target_listing_id UUID,
    target_user_id UUID,
    report_id UUID,
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT actions_moderator_fk FOREIGN KEY (moderator_id) REFERENCES users (id),
    CONSTRAINT actions_listing_fk FOREIGN KEY (target_listing_id) REFERENCES listings (id),
    CONSTRAINT actions_user_fk FOREIGN KEY (target_user_id) REFERENCES users (id),
    CONSTRAINT actions_report_fk FOREIGN KEY (report_id) REFERENCES content_reports (id),
    CONSTRAINT actions_action_allowed CHECK (action IN (
        'HIDE_LISTING', 'RESTORE_LISTING', 'SUSPEND_USER', 'REINSTATE_USER', 'RESOLVE_REPORT', 'DISMISS_REPORT'
    ))
);

CREATE INDEX idx_actions_listing ON moderation_actions (target_listing_id, created_at) WHERE target_listing_id IS NOT NULL;
CREATE INDEX idx_actions_user ON moderation_actions (target_user_id, created_at) WHERE target_user_id IS NOT NULL;
CREATE INDEX idx_actions_report ON moderation_actions (report_id) WHERE report_id IS NOT NULL;
