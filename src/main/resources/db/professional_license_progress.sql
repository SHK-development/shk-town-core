CREATE TABLE IF NOT EXISTS professional_license_progress (
    player_uuid CHAR(36)    NOT NULL,
    license_id  VARCHAR(64) NOT NULL,
    grade_code  VARCHAR(16) NOT NULL,
    experience INT NOT NULL,

    PRIMARY KEY (player_uuid, license_id),
    CHECK (grade_code IN ('grade_3', 'pre_grade_2', 'grade_2', 'pre_grade_1', 'grade_1')),
    CHECK (experience >= 0)
) ENGINE=InnoDB;