-- The report export folder is a filesystem path local to whichever machine
-- runs the export. Storing it as a shared system_settings row meant one
-- client's chosen path silently overrode every other client's export
-- location on the LAN. It now lives in each machine's own external
-- application.properties (see DatabaseConfig.setProperty /
-- ReportExportLocationResolver), so remove it from the shared table.
DELETE FROM system_settings WHERE setting_key = 'reports.export.folder';
