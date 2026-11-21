# My Money Map — Spring Boot edition

A personal finance tracker rebuilt in **Java 17 + Spring Boot 3**. It is a port of the original
React/Supabase project (*moneytrackerbypratosh*): same features, but the backend is now Spring Boot
with a REST API (stored in **MySQL**), and the web UI is served by Spring Boot itself — **one application, one command to run**.

## Features
- **Accounts**: register with a unique User ID + 4–6 digit PIN, sign in/out, protected pages, each user's data fully isolated.
- **Escalating lockout** for wrong PINs (10 tries → 10 h, then 5 → 15 h, 4 → 24 h, 2 → 24 h), enforced on the server.
- **Currency**: 20 currencies (INR, USD, EUR, GBP, JPY, CAD, AUD, SGD, AED …) chosen at sign-up and changeable in Settings. Old amounts are *never* silently converted.
- **Overview**: monthly income / expenses / net / savings rate, budget usage, category donut, recent transactions, upcoming recurring items, account balances, month selector, quick actions.
- **Transactions**: add / edit / delete (with confirmation), search, filters (type, category, account, payment method, month, year, date range), sorting, pagination, CSV import & export.
- **Budgets**: monthly or yearly category budgets, progress bars with *On track / Approaching limit / Exceeded* labels, remaining amount, previous-period comparison, budget-vs-actual charts.
- **Insights**: income vs expense trend, savings-rate trend, category distribution, top categories, largest expenses, averages, budget adherence, month-over-month and year-over-year comparison, and plain-language observations generated only from your data.
- **Extras**: accounts/wallets, categories manager, savings goals, recurring transactions (auto-created when due), keyboard shortcuts (`n` new expense, `i` new income, `/` search), dark/light theme, optional labelled **demo data**, JSON/CSV export.

## What you need
| Tool | Version |
|------|---------|
| JDK  | 17 or newer (21 works) |
| Maven | 3.9 or newer (or open the folder in IntelliJ IDEA / Eclipse / VS Code and run `MoneyTrackerApplication`) |
| MySQL | 8.0 or newer |

You also need **MySQL 8.0 or newer** running locally (or reachable over the network).
The browser needs internet access once per page load for the Chart.js library (loaded from a CDN).

## Set up MySQL
1. Start MySQL and log in (e.g. `mysql -u root -p`).
2. Create the database (the app can also do this itself):
   ```sql
   CREATE DATABASE IF NOT EXISTS moneytracker CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
   (`schema-mysql.sql` contains this plus an optional dedicated user.)
3. Tell the app how to connect (Windows PowerShell shown; use `export` on Mac/Linux):
   ```powershell
   $env:DB_USER="root"
   $env:DB_PASSWORD="your-mysql-password"
   ```
   Tables are created automatically on first start.

## Run it
```bash
cd moneytracker
mvn spring-boot:run
```
Open **http://localhost:8080**, choose *Create account*, pick a currency, and start tracking.
Your data is stored in the MySQL database `moneytracker`.

### Build a standalone jar
```bash
mvn clean package            # add -DskipTests to skip the unit tests
java -jar target/moneytracker-1.0.0.jar
```

## Configuration
Set these as environment variables (or edit `src/main/resources/application.yml`):

| Variable | Default | Meaning |
|----------|---------|---------|
| `PORT` | `8080` | HTTP port |
| `PIN_PEPPER` | `dev-only-change-me` | Secret mixed into every PIN before hashing. **Set your own before creating real accounts** and keep it — changing it later locks existing users out. |
| `DB_URL` | `jdbc:mysql://localhost:3306/moneytracker?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC` | JDBC URL |
| `DB_USER` / `DB_PASSWORD` | `root` / empty | MySQL login |

### Optional: try it without MySQL
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```
This uses a local file database in `data/` — handy for a quick look, but use MySQL for real data.

## Project layout
```
src/main/java/com/pratosh/moneytracker
├── MoneyTrackerApplication.java
├── config/    SecurityConfig, ApiExceptionHandler
├── domain/    JPA entities (AppUser, Txn, Category, Account, Budget, Goal, RecurringRule, LoginAttempt)
├── repo/      Spring Data repositories
├── service/   business logic (Auth, Txn, Budget, Overview, Insights, Recurring, Demo, Csv …)
├── web/       REST controllers
├── dto/       request / response records
└── util/      CSV, currencies, period helpers
src/main/resources
├── application.yml, application-h2.yml
└── static/    index.html, css/app.css, js/*.js   (the web UI)
```

## REST API (all under `/api`, JSON, session cookie)
| Area | Endpoints |
|------|-----------|
| Auth | `POST /auth/register`, `POST /auth/login`, `POST /auth/logout`, `GET /auth/session`, `GET /auth/available?userId=`, `GET /auth/currencies` |
| Profile | `GET/PUT /me`, `PUT /me/pin`, `POST/DELETE /demo`, `GET /export/json` |
| Transactions | `GET/POST /transactions`, `PUT/DELETE /transactions/{id}`, `GET /transactions/export`, `POST /transactions/import` |
| Budgets | `GET/POST /budgets`, `PUT/DELETE /budgets/{id}`, `GET /budgets/performance` |
| Analytics | `GET /overview?month=YYYY-MM`, `GET /insights?view=&month=&year=&categoryId=&from=&to=` |
| Master data | `/categories`, `/accounts`, `/goals`, `/recurring` (GET, POST, PUT/{id}, DELETE/{id}), `POST /recurring/run` |

## How the original requirements map to this version
- **Data isolation** (was Supabase Row Level Security): every table has an `owner_id`; every service query filters by the signed-in user, and a foreign id returns *404*.
- **Precise money**: `NUMERIC(14,2)` columns, `BigDecimal` in Java, `amount > 0` checks in the database and in validation.
- **PIN security**: the PIN is combined with the User ID and server secret (HMAC-SHA256), then hashed with BCrypt (cost 12). Raw PINs are never stored or logged.
- **Sessions**: HttpOnly, SameSite=Lax cookie; session id rotates at sign-in; a Content-Security-Policy is sent.
- **Instant refresh**: after any add/edit/delete the page re-reads from the server, so every tab shows the same data.

## Good to know
- The login lockout is per User ID (the original was per browser). This is stronger against guessing, but it also means someone could lock a known User ID out temporarily.
- Deleting a category keeps its transactions (shown as *Uncategorised*) and removes that category's budgets. Deleting an account keeps its transactions unlinked.
- Recurring rules run when you sign in / open the app, and again every hour in the background.
- Transactions use the local date and time you enter; they are stored without a timezone.

## Troubleshooting
- *Port already in use* → `PORT=9090 mvn spring-boot:run`
- *Access denied for user* → check `DB_USER` / `DB_PASSWORD`.
- *Communications link failure* → MySQL is not running or the host/port in `DB_URL` is wrong.
- *Can't sign in after changing `PIN_PEPPER`* → set it back, or run `DROP DATABASE moneytracker;` to start fresh.
- *Charts show a message instead of graphs* → the browser could not reach the Chart.js CDN; check your internet connection.
- *Reset everything* → stop the app and run `DROP DATABASE moneytracker;` in MySQL.


## Fix included in this build
The frontend page registry (`Pages`) is initialized in `core.js`, which loads before
`overview.js`, `transactions.js`, `budgets.js`, `insights.js`, and `settings.js`.
Previously it was initialized in `main.js`, which loads after those page modules.
That load-order bug caused navigation to fall back to Overview and show
`Pages[page] is not a function` (including when opening Settings).
