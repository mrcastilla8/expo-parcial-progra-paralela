@echo off
setlocal
cd /d "%~dp0.."
if not exist build mkdir build
set "JAVAC=javac"
if defined JAVA_HOME set "JAVAC=%JAVA_HOME%\bin\javac.exe"
"%JAVAC%" --release 17 -encoding UTF-8 -d build src\*.java
exit /b %errorlevel%
