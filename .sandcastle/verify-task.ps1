[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Config,
    [Parameter(Mandatory)][string]$Commit,
    [string]$Checkout = (Split-Path $PSScriptRoot -Parent),
    [string]$Distribution = 'Ubuntu',
    [string]$Evidence
)
$ErrorActionPreference = 'Stop'
$Checkout = (Resolve-Path -LiteralPath $Checkout).Path
$Config = (Resolve-Path -LiteralPath $Config).Path
if (!$Evidence) { $Evidence = Join-Path $Checkout '.scratch/sandcastle-evidence' }
$Evidence = [IO.Path]::GetFullPath($Evidence)
function Convert-WslPath([string]$Path) {
    $converted = & wsl.exe -d $Distribution --exec wslpath -a -u $Path
    if ($LASTEXITCODE -ne 0) { throw "WSL path conversion failed: $Path" }
    return ($converted -join "`n").Trim()
}
$runner = Convert-WslPath (Join-Path $Checkout '.sandcastle/verify-task.py')
$linuxConfig = Convert-WslPath $Config
$linuxEvidence = Convert-WslPath $Evidence
# Pass arguments directly; no nested shell, global Git settings or auth mount.
& wsl.exe -d $Distribution --exec env "SANDCASTLE_EVIDENCE=$linuxEvidence" python3 $runner --config $linuxConfig --commit $Commit
exit $LASTEXITCODE
