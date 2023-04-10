<#
    .SYNOPSIS
    Utility functions and constant variables used in MNA build scripts
#>

# Define our directory constant values
$ProjectDir = Resolve-Path "$PSScriptRoot/../.."
$BuildOutputDir = Join-Path "$ProjectDir" "Build"
$ClientOutputDir = Join-Path "$BuildOutputDir" "Client"