@echo off
setlocal

REM Use the common local install path if CATALINA_HOME is not already configured.
if not defined CATALINA_HOME if exist "C:\apache-tomcat\lib\servlet-api.jar" set "CATALINA_HOME=C:\apache-tomcat"
if not defined CATALINA_HOME (
  echo ERROR: Tomcat was not found at C:\apache-tomcat.
  echo Set CATALINA_HOME to your Tomcat installation folder, for example:
  echo   set "CATALINA_HOME=C:\apache-tomcat"
  exit /b 1
)
if not exist "%CATALINA_HOME%\lib\servlet-api.jar" (
  echo ERROR: CATALINA_HOME does not look like a Tomcat installation: %CATALINA_HOME%
  exit /b 1
)
if not exist "WEB-INF\lib\mysql-connector-j.jar" (
  echo ERROR: Add MySQL Connector/J at WEB-INF\lib\mysql-connector-j.jar.
  exit /b 1
)
if not exist "WEB-INF\classes" mkdir "WEB-INF\classes"

echo Compiling Java source with javac...
javac -encoding UTF-8 -cp "%CATALINA_HOME%\lib\servlet-api.jar" -d "WEB-INF\classes" src\com\canteen\CanteenServlet.java
if errorlevel 1 (echo ERROR: Compilation failed.& exit /b 1)

set "DEST=%CATALINA_HOME%\webapps\CanteenApp"
if not exist "%DEST%" mkdir "%DEST%"
echo Copying app files to %DEST% ...
robocopy "%CD%" "%DEST%" /E /R:2 /W:1 /XD ".git" "src" /XF "start-windows.bat" "start-linux.sh"
if errorlevel 8 (echo ERROR: Copy failed.& exit /b 1)

echo.
echo Build and copy complete.
echo Database defaults: DB_USER=root and DB_PASSWORD is empty unless explicitly set.
echo If you change source code, stop and restart Tomcat to reload the application.
echo Open http://localhost:8080/CanteenApp/
endlocal
