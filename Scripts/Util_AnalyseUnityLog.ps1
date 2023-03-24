Param(
    [string][Parameter(Mandatory=$true)] $LogFile
)

$ErrorActionPreference = "Stop"
$errorStart = "*CompilerOutput:*"
$errorEnd = "*EndCompilerOutput*"

$lines = Get-Content $LogFile

$compileErrorLines = $lines.Where({$_ -like $errorStart}, 'SkipUntil').Where({$_ -like $errorEnd}, 'Until')

if ($lines -eq $null) {
    echo "Unable to find $LogFile, Unity may not have run"
}
else {
    echo "Unknown error, showing last 20 lines"

    $lastLines = $lines | select -Last 20

    echo "``````"

    foreach($line in $lastLines) {
        echo "$line"
    }

    echo "``````"
}