@echo off
call "%~dp0compilar.bat"
if errorlevel 1 exit /b 1
python "%~dp0demo_local.py" %*
exit /b %errorlevel%
