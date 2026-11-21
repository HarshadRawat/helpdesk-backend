ALTER TABLE ticket
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER assigned_to,
    ADD COLUMN sla_due_at TIMESTAMP NULL AFTER version,
    ADD COLUMN sla_breached BOOLEAN NOT NULL DEFAULT FALSE AFTER sla_due_at;

CREATE INDEX idx_ticket_sla ON ticket(sla_breached, sla_due_at, status);
