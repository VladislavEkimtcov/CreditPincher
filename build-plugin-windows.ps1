<#
.SYNOPSIS
Increments the patch/build component of gradle.properties' version, builds the
IntelliJ plugin ZIP under build/distributions/, archives a copy under releases/
and commits both. If anything fails the version bump is rolled back, so a failed
run does not leave gradle.properties pointing at a version that was never built.
#>

$ErrorActionPreference = "Stop"

Set-Location -Path $PSScriptRoot

$pluginName = "CreditPincher"
$propertiesFile = "gradle.properties"
$distributionDirectory = "build/distributions"
$releasesDir = "releases"

if (-not (Test-Path -Path $propertiesFile)) {
    Write-Error "Cannot find $propertiesFile"
    exit 1
}

# Parse and increment the version safely
$originalLines = Get-Content -Path $propertiesFile
$lines = $originalLines.Clone()
$updated = $false
$currentVersion = ""
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

$releaseCommitted = $false

try {
    if (Test-Path -Path $distributionDirectory) {
        Get-ChildItem -Path $distributionDirectory -Filter "*.zip" -File | Remove-Item -Force
    }

    Write-Host "Building $pluginName version $nextVersion..."

    # Run gradlew natively (checking for .bat on Windows vs .sh/Unix)
    $gradlew = if (Test-Path ".\gradlew.bat") { ".\gradlew.bat" } else { ".\gradlew" }
    & $gradlew buildPlugin

    if ($LASTEXITCODE -ne 0) {
        Write-Error "Gradle build failed."
    }

    $builtZip = Join-Path -Path $distributionDirectory -ChildPath "$pluginName-$nextVersion.zip"

    if (-not (Test-Path -Path $builtZip -PathType Leaf)) {
        Write-Host "Archives actually produced:"
        Get-ChildItem -Path $distributionDirectory -Filter "*.zip" -File -ErrorAction SilentlyContinue |
            ForEach-Object { Write-Host "  $($_.FullName)" }
        Write-Error "Expected plugin archive not found at $builtZip"
    }

    Write-Host "Plugin built: $builtZip"

    if (-not (Test-Path -Path $releasesDir)) {
        New-Item -ItemType Directory -Path $releasesDir -Force | Out-Null
    }

    Copy-Item -Path $builtZip -Destination $releasesDir -Force
    Write-Host "Archived to $releasesDir\$pluginName-$nextVersion.zip"

    $releaseZip = Join-Path -Path $releasesDir -ChildPath "$pluginName-$nextVersion.zip"

    git add $propertiesFile $releaseZip
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to stage files for commit"
    }

    git commit -m "Release version $nextVersion"
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to commit release"
    }

    $releaseCommitted = $true

    Write-Host "Committed release $nextVersion."
    Write-Host "Note: pushing this commit triggers the Release workflow, which publishes"
    Write-Host "its own v1.0.<run number> tag independently of this version."
} finally {
    if (-not $releaseCommitted) {
        $originalLines | Set-Content -Path $propertiesFile
        Write-Host "Rolled $propertiesFile back to version $currentVersion."
    }
}
