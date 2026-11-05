# Evidenze E2E — JForum su Spring Boot 3 / MongoDB 8.2

Stack avviato con `docker compose down -v && docker compose up -d --build` (database pulito, seed demo attivo), poi pagine e flussi percorsi con Chrome headless tramite [`capture.cjs`](capture.cjs).

Per rigenerare le evidenze:

```bash
npm i --prefix /tmp/pw playwright-core
NODE_PATH=/tmp/pw/node_modules node docs/demo/capture.cjs http://localhost:8080 /path/to/chrome
```

| File | Flusso |
|------|--------|
| `01-index-anonymous.png` | Indice dei forum da anonimo (`/forums/list.page`) |
| `02-register-form.png` | Registrazione (`/user/insert.page`) |
| `03-index-logged-mario.png` | Indice dopo il login di `mario` |
| `04-forum-topics.png` | Lista topic del forum (`/forums/show/1.page`) |
| `05-topic-view.png` | Vista del topic (`/posts/list/1.page`) |
| `06-new-topic-form.png` | Nuovo topic con BBCode e allegato |
| `07-new-topic-with-attachment.png` | Topic creato con l'allegato salvato in GridFS |
| `08-flood-control.png` | Flood control legacy (`posts.new.delay` = 15 s) sulla risposta immediata |
| `09-topic-after-reply.png` | Topic dopo la risposta |
| `10-search-results.png` | Ricerca sul text index MongoDB |
| `11-recent-topics.png` | Topic recenti |
| `12`–`14` | PM: composizione, conferma, posta inviata |
| `15-profile.png` | Profilo utente |
| `16`–`17` | PM: inbox di `giulia` e lettura |
| `18-topic-moderation-tools-admin.png` | Strumenti di moderazione sul topic (delete/move/lock/unlock) |
| `19-forum-admin.png` | Forum visto da Admin, link alla moderazione |
| `20`–`21` | Coda dei post da approvare e log di moderazione |
| `22`–`27` | Pannello admin: indice, categorie, forum, gruppi, utenti, configurazione |
| `28-index-mobile.png` | Layout responsive a 390 px |
| [`e2e.log`](e2e.log) | Log dei passi con status HTTP, RSS, redirect `/jforum.page` e 404 |
| [`rss-forumTopics-1.xml`](rss-forumTopics-1.xml) | Feed RSS 2.0 del forum 1 |
| [`compose.log`](compose.log) | `docker compose ps` e log di avvio dell'app |
| [`test-summary.txt`](test-summary.txt) | Esito di `./mvnw -B verify` (113 test, 0 fallimenti) |

La registrazione video dei flussi va fatta a mano.
