@echo off
cd /d "%~dp0"
chcp 65001 >nul
cls
set MAVEN_OPTS=--enable-native-access=ALL-UNNAMED

echo Building FastSoftware3D (Core)...
call mvn clean install -DskipTests -q
if %ERRORLEVEL% NEQ 0 ( echo Core build failed. & pause & exit /b %ERRORLEVEL% )

echo Building TileDemo...
call mvn -f examples/TileDemo/pom.xml clean compile dependency:build-classpath -Dmdep.outputFile=cp.txt -DskipTests -q
if %ERRORLEVEL% NEQ 0 ( echo Demo build failed. & pause & exit /b %ERRORLEVEL% )

echo Running TileDemo...
set /p CP=<examples\TileDemo\cp.txt
java --enable-native-access=ALL-UNNAMED -cp "examples\TileDemo\target\classes;%CP%" fastsoftware3d.demo.TileDemo
if %ERRORLEVEL% NEQ 0 ( echo Execution failed. & pause & exit /b %ERRORLEVEL% )
