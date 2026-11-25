CREATE TABLE IF NOT EXISTS player_licenses (
    player_uuid CHAR(36)    NOT NULL,
    license_id  VARCHAR(64) NOT NULL,

    PRIMARY KEY (player_uuid, license_id)
) ENGINE=InnoDB;