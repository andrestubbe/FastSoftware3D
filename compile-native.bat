@echo off
set PROJECT_NAME=fastsoftware3d
set JAVA_PATH=C:\Program Files\Java\jdk-25.0.3
set VS_PATH=C:\Program Files\Microsoft Visual Studio\18\Community

echo ===========================================
echo Fast3D Native Builder (v0.1.0)
echo ===========================================

if not exist build mkdir build
if not exist src\main\resources\native mkdir src\main\resources\native

echo.
echo Using JDK: %JAVA_PATH%
echo Using VS:  %VS_PATH%

call "%VS_PATH%\VC\Auxiliary\Build\vcvars64.bat"

echo.
echo Compiling C++ Native Library...
cl.exe /LD /Fe:src\main\resources\native\%PROJECT_NAME%.dll native\fastsoftware3d.cpp /I"%JAVA_PATH%\include" /I"%JAVA_PATH%\include\win32" /EHsc /std:c++17 /link /SUBSYSTEM:WINDOWS user32.lib gdi32.lib

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ===========================================
    echo BUILD SUCCESSFUL 
    echo DLL Location: src\main\resources\native\%PROJECT_NAME%.dll
    echo ===========================================
) else (
    echo.
    echo !!!!!!!!! BUILD FAILED !!!!!!!!!
    pause
)
