param([switch]$Offline, [switch]$Check)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
# Honor a configured JDK/SDK; cached paths are optional local fallbacks.
if (!$env:JAVA_HOME) {
    $cachedJdk = Join-Path $env:USERPROFILE '.gradle/jdks/eclipse_adoptium-25-amd64-windows.2'
    if (Test-Path -LiteralPath $cachedJdk) { $env:JAVA_HOME = $cachedJdk }
}
if (!$env:ANDROID_HOME) {
    $localSdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk'
    if (Test-Path -LiteralPath $localSdk) { $env:ANDROID_HOME = $localSdk }
}
$buildArguments = @('--no-daemon')
if ($Offline) { $buildArguments += '--offline' }
$buildArguments += ':app:assembleDebug'
if ($Check) { $buildArguments += @(':app:testDebugUnitTest', ':app:lintDebug') }
& (Join-Path $PSScriptRoot 'gradlew.bat') @buildArguments
if ($LASTEXITCODE -ne 0) { throw 'Android 构建未通过。' }
