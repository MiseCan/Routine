$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$nodePath = (Get-Command node -ErrorAction Stop).Source
$tokenFile=Join-Path $PSScriptRoot '.client-token'
if(Test-Path -LiteralPath $tokenFile) {
    try {
        $accessCode=[IO.File]::ReadAllText($tokenFile).Trim()
        $health=Invoke-RestMethod -Uri 'http://127.0.0.1:8787/health' -Headers @{Authorization=('Bearer '+$accessCode)} -TimeoutSec 2
        Write-Output ('服务已运行；服务商：'+$health.provider+'；密钥已配置：'+$health.configured)
        exit 0
    } catch {}
}
$serverProcess=Start-Process -FilePath $nodePath -ArgumentList @('server.mjs') -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $PSScriptRoot 'service.log') -RedirectStandardError (Join-Path $PSScriptRoot 'service-error.log') -PassThru
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'service.pid'),$serverProcess.Id.ToString())
Write-Output ('AI 服务已启动，进程 '+$serverProcess.Id+'。API 密钥可以在手机设置中填写。')
