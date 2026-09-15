@echo off
cd /d "%~dp0"
call mvnw.cmd -pl rest spring-boot:run
pause
