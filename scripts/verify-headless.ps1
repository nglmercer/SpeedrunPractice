param(
    [ValidateSet('116', '121', '263')]
    [string]$Version,
[ValidateSet('lava', 'fixtures', 'all')]
    [string]$Suite = 'lava',
    [int]$StartupTimeoutSeconds = 1800,
    [int]$SuiteTimeoutSeconds = 3600,
    [string]$JavaHome = ''
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$gradle = Join-Path $repo 'gradlew.bat'

$targets = @{
    '116' = @{ Version = '1.16.1'; GradleTask = ':versions:fabric-1.16.1:runServer'; RunDir = 'versions/fabric-1.16.1/run'; Jar = 'versions/fabric-1.16.1/build/libs/speedrun-practice-verification-1.16.1-2.0.0.jar'; Port = 25577; Password = 'verify116'; ServerJavaVersion = 17 }
    '121' = @{ Version = '1.21.1'; GradleTask = ':versions:fabric-1.21.1:runServer'; RunDir = 'versions/fabric-1.21.1/run'; Jar = 'versions/fabric-1.21.1/build/libs/speedrun-practice-verification-1.21.1-2.0.0.jar'; Port = 25575; Password = 'verify116'; ServerJavaVersion = $null }
    '263' = @{ Version = '26.3'; GradleTask = ':versions:fabric-26.3:runServer'; RunDir = 'versions/fabric-26.3/run'; Jar = 'versions/fabric-26.3/build/libs/speedrun-practice-verification-26.3-2.0.0.jar'; Port = 25576; Password = 'verify116'; ServerJavaVersion = $null }
}
$target = $targets[$Version]
$runDir = Join-Path $repo $target.RunDir
$modsDir = Join-Path $runDir 'mods'
$verificationJar = Join-Path $repo $target.Jar
$reportDir = Join-Path $repo (Join-Path 'build/verification' $target.Version)
$reportPath = Join-Path $reportDir 'report.json'
$serverLog = Join-Path $runDir 'logs/latest.log'
$consoleLog = Join-Path $reportDir 'server-console.log'
$errorLog = Join-Path $reportDir 'server-console.error.log'
$child = $null
$oldJavaHome = $env:JAVA_HOME
$startedAt = [DateTime]::UtcNow
$serverLogLineCount = 0

function Test-TcpPortOpen([int]$port) {
    $client = New-Object Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect('127.0.0.1', $port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne(500)) { return $false }
        $client.EndConnect($async)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Send-RconPacket($stream, [int]$id, [int]$type, [string]$body) {
    $payload = [Text.Encoding]::ASCII.GetBytes($body)
    $length = 10 + $payload.Length
    $buffer = New-Object byte[] ($length + 4)
    [BitConverter]::GetBytes($length).CopyTo($buffer, 0)
    [BitConverter]::GetBytes($id).CopyTo($buffer, 4)
    [BitConverter]::GetBytes($type).CopyTo($buffer, 8)
    $payload.CopyTo($buffer, 12)
    $stream.Write($buffer, 0, $buffer.Length)
    $stream.Flush()
}

function Read-RconPacket($stream) {
    $header = New-Object byte[] 4
    $read = 0
    while ($read -lt 4) {
        $count = $stream.Read($header, $read, 4 - $read)
        if ($count -le 0) { return $null }
        $read += $count
    }
    $length = [BitConverter]::ToInt32($header, 0)
    $body = New-Object byte[] $length
    $read = 0
    while ($read -lt $length) {
        $count = $stream.Read($body, $read, $length - $read)
        if ($count -le 0) { return $null }
        $read += $count
    }
    return [Text.Encoding]::UTF8.GetString($body, 8, $length - 10)
}

function Invoke-Rcon([string]$command) {
    $client = New-Object Net.Sockets.TcpClient
    $client.ReceiveTimeout = 8000
    $client.SendTimeout = 5000
    $client.Connect('127.0.0.1', $target.Port)
    $stream = $client.GetStream()
    try {
        Send-RconPacket $stream 1 3 $target.Password
        $auth = Read-RconPacket $stream
        if ($null -eq $auth) { throw 'RCON authentication returned no response' }
        Start-Sleep -Milliseconds 150
        while ($stream.DataAvailable) { [void](Read-RconPacket $stream) }
        Send-RconPacket $stream 2 2 $command
        $deadline = [DateTime]::UtcNow.AddSeconds(60)
        while (-not $stream.DataAvailable -and [DateTime]::UtcNow -lt $deadline) {
            Start-Sleep -Milliseconds 200
        }
        $result = ''
        while ($stream.DataAvailable) { $result += Read-RconPacket $stream }
        return $result
    } finally {
        $stream.Close()
        $client.Close()
    }
}

try {
    if (-not (Test-Path -LiteralPath $verificationJar -PathType Leaf)) {
        throw "Verification jar is missing: $verificationJar. Run the version verificationJar task first."
    }
    New-Item -ItemType Directory -Force -Path $modsDir, $reportDir | Out-Null
    Copy-Item -LiteralPath $verificationJar -Destination (Join-Path $modsDir (Split-Path $verificationJar -Leaf)) -Force
    if (Test-Path -LiteralPath $reportPath) {
        Move-Item -LiteralPath $reportPath -Destination (Join-Path $reportDir ("report.previous." + (Get-Date -Format 'yyyyMMddHHmmss') + '.json')) -Force
    }
    if (Test-TcpPortOpen $target.Port) {
        throw "RCON port $($target.Port) is already in use. Stop the existing $($target.Version) verification server before starting a fresh run."
    }
    if (Test-Path -LiteralPath $serverLog) {
        $previousServerLog = Join-Path $reportDir ("server-log.previous." + (Get-Date -Format 'yyyyMMddHHmmss') + '.log')
        Move-Item -LiteralPath $serverLog -Destination $previousServerLog -Force
    }
    $env:SPEEDRUN_PRACTICE_VERIFICATION_OUTPUT = Join-Path $repo 'build/verification'
    if ($JavaHome) {
        if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe') -PathType Leaf)) {
            throw "Requested server JavaHome does not contain bin/java.exe: $JavaHome"
        }
        if ($null -eq $target.ServerJavaVersion) {
            throw "No server Java version is configured for $($target.Version); update the target map before using -JavaHome."
        }
    }

    $arguments = "$($target.GradleTask) --console=plain --no-daemon"
    if ($JavaHome) {
        $arguments += " -PspeedrunPracticeServerJavaVersion=$($target.ServerJavaVersion)"
    }
    $child = Start-Process -FilePath $gradle -ArgumentList $arguments -WorkingDirectory $repo `
        -RedirectStandardOutput $consoleLog -RedirectStandardError $errorLog -WindowStyle Hidden -PassThru

    $readyDeadline = [DateTime]::UtcNow.AddSeconds($StartupTimeoutSeconds)
    while ([DateTime]::UtcNow -lt $readyDeadline) {
        if ($child.HasExited) { throw "Minecraft server process exited with code $($child.ExitCode). See $consoleLog" }
        if (Test-Path -LiteralPath $serverLog) {
            $freshLines = @(Get-Content -LiteralPath $serverLog)
            $ready = $freshLines -match 'Done \('
            if ($ready) { break }
        }
        Start-Sleep -Seconds 2
    }
    $freshLines = @()
    if (Test-Path -LiteralPath $serverLog) {
        $freshLines = @(Get-Content -LiteralPath $serverLog)
    }
    if (-not ($freshLines -match 'Done \(')) {
        throw "Minecraft server did not become ready within $StartupTimeoutSeconds seconds. See $consoleLog"
    }

    $response = Invoke-Rcon "practiceverify run $Suite"
    if ($response -notmatch 'Verification suite') {
        throw "Verification command was not accepted: $response"
    }
    $suiteDeadline = [DateTime]::UtcNow.AddSeconds($SuiteTimeoutSeconds)
    while ([DateTime]::UtcNow -lt $suiteDeadline) {
        if (Test-Path -LiteralPath $reportPath) {
            $report = Get-Content -Raw -LiteralPath $reportPath | ConvertFrom-Json
            if ([int]$report.failed -gt 0) {
                throw "Verification failed; see $reportPath"
            }
            if ([int]$report.passed -gt 1) { break }
        }
        Start-Sleep -Seconds 2
    }
    if (-not (Test-Path -LiteralPath $reportPath)) {
        throw "Verification did not produce $reportPath"
    }
    $final = Get-Content -Raw -LiteralPath $reportPath | ConvertFrom-Json
    if ([int]$final.failed -gt 0 -or [int]$final.passed -lt 2) {
        throw "Verification did not complete successfully; see $reportPath"
    }
    Write-Output (Get-Content -Raw -LiteralPath (Join-Path $reportDir 'summary.txt'))
} finally {
    try {
        if (Test-TcpPortOpen $target.Port) { Invoke-Rcon 'stop' | Out-Null }
    } catch { }
    if ($child -and -not $child.HasExited) {
        [void]$child.WaitForExit(30000)
        if (-not $child.HasExited) { Stop-Process -Id $child.Id -Force }
    }
    Remove-Item Env:SPEEDRUN_PRACTICE_VERIFICATION_OUTPUT -ErrorAction SilentlyContinue
}
