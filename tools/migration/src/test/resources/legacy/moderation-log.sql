-- jforum_moderation_log as it should be in sqlserver_db_struct.sql (the shipped DDL is malformed:
-- "CREATE TABLE CREATE TABLE", BLOB/DATE types). Used by the tests in place of the broken batch.
CREATE TABLE [dbo].[jforum_moderation_log] (
	[log_id] [bigint] IDENTITY(1,1) NOT NULL PRIMARY KEY,
	[user_id] [bigint] NOT NULL,
	[log_description] [text] NOT NULL,
	[log_original_message] [text] NULL,
	[log_date] [datetime] NOT NULL,
	[log_type] [tinyint] NULL DEFAULT ((0)),
	[post_id] [bigint] NULL,
	[topic_id] [bigint] NULL,
	[post_user_id] [bigint] NULL
)
