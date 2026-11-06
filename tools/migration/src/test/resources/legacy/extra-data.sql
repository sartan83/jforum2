-- Extra rows on top of sqlserver_data_dump.sql: users, roles with values, PMs, polls, attachments, etc.
SET IDENTITY_INSERT jforum_groups ON;
INSERT INTO jforum_groups (group_id, group_name, group_description, parent_id) VALUES (3, 'Moderators', 'Forum moderators', 1);
SET IDENTITY_INSERT jforum_groups OFF;

SET IDENTITY_INSERT jforum_users ON;
INSERT INTO jforum_users (user_id, user_active, username, user_password, user_regdate, user_lastvisit, user_email, user_posts,
  rank_id, user_karma, user_viewemail, user_allowhtml, user_notify_always, gender, user_sig, user_from, user_actkey, user_avatar_type, deleted)
  VALUES (3, 1, 'alice', '6384e2b2184bcbf58eccf10ca7a6563c', '2024-01-15 10:30:00', '2024-02-01 08:00:00', 'alice@example.com', 2,
  2, 4.50, 1, 1, 1, 'F', 'Alice [b]sig[/b]', 'Milano', '', 1, 0);
INSERT INTO jforum_users (user_id, user_active, username, user_password, user_regdate, user_email, user_actkey, user_allow_pm)
  VALUES (4, 0, 'bob', '9f9d51bc70ef21ca5c14f307980a29d8', '2024-01-16 09:00:00', 'bob@example.com', 'k3y', 0);
INSERT INTO jforum_users (user_id, username, user_password, user_regdate, user_email)
  VALUES (7, 'carol', '81dc9bdb52d04dc20036dbd8313ed055', '2024-01-17 09:00:00', 'carol@example.com');
SET IDENTITY_INSERT jforum_users OFF;
INSERT INTO jforum_user_groups (group_id, user_id) VALUES (1, 3);
INSERT INTO jforum_user_groups (group_id, user_id) VALUES (3, 3);
INSERT INTO jforum_user_groups (group_id, user_id) VALUES (3, 3);
INSERT INTO jforum_user_groups (group_id, user_id) VALUES (1, 4);
INSERT INTO jforum_user_groups (group_id, user_id) VALUES (1, 7);

DECLARE @role bigint;
INSERT INTO jforum_roles (group_id, name) VALUES (3, 'perm_moderation_forums');
SET @role = SCOPE_IDENTITY();
INSERT INTO jforum_role_values (role_id, role_value) VALUES (@role, '1');
INSERT INTO jforum_role_values (role_id, role_value) VALUES (@role, '2');
INSERT INTO jforum_roles (group_id, name) VALUES (3, 'perm_moderation');
-- user-level role: not part of any group, ignored by the group model
INSERT INTO jforum_roles (group_id, user_id, name) VALUES (0, 3, 'perm_vote');

SET IDENTITY_INSERT jforum_ranks ON;
INSERT INTO jforum_ranks (rank_id, rank_title, rank_min, rank_special, rank_image) VALUES (1, 'Newbie', 0, 0, NULL);
INSERT INTO jforum_ranks (rank_id, rank_title, rank_min, rank_special, rank_image) VALUES (2, 'Moderator', 0, 1, 'mod.gif');
SET IDENTITY_INSERT jforum_ranks OFF;

SET IDENTITY_INSERT jforum_categories ON;
INSERT INTO jforum_categories (categories_id, title, display_order, moderated) VALUES (5, 'Second category', 2, 1);
SET IDENTITY_INSERT jforum_categories OFF;
SET IDENTITY_INSERT jforum_forums ON;
INSERT INTO jforum_forums (forum_id, categories_id, forum_name, forum_desc, forum_order, forum_topics, forum_last_post_id, moderated)
  VALUES (8, 5, 'Polls forum', 'Forum with a poll', 1, 1, 12, 0);
SET IDENTITY_INSERT jforum_forums OFF;

SET IDENTITY_INSERT jforum_topics ON;
INSERT INTO jforum_topics (topic_id, forum_id, topic_title, user_id, topic_time, topic_views, topic_replies, topic_status, topic_vote_id,
  topic_type, topic_first_post_id, topic_last_post_id, moderated)
  VALUES (10, 8, 'What do you prefer?', 3, '2024-03-01 12:00:00', 42, 1, 1, 1, 2, 11, 12, 0);
SET IDENTITY_INSERT jforum_topics OFF;

SET IDENTITY_INSERT jforum_posts ON;
INSERT INTO jforum_posts (post_id, topic_id, forum_id, user_id, post_time, poster_ip, enable_bbcode, enable_html, enable_smilies, enable_sig,
  post_edit_time, post_edit_count, status, attach, need_moderate)
  VALUES (11, 10, 8, 3, '2024-03-01 12:00:00', '10.0.0.1', 1, 0, 1, 1, NULL, 0, 1, 1, 0);
INSERT INTO jforum_posts (post_id, topic_id, forum_id, user_id, post_time, poster_ip, enable_bbcode, enable_html, enable_smilies, enable_sig,
  post_edit_time, post_edit_count, status, attach, need_moderate)
  VALUES (12, 10, 8, 4, '2024-03-02 18:45:10', '10.0.0.2', 0, 1, 0, 0, '2024-03-02 19:00:00', 2, 1, 0, 1);
SET IDENTITY_INSERT jforum_posts OFF;
INSERT INTO jforum_posts_text (post_id, post_text, post_subject) VALUES (11, 'Vote [b]now[/b]!', 'What do you prefer?');
INSERT INTO jforum_posts_text (post_id, post_text, post_subject) VALUES (12, 'I voted <i>Java</i>.', 'Re: What do you prefer?');

SET IDENTITY_INSERT jforum_vote_desc ON;
INSERT INTO jforum_vote_desc (vote_id, topic_id, vote_text, vote_start, vote_length) VALUES (4, 10, 'Favourite language?', 1709294400, 7);
SET IDENTITY_INSERT jforum_vote_desc OFF;
INSERT INTO jforum_vote_results (vote_id, vote_option_id, vote_option_text, vote_result) VALUES (4, 1, 'Java', 2);
INSERT INTO jforum_vote_results (vote_id, vote_option_id, vote_option_text, vote_result) VALUES (4, 2, 'Kotlin', 0);
INSERT INTO jforum_vote_results (vote_id, vote_option_id, vote_option_text, vote_result) VALUES (4, 3, 'Scala', 1);
INSERT INTO jforum_vote_voters (vote_id, vote_user_id, vote_user_ip) VALUES (4, 3, '10.0.0.1');
INSERT INTO jforum_vote_voters (vote_id, vote_user_id, vote_user_ip) VALUES (4, 4, '10.0.0.2');

SET IDENTITY_INSERT jforum_privmsgs ON;
INSERT INTO jforum_privmsgs (privmsgs_id, privmsgs_type, privmsgs_subject, privmsgs_from_userid, privmsgs_to_userid, privmsgs_date,
  privmsgs_ip, privmsgs_enable_bbcode, privmsgs_enable_html, privmsgs_enable_smilies, privmsgs_attach_sig)
  VALUES (20, 1, 'Hello Bob', 3, 4, '2024-03-05 09:15:00', '10.0.0.1', 1, 0, 1, 0);
INSERT INTO jforum_privmsgs (privmsgs_id, privmsgs_type, privmsgs_subject, privmsgs_from_userid, privmsgs_to_userid, privmsgs_date,
  privmsgs_ip, privmsgs_enable_bbcode, privmsgs_enable_html, privmsgs_enable_smilies, privmsgs_attach_sig)
  VALUES (21, 2, 'Hello Bob', 3, 4, '2024-03-05 09:15:00', '10.0.0.1', 1, 0, 1, 0);
SET IDENTITY_INSERT jforum_privmsgs OFF;
INSERT INTO jforum_privmsgs_text (privmsgs_id, privmsgs_text) VALUES (20, 'See the attached [i]file[/i].');
INSERT INTO jforum_privmsgs_text (privmsgs_id, privmsgs_text) VALUES (21, 'See the attached [i]file[/i].');

SET IDENTITY_INSERT jforum_extension_groups ON;
INSERT INTO jforum_extension_groups (extension_group_id, name, allow, upload_icon, download_mode) VALUES (1, 'Images', 1, 'image.gif', 1);
INSERT INTO jforum_extension_groups (extension_group_id, name, allow, upload_icon, download_mode) VALUES (2, 'Executables', 0, NULL, 2);
SET IDENTITY_INSERT jforum_extension_groups OFF;
SET IDENTITY_INSERT jforum_extensions ON;
INSERT INTO jforum_extensions (extension_id, extension_group_id, description, upload_icon, extension, allow) VALUES (1, 1, 'PNG image', NULL, 'png', 1);
INSERT INTO jforum_extensions (extension_id, extension_group_id, description, upload_icon, extension, allow) VALUES (2, 2, 'Windows exe', NULL, 'exe', 0);
SET IDENTITY_INSERT jforum_extensions OFF;

SET IDENTITY_INSERT jforum_attach ON;
INSERT INTO jforum_attach (attach_id, post_id, privmsgs_id, user_id) VALUES (30, 11, NULL, 3);
INSERT INTO jforum_attach (attach_id, post_id, privmsgs_id, user_id) VALUES (31, NULL, 20, 3);
INSERT INTO jforum_attach (attach_id, post_id, privmsgs_id, user_id) VALUES (32, 11, NULL, 3);
SET IDENTITY_INSERT jforum_attach OFF;
INSERT INTO jforum_attach_desc (attach_id, physical_filename, real_filename, download_count, [description ], mimetype, filesize, upload_time, thumb, extension_id)
  VALUES (30, '2024/3/1_abc123.png', 'diagram.png', 5, 'Architecture diagram', 'image/png', 12, '2024-03-01 12:00:00', 1, 1);
INSERT INTO jforum_attach_desc (attach_id, physical_filename, real_filename, download_count, [description ], mimetype, filesize, upload_time, thumb, extension_id)
  VALUES (31, '2024/3/5_def456.txt', 'notes.txt', 0, NULL, 'text/plain', 11, '2024-03-05 09:15:00', 0, NULL);
INSERT INTO jforum_attach_desc (attach_id, physical_filename, real_filename, download_count, [description ], mimetype, filesize, upload_time, thumb, extension_id)
  VALUES (32, '2024/3/1_missing.png', 'lost.png', 0, 'File no longer on disk', 'image/png', 99, '2024-03-01 12:01:00', 0, 1);

SET IDENTITY_INSERT jforum_quota_limit ON;
INSERT INTO jforum_quota_limit (quota_limit_id, quota_desc, quota_limit, quota_type) VALUES (1, 'Small', 1, 2);
SET IDENTITY_INSERT jforum_quota_limit OFF;
INSERT INTO jforum_attach_quota (group_id, quota_limit_id) VALUES (1, 1);
INSERT INTO jforum_attach_quota (group_id, quota_limit_id) VALUES (3, 1);

SET IDENTITY_INSERT jforum_karma ON;
INSERT INTO jforum_karma (karma_id, post_id, topic_id, post_user_id, from_user_id, points, rate_date) VALUES (1, 11, 10, 3, 4, 5, '2024-03-02 10:00:00');
INSERT INTO jforum_karma (karma_id, post_id, topic_id, post_user_id, from_user_id, points, rate_date) VALUES (2, 11, 10, 3, 2, 2, '2024-03-02 11:00:00');
SET IDENTITY_INSERT jforum_karma OFF;

SET IDENTITY_INSERT jforum_bookmarks ON;
INSERT INTO jforum_bookmarks (bookmark_id, user_id, relation_id, relation_type, public_visible, title, description) VALUES (1, 3, 10, 2, 1, 'Poll topic', 'Remember to vote');
INSERT INTO jforum_bookmarks (bookmark_id, user_id, relation_id, relation_type, public_visible, title, description) VALUES (2, 3, 4, 1, 0, 'Bob', NULL);
SET IDENTITY_INSERT jforum_bookmarks OFF;

INSERT INTO jforum_topics_watch (topic_id, user_id, is_read) VALUES (10, 3, 1);
INSERT INTO jforum_topics_watch (topic_id, user_id, is_read) VALUES (10, 4, 0);
SET IDENTITY_INSERT jforum_forums_watch ON;
INSERT INTO jforum_forums_watch (forum_id, user_id) VALUES (8, 3);
SET IDENTITY_INSERT jforum_forums_watch OFF;

SET IDENTITY_INSERT jforum_banlist ON;
INSERT INTO jforum_banlist (banlist_id, user_id, banlist_ip, banlist_email) VALUES (1, NULL, '192.168.1.1', NULL);
INSERT INTO jforum_banlist (banlist_id, user_id, banlist_ip, banlist_email) VALUES (2, 4, NULL, ' ');
SET IDENTITY_INSERT jforum_banlist OFF;

INSERT INTO jforum_config (config_name, config_value) VALUES ('most.users.ever.online', '12');
INSERT INTO jforum_config (config_name, config_value) VALUES ('most.users.ever.online.date', '1709294400000');

SET IDENTITY_INSERT jforum_moderation_log ON;
INSERT INTO jforum_moderation_log (log_id, user_id, log_description, log_original_message, log_date, log_type, post_id, topic_id, post_user_id)
  VALUES (1, 2, 'Edited for language', 'I voted <b>Java</b>!!!', '2024-03-02 19:00:00', 1, 12, 10, 4);
SET IDENTITY_INSERT jforum_moderation_log OFF;

SET IDENTITY_INSERT jforum_banner ON;
INSERT INTO jforum_banner (banner_id, banner_name, banner_placement, banner_description, banner_clicks, banner_views, banner_url, banner_weight,
  banner_active, banner_comment, banner_type, banner_width, banner_height)
  VALUES (1, 'Sponsor', 2, 'Top banner', 3, 100, 'https://example.com', 60, 1, 'Q1 campaign', 1, 468, 60);
SET IDENTITY_INSERT jforum_banner OFF;
SET IDENTITY_INSERT jforum_api ON;
INSERT INTO jforum_api (api_id, api_key, api_validity) VALUES (1, '0123456789abcdef0123456789abcdef', '2030-12-31 23:59:59');
SET IDENTITY_INSERT jforum_api OFF;

INSERT INTO jforum_sessions (session_id, session_user_id, session_start, session_time, session_ip, session_page, session_logged_int)
  VALUES ('5F2C1A9B', 3, '2024-03-05 09:00:00', 900000, '0a000001', 0, 1);
