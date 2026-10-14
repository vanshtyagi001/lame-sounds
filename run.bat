@echo off
set "DOTNET_ROOT=%USERPROFILE%\.dotnet"
set "PATH=%USERPROFILE%\.dotnet;%PATH%"

if exist "%~dp0src\LameSounds.App\bin\Release\net9.0-windows\LameSounds.App.exe" (
    echo Launching LameSounds Desktop App...
    start "" "%~dp0src\LameSounds.App\bin\Release\net9.0-windows\LameSounds.App.exe"
    exit /b 0
)

if exist "%~dp0publish\standalone\LameSounds.App.exe" (
    echo Launching LameSounds Standalone App...
    start "" "%~dp0publish\standalone\LameSounds.App.exe"
    exit /b 0
)

if exist "%~dp0publish\single-file\LameSounds.App.exe" (
    echo Launching LameSounds App...
    start "" "%~dp0publish\single-file\LameSounds.App.exe"
    exit /b 0
)

if exist "%~dp0src\LameSounds.App\bin\Debug\net9.0-windows\LameSounds.App.exe" (
    echo Launching LameSounds Desktop App...
    start "" "%~dp0src\LameSounds.App\bin\Debug\net9.0-windows\LameSounds.App.exe"
    exit /b 0
)

echo Building and running LameSounds...
dotnet run --project "%~dp0src\LameSounds.App"
