<#
  Starts the local Paper test server, waits until it is fully started, optionally runs console
  commands, stops it gracefully and prints a summary of all warnings/errors.
  Exit code: 0 = clean run, 1 = errors/exceptions logged, 2 = startup timed out.
#>
param(
    [Parameter(Mandatory = $true)][string]$ServerDir,
    [string]$Java = $(if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }),
    # console commands separated by ";", e.g. "ep debug;plugins"
    [string]$Commands = '',
    [int]$StartupTimeoutSeconds = 180,
    # time to let delayed tasks (updater, scheduled warnings, ...) run before stopping
    [int]$SettleSeconds = 5,
    [string]$Memory = '2500M'
)

$ErrorActionPreference = 'Stop'

# a running server holds a lock on its world - never start a second instance on the same folder
$sessionLock = Join-Path $ServerDir 'world\session.lock'
if (Test-Path $sessionLock) {
    try {
        [System.IO.File]::Open($sessionLock, 'Open', 'ReadWrite', 'None').Close()
    } catch {
        Write-Host '>>> The server is already running - stop it first (or test on the running server).' -ForegroundColor Red
        exit 3
    }
}

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $Java
$psi.Arguments = "-Xmx$Memory -jar paper.jar --nogui"
$psi.WorkingDirectory = $ServerDir
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.StandardOutputEncoding = [System.Text.Encoding]::UTF8

# Windows PowerShell writes a UTF-8 BOM to stdin when the process starts, which breaks the first console
# command ("?ctb ..."): avoid it where possible and send an empty line that swallows it otherwise
try { [Console]::InputEncoding = New-Object System.Text.UTF8Encoding($false) } catch { }
$process = [System.Diagnostics.Process]::Start($psi)
$stdin = New-Object System.IO.StreamWriter($process.StandardInput.BaseStream, (New-Object System.Text.UTF8Encoding($false)))
$stdin.AutoFlush = $true
$stdin.WriteLine('')
$problems = New-Object System.Collections.Generic.List[string]
$inStackTrace = $false

function Read-Output([int]$timeoutMs, [scriptblock]$until) {
    $deadline = [DateTime]::Now.AddMilliseconds($timeoutMs)
    while ([DateTime]::Now -lt $deadline) {
        if ($null -eq $script:pending) { $script:pending = $process.StandardOutput.ReadLineAsync() }
        $remaining = [Math]::Max(0, ($deadline - [DateTime]::Now).TotalMilliseconds)
        if (-not $script:pending.Wait([int][Math]::Min(500, $remaining))) { continue }
        $line = $script:pending.Result
        $script:pending = $null
        if ($null -eq $line) { return $false } # stream closed -> process exited
        Write-Host $line
        # known noise: Paper dumps the server thread on every regular stop, oshi fails on broken Windows perf counters
        if ($line -match 'Throwable: Server stopped|\[oshi\.') { $script:ignoreTrace = $true; continue }
        if ($script:ignoreTrace -and ($line -notmatch '^\[[\d:]+ ' -or $line -match '^\[[\d:]+ WARN\]: \s+at ')) { continue }
        $script:ignoreTrace = $false
        if ($line -match '(WARN|ERROR)\]:' -or ($line -match '(Exception|Error)[:\s]' -and $line -notmatch 'INFO\]:')) {
            $script:problems.Add($line); $script:inStackTrace = $true
        } elseif ($script:inStackTrace -and $line -match '^\s+(at |\.\.\.|Caused by)') {
            $script:problems.Add($line)
        } else {
            $script:inStackTrace = $false
        }
        if ($until -and (& $until $line)) { return $true }
    }
    return $false
}

$pending = $null
$started = Read-Output ($StartupTimeoutSeconds * 1000) { param($l) $l -match 'Done \([\d.,]+s\)!' }
$exitCode = 0
if (-not $started) {
    Write-Host "`n>>> Server did not finish starting within $StartupTimeoutSeconds s" -ForegroundColor Red
    $exitCode = 2
} else {
    Read-Output ($SettleSeconds * 1000) $null | Out-Null
    foreach ($command in ($Commands -split ';' | ForEach-Object { $_.Trim() } | Where-Object { $_ })) {
        Write-Host ">>> $command" -ForegroundColor Cyan
        # "wait <seconds>": keep the server running, e.g. while bots connect
        if ($command -match '^wait (\d+)$') {
            Read-Output ([int]$Matches[1] * 1000) $null | Out-Null
            continue
        }
        $stdin.WriteLine($command)
        Read-Output 2000 $null | Out-Null
    }
}

if (-not $process.HasExited) {
    $stdin.WriteLine('stop')
    Read-Output 60000 $null | Out-Null
    if (-not $process.WaitForExit(5000)) {
        Write-Host '>>> Server did not stop in time, killing it' -ForegroundColor Red
        $process.Kill()
    }
}

Write-Host "`n==================== SUMMARY ====================" -ForegroundColor Yellow
if ($problems.Count -eq 0) {
    Write-Host 'No warnings or errors.' -ForegroundColor Green
} else {
    $problems | ForEach-Object { Write-Host $_ }
    if ($exitCode -eq 0 -and ($problems | Where-Object { $_ -match 'ERROR\]:|Exception' })) { $exitCode = 1 }
}
exit $exitCode
