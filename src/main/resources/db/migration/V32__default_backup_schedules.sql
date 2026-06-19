INSERT INTO backup_schedules (schedule_name, backup_time, enabled, created_at)
SELECT seed.schedule_name, seed.backup_time, TRUE, CURRENT_TIMESTAMP
FROM (
    SELECT 'Daily 9.00 AM' AS schedule_name, TIME('09:00:00') AS backup_time
    UNION ALL SELECT 'Daily 12.00 PM', TIME('12:00:00')
    UNION ALL SELECT 'Daily 4.00 PM', TIME('16:00:00')
) seed
WHERE NOT EXISTS (
    SELECT 1
    FROM backup_schedules existing
    WHERE existing.backup_time = seed.backup_time
);
