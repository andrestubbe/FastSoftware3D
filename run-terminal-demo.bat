@echo off
cd /d "%~dp0"
chcp 65001 >nul
cls

set MAVEN_OPTS=--enable-native-access=ALL-UNNAMED

echo Building Project...
call mvn clean package dependency:build-classpath -Dmdep.outputFile=cp.txt -DskipTests -q
if %ERRORLEVEL% NEQ 0 ( echo Build failed. & pause & exit /b %ERRORLEVEL% )

echo Running Terminal Demo...
:: Force Windows Console to support 24-bit True Color
reg add HKCU\Console /v VirtualTerminalLevel /t REG_DWORD /d 1 /f >nul 2>&1

set /p CP=<cp.txt
java --enable-native-access=ALL-UNNAMED -cp "target\classes;%CP%" fastsoftware3d.frontend.terminal.Demo3DTerminal
if %ERRORLEVEL% NEQ 0 ( echo Execution failed. & pause & exit /b %ERRORLEVEL% )

pause
