@echo off
setlocal
set "DIST=%USERPROFILE%\.gradle\darkman-distributions\gradle-8.7"
if not exist "%DIST%\bin\gradle.bat" (
  echo Download Gradle 8.7 from https://services.gradle.org/distributions/gradle-8.7-bin.zip and extract it to %DIST%
  exit /b 1
)
call "%DIST%\bin\gradle.bat" %*
