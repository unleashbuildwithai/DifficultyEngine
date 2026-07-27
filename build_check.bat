@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"
set CP=cache\mojang_26.2.jar
for /f "delims=" %%i in (cp_list.txt) do set CP=!CP!;%%i
if exist out_check rmdir /s /q out_check
mkdir out_check
javac -encoding UTF-8 -Xlint:none -d out_check -cp "!CP!" @sources.txt > compile_check.txt 2>&1
echo EXITCODE=%errorlevel%
