@echo off
setlocal
cd /d "%~dp0.."
call scripts\compilar.bat || exit /b 1
set "JAVA=java"
if defined JAVA_HOME set "JAVA=%JAVA_HOME%\bin\java.exe"
for %%P in (PruebaBalanceador PruebaTransferencia PruebaInterrupcion PruebaCluster) do (
    echo == %%P
    "%JAVA%" -cp build %%P > "build\%%P.log" 2>&1
    if errorlevel 1 (
        set "FALLIDA=%%P"
        goto fallo
    )
    type "build\%%P.log"
)
echo Pruebas P2: OK
exit /b 0

:fallo
type "build\%FALLIDA%.log"
echo FALLO: %FALLIDA%
exit /b 1
