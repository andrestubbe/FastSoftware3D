@echo off
cd /d "%~dp0"
chcp 65001 >nul
cls

set MAVEN_OPTS=--enable-native-access=ALL-UNNAMED

echo Building Project...
call mvn clean package dependency:build-classpath -Dmdep.outputFile=cp.txt -DskipTests -q
if %ERRORLEVEL% NEQ 0 ( echo Build failed. & pause & exit /b %ERRORLEVEL% )

echo Running Wolf Desktop Demo...
set /p CP=<cp.txt
java --enable-native-access=ALL-UNNAMED -cp "target\classes;%CP%" fastsoftware3d.frontend.desktop.DemoWolf
if %ERRORLEVEL% NEQ 0 ( echo Execution failed. & pause & exit /b %ERRORLEVEL% )
