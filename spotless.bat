@echo off
setlocal enabledelayedexpansion

REM
REM Script that runs spotlessApply for a single file.
REM In case no file path is provided, the formatting is applied to the whole project.
REM
REM Advantage over calling Gradle for a single file directly is identifying a subproject and only running the task there.
REM Also, Gradle is only run for certain file extensions (in case this can't bbe filtered easily when calling the script)
REM
REM We can't avoid the Gradle configuration phase which takes relatively long due to the big number of subprojects,
REM But when targeting individual projects we can speed up the process using the on-demand configuration feature
REM (see https://docs.gradle.org/current/userguide/multi_project_configuration_and_execution.html).
REM
REM Configuration as file watcher in IntelliJ:
REM
REM 1. Install File Watchers plugin
REM 2. Settings -> Tools -> File Watchers -> Add
REM
REM Program:                 $ProjectFileDir$/spotless.bat
REM Arguments:               $FilePath$
REM Output paths to refresh: $FilePath$
REM Working directory:       $ProjectFileDir$
REM
REM Disable auto save on editing running the tool while editing
REM

REM Allowed file extensions
set allowed_exts=java md groovy gradle kt scala sc

REM Check if a file path is provided
if "%~1"=="" (
    echo No file path provided
    call gradlew spotlessApply --parallel
    exit /b 0
)

REM Get file extension
for %%F in ("%~1") do set "file_ext=%%~xF"
set "file_ext=!file_ext:~1!"

REM Check if extension is allowed
set "allowed=no"
for %%E in (%allowed_exts%) do (
    if /I "!file_ext!"=="%%E" set "allowed=yes"
)
if "!allowed!"=="no" (
    echo Error: The file extension .!file_ext! is not allowed.
    exit /b 0
)

REM Get absolute path of the file
for /f "delims=" %%A in ('powershell -NoProfile -Command "Resolve-Path -LiteralPath \"%~1\" | Select-Object -ExpandProperty Path"') do set "file_path=%%A"

REM Get script directory
set "script_dir=%~dp0"
REM Remove trailing slash to allow comparison with current_dir later
set "script_dir=!script_dir:~0,-1!"

REM Start from file's directory
for %%A in ("!file_path!") do set "current_dir=%%~dpA"

:find_build_gradle
if exist "!current_dir!\build.gradle" (
    set "build_file=build.gradle"
) else if exist "!current_dir!\build.gradle.kts" (
    set "build_file=build.gradle.kts"
) else (
    set "build_file="
)

if defined build_file (
    REM Get relative path from script_dir to current_dir
    for /f "delims=" %%R in ('powershell -NoProfile -Command "Resolve-Path -Relative -Path \"!current_dir!\""') do set "relative_path=%%R"
    REM Remove leading .\ from relative path output
    set "relative_path=!relative_path:~2!"
    if "!current_dir!"=="!script_dir!" (
        echo Found !build_file! in the project root: !relative_path!
        call gradlew spotlessApply -PspotlessIdeHook="!file_path!" --parallel --configure-on-demand
    ) else (
        set "formatted_path=!relative_path:\=:!"
        echo Found !build_file! in a subfolder: !formatted_path!
        call gradlew :!formatted_path!:spotlessApply -PspotlessIdeHook="!file_path!" --parallel --configure-on-demand
    )
    exit /b 0
)

REM Stop if we reach the script's directory
if /I "!current_dir!"=="!script_dir!" goto not_found

REM Move up one directory
for %%A in ("!current_dir!") do set "current_dir=%%~dpA"
set "current_dir=!current_dir:~0,-1!"
goto find_build_gradle

:not_found
echo No build.gradle or build.gradle.kts found within the script's directory.
exit /b 1
