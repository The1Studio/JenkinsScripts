<#
    .SYNOPSIS
    Compresses the output of the build directory into its zip files to uploaded to S3 for Windows builds

    .DESCRIPTION
    Compresses the output of the build directory into zip files for Windows builds so they can be uploaded to S3 
    in a later step of the Jenkins pipeline. This script requires 7zip to be installed on the host machine.

    .PARAMETER Runtimes
    Specifies the runtimes to create zip files for. This script currently supports 'win-x86' and 'win-x64'
    Variable defaults to 'win-x86','win-x64'
#>

Param(
   [string[]][ValidateSet('win-x86','win-x64', 'osx-x64', 'android')] $Runtimes = $("win-x86", "win-x64", "osx-x64", 'android'),
   $BuildVersion
)

$ErrorActionPreference = "Stop"

# Include our required utility files
. "$PSScriptRoot/Util_BuildTools.ps1"
. "$PSScriptRoot/Util_CompressionTools.ps1"

function Compress-FileRuntimes([string] $base, [string] $prefix) {
    $compressFiles = @()
    foreach ($runtime in $Runtimes)  {
        $code = Get-PlatformToCode $runtime
        $runtimePath = Resolve-Path "$base/$runtime"

        $compressFile = "$($prefix)-$($code)_$($BuildVersion).zip"
        $destPath = Join-Path $BuildOutputDir $compressFile
        $compressFiles += $compressFile
        Compress-Directory -Source $runtimePath -Dest $destPath
    }
    return $compressFiles -join ','
}

# Compress our requested runtimes
Compress-FileRuntimes -base $ClientOutputDir -prefix "MNA"
