@echo off
setlocal
set "ATLAS_TARGET=%~1"
if not defined ATLAS_TARGET set /p "ATLAS_TARGET=Paste Minecraft CLIENT instance folder (contains mods): "
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Apply-Atlas.ps1" -MinecraftDirectory "%ATLAS_TARGET%"
pause
