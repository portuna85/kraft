-- Production-like MariaDB 11.7 plans (50k reports, 100k operation logs) showed that
-- target cleanup scanned the reporter-leading unique index, and a filtered log page
-- scanned/sorted every row sharing type/status. Preserve the existing filter prefix
-- while adding the range/order columns, and add the missing target-leading report path.
CREATE INDEX idx_community_reports_target
    ON community_reports (target_type, target_id);

DROP INDEX idx_operation_logs_type_status ON winning_number_operation_logs;
CREATE INDEX idx_operation_logs_type_status_created
    ON winning_number_operation_logs (operation_type, execution_status, created_at DESC, id DESC);
