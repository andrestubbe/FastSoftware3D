@echo off

echo [_BluePrint] Running Demo (via JitPack)...
cd examples
call mvn compile exec:java -Dexec.mainClass=fastsoftware3d.Demo
cd ..
pause
