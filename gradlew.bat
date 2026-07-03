@echo off
REM Lightweight wrapper runner for Windows: executes gradle-wrapper.jar if present,
REM otherwise instructs how to generate the wrapper locally.
set SCRIPT_DIR=%~dp0
set JAR_PATH=%SCRIPT_DIR%gradle\wrapper\gradle-wrapper.jar
if exist "%JAR_PATH%" (
  java -jar "%JAR_PATH%" %*
) else (
  echo Gradle wrapper JAR not found. Run install-wrapper.sh to generate wrapper (requires local Gradle).
  exit /b 1
)
