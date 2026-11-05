# jforum2 — JForum 2.1.8 su Spring Boot 3 / Java 21 / MongoDB 8

Questo repository contiene la riscrittura di JForum 2.1.8 come applicazione **Spring Boot 3.5** su **Java 21**, con persistenza su **MongoDB 8** tramite Spring Data MongoDB. URL, flussi utente e permessi sono gli stessi dell'originale. Il codice legacy (Ant/WAR/JDBC in `src/`, `templates/`, `WEB-INF/`) resta nel repo come riferimento.

| Path | Contenuto |
|------|-----------|
| `jforum-boot/` | Applicazione Spring Boot (package `net.jforum`): web MVC + FreeMarker, Spring Security, Spring Data MongoDB, GridFS, Spring Session |
| `tools/migration/` | Tool di migrazione SQL Server → MongoDB (vedi [`tools/migration/README.md`](tools/migration/README.md)) |
| `docker-compose.yml`, `Dockerfile` | Stack locale: `mongo:8.2` (MongoDB 8.x, replica set, volume persistente) + app + mailpit |
| `docs/demo/` | Screenshot e log della verifica end-to-end |

## Requisiti

- JDK 21 (es. Eclipse Temurin 21)
- Docker con Compose v2. Serve sia per `docker compose` sia per i test Testcontainers.
- Maven non va installato: si usa il wrapper `./mvnw`.

## Build e test

```bash
./mvnw -B verify                              # build + test di tutti i moduli (Testcontainers MongoDB 8)
./mvnw -B -pl jforum-boot -am package -DskipTests   # solo il jar: jforum-boot/target/jforum.jar
```

## Avvio con Docker Compose

```bash
docker compose up --build
```

- Forum: http://localhost:8080. Gli URL legacy restano validi, es. `/forums/list.page`, `/posts/list/1.page`, `/jforum.page?module=forums&action=list`.
- Amministratore: `Admin` / `admin`. La password si cambia con `JFORUM_ADMIN_PASSWORD=... docker compose up`, prima di qualsiasi uso non locale.
- Dati demo (categorie, forum, utenti `mario`/`giulia`/`luca` con password `demo123`, topic e post): sono attivi di default (`JFORUM_SEED_DEMO=true`). Si disattivano con `JFORUM_SEED_DEMO=false`.
- Email (attivazione account, notifiche, PM): mailpit su http://localhost:8025.
- Reset completo dei dati: `docker compose down -v`.
- Se il volume `mongo-data` è stato creato con MongoDB 8.3, mongod 8.2 esce con codice 62 (`Invalid featureCompatibilityVersion`): ricrearlo con `docker compose down -v`. Si usa 8.2 perché 8.0 e 8.3 non partono su kernel Linux ≥ 6.19 (SERVER-121912); per cambiare immagine: `MONGO_IMAGE=...`.

Variabili principali: `MONGODB_URI`, `FORUM_LINK`, `MAIL_HOST`/`MAIL_PORT`, `JFORUM_ADMIN_PASSWORD`, `JFORUM_SEED_DEMO`. Qualsiasi chiave di `SystemGlobals.properties` si può sovrascrivere come proprietà Spring `jforum.<chiave>` (es. `--jforum.topicsPerPage=20`). Dal pannello admin la si scrive nella collection `config`.

## Avvio senza Docker per l'app

```bash
docker run -d --name jforum-mongo -p 27017:27017 mongo:8.2
./mvnw -pl jforum-boot -am spring-boot:run
```

## Migrazione dati da SQL Server

1. Fare un backup del database SQL Server di JForum.
2. Avviare MongoDB 8 vuoto, es. `docker compose up -d mongo`.
3. Lanciare il tool: `./mvnw -pl tools/migration -am package` e poi `java -jar tools/migration/target/*.jar` con l'URL JDBC SQL Server e l'URI MongoDB. Opzioni, mapping tabelle → collection, verifica e rollback sono descritti in [`tools/migration/README.md`](tools/migration/README.md).
4. Avviare l'app sullo stesso database con `JFORUM_SEED_DEMO=false`. Il bootstrap non tocca un database già popolato.

Il tool conserva gli ID legacy (`_id` numerici) e le relazioni. Le password MD5 legacy restano valide e vengono convertite in bcrypt al primo login.

## Architettura in breve

- **Document model**: collection `users`, `groups` (ruoli embedded `{name, values[]}` come `jforum_roles`/`jforum_role_values`), `categories`, `forums` (contatori e ultimo post denormalizzati), `topics` (sondaggi embedded), `posts` (collection separata, indice `{topicId, time}` per la paginazione + text index per la ricerca), `private_messages`, `attachments` (file e avatar in GridFS), `karma`, `bookmarks`, `topic_watch`, `forum_watch`, `rankings`, `smilies`, `banlist`, `config`, `moderation_log`, `counters` (sequenze degli ID numerici), `http_sessions` (Spring Session).
- **URL**: controller Spring MVC sugli stessi pattern `*.page` di `urlPattern.properties`. Il `LegacyUrlFilter` converte `/jforum.page?module=&action=` negli URL friendly.
- **Sicurezza**: form login Spring Security, CSRF, remember-me, permessi per gruppo con la semantica di `permissions.xml`.
- **Formattazione**: BBCode da `bb_config.xml`, SafeHtml ed emoticon, portati dal codice legacy.

---

## README originale

# jforum2
JForum 2.x series

JForum was a bulletin board software based on PHPBB which I wrote back in the 2000's. It was my first big open source project, and I worked on it for several years. There are still code from my early Java days, and you can still see the tradicional MVC implementation. 

In the gold Java years it was used by some big sites, like JavaRanch.com, GUJ.com.br (biggest java community in Brazil) and Sony Online Entertainment. 

I have ceased development and maintenance for several years already, and I only keep the code alive for historial reasons. I do not intend to apply fixes or develop new features. 

Keep in mind that this a very old codebase.

## Check [http://jforum.net](jforum.net) for the currently maintained version
