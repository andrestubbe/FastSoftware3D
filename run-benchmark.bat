@echo off
cd /d "%~dp0"
chcp 65001 >nul
cls
set MAVEN_OPTS=--enable-native-access=ALL-UNNAMED
echo Building Project...
call mvn -f examples/BenchmarkScene/pom.xml clean compile dependency:build-classpath -Dmdep.outputFile=cp.txt -DskipTests -q
if %ERRORLEVEL% NEQ 0 ( echo Build failed. & pause & exit /b %ERRORLEVEL% )
echo Running BenchmarkScene...
set /p CP=<examples\BenchmarkScene\cp.txt
java --enable-native-access=ALL-UNNAMED -cp "examples\BenchmarkScene\target\classes;%CP%" fastsoftware3d.demo.BenchmarkScene
if %ERRORLEVEL% NEQ 0 ( echo Execution failed. & pause & exit /b %ERRORLEVEL% )
