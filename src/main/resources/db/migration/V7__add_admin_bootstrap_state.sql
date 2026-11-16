CREATE TABLE account_bootstrap_state (
    id SMALLINT PRIMARY KEY,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_account_bootstrap_state_singleton CHECK (id = 1),
    CONSTRAINT chk_account_bootstrap_state_completion CHECK (
        (completed = TRUE AND completed_at IS NOT NULL)
        OR (completed = FALSE AND completed_at IS NULL)
    )
);

INSERT INTO account_bootstrap_state (id, completed) VALUES (1, FALSE);
