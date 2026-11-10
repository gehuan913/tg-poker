@echo off
chcp 65001 >nul
title tg-poker

set "JAVA_HOME=D:\jdk21"
set "MVN=C:\Users\HuanGe\.m2\wrapper\dists\apache-maven-3.9.11-bin\6mqf5t809d9geo83kj4ttckcbc\apache-maven-3.9.11\bin\mvn.cmd"
set "PATH=%JAVA_HOME%\bin;%PATH%"

cd /d "%~dp0"

echo ============================================
echo   tg-poker  -  Telegram 德州扑克 Mini App
echo   JDK : %JAVA_HOME%
echo   URL : http://localhost:8080
echo ============================================
echo.

rem 前端产物已被 .gitignore 排除，首次克隆后需要先构建一次
if not exist "src\main\resources\static\index.html" (
  echo 前端产物缺失，正在构建前端...
  pushd web
  call npm install
  call npm run build
  popd
  if errorlevel 1 goto fail
)

echo 正在打包（跳过测试）...
call "%MVN%" -B -DskipTests package
if errorlevel 1 goto fail

echo 启动中，浏览器打开 http://localhost:8080
echo.
"%JAVA_HOME%\bin\java.exe" -jar target\tg-poker.jar --server.port=8080
goto end

:fail
echo 打包失败，请检查上方错误。

:end
pause
