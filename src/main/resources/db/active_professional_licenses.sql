CREATE TABLE IF NOT EXISTS active_professional_licenses (
    player_uuid CHAR(36)    NOT NULL,
    license_id  VARCHAR(64) NOT NULL,

    PRIMARY KEY (player_uuid, license_id),
    FOREIGN KEY (player_uuid, license_id) REFERENCES player_licenses (player_uuid, license_id)
) ENGINE=InnoDB;