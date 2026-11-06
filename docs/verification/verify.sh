#!/usr/bin/env bash
# Smoke verification of a running JForum (default http://localhost:8080) started with the
# "demo" profile: saves every response under docs/verification/output/ and prints one
# PASS/FAIL line per check (also written to output/results.txt).
#   docs/verification/verify.sh [base-url]
set -u
BASE=${1:-http://localhost:8080}
DIR=$(cd "$(dirname "$0")" && pwd)/output
rm -rf "$DIR"; mkdir -p "$DIR"
ANON="$DIR/anon.cookies"; ADMIN="$DIR/admin.cookies"; MARIO="$DIR/mario.cookies"
FAILS=0

result() { printf '%-4s %-38s %s\n' "$1" "$2" "$3" | tee -a "$DIR/results.txt"; [ "$1" = PASS ] || FAILS=$((FAILS+1)); }

# check <name> <cookiejar> <path> <expected status> <text expected in body> [curl args...]
check() {
	local name=$1 jar=$2 path=$3 status=$4 text=$5; shift 5
	local code
	code=$(curl -s -c "$jar" -b "$jar" -o "$DIR/$name.html" -w '%{http_code}' "$@" "$BASE$path")
	if [ "$code" != "$status" ]; then result FAIL "$name" "$path -> HTTP $code (expected $status)"; return; fi
	if [ -n "$text" ] && ! grep -q -- "$text" "$DIR/$name.html"; then result FAIL "$name" "$path -> '$text' not found"; return; fi
	if grep -q "An error has occurred" "$DIR/$name.html"; then result FAIL "$name" "$path -> JForum error page"; return; fi
	result PASS "$name" "$path -> HTTP $code${text:+, contains '$text'}"
}

login() { # login <jar> <user> <password>
	curl -s -c "$1" -b "$1" -o /dev/null "$BASE/forums/list.page"
	curl -s -c "$1" -b "$1" -o /dev/null -d "module=user&action=validateLogin&username=$2&password=$3&login=Login" "$BASE/jforum.page"
}

STAMP=$(date +%s)

check 01-health              "$ANON"  /actuator/health                200 '"UP"'
check 02-root-redirect       "$ANON"  /                               302 ''
check 03-forum-list          "$ANON"  /forums/list.page               200 'Spring Boot'
check 04-forum-show          "$ANON"  /forums/show/2.page             200 'Migrating JForum to Spring Boot 3'
check 05-topic-show          "$ANON"  /posts/list/2.page              200 'Are the original templates still used?'
check 06-bbcode-rendered     "$ANON"  /posts/list/2.page              200 '<i>all of them</i>'
check 07-recent-topics       "$ANON"  /recentTopics/list.page         200 'Hello everybody'
check 08-member-list         "$ANON"  /user/list.page                 200 'giulia'
check 09-user-profile        "$ANON"  /user/profile/3.page            200 'mario'
check 10-search              "$ANON"  "/jforum.page?module=search&action=search&search_keywords=Atlas&match_type=all&sort_by=time&sort_dir=DESC" 200 'Connecting to MongoDB Atlas'
check 11-rss                 "$ANON"  /rss/forumTopics/2.page         200 '<rss'
check 12-static-css          "$ANON"  /templates/default/styles/style.css 200 'body'
check 13-template-source-hidden "$ANON" /templates/default/forum_list.htm 404 ''
check 14-login-form          "$ANON"  /user/login.page                200 'username'
check 15-register-agreement  "$ANON"  /user/insert.page               200 'Registration agreement terms'
check 16-anon-cannot-post    "$ANON"  /posts/insert/2.page            200 'login'

# login: wrong password, then the real one
check 17-login-wrong-password "$MARIO" /jforum.page 200 'Invalid' -d 'module=user&action=validateLogin&username=mario&password=wrong&login=Login'
login "$MARIO" mario demo
check 18-login-mario         "$MARIO" /forums/list.page               200 'Logout \[mario\]'

# new topic + reply (as mario), then read them back
check 19-new-topic           "$MARIO" /jforum.page 302 '' --data-urlencode "subject=Verification topic $STAMP" \
	--data-urlencode 'message=Posted by [b]verify.sh[/b] :)' -d 'module=posts&action=insertSave&forum_id=4&topic_type=0&start=0&total_files=0'
TOPIC=$(curl -s -b "$MARIO" "$BASE/forums/show/4.page" | grep -B3 "Verification topic $STAMP" | grep -o 'posts/list/[0-9]*\.page' | tail -1 | grep -o '[0-9]*')
check 20-new-topic-listed    "$MARIO" /forums/show/4.page             200 "Verification topic $STAMP"
check 21-new-topic-bbcode    "$MARIO" "/posts/list/$TOPIC.page"       200 '<b>verify.sh</b>'
sleep 16   # posts.flood control: one message every 15 seconds per user (legacy behaviour)
check 22-reply               "$MARIO" /jforum.page 302 '' --data-urlencode "subject=Re: Verification topic $STAMP" \
	--data-urlencode "message=Reply $STAMP" -d "module=posts&action=insertSave&forum_id=4&topic_id=$TOPIC&start=0&total_files=0"
check 23-reply-shown         "$MARIO" "/posts/list/$TOPIC.page"       200 "Reply $STAMP"

# private messages: mario -> giulia
check 24-pm-send             "$MARIO" /jforum.page 200 '' --data-urlencode "subject=PM $STAMP" --data-urlencode 'message=Ciao Giulia' \
	-d 'module=pm&action=sendSave&toUsername=giulia&toUserId=4'
check 25-pm-sentbox          "$MARIO" /pm/sentbox.page                200 "PM $STAMP"
login "$ANON" giulia demo
check 26-pm-inbox            "$ANON"  /pm/inbox.page                  200 "PM $STAMP"

# permissions: a regular user is sent to the admin login instead of the admin panel (legacy behaviour)
check 27-user-not-admin      "$MARIO" "/jforum.page?module=admBase&action=main" 302 ''

# administration
login "$ADMIN" admin admin
check 28-admin-frameset      "$ADMIN" /admBase/login.page             200 'module=admBase&amp;action=menu\|module=admBase&action=menu'
check 29-admin-menu          "$ADMIN" "/jforum.page?module=admBase&action=menu" 200 'adminCategories'
check 30-admin-main          "$ADMIN" "/jforum.page?module=admBase&action=main" 200 'Admin'
check 31-admin-config        "$ADMIN" /adminConfig/list.page          200 'forum.link'
check 32-admin-categories    "$ADMIN" /adminCategories/list.page      200 'Java &amp; Spring\|Java & Spring'
check 33-admin-forums        "$ADMIN" /adminForums/list.page          200 'MongoDB'
check 34-admin-groups        "$ADMIN" /adminGroups/list.page          200 'Administration'
check 35-admin-permissions   "$ADMIN" /adminGroups/permissions/1.page 200 'perm_'
check 36-admin-users         "$ADMIN" /adminUsers/list.page           200 'luca'
check 37-admin-smilies       "$ADMIN" /adminSmilies/list.page         200 'smilies'
check 38-admin-rankings      "$ADMIN" /adminRankings/list.page        200 ''
check 39-admin-banlist       "$ADMIN" /adminBanning/list.page         200 ''
check 40-admin-attachments   "$ADMIN" /adminAttachments/configurations.page 200 ''
check 41-admin-moderation-log "$ADMIN" /moderation/showActivityLog.page 200 ''
check 42-admin-search-stats  "$ADMIN" /adminSearchStats/list.page     200 ''
check 43-admin-cache         "$ADMIN" /adminCache/list.page           200 ''

echo "FAILURES: $FAILS" | tee -a "$DIR/results.txt"
rm -f "$DIR"/*.cookies
exit $FAILS
