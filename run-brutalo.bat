@echo off
cd /d "%~dp0"
chcp 65001 >nul
cls
set MAVEN_OPTS=--enable-native-access=ALL-UNNAMED
echo Building Project...
call mvn -f examples/Brutalo/pom.xml clean compile dependency:build-classpath -Dmdep.outputFile=cp.txt -DskipTests -q
if %ERRORLEVEL% NEQ 0 ( echo Build failed. & pause & exit /b %ERRORLEVEL% )
echo Running Brutalo...
set /p CP=<examples\Brutalo\cp.txt
java --enable-native-access=ALL-UNNAMED -cp "examples\Brutalo\target\classes;%CP%" fastsoftware3d.demo.Brutalo
if %ERRORLEVEL% NEQ 0 ( echo Execution failed. & pause & exit /b %ERRORLEVEL% )
