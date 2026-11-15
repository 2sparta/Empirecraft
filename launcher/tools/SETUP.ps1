# OWNER ONLY. Review ADMIN-SETUP.md. This creates a PUBLIC client distribution.
# Never include the private setup directory in a player download.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
function Invoke-Gh {
    & gh @args
    if ($LASTEXITCODE -ne 0) { throw 'GitHub CLI command failed. No further setup steps were run.' }
}
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw 'Install GitHub CLI, then run gh auth login first.' }
$owner = & gh api user --jq .login
if ($LASTEXITCODE -ne 0 -or $owner -ne '2sparta') { throw 'Sign in as 2sparta with gh auth login.' }
foreach ($file in @('empirecraft-release-key.txt', 'empirecraft-distribution-ssh', 'empirecraft-distribution-ssh.pub')) {
    if (-not (Test-Path $file -PathType Leaf)) { throw "Missing private setup file: $file" }
}
& gh repo view 2sparta/EmpireCraftLauncher --json name 1>$null 2>$null
if ($LASTEXITCODE -ne 0) {
    Invoke-Gh repo create 2sparta/EmpireCraftLauncher --public --add-readme --description 'Verified Empirecraft client downloads and signed update channel'
}
$visibility = & gh repo view 2sparta/EmpireCraftLauncher --json visibility --jq .visibility
if ($LASTEXITCODE -ne 0 -or $visibility -ne 'PUBLIC') { throw 'Distribution repository must be public. Existing visibility was not changed.' }
$branch = & gh repo view 2sparta/EmpireCraftLauncher --json defaultBranchRef --jq .defaultBranchRef.name
if ($LASTEXITCODE -ne 0 -or $branch -ne 'main') { throw 'Set the distribution default branch to main, then continue setup.' }
Get-Content -Raw empirecraft-release-key.txt | & gh secret set LAUNCHER_SIGNING_KEY --repo 2sparta/EmpireCraftLauncher
if ($LASTEXITCODE -ne 0) { throw 'Could not save public-channel signing key.' }
Invoke-Gh variable set LAUNCHER_SIGNING_ENABLED --repo 2sparta/EmpireCraftLauncher --body true
Invoke-Gh workflow run publish-channel.yml --repo 2sparta/EmpireCraftLauncher --ref main
Invoke-Gh repo deploy-key add empirecraft-distribution-ssh.pub --repo 2sparta/EmpireCraftLauncher --title 'Empirecraft client publisher' --allow-write
Get-Content -Raw empirecraft-release-key.txt | & gh secret set LAUNCHER_SIGNING_KEY --repo 2sparta/Empirecraft
if ($LASTEXITCODE -ne 0) { throw 'Could not save signing key.' }
Get-Content -Raw empirecraft-distribution-ssh | & gh secret set LAUNCHER_DISTRIBUTION_SSH_KEY --repo 2sparta/Empirecraft
if ($LASTEXITCODE -ne 0) { throw 'Could not save distribution deploy key.' }
Invoke-Gh variable set LAUNCHER_DISTRIBUTION_ENABLED --repo 2sparta/Empirecraft --body true
& gh workflow run launcher-channel.yml --repo 2sparta/Empirecraft --ref main
if ($LASTEXITCODE -ne 0) { Write-Warning 'Merge private PR #137, then run launcher-channel.yml on main to enable source-pack export.' }
Write-Host 'Publisher requested. Check GitHub Actions and the public signed channel. Keep this setup directory private.'
