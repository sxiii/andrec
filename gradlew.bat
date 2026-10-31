@rem Gradle startup script for Windows
@if "%DEBUG%"=="" @echo off
set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%DIRNAME%") do set APP_HOME=%%~fi
set APP_NAME="Gradle"
set APP_BASE_NAME=%~n0
set DEFAULT_JVM_OPTS="--add-opens" "java.base/java.util=ALL-UNNAMED" "--add-opens" "java.base/java.lang=ALL-UNNAMED"
set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome
set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute
goto fail
:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe
if exist "%JAVA_EXE%" goto execute
goto fail
:execute
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd
:fail
exit /b 1
:mainEnd
endlocal & set ERROR_CODE=%ERRORLEVEL%
exit /b %ERROR_CODE%
