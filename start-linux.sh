#!/usr/bin/env bash
set -euo pipefail
if [[ -z "${CATALINA_HOME:-}" ]]; then echo "ERROR: Set CATALINA_HOME to your Tomcat folder."; exit 1; fi
if [[ ! -f "$CATALINA_HOME/lib/servlet-api.jar" ]]; then echo "ERROR: CATALINA_HOME does not look like Tomcat."; exit 1; fi
if [[ ! -f "WEB-INF/lib/mysql-connector-j.jar" ]]; then echo "ERROR: Add Connector/J at WEB-INF/lib/mysql-connector-j.jar."; exit 1; fi
mkdir -p WEB-INF/classes
echo "Compiling Java source with javac..."
javac -encoding UTF-8 -cp "$CATALINA_HOME/lib/servlet-api.jar" -d WEB-INF/classes src/com/canteen/CanteenServlet.java
DEST="$CATALINA_HOME/webapps/CanteenApp"
mkdir -p "$DEST"
echo "Copying application files to $DEST ..."
for item in index.html student.html admin.html assets WEB-INF META-INF database; do
  if [[ -e "$item" ]]; then cp -R "$item" "$DEST/"; fi
done
echo "Done. Ensure DB_USER and DB_PASSWORD are set, then start/restart Tomcat."
echo "Open http://localhost:8080/CanteenApp/"
