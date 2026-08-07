<#
.SYNOPSIS
Increments the patch/build component of gradle.properties' version, then
creates the IntelliJ plugin ZIP under build/distributions/ and archives it.
#>

$ErrorActionPreference = "Stop"

# cd "$(dirname "$0")"
Set-Location -Path $PSScriptRoot

$propertiesFile = "gradle.properties"

if (-not (Test-Path -Path $propertiesFile)) {
    Write-Error "Cannot find $propertiesFile"
    exit 1
}

# Parse and increment the version safely
$lines = Get-Content -Path $propertiesFile
$updated = $false
$nextVersion = ""

for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match '^\s*version\s*=\s*(.*)') {
        $currentVersion = $Matches[1].Trim()

        if ($currentVersion -match '^(\d+)\.(\d+)\.(\d+)$') {
            $major = [int]$Matches[1]
            $minor = [int]$Matches[2]
            $build = [int]$Matches[3]
            $nextVersion = "$major.$minor.$($build + 1)"

            $lines[$i] = "version = $nextVersion"
            $updated = $true
            break
        } else {
            Write-Error "Expected a numeric major.minor.build version in $propertiesFile; found: $currentVersion"
            exit 1
        }
    }
}

if (-not $updated) {
    Write-Error "Expected a numeric major.minor.build version in $propertiesFile; found: <none>"
    exit 1
}

# Write the updated version back to gradle.properties
$lines | Set-Content -Path $propertiesFile

$distributionDirectory = "build/distributions"
if (Test-Path -Path $distributionDirectory) {
    Get-ChildItem -Path $distributionDirectory -Filter "*.zip" -File | Remove-Item -Force
}

Write-Host "Building CreditPincher version $nextVersion..."

# Run gradlew natively (checking for .bat on Windows vs .sh/Unix)
$gradlew = if (Test-Path ".\gradlew.bat") { ".\gradlew.bat" } else { ".\gradlew" }
& $gradlew buildPlugin

if ($LASTEXITCODE -ne 0) {
    Write-Error "Gradle build failed."
    exit $LASTEXITCODE
}

$builtZip = Join-Path -Path $distributionDirectory -ChildPath "CreditPincher-$nextVersion.zip"
Write-Host "Plugin built: $builtZip"

$releasesDir = "releases"

if (-not (Test-Path -Path $builtZip -PathType Leaf)) {
    Write-Error "Error: Expected plugin archive not found at $builtZip"
    exit 1
}

if (-not (Test-Path -Path $releasesDir)) {
    New-Item -ItemType Directory -Path $releasesDir -Force | Out-Null
}

Copy-Item -Path $builtZip -Destination $releasesDir -Force
Write-Host "Successfully archived to $releasesDir\"

$releaseZip = Join-Path -Path $releasesDir -ChildPath "CreditPincher-$nextVersion.zip"

git add $propertiesFile $releaseZip
if ($LASTEXITCODE -ne 0) {
    Write-Error "Error: Failed to stage files for commit"
    exit 1
}

git commit -m "Release version $nextVersion"
if ($LASTEXITCODE -ne 0) {
    Write-Error "Error: Failed to commit release"
    exit 1
}
