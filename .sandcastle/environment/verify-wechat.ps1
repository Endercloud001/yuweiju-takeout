param(
    [string]$CliPath = 'E:\微信web开发者工具\cli.bat',
    [string]$ProjectDirectory = 'E:\Learning Files\yuweiju-takeout\.scratch\sandcastle-complement\wechat-fixture',
    [int]$IdePort = 57377,
    [int]$AutoPort = 9420,
    [switch]$Close
)
$ErrorActionPreference = 'Stop'
$target = [IO.Path]::GetFullPath($ProjectDirectory)
if ($target -match '[\\/]yuweiju-weixin-miniapp([\\/]|$)') { throw 'Use an isolated fixture directory, never the business miniapp.' }
if (Test-Path $target) {
    $config = Get-Content (Join-Path $target 'project.config.json') -Raw | ConvertFrom-Json
    if ($config.projectname -ne 'sandcastle-isolated-probe') { throw 'Existing directory belongs to another project.' }
}
if ($Close) {
    if (-not (Test-Path $target)) { throw 'Isolated fixture project is missing.' }
    & $CliPath --port $IdePort close --project $target; exit $LASTEXITCODE
}
New-Item -ItemType Directory -Path $target -Force | Out-Null
Copy-Item -Path (Join-Path $PSScriptRoot 'wechat-fixture/*') -Destination $target -Recurse -Force
& $CliPath --port $IdePort auto --project $target --auto-port $AutoPort
exit $LASTEXITCODE
