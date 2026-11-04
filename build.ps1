param([string]$JdkPath)
$ErrorActionPreference = 'Stop'
$candidates = @()
if ($JdkPath) { $candidates += $JdkPath }
if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
$roots = @("$env:ProgramFiles\Java", "$env:ProgramFiles\Microsoft", "$env:ProgramFiles\Eclipse Adoptium",
           "$env:ProgramFiles\Zulu", "$env:ProgramFiles\BellSoft", "$env:USERPROFILE\.jdks",
           "$env:LOCALAPPDATA\Programs\Eclipse Adoptium")
foreach ($root in $roots) {
    if (Test-Path $root) { $candidates += Get-ChildItem $root -Directory | ForEach-Object { $_.FullName } }
}
$jdk = $candidates | Select-Object -Unique | Where-Object {
    (Test-Path (Join-Path $_ 'bin\javac.exe')) -and
    (Test-Path (Join-Path $_ 'release')) -and
    ((Get-Content (Join-Path $_ 'release')) -match '^JAVA_VERSION="25([.+-]|\")')
} | Select-Object -First 1
if (-not $jdk) { throw 'JDK 25 not found. Use: .\build.ps1 -JdkPath "C:\path\to\jdk-25"' }
$previousJava = $env:JAVA_HOME
Push-Location $PSScriptRoot
try {
    $env:JAVA_HOME = $jdk
    Write-Host "Using JDK: $jdk"
    & .\mvnw.cmd clean verify
    if ($LASTEXITCODE -ne 0) { throw "Build failed, exit code $LASTEXITCODE" }
    Write-Host 'Ready: target\Aerodynamics-1.0.0.jar'
} finally {
    $env:JAVA_HOME = $previousJava
    Pop-Location
}
