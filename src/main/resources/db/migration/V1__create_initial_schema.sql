CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255),
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255),
    role VARCHAR(50),
    PRIMARY KEY (id),
    UNIQUE (email)
);

CREATE TABLE ticket (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(255),
    description TEXT,
    priority VARCHAR(50),
    status VARCHAR(50),
    created_by BIGINT,
    assigned_to BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT fk_ticket_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_ticket_assigned_to FOREIGN KEY (assigned_to) REFERENCES users(id)
);

CREATE TABLE comment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    message VARCHAR(255),
    ticket_id BIGINT,
    user_id BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT fk_comment_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id),
    CONSTRAINT fk_comment_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_ticket_created_by ON ticket(created_by);
CREATE INDEX idx_ticket_assigned_to ON ticket(assigned_to);
CREATE INDEX idx_ticket_status ON ticket(status);
CREATE INDEX idx_ticket_priority ON ticket(priority);
CREATE INDEX idx_comment_ticket_id ON comment(ticket_id);
