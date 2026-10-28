@echo off
setlocal
cd /d "%~dp0.."
call scripts\compilar.bat || exit /b 1
set "JAVA=java"
if defined JAVA_HOME set "JAVA=%JAVA_HOME%\bin\java.exe"
for %%P in (PruebaDataset PruebaNodosDisponibles PruebaCorrelaciones PruebaTendencias PruebaIntegracion PruebaAnalisis) do (
    echo == %%P
    "%JAVA%" -Xmx256m -cp build %%P > "build\%%P.log" 2>&1
    if errorlevel 1 (
        set "FALLIDA=%%P"
        goto fallo
    )
    findstr /v /r /c:"^[A-H],INICIO," /c:"^[A-H],FIN," /c:"^[A-H],ERROR," "build\%%P.log"
)
echo Pruebas P1: OK
exit /b 0

:fallo
type "build\%FALLIDA%.log"
echo FALLO: %FALLIDA%
exit /b 1
