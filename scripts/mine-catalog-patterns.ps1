# Mine recurring message/process patterns from WORKS log datasets
$ErrorActionPreference = "SilentlyContinue"
# LOGS_2 (~900+ fichiers) : trop lent en PowerShell — utiliser le test Java à la place.
$dirs = @(
    "$env:USERPROFILE\Desktop\Metier",
    "$env:USERPROFILE\Desktop\LOGS"
)
if ($env:INCLUDE_LOGS_2 -eq "1") {
    $dirs += "$env:USERPROFILE\Desktop\LOGS_2"
}
$maxPerFile = 8000
$maxFilesPerDir = 3
$processCounts = @{}
$msgPrefixCounts = @{}
$unknownSnippets = @{}
$total = 0
$worksLines = 0

function Get-Message([string[]]$parts) {
    $idx = $parts.Length - 8
    if ($idx -ge 6 -and $parts[$idx] -match '^\d{3,6}$') {
        $start = 6
        $end = $idx
        while ($start -lt $end -and [string]::IsNullOrWhiteSpace($parts[$start])) { $start++ }
        if ($start -lt $end) {
            return ($parts[$start..($end-1)] -join '|').Trim()
        }
    }
    return $null
}

function Normalize-Prefix([string]$msg) {
    if ([string]::IsNullOrWhiteSpace($msg)) { return $null }
    $m = $msg.Trim()
    if ($m.Length -gt 90) { $m = $m.Substring(0, 90) }
    $m = $m -replace '\d{10,}', '#' -replace 'uuid\s*\[[^\]]+\]', 'uuid [#]' -replace '\[[^\]]{20,}\]', '[#]'
    return $m
}

function Is-WorksProcess([string]$p) {
    if ([string]::IsNullOrWhiteSpace($p)) { return $false }
    return $p -match '^(processFilter|processWebService|processRunRules|process\.)'
}

foreach ($dir in $dirs) {
    if (-not (Test-Path $dir)) { continue }
    Get-ChildItem $dir -Filter "*.log" | Sort-Object Length | Select-Object -First $maxFilesPerDir | ForEach-Object {
        Write-Host "Fichier: $($_.Name) ..."
        $n = 0
        Get-Content $_.FullName -Encoding UTF8 | ForEach-Object {
            if (++$n -gt $maxPerFile) { return }
            $total++
            $parts = $_ -split '\|', -1
            if ($parts.Length -lt 8) { return }
            $proc = $parts[5].Trim()
            if (-not (Is-WorksProcess $proc)) { return }
            $worksLines++
            $root = ($proc -split '-')[0]
            if (-not $processCounts.ContainsKey($root)) { $processCounts[$root] = 0 }
            $processCounts[$root]++
            $msg = Get-Message $parts
            if (-not $msg) { return }
            $prefix = Normalize-Prefix $msg
            if (-not $msgPrefixCounts.ContainsKey($prefix)) { $msgPrefixCounts[$prefix] = 0 }
            $msgPrefixCounts[$prefix]++
        }
    }
}

Write-Host "=== Lignes WORKS analysees: $worksLines / $total ==="
Write-Host "`n--- Top process (racine colonne) ---"
$processCounts.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 15 | ForEach-Object { Write-Host "$($_.Key): $($_.Value)" }

Write-Host "`n--- Top 40 prefixes message (normalises) ---"
$msgPrefixCounts.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 40 | ForEach-Object {
    Write-Host ("{0,6} | {1}" -f $_.Value, $_.Key)
}
