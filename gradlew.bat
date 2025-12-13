@echo off
setlocal

set "APP_HOME=%~dp0"
set "WRAPPER_JAR=%APP_HOME%gradle\\wrapper\\gradle-wrapper.jar"

if not exist "%WRAPPER_JAR%" (
  echo Missing Gradle wrapper jar: %WRAPPER_JAR%
  exit /b 1
)

if defined JAVA_HOME (
  set "JAVA_EXE=%JAVA_HOME%\\bin\\java.exe"
) else (
  set "JAVA_EXE=java"
)

"%JAVA_EXE%" -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
