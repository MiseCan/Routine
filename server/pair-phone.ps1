param([string]$Serial='')
$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
if(!(Test-Path -LiteralPath 'connection.json')) {throw '请先运行 start.ps1 启动 AI 服务。'}
if(!$Serial) {
    $deviceLines=@(& adb devices | Select-String '^\S+\s+device$')
    if($deviceLines.Count -ne 1) {throw '请连接并授权一台手机，或通过 -Serial 指定设备。'}
    $Serial=($deviceLines[0].ToString() -split '\s+')[0]
}
function Run-Adb([string[]]$Arguments) {
    & adb -s $Serial @Arguments
    if($LASTEXITCODE -ne 0) {throw '手机连接操作未完成。'}
}
Run-Adb @('shell','am','force-stop','com.daystep.app')
Run-Adb @('reverse','tcp:8787','tcp:8787')
Run-Adb @('push',(Join-Path $PSScriptRoot 'connection.json'),'/data/local/tmp/daystep-connection.json')
Run-Adb @('shell','run-as','com.daystep.app','mkdir','-p','files')
Run-Adb @('shell','run-as','com.daystep.app','cp','/data/local/tmp/daystep-connection.json','files/connection.json')
Run-Adb @('shell','rm','/data/local/tmp/daystep-connection.json')
Run-Adb @('shell','am','start','-W','-n','com.daystep.app/.MainActivity','--ez','open_settings','true')
Write-Output '手机已连接 AI 服务。请在设置的“模型与密钥”中填写 API 密钥。'
