@echo off
setlocal
if "%CATALINA_HOME%"=="" (echo ERROR: Set CATALINA_HOME to your Tomcat folder first.& exit /b 1)
if not exist "%CATALINA_HOME%\lib\servlet-api.jar" (echo ERROR: CATALINA_HOME does not look like Tomcat.& exit /b 1)
if not exist "WEB-INF\lib\mysql-connector-j.jar" (echo ERROR: Add Connector/J at WEB-INF\lib\mysql-connector-j.jar.& exit /b 1)
if not exist "WEB-INF\classes" mkdir "WEB-INF\classes"
echo Compiling Java source with javac...
javac -encoding UTF-8 -cp "%CATALINA_HOME%\lib\servlet-api.jar" -d "WEB-INF\classes" src\com\canteen\CanteenServlet.java
if errorlevel 1 (echo ERROR: Compilation failed.& exit /b 1)
set "DEST=%CATALINA_HOME%\webapps\CanteenApp"
if not exist "%DEST%" mkdir "%DEST%"
echo Copying app files to %DEST% ...
robocopy "%CD%" "%DEST%" /E /R:2 /W:1 /XD ".git" "src" /XF "start-windows.bat" "start-linux.sh"
if errorlevel 8 (echo ERROR: Copy failed.& exit /b 1)
echo Done. Ensure DB_USER/DB_PASSWORD are configured, then start or restart Tomcat.
echo Open http://localhost:8080/CanteenApp/
endlocal
