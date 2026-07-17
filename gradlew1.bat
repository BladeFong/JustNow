@echo off
setlocal
:: 将当前目录转换为 WSL 路径（/mnt/...）
for /f "usebackq" %%i in (`wsl wslpath "%cd%"`) do set WSL_PATH=%%i
:: 调用 WSL 内的 gradlew，并传递所有参数
wsl cd %WSL_PATH% && ./gradlew %*