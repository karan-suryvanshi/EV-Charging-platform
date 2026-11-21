-- Reviewed additive migration for admin-controlled booking enablement.
-- Apply only after a verified backup and compatibility review.
-- Existing station rows receive booking_enabled = 1 through the column default;
-- no station records are updated, copied, or re-imported.

ALTER TABLE station
    ADD COLUMN booking_enabled BIT(1) NOT NULL DEFAULT b'1';

ALTER TABLE public_charging_stations
    ADD COLUMN booking_enabled BIT(1) NOT NULL DEFAULT b'1';

-- Existing managed bookings remain linked through station_id. Public bookings
-- use public_station_id, so identical numeric IDs in the two tables never alias.
ALTER TABLE bookings
    MODIFY COLUMN station_id BIGINT NULL,
    ADD COLUMN public_station_id BIGINT NULL,
    MODIFY COLUMN status ENUM('REQUESTED', 'CONFIRMED', 'CANCELLED') NOT NULL,
    ADD INDEX ix_booking_managed_slot (station_id, booking_date, status, start_time, end_time),
    ADD INDEX ix_booking_public_slot (public_station_id, booking_date, status, start_time, end_time),
    ADD CONSTRAINT fk_booking_public_station
        FOREIGN KEY (public_station_id) REFERENCES public_charging_stations (id),
    ADD CONSTRAINT ck_booking_single_station_source
        CHECK ((station_id IS NULL) <> (public_station_id IS NULL));
