@echo off
cd /d "%~dp0"
chcp 65001 >nul
cls

set MAVEN_OPTS=--enable-native-access=ALL-UNNAMED

echo Building Project...
call mvn -f examples/Demo/pom.xml clean compile dependency:build-classpath -Dmdep.outputFile=cp.txt -DskipTests -q
if %ERRORLEVEL% NEQ 0 ( echo Build failed. & pause & exit /b %ERRORLEVEL% )

echo Running 3D Desktop Demo...
set /p CP=<examples\Demo\cp.txt
java --enable-native-access=ALL-UNNAMED -cp "examples\Demo\target\classes;%CP%" fastsoftware3d.demo.Demo3D
if %ERRORLEVEL% NEQ 0 ( echo Execution failed. & pause & exit /b %ERRORLEVEL% )
