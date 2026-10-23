@echo off
setlocal

REM ============================================================
REM CanteenApp Windows configuration
REM Edit these values if your local installation differs.
REM Do not put a real database password in a public repository.
REM ============================================================

REM Java: use JAVA_HOME if configured; otherwise use java/javac from PATH.
if not defined JAVA_HOME (
  for %%J in (javac.exe) do set "JAVAC_PATH=%%~$PATH:J"
) else (
  set "JAVAC_PATH=%JAVA_HOME%\bin\javac.exe"
)
if not defined JAVAC_PATH if defined JAVA_HOME set "JAVAC_PATH=%JAVA_HOME%\bin\javac.exe"
if not defined JAVA_HOME if not defined JAVAC_PATH (
  echo ERROR: JDK not found. Install JDK 17+ and add javac to PATH, or set JAVA_HOME.
  exit /b 1
)
if defined JAVA_HOME if not exist "%JAVA_HOME%\bin\javac.exe" (
  echo ERROR: JAVA_HOME does not contain bin\javac.exe: %JAVA_HOME%
  exit /b 1
)

REM Tomcat installation and deployment directory.
if not defined CATALINA_HOME set "CATALINA_HOME=C:\apache-tomcat"
if not exist "%CATALINA_HOME%\lib\servlet-api.jar" (
  echo ERROR: Tomcat servlet-api.jar not found at "%CATALINA_HOME%\lib\servlet-api.jar".
  echo Edit CATALINA_HOME near the top of this script to match your Tomcat folder.
  exit /b 1
)
set "CATALINA_BASE=%CATALINA_HOME%"
set "APP_NAME=CanteenApp"
set "APP_URL=http://localhost:8080/%APP_NAME%"
set "DEST=%CATALINA_BASE%\webapps\%APP_NAME%"

REM MySQL JDBC configuration. Java uses these environment variables.
set "DB_URL=jdbc:mysql://localhost:3306/canteen_app?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata"
set "DB_USER=root"
REM Do not hard-code or clear the MySQL password here.
REM Set DB_PASSWORD in the same CMD window before running this script.
REM Example: set "DB_PASSWORD=your_actual_mysql_password"
if not defined DB_PASSWORD (
  echo WARNING: DB_PASSWORD is not set. This only works if MySQL root has no password.
)

REM Ensure we're running from the repository root.
if not exist "src\com\canteen\CanteenServlet.java" (
  echo ERROR: Run this script from the CanteenApp project root.
  echo Expected file: src\com\canteen\CanteenServlet.java
  exit /b 1
)
if not exist "WEB-INF\lib\mysql-connector-j.jar" (
  echo ERROR: Add MySQL Connector/J at WEB-INF\lib\mysql-connector-j.jar.
  exit /b 1
)
if not exist "%CATALINA_HOME%\bin\startup.bat" (
  echo ERROR: startup.bat not found under "%CATALINA_HOME%\bin".
  exit /b 1
)
if not exist "%CATALINA_HOME%\bin\shutdown.bat" (
  echo ERROR: shutdown.bat not found under "%CATALINA_HOME%\bin".
  exit /b 1
)

echo.
echo Stopping any previously running Tomcat instance before deploying...
call "%CATALINA_HOME%\bin\shutdown.bat"
timeout /t 3 /nobreak >nul

if not exist "WEB-INF\classes" mkdir "WEB-INF\classes"
echo.
echo [1/3] Compiling Java source...
if defined JAVA_HOME (
  "%JAVA_HOME%\bin\javac.exe" -encoding UTF-8 -cp "%CATALINA_HOME%\lib\servlet-api.jar" -d "WEB-INF\classes" src\com\canteen\CanteenServlet.java
) else (
  javac -encoding UTF-8 -cp "%CATALINA_HOME%\lib\servlet-api.jar" -d "WEB-INF\classes" src\com\canteen\CanteenServlet.java
)
if errorlevel 1 (
  echo ERROR: Java compilation failed. Fix the errors above before continuing.
  exit /b 1
)

echo.
echo [2/3] Copying application to "%DEST%"...
if not exist "%DEST%" mkdir "%DEST%"
robocopy "%CD%" "%DEST%" /E /R:2 /W:1 /XD ".git" "src" ".vscode" /XF "start-windows.bat" "start-linux.sh"
if errorlevel 8 (
  echo ERROR: Robocopy failed with exit code %ERRORLEVEL%.
  exit /b 1
)

echo.
echo [3/3] Build and deployment copy complete.
echo.
echo Configuration for this script:
echo   JAVA_HOME=%JAVA_HOME%
echo   CATALINA_HOME=%CATALINA_HOME%
echo   CATALINA_BASE=%CATALINA_BASE%
echo   DB_URL=[configured; URL omitted because JDBC URLs contain CMD special characters]
echo   DB_USER=%DB_USER%
echo   DB_PASSWORD= [empty for local MySQL setup]
echo   APP_URL=%APP_URL%
echo.
echo.
echo Starting Tomcat with the configured environment...
call "%CATALINA_HOME%\bin\startup.bat"
if errorlevel 1 (
  echo WARNING: Tomcat startup returned a non-zero status. Check the Tomcat logs.
)
echo.
echo Checking backend and database readiness...
timeout /t 5 /nobreak >nul
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; try { $r=Invoke-WebRequest -Uri '%APP_URL%/api/health' -UseBasicParsing -TimeoutSec 8; Write-Host ('Health check: ' + $r.Content) } catch { Write-Host ('Health check failed: ' + $_.Exception.Message); Write-Host 'Inspect logs under C:\apache-tomcat\logs\.' }"
echo.
echo Open %APP_URL%
endlocal
