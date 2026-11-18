@echo off
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0"

rem ============================================================
rem  Z-Tweaks 实机验证启动器
rem  默认：构建 -> 启动 Forge 开发客户端 -> 退出后扫日志
rem  --skip-build  跳过构建，直接启动
rem  --check       只检查依赖与目录，不启动游戏
rem ============================================================

set "TACZ_JAR=libs\tacz-1.20.1-1.1.8-hotfix.jar"
set "SKIP_BUILD="
set "CHECK_ONLY="

:parse_args
if "%~1"=="" goto prereq
if /i "%~1"=="--skip-build" (
    set "SKIP_BUILD=1"
    shift
    goto parse_args
)
if /i "%~1"=="--check" (
    set "CHECK_ONLY=1"
    shift
    goto parse_args
)
echo [!] 未知参数：%~1
echo     可用：--skip-build  --check
exit /b 2

:prereq
if not exist "gradlew.bat" (
    echo [x] 当前目录没有 gradlew.bat，请把本文件放在仓库根目录。
    exit /b 1
)
if not exist "%TACZ_JAR%" (
    echo [x] 缺少 TACZ 本体：%TACZ_JAR%
    echo     把 TACZ 1.20.1-1.1.8-hotfix 的 jar 放进 libs\ 后再试。
    exit /b 1
)
echo [ok] 仓库根目录：%CD%
echo [ok] TACZ 本体：%TACZ_JAR%

if defined CHECK_ONLY (
    echo [ok] 检查通过（--check 不启动游戏）。
    exit /b 0
)

if defined SKIP_BUILD goto launch
echo.
echo [..] gradlew build
call gradlew.bat build --console=plain
if errorlevel 1 (
    echo [x] 构建失败，未启动客户端。
    exit /b 1
)
echo [ok] 构建通过

:launch
echo.
echo [i] 进游戏后重点看：
echo     1. 四套主题各切一遍：面板高光/浮雕、按钮受光面、弹层投影
echo     2. 候选列表选中行的强调色渐变、当前槽位的渐变
echo     3. 详情条三栏之间的软引导线（只到按钮上方）
echo     4. 低分辨率（480x270）下分栏线是否太抢眼
echo     dev 环境装不了 TACZ Addon / GunsmithLib；验「随意配件」请用正式实例
echo.
echo [..] gradlew runClient（关闭游戏窗口后本窗口才会继续）
call gradlew.bat runClient --console=plain
set "GAME_EXIT=%ERRORLEVEL%"

echo.
if exist "run\logs\latest.log" (
    findstr /c:"at com.ztweaks" "run\logs\latest.log" >nul 2>&1
    if errorlevel 1 (
        echo [ok] 日志里没有 com.ztweaks 的异常栈
    ) else (
        echo [!] 日志里有 com.ztweaks 的异常栈：
        findstr /n /c:"at com.ztweaks" "run\logs\latest.log"
    )
) else (
    echo [!] 没找到 run\logs\latest.log，跳过日志检查
)
if exist "run\screenshots" echo [i] 截图目录：run\screenshots

echo.
echo [i] 客户端退出码：%GAME_EXIT%
pause
exit /b %GAME_EXIT%
