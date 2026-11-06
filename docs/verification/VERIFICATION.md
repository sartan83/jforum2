# Verification report

Run of the modernized JForum (Java 21, Spring Boot 3.5.16, Spring Data MongoDB, MongoDB 8.0) started with
`docker compose` and the `demo` seed profile. The evidence is in this directory:

- `verify.sh` – the curl script that performed the HTTP checks below (re-runnable: `docs/verification/verify.sh [base-url]`)
- `output/results.txt` – its PASS/FAIL log; `output/NN-*.html` – the raw response of every check
- `screenshots.py` + `screenshots/*.png` – headless Chrome screenshots of the main pages

## Environment

| Item | Value |
| --- | --- |
| Host | Linux 6.8 (x86_64), Docker 29.9.2, Compose 5.6.1 |
| Database | `mongo:8.0` container, server version 8.0.34, volume `mongo-data` |
| Application | image built by the multi-stage `Dockerfile` (Maven + Temurin 21 → `eclipse-temurin:21-jre`), OpenJDK 21.0.13, MongoDB Java driver 5.5.2, Tomcat 10.1.55 |
| Start command | `MAVEN_MIRROR_URL=https://maven-central-eu.storage-download.googleapis.com/maven2 SPRING_PROFILES_ACTIVE=demo docker compose up --build -d` |
| Connection | `SPRING_DATA_MONGODB_URI=mongodb://mongo:27017/jforum` (compose default) |

## Seed data (`demo` profile)

On an empty database the installation data (groups *General*/*Administration*, users *Anonymous* and *Admin*/`admin`,
smilies, roles, *Test Forum* with its welcome topic) is created first, then `DemoData` adds users `mario`, `giulia`,
`luca` (password `demo`), the categories *Java & Spring* and *Community*, the forums *Spring Boot*, *MongoDB*,
*Off-topic*, and 5 topics with replies (BBCode and smilies included).

After the run (seed + what `verify.sh` posted): 3 categories, 4 forums, 7 topics, 13 posts, 5 users, 2 private
messages (sent copy + inbox copy). Forum/topic/user counters match the documents (`forumTopics`, `topicReplies`,
`userPosts` checked against `countDocuments` per forum, topic and user).

Indexes created at startup, e.g. `posts`: `topicId`, `forumId`, `userId`, `postTime`, `needModerate`, text index
`posts_text_search`; `topics`: `forumId`, `forum_listing`, `topicLastPostId`; `users`: `usernameLower` (unique),
`userEmail`, `groupIds`.

## HTTP checks (`verify.sh`) – 43/43 PASS

| # | Check | Request | Result |
| --- | --- | --- | --- |
| 01 | Health (includes MongoDB ping) | `GET /actuator/health` | 200, `"UP"` |
| 02 | Home | `GET /` | 302 → `/forums/list.page` |
| 03 | Forum list | `GET /forums/list.page` | 200, categories and forums |
| 04 | Forum page | `GET /forums/show/2.page` | 200, topic list |
| 05 | Topic page | `GET /posts/list/2.page` | 200, all posts |
| 06 | BBCode rendering | same | `[i]…[/i]` rendered as `<i>all of them</i>` |
| 07 | Recent topics | `GET /recentTopics/list.page` | 200 |
| 08 | Member list | `GET /user/list.page` | 200 |
| 09 | User profile | `GET /user/profile/3.page` | 200 |
| 10 | Search (MongoDB text index) | `module=search&action=search&search_keywords=Atlas` | 200, matching topic found |
| 11 | RSS | `GET /rss/forumTopics/2.page` | 200, `<rss` |
| 12 | Static assets | `GET /templates/default/styles/style.css` | 200 |
| 13 | Template sources not exposed | `GET /templates/default/forum_list.htm` | 404 |
| 14 | Login form | `GET /user/login.page` | 200 |
| 15 | Registration | `GET /user/insert.page` | 200, agreement terms (first step, as in 2.1.8) |
| 16 | Anonymous cannot post | `GET /posts/insert/2.page` | 200, login required |
| 17 | Wrong password | `validateLogin` mario/wrong | 200, "Invalid" message |
| 18 | Login | `validateLogin` mario/demo | header shows `Logout [mario]` |
| 19 | New topic | `posts insertSave` in forum 4 | 302 to the new topic |
| 20 | New topic listed | `GET /forums/show/4.page` | 200, topic present |
| 21 | New topic content | `GET /posts/list/<id>.page` | `[b]verify.sh[/b]` → `<b>verify.sh</b>` |
| 22 | Reply (after the 15 s flood-control delay) | `posts insertSave` with `topic_id` | 302 |
| 23 | Reply shown | `GET /posts/list/<id>.page` | 200, reply present |
| 24 | Send PM mario → giulia | `pm sendSave` | 200 |
| 25 | Sent box | `GET /pm/sentbox.page` | PM present |
| 26 | Recipient inbox (login as giulia) | `GET /pm/inbox.page` | PM present |
| 27 | Non-admin user on admin panel | `module=admBase&action=main` as mario | 302 to the admin login (legacy behaviour) |
| 28-30 | Admin login, frameset, menu, main page | `/admBase/login.page`, `admBase&action=menu/main` | 200 |
| 31-43 | Admin panels | `adminConfig`, `adminCategories`, `adminForums`, `adminGroups`, group permissions, `adminUsers`, `adminSmilies`, `adminRankings`, `adminBanning`, `adminAttachments`, moderation log, `adminSearchStats`, `adminCache` | 200, expected content, no JForum error page |

## Other checks

| Check | Result |
| --- | --- |
| Persistence across restart | `docker compose down` (volumes kept) + `up`: the topic created by `verify.sh` is still listed; the `demo` seed is not applied twice (7 topics, 13 posts) |
| mongo-express | `docker compose --profile tools up -d mongo-express` → `http://localhost:8081` answers 200 |
| Templates unchanged | `git diff master -- templates images` is empty |
| Unit/integration tests | `mvn verify` (root): 132 tests, 0 failures, 0 errors (Testcontainers `mongo:8.0`) |
| Migration tool | `mvn verify` in `tools/migration`: 12 tests, 0 failures (Testcontainers SQL Server 2022 + `mongo:8.0`) |

## Screenshots

| File | Page |
| --- | --- |
| `01-forum-list.png` | forum list (anonymous) |
| `02-forum-show.png` | forum *Spring Boot* |
| `03-topic.png` | topic with BBCode and smilie |
| `04-login.png` | login |
| `05-search.png` | search results |
| `06-member-list.png` | member list |
| `07-logged-in-forum-list.png` | forum list as mario (who is online, last visit) |
| `08-new-topic-form.png` | new topic form |
| `09-pm-sentbox.png` | private messages, sent box |
| `10-admin-main.png` … `14-admin-group-permissions.png` | administration panel |

## Known limitations of this run

- No SMTP server is part of the compose stack: e-mail notifications (e.g. new PM) fail with
  `MailConnectException localhost:25` in the log, as the legacy application did without a mail server. Set the
  `jforum."mail.smtp.host"` keys to use a real server.
- The admin home "Version Information" box cannot reach the jforum.net version file (the page now returns HTML);
  it shows the legacy "could not determine" message and a warning is logged.
- File attachment upload and the POP mail integration were covered by the automated tests (GridFS, Testcontainers),
  not by this HTTP run.
