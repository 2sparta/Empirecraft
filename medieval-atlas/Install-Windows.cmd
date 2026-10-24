@echo off
setlocal EnableExtensions DisableDelayedExpansion
set "ATLAS_TARGET=%~1"
if not defined ATLAS_TARGET set /p "ATLAS_TARGET=Paste Minecraft CLIENT instance folder (contains mods): "
if not defined ATLAS_TARGET goto no_target
set "ATLAS_TARGET=%ATLAS_TARGET:"=%"
if not defined ATLAS_TARGET goto no_target
if not exist "%~dp0Apply-Atlas.ps1" goto missing_script

rem Find an installed engine without requiring PowerShell or where.exe in PATH.
rem Sysnative also supports a 32-bit launcher on 64-bit Windows.
set "ATLAS_POWERSHELL="
if exist "%SystemRoot%\Sysnative\WindowsPowerShell\v1.0\powershell.exe" set "ATLAS_POWERSHELL=%SystemRoot%\Sysnative\WindowsPowerShell\v1.0\powershell.exe"
if not defined ATLAS_POWERSHELL if exist "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" set "ATLAS_POWERSHELL=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
for %%P in ("%ProgramW6432%" "%ProgramFiles%" "%ProgramFiles(x86)%") do if not "%%~P"=="" if not defined ATLAS_POWERSHELL if exist "%%~P\PowerShell\7\pwsh.exe" set "ATLAS_POWERSHELL=%%~P\PowerShell\7\pwsh.exe"
if not defined ATLAS_POWERSHELL if defined LocalAppData if exist "%LocalAppData%\Microsoft\WindowsApps\pwsh.exe" set "ATLAS_POWERSHELL=%LocalAppData%\Microsoft\WindowsApps\pwsh.exe"
for %%I in (powershell.exe pwsh.exe) do if not defined ATLAS_POWERSHELL if not "%%~$PATH:I"=="" set "ATLAS_POWERSHELL=%%~$PATH:I"
if not defined ATLAS_POWERSHELL goto no_powershell

"%ATLAS_POWERSHELL%" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0Apply-Atlas.ps1" -MinecraftDirectory "%ATLAS_TARGET%"
set "ATLAS_RESULT=%ERRORLEVEL%"
if not "%ATLAS_RESULT%"=="0" echo Installation failed. Error code: %ATLAS_RESULT%
pause
exit /b %ATLAS_RESULT%

:no_target
echo No Minecraft client folder was entered.
pause
exit /b 2

:missing_script
echo Apply-Atlas.ps1 is missing. Extract the complete archive before running this file.
pause
exit /b 2

:no_powershell
echo Windows PowerShell and PowerShell 7 were not found.
echo Install PowerShell 7 from https://aka.ms/powershell-release?tag=stable
echo Alternatively, use the manual installation steps in README.md.
pause
exit /b 3
