# CanteenApp

Plain HTML/CSS/JavaScript + Java Servlets on Tomcat 10.1+ + MySQL JDBC. No Maven, Gradle, npm, UI framework, or JSON dependency.

Features: PRN/username sign-in; local-demo plain-text passwords; session authentication; student/admin role checks; menu, cart, orders and pickup ticket codes; admin menu/order management; CSRF protection for state-changing requests; parameterized SQL.

## Demo accounts

| Role | Username | Password |
|---|---|---|
| Admin | `admin` | `admin` |
| Student | `student` | `student` |

These are local-demo accounts only. Passwords are stored in plain text at the user's request; this is insecure and must never be used in production or on a public server.

## Requirements
- JDK 17+
- Apache Tomcat 10.1+
- MySQL 8+
- MySQL Connector/J JAR

## Run it

1. Create the database in MySQL (Workbench or command line):
   ```sql
   CREATE DATABASE IF NOT EXISTS canteen_app CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
   The SQL script creates the tables, sample menu rows, and demo users. The application also creates missing tables and resets the two demo accounts on startup.
2. Download MySQL Connector/J from https://dev.mysql.com/downloads/connector/j/ and place the JAR at `WEB-INF/lib/mysql-connector-j.jar` (rename the versioned JAR to that filename). The driver is not committed.
3. Set `CATALINA_HOME` to your Tomcat folder. Keep this project outside Tomcat's `webapps` directory.
4. Database defaults: URL `jdbc:mysql://localhost:3306/canteen_app?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata`, user `root`, and **empty password** (for a local MySQL root account with no password). If your MySQL account has a password, configure `DB_PASSWORD` before starting Tomcat; you can also set `DB_USER` and `DB_URL`. Never commit real passwords to GitHub.
   - Windows CMD for a password-protected account: `set "DB_USER=root"` and `set "DB_PASSWORD=your_actual_password"` in the same terminal that launches Tomcat.
   - Linux: `export DB_USER=root; export DB_PASSWORD='your_actual_password'`.
   - Windows CMD cannot keep an environment variable defined with an empty value using `set "DB_PASSWORD="`; that command unsets it. The Java default is empty when `DB_PASSWORD` is unset.
5. On Windows, `start-windows.bat` automatically uses `C:\\apache-tomcat` if `CATALINA_HOME` is not set and that folder exists. Otherwise set `CATALINA_HOME` to your Tomcat installation folder. Run `start-windows.bat` from the project folder. On Linux run `chmod +x start-linux.sh && ./start-linux.sh`. These compile the Java source with `javac` and copy the app to Tomcat—no build tool.
6. Start/restart Tomcat: Windows `%CATALINA_HOME%\\bin\\startup.bat`; Linux `$CATALINA_HOME/bin/startup.sh`. `CATALINA_HOME` is the path to the Tomcat installation, not a database setting. The app does not require a separate Catalina configuration file for this setup.
7. Visit http://localhost:8080/CanteenApp/

## Try the flow
Log in as student/student, add items, update your cart, place an order, then use its CA ticket code on the My Orders tab. Sign out and log in as admin/admin to add/hide menu items and update order statuses.

## Files
```
index.html, student.html, admin.html
assets/styles.css, assets/app.js
src/com/canteen/CanteenServlet.java
WEB-INF/web.xml, WEB-INF/lib/
META-INF/context.xml
database/create-database.sql
start-windows.bat, start-linux.sh
```

Security: passwords are hashed with an individual random salt and PBKDF2-HMAC-SHA-256; sessions, CSRF tokens, role-based server checks, generic login errors, input checks, JDBC prepared statements, and basic security headers are used. This is a teaching demo, not a production payment system. For production, use HTTPS, a least-privilege DB account, secure cookie configuration, login throttling, stronger account provisioning and auditing. No actual payment is processed.
