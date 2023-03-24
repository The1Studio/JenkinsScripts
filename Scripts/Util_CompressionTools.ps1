<#
    .SYNOPSIS
    Utility functions and constant variables used in MNA compression scripts
#>


function Compress-Directory([string] $Source, [string] $Dest) {
    <#
        .SYNOPSIS
            Compresses the source directory into the output destination file.

        .DESCRIPTION
            Compresses the source directory into the output destination file. This function
            uses 7Zip as its means of compression. All output files 7zip supports such as .zip, .tar, .7z
            are supported by this compression function
    #>

    if (Test-Path $Source) {
        Write-Output "Compressing $Source into $Dest"
        if (Test-Path $Dest) { Remove-Item $Dest }

        7z a $Dest $Source/*
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to compress $Source into $Dest."
        }
    } 
    else { throw "Can't compress $Source into $Dest. Source not found." }
}

function Compress-DirectoryTarGz([string] $Source, [string] $Dest) {
    <#
        .SYNOPSIS
            Compresses the source directory into the output destination tar.gz file.

        .DESCRIPTION
            Compresses the source directory into the output destination tar.gz file. This function
            uses the native Windows tar command as its means of compression.
    #>

    if (Test-Path $Source) {
        Write-Output "Compressing $Source into $Dest"
        if (Test-Path $Dest) { Remove-Item $Dest }
        if (!($Dest.EndsWith('.tar.gz'))) { throw "Invalid destination path. Destination extension must end with .tar.gz" }

        $cdDirectory = Split-Path $Source
        $packageDirectory = $Source.Replace($cdDirectory, '').TrimStart('\').TrimStart('/')
        tar -cvzf $Dest -C $cdDirectory $packageDirectory
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to compress $Source into $Dest."
        }
    } 
    else { throw "Can't compress $Source into $Dest. Source not found." }
}

function Get-PlatformToCode([string] $Runtime) {
    <#
        .SYNOPSIS
            Converts a Unity runtime environment code to a platform label
    #>

    switch ($Runtime) {
        "win-x86" { "PC32" }
        "win-x64" { "PC64" }
        "osx-x64" { "OSX" }
    }
}