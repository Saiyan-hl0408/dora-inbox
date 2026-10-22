param(
    [string]$SdkPath = $env:ANDROID_HOME,
    [string]$JdkPath = $env:JAVA_HOME,
    [string]$GradleExecutable,
    [string]$SocketPath,
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
if (-not $SdkPath) { $SdkPath = $env:ANDROID_SDK_ROOT }
if (-not $SdkPath -or -not (Test-Path -LiteralPath $SdkPath)) { throw 'Set ANDROID_HOME or pass -SdkPath (Android SDK with platform 36/build-tools 36.0.0).' }
if (-not $JdkPath -or -not (Test-Path -LiteralPath (Join-Path $JdkPath 'bin/java.exe'))) { throw 'Set JAVA_HOME or pass -JdkPath (JDK 17 or 21).' }
$taskEnvNames = @('JAVA_HOME','ANDROID_HOME','PATH','TEMP','TMP','JAVA_TOOL_OPTIONS')
$taskOldEnv = @{}
foreach ($taskName in $taskEnvNames) { $taskOldEnv[$taskName] = [Environment]::GetEnvironmentVariable($taskName,'Process') }
try {
    $env:JAVA_HOME = $JdkPath
    $env:ANDROID_HOME = $SdkPath
    $env:PATH = (Join-Path $JdkPath 'bin') + ';' + $env:PATH
    # A short user-local directory avoids Windows Unix-domain socket path failures.
    $taskSocketPath = if ($SocketPath) { $SocketPath } else { Join-Path ([IO.Path]::GetTempPath()) 'di' }
    New-Item -ItemType Directory -Path $taskSocketPath -Force | Out-Null
    $env:TEMP = $taskSocketPath
    $env:TMP = $taskSocketPath
    $env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir="' + $taskSocketPath + '"'
    if (-not $GradleExecutable) { $GradleExecutable = Join-Path $taskRoot 'gradlew.bat' }
    $taskArgs = @('--no-daemon','--console=plain',':app:assembleDebug',':app:testDebugUnitTest')
    if ($Offline) { $taskArgs += '--offline' }
    Push-Location $taskRoot
    try {
        & $GradleExecutable @taskArgs
        if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
    } finally { Pop-Location }
} finally {
    foreach ($taskName in $taskEnvNames) { [Environment]::SetEnvironmentVariable($taskName,$taskOldEnv[$taskName],'Process') }
}
