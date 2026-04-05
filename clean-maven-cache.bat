@echo off
REM Maven Cache Cleanup Script for Windows
REM Fixes corrupted JAR files and failed downloads in Maven local repository

setlocal enabledelayedexpansion

echo ==========================================
echo Maven Cache Cleanup Script
echo ==========================================
echo.

REM Detect Maven local repository location
if defined M2_HOME (
    set "MAVEN_REPO=%M2_HOME%\repository"
) else if exist "%USERPROFILE%\.m2\repository" (
    set "MAVEN_REPO=%USERPROFILE%\.m2\repository"
) else (
    echo Error: Could not locate Maven local repository
    echo Expected location: %USERPROFILE%\.m2\repository
    exit /b 1
)

echo Maven repository: %MAVEN_REPO%
echo.

REM Check for specific corrupted dependency
set "CHATCOLOR_PATH=%MAVEN_REPO%\me\dave\ChatColorHandler"
if exist "%CHATCOLOR_PATH%" (
    echo WARNING: Found ChatColorHandler dependency
    echo   Path: %CHATCOLOR_PATH%
    echo   This dependency is NOT required by the Wpets project!
    echo   It may be corrupted and will be removed.
    echo.
)

REM Prompt for confirmation
set /p CONFIRM="Proceed with cleanup? [y/N] "
if /i not "%CONFIRM%"=="y" (
    echo Cleanup cancelled.
    exit /b 0
)

echo.
echo Starting cleanup...
echo.

REM Remove failed download markers using PowerShell
echo [1/3] Removing *.lastUpdated files...
powershell -NoProfile -Command "Get-ChildItem -Path '%MAVEN_REPO%' -Recurse -Filter '*.lastUpdated' | Remove-Item -Force"
echo   Completed

echo [2/3] Removing resolver-status.properties files...
powershell -NoProfile -Command "Get-ChildItem -Path '%MAVEN_REPO%' -Recurse -Filter 'resolver-status.properties' | Remove-Item -Force"
echo   Completed

REM Remove corrupted ChatColorHandler if exists
if exist "%CHATCOLOR_PATH%" (
    echo [3/3] Removing corrupted ChatColorHandler dependency...
    rmdir /s /q "%CHATCOLOR_PATH%"
    echo   Removed: %CHATCOLOR_PATH%
) else (
    echo [3/3] No ChatColorHandler dependency to remove
)

echo.
echo ==========================================
echo Cleanup Complete!
echo ==========================================
echo.
echo Next steps:
echo   1. Run: mvn clean compile
echo   2. Maven will re-download any missing dependencies
echo   3. Build should now succeed
echo.

pause
