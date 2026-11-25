CREATE TABLE IF NOT EXISTS professional_license_slots (
    player_uuid CHAR(36)    NOT NULL,
    extra_slots INT         NOT NULL DEFAULT 0,

    PRIMARY KEY (player_uuid),
    CHECK (extra_slots >= 0)
) ENGINE=InnoDB;