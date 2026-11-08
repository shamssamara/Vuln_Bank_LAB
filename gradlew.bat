@ECHO OFF
REM VulnBank LAB bootstrap; source: gradle\wrapper\BootstrapWrapper.java
SET "APP_HOME=%~dp0"
IF DEFINED JAVA_HOME (
  SET "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) ELSE (
  SET "JAVA_EXE=java.exe"
)
PUSHD "%APP_HOME%"
"%JAVA_EXE%" -cp "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
SET "GRADLE_EXIT=%ERRORLEVEL%"
POPD
EXIT /B %GRADLE_EXIT%
