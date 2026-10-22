@echo off
setlocal
cd /d "%~dp0.."
set "JAVA=java"
if defined JAVA_HOME set "JAVA=%JAVA_HOME%\bin\java.exe"
"%JAVA%" -cp build Principal %*
exit /b %errorlevel%
