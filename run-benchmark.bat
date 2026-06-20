@echo off
chcp 65001 >nul

    echo.
    echo âŒ Maven build failed.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo Running Benchmark...
cd examples\Benchmark
call mvn compile exec:java
cd ..\..
pause
