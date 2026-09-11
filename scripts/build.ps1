param([string[]]$Tasks = @('testDebugUnitTest', 'lintDebug', 'assembleDebug'), [string]$TestFilter = '')
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
}
if (-not $env:ANDROID_HOME) {
    $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
}
$gradleOptions = @('--console=plain')
if ($TestFilter) { $gradleOptions += @('--tests', $TestFilter) }
& .\gradlew.bat @Tasks @gradleOptions
exit $LASTEXITCODE
