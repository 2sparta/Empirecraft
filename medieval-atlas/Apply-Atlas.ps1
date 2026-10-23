param(
    [string]$MinecraftDirectory,
    [switch]$Restore,
    [string]$BackupDirectory
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2
if (-not $MinecraftDirectory) { $MinecraftDirectory = Read-Host 'Minecraft CLIENT instance folder (contains mods)' }
$root = [IO.Path]::GetFullPath($MinecraftDirectory.Trim('"'))
if ($root.Length -gt [IO.Path]::GetPathRoot($root).Length) { $root = $root.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar) }
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Read-JsonFile([string]$Path) {
    $text = [IO.File]::ReadAllText($Path, [Text.Encoding]::UTF8)
    # JourneyMap writes // header lines ahead of its JSON. Keep strings/URLs intact.
    $text = [regex]::Replace($text, '(?m)^[ \t]*//[^\r\n]*(?:\r?\n|$)', '')
    return ($text | ConvertFrom-Json)
}

function Target-Path([string]$Relative) {
    $path = [IO.Path]::GetFullPath((Join-Path $root $Relative))
    if (-not $path.StartsWith($root.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Path outside the selected instance: $Relative"
    }
    return $path
}

function Restore-Files($Records, [string]$Backup) {
    foreach ($record in $Records) {
        $target = Target-Path $record.path
        if ($record.existed) {
            [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
            [IO.File]::Copy((Join-Path $Backup ('files/' + $record.path)), $target, $true)
        } elseif ([IO.File]::Exists($target)) {
            [IO.File]::Delete($target)
        }
    }
}

if ($Restore) {
    if (-not $BackupDirectory) { throw 'Restore requires -BackupDirectory with the path printed at installation.' }
    $backup = [IO.Path]::GetFullPath($BackupDirectory)
    $manifest = Read-JsonFile (Join-Path $backup 'manifest.json')
    if ($manifest.instance -ne $root) { throw 'This backup belongs to another Minecraft instance.' }
    Restore-Files $manifest.files $backup
    Write-Host 'Original appearance restored. Restart Minecraft.'
    exit 0
}

if (-not (Test-Path -LiteralPath (Join-Path $root 'mods') -PathType Container)) { throw 'Select the Minecraft client instance folder that contains mods/.' }
$jm = @(Get-ChildItem -LiteralPath (Join-Path $root 'mods') -File | Where-Object { $_.Name -like '*journeymap*6.0.7*.jar' })
if ($jm.Count -eq 0) { throw 'This theme is validated for JourneyMap 6.0.7. Its JAR was not found in mods/.' }
if (Test-Path -LiteralPath (Join-Path $root 'plugins/Towny') -PathType Container) { throw 'This is a server folder. Install the theme in the CLIENT instance instead.' }
$payload = Join-Path $PSScriptRoot 'payload'
$settings = Read-JsonFile (Join-Path $PSScriptRoot 'settings.json')
$operations = New-Object 'System.Collections.Generic.List[object]'

# Build and validate the complete plan before changing any existing file.
foreach ($file in Get-ChildItem -LiteralPath $payload -File -Recurse) {
    $relative = $file.FullName.Substring($payload.Length + 1)
    $operations.Add([pscustomobject]@{ path = $relative; data = [IO.File]::ReadAllBytes($file.FullName) })
}
$configs = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::OrdinalIgnoreCase)
foreach ($patch in $settings.PSObject.Properties) {
    [void]$configs.Add(('journeymap/config/6.0/' + $patch.Name))
}
$worldData = Join-Path $root 'journeymap/data'
if (Test-Path -LiteralPath $worldData -PathType Container) {
    foreach ($file in Get-ChildItem -LiteralPath $worldData -Filter 'journeymap.*.config' -Recurse -File) {
        if ($file.Directory.Name -eq 'config' -and $settings.PSObject.Properties.Name -contains $file.Name) {
            [void]$configs.Add($file.FullName.Substring($root.TrimEnd([IO.Path]::DirectorySeparatorChar).Length + 1))
        }
    }
}
foreach ($relative in $configs) {
    $path = Target-Path $relative
    $config = if ([IO.File]::Exists($path)) { Read-JsonFile $path } else { [pscustomobject]@{ configVersion = '6.0.7' } }
    $filename = [IO.Path]::GetFileName($path)
    foreach ($patch in $settings.$filename.PSObject.Properties) {
        $property = $config.PSObject.Properties[$patch.Name]
        if ($null -eq $property) { $config | Add-Member -MemberType NoteProperty -Name $patch.Name -Value $patch.Value }
        elseif ($null -ne $property.Value -and $null -ne $property.Value.PSObject.Properties['value']) { $property.Value.value = $patch.Value }
        else { $property.Value = $patch.Value }
    }
    $operations.Add([pscustomobject]@{ path = $relative; data = $utf8.GetBytes((ConvertTo-Json -InputObject $config -Depth 100) + "`n") })
}

$optionsPath = Target-Path 'options.txt'
$options = if ([IO.File]::Exists($optionsPath)) { [IO.File]::ReadAllText($optionsPath, [Text.Encoding]::UTF8) } else { '' }
$match = [regex]::Match($options, '(?m)^resourcePacks:([^\r\n]*)')
$selected = if ($match.Success) { @($match.Groups[1].Value | ConvertFrom-Json) } else { @('vanilla') }
$pack = 'file/EmpireCraft-Medieval-Icons-1.0.0.zip'
if ($selected -notcontains $pack) { $selected = @($selected) + @($pack) }
$resourceLine = 'resourcePacks:' + (ConvertTo-Json -InputObject @($selected) -Compress)
if ($match.Success) { $options = $options.Substring(0, $match.Index) + $resourceLine + $options.Substring($match.Index + $match.Length) }
else { $options = $options.TrimEnd("`r", "`n") + "`n" + $resourceLine + "`n" }
$operations.Add([pscustomobject]@{ path = 'options.txt'; data = $utf8.GetBytes($options) })

$backup = Join-Path $root ('journeymap/empirecraft-atlas-backups/' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8))
$records = @()
foreach ($operation in $operations) {
    $path = Target-Path $operation.path
    $exists = [IO.File]::Exists($path)
    $records += [pscustomobject]@{ path = $operation.path; existed = $exists }
    if ($exists) {
        $copy = Join-Path $backup ('files/' + $operation.path)
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($copy)) | Out-Null
        [IO.File]::Copy($path, $copy)
    }
}
[IO.Directory]::CreateDirectory($backup) | Out-Null
[IO.File]::WriteAllText((Join-Path $backup 'manifest.json'), (ConvertTo-Json -Depth 100 -InputObject @{ instance = $root; files = $records }), $utf8)
try {
    foreach ($operation in $operations) {
        $path = Target-Path $operation.path
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($path)) | Out-Null
        [IO.File]::WriteAllBytes($path, $operation.data)
    }
} catch {
    Restore-Files $records $backup
    throw
}
Write-Host 'EmpireCraft Atlas installed. Start Minecraft and press J.'
Write-Host 'The terrain palette uses Topographic mode; the day/surface map remains available.'
Write-Host ('Backup: ' + $backup)
