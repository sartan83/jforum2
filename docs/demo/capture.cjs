// E2E evidence capture for the Spring Boot rewrite.
// Usage: NODE_PATH=<dir with playwright-core> node docs/demo/capture.cjs [baseUrl] [chromePath]
const { chromium } = require('playwright-core');
const fs = require('fs');
const path = require('path');

const BASE = process.argv[2] || 'http://localhost:8080';
const CHROME = process.argv[3] || process.env.CHROME || 'google-chrome';
const OUT = __dirname;
const log = [];
const note = (m) => { const l = `${new Date().toISOString()} ${m}`; log.push(l); console.log(l); };

(async () => {
  const browser = await chromium.launch({ executablePath: CHROME, headless: true, args: ['--no-sandbox'] });
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await ctx.newPage();
  let n = 0;
  const shot = async (name) => {
    const file = `${String(++n).padStart(2, '0')}-${name}.png`;
    await page.screenshot({ path: path.join(OUT, file), fullPage: true });
    note(`screenshot ${file} <- ${page.url().replace(BASE, '')}`);
  };
  const go = async (url, name) => {
    const r = await page.goto(BASE + url);
    note(`GET ${url} -> ${r.status()}`);
    if (name) await shot(name);
    return r;
  };
  const login = async (user, pass) => {
    await go('/user/login.page');
    await page.fill('input[name=username]', user);
    await page.fill('input[name=password]', pass);
    await Promise.all([page.waitForNavigation(), page.press('input[name=password]', 'Enter')]);
    note(`login ${user} -> ${page.url().replace(BASE, '')}`);
  };
  const logout = async () => {
    await Promise.all([page.waitForNavigation(), page.click('form[action*="logout"] button[type=submit]')]);
    note(`logout -> ${page.url().replace(BASE, '')}`);
  };

  try {
    await go('/forums/list.page', 'index-anonymous');
    await go('/user/insert.page', 'register-form');
    await login('mario', 'demo123');
    await go('/forums/list.page', 'index-logged-mario');
    await go('/forums/show/1.page', 'forum-topics');
    await go('/posts/list/1.page', 'topic-view');

    await go('/posts/insert/1.page');
    const subject = `Demo E2E ${Date.now()}`;
    await page.fill('input[name=subject]', subject);
    await page.fill('textarea[name=message]', '[b]Topic creato dal test E2E[/b] con [i]BBCode[/i] e un allegato :)');
    const attach = path.join(require('os').tmpdir(), 'sample-attachment.txt');
    fs.writeFileSync(attach, 'Allegato di esempio salvato in GridFS\n');
    if (await page.$('input[name=file_0]')) {
      await page.setInputFiles('input[name=file_0]', attach);
      await page.fill('input[name=comment_0]', 'allegato demo');
    }
    await shot('new-topic-form');
    await Promise.all([page.waitForNavigation(), page.click('form[action*="insertSave"] button[type=submit]:not([name=preview])')]);
    note(`new topic "${subject}" -> ${page.url().replace(BASE, '')}`);
    await shot('new-topic-with-attachment');
    const topicUrl = page.url().replace(BASE, '').split('#')[0];

    const replyLink = await page.$('a[href*="/posts/reply/"]');
    if (replyLink) {
      await Promise.all([page.waitForNavigation(), replyLink.click()]);
      await page.fill('textarea[name=message]', 'Risposta di Mario dal test E2E.');
      const submitReply = () => Promise.all([page.waitForNavigation(), page.click('form[action*="insertSave"] button[type=submit]:not([name=preview])')]);
      await submitReply();
      if (page.url().includes('insertSave')) {
        note('reply rejected by flood control (posts.new.delay = 15s, legacy behaviour)');
        await shot('flood-control');
        await page.waitForTimeout(16000);
        await submitReply();
      }
      note(`reply -> ${page.url().replace(BASE, '')}`);
      await shot('topic-after-reply');
    }

    await go('/search/search.page?search_keywords=Demo&match_type=all', 'search-results');
    await go('/recentTopics/list.page', 'recent-topics');

    await go('/pm/send.page');
    const to = await page.$('input[name=toUsername]');
    if (to) await to.fill('giulia');
    await page.fill('input[name=subject]', 'Ciao Giulia');
    await page.fill('textarea[name=message]', 'Messaggio privato inviato dal test E2E.');
    await shot('pm-compose');
    await Promise.all([page.waitForNavigation(), page.click('form[action*="sendSave"] button[type=submit]:not([name=preview])')]);
    note(`pm send -> ${page.url().replace(BASE, '')}`);
    await shot('pm-sent');
    await go('/pm/sentbox.page', 'pm-sentbox');
    await go('/user/profile/' + (await page.evaluate(() => document.querySelector('a[href*="/user/profile/"]')?.getAttribute('href')?.match(/(\d+)\.page/)?.[1] || '2')) + '.page', 'profile');
    await logout();

    await login('giulia', 'demo123');
    await go('/pm/inbox.page', 'pm-inbox-giulia');
    const pm = await page.$('a[href*="/pm/read/"]');
    if (pm) { await Promise.all([page.waitForNavigation(), pm.click()]); await shot('pm-read'); }
    await logout();

    await login('Admin', process.env.JFORUM_ADMIN_PASSWORD || 'admin');
    await go(topicUrl, 'topic-moderation-tools-admin');
    await go('/forums/show/1.page', 'forum-admin');
    await go('/moderation/pending.page', 'moderation-pending');
    await go('/moderation/showActivityLog.page', 'moderation-log');
    const admin = [['/admBase/main.page', 'admin-panel'], ['/adminCategories/list.page', 'admin-categories'],
      ['/adminForums/list.page', 'admin-forums'], ['/adminGroups/list.page', 'admin-groups'],
      ['/adminUsers/list.page', 'admin-users'], ['/adminConfig/list.page', 'admin-config']];
    for (const [u, name] of admin) await go(u, name);

    for (const u of ['/rss/forums.page', '/rss/forumTopics/1.page', '/rss/topicPosts/1.page']) {
      const r = await ctx.request.get(BASE + u);
      const body = await r.text();
      note(`RSS ${u} -> ${r.status()} ${r.headers()['content-type']} items=${(body.match(/<item>/g) || []).length}`);
      if (u.includes('forumTopics')) fs.writeFileSync(path.join(OUT, 'rss-forumTopics-1.xml'), body);
    }
    const legacy = await ctx.request.get(BASE + '/jforum.page?module=posts&action=list&topic_id=1', { maxRedirects: 0 });
    note(`legacy /jforum.page?module=posts&action=list&topic_id=1 -> ${legacy.status()} ${legacy.headers()['location'] || ''}`);
    const nf = await ctx.request.get(BASE + '/posts/list/999999.page');
    note(`GET /posts/list/999999.page -> ${nf.status()}`);

    const mobile = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true });
    const mp = await mobile.newPage();
    await mp.goto(BASE + '/forums/list.page');
    await mp.screenshot({ path: path.join(OUT, `${String(++n).padStart(2, '0')}-index-mobile.png`), fullPage: true });
    note('screenshot index-mobile (390x844)');
  } catch (e) {
    note(`ERROR ${e.stack || e}`);
    await shot('error').catch(() => {});
    process.exitCode = 1;
  } finally {
    fs.writeFileSync(path.join(OUT, 'e2e.log'), log.join('\n') + '\n');
    await browser.close();
  }
})();
