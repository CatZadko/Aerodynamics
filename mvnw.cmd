@echo off
setlocal
set "TASK_JAVA=java.exe"
if defined JAVA_HOME set "TASK_JAVA=%JAVA_HOME%\bin\java.exe"
"%TASK_JAVA%" "-Dmaven.multiModuleProjectDirectory=%~dp0." -classpath "%~dp0.mvn\wrapper\maven-wrapper.jar" org.apache.maven.wrapper.MavenWrapperMain %*
exit /b %ERRORLEVEL%
