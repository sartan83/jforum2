-- Tables of JForum 2.1.8 that the SQL Server script does not create (present in the MySQL schema / upgrades)
CREATE TABLE jforum_banner (
	banner_id INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
	banner_name VARCHAR(90),
	banner_placement INT NOT NULL DEFAULT 0,
	banner_description VARCHAR(250),
	banner_clicks INT NOT NULL DEFAULT 0,
	banner_views INT NOT NULL DEFAULT 0,
	banner_url VARCHAR(250),
	banner_weight TINYINT NOT NULL DEFAULT 50,
	banner_active TINYINT NOT NULL DEFAULT 0,
	banner_comment VARCHAR(250),
	banner_type INT NOT NULL DEFAULT 0,
	banner_width INT NOT NULL DEFAULT 0,
	banner_height INT NOT NULL DEFAULT 0
);
CREATE TABLE jforum_api (
	api_id INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
	api_key VARCHAR(32) NOT NULL,
	api_validity DATETIME NOT NULL
);
