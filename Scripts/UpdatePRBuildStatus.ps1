Param(
   [string] [Parameter(Mandatory=$true)] $LogFile,
   [string] [Parameter(Mandatory=$true)] $PullRequestId,
   [string] [Parameter(Mandatory=$true)] $Token, 
   [string] [Parameter(Mandatory=$true)] $GitUrlPR
)
#bootstrapping agent in case we need it
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

#getting logs
try {
    $Message = [IO.File]::ReadAllText($LogFile)
}
catch {
     $Message = "Error reading the file!!!"
}
$Body = @{
        body = $Message
        }
$Body = $Body | ConvertTo-Json -Depth 2

#updating PR on github
try {
    Invoke-RestMethod `
         -Uri "https://api.github.com/repos/$GitUrlPR/issues/$PullRequestId/comments" `
         -Method Post `
         -Headers @{Authorization="Bearer $token"} `
         -Body $Body

} catch {
    Write-Host "ErrorCode:" $_.Exception.Response.StatusCode.value__ 
    Write-Host "Description:" $_.Exception.Response.StatusDescription
}