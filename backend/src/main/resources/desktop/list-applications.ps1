$apps = [System.Collections.Generic.List[object]]::new()
$warnings = [System.Collections.Generic.List[string]]::new()
$timer = [Diagnostics.Stopwatch]::StartNew()
function Add-App($name, $source, $exe, $identity, $key) {
    if ($apps.Count -ge 4096 -or [string]::IsNullOrWhiteSpace($name)) { return }
    $apps.Add(@{displayName=[string]$name;source=$source;executable=[string]$exe;applicationId=[string]$identity;key=[string]$key})
}
try {
    $shell = New-Object -ComObject WScript.Shell
    foreach ($root in @([Environment]::GetFolderPath('StartMenu'), [Environment]::GetFolderPath('CommonStartMenu'))) {
        if (!(Test-Path -LiteralPath $root)) { continue }
        Get-ChildItem -LiteralPath $root -Filter '*.lnk' -File -Recurse -Depth 5 -ErrorAction SilentlyContinue | Select-Object -First 3000 | ForEach-Object {
            $shortcut = $shell.CreateShortcut($_.FullName)
            $target = [Environment]::ExpandEnvironmentVariables($shortcut.TargetPath)
            if ($shortcut.Arguments -match '(--app-id=|--app=|[A-Za-z][A-Za-z0-9+.-]*://)') { $target = '' }
            if ($target -notmatch '\.exe$' -or !(Test-Path -LiteralPath $target -PathType Leaf)) { $target = '' }
            Add-App $_.BaseName 'Windows app' $target '' $_.FullName
        }
    }
} catch { $warnings.Add('Some Start menu applications could not be read.') }
foreach ($root in @('HKCU:\Software\Microsoft\Windows\CurrentVersion\App Paths', 'HKLM:\Software\Microsoft\Windows\CurrentVersion\App Paths', 'HKCU:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\App Paths', 'HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\App Paths')) {
    try {
        if (!(Test-Path -LiteralPath $root)) { continue }
        Get-ChildItem -LiteralPath $root | Select-Object -First 1000 | ForEach-Object {
            $target = [Environment]::ExpandEnvironmentVariables([string]$_.GetValue('')).Trim('"')
            if ($target -match '\.exe$' -and (Test-Path -LiteralPath $target -PathType Leaf)) {
                Add-App ([IO.Path]::GetFileNameWithoutExtension($_.PSChildName)) 'Windows app' $target '' $_.Name
            }
        }
    } catch { $warnings.Add('Some registered applications could not be read.') }
}
try {
    $names = @{}
    try { Get-StartApps -ErrorAction SilentlyContinue | ForEach-Object { $names[$_.AppID] = $_.Name } } catch { }
    Get-AppxPackage | Where-Object { !$_.IsFramework -and !$_.IsResourcePackage } | Select-Object -First 1000 | ForEach-Object {
        if ($timer.Elapsed.TotalSeconds -gt 8) { $warnings.Add('Some Microsoft Store applications were omitted because discovery took too long.'); return }
        $package = $_
        try {
            $manifest = Get-AppxPackageManifest -Package $package.PackageFullName
            foreach ($application in $manifest.Package.Applications.Application) {
                if ([string]::IsNullOrWhiteSpace([string]$application.Id)) { continue }
                $aumid = $package.PackageFamilyName + '!' + $application.Id
                $name = $names[$aumid]
                if (!$name) { $name = $package.Name }
                Add-App $name 'Microsoft Store' '' ('windows:' + $aumid) $aumid
            }
        } catch { $warnings.Add('Some Microsoft Store applications could not be read.') }
    }
} catch { $warnings.Add('Microsoft Store applications could not be read.') }
ConvertTo-Json -Depth 5 -Compress -InputObject @{applications=@($apps.ToArray());warnings=@($warnings.ToArray() | Select-Object -Unique)}
