@echo off
cd /d "%~dp0"
chcp 65001 >nul
cls

set MAVEN_OPTS=--enable-native-access=ALL-UNNAMED

echo Building Project...
call mvn -f examples/Demo/pom.xml clean compile dependency:build-classpath -Dmdep.outputFile=cp.txt -DskipTests -q
if %ERRORLEVEL% NEQ 0 ( echo Build failed. & pause & exit /b %ERRORLEVEL% )

echo Running Reflection Demo...
for /f "usebackq delims=" %%i in ("examples\Demo\cp.txt") do set CP=%%i
java --enable-native-access=ALL-UNNAMED -cp "examples\Demo\target\classes;%CP%" fastsoftware3d.demo.DemoReflection
if %ERRORLEVEL% NEQ 0 ( echo Execution failed. & pause & exit /b %ERRORLEVEL% )

pause
