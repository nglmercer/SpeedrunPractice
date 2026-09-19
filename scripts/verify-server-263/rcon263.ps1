# Minimal Source-RCON client for the headless 1.16.1 verification run.
# Usage: powershell -File C:\Temp\rcon116.ps1 -Command "practice seeds results"
#    or: powershell -File C:\Temp\rcon116.ps1 -Command "a" -Command "b"
param(
    [string[]]$Command = @("list"),
    [string]$Server = "127.0.0.1",
    [int]$Port = 25575,
    [string]$Password = "verify116"
)

function Send-Packet($stream, $id, $type, $body) {
    $payload = [System.Text.Encoding]::ASCII.GetBytes($body)
    $len = 10 + $payload.Length
    $buf = New-Object byte[] ($len + 4)
    [System.BitConverter]::GetBytes($len).CopyTo($buf, 0)
    [System.BitConverter]::GetBytes($id).CopyTo($buf, 4)
    [System.BitConverter]::GetBytes($type).CopyTo($buf, 8)
    $payload.CopyTo($buf, 12)
    $stream.Write($buf, 0, $buf.Length)
    $stream.Flush()
}

function Read-Packet($stream) {
    $hdr = New-Object byte[] 4
    $read = 0
    while ($read -lt 4) {
        $n = $stream.Read($hdr, $read, 4 - $read)
        if ($n -le 0) { return $null }
        $read += $n
    }
    $len = [System.BitConverter]::ToInt32($hdr, 0)
    $body = New-Object byte[] $len
    $read = 0
    while ($read -lt $len) {
        $n = $stream.Read($body, $read, $len - $read)
        if ($n -le 0) { return $null }
        $read += $n
    }
    $id = [System.BitConverter]::ToInt32($body, 0)
    $type = [System.BitConverter]::ToInt32($body, 4)
    $text = [System.Text.Encoding]::UTF8.GetString($body, 8, $len - 10)
    return @{ id = $id; type = $type; text = $text }
}

$client = New-Object System.Net.Sockets.TcpClient
$client.ReceiveTimeout = 8000
$client.SendTimeout = 5000
$client.Connect($Server, $Port)
$stream = $client.GetStream()
try {
    Send-Packet $stream 1 3 $Password
    $auth = Read-Packet $stream
    if ($auth -eq $null -or $auth.id -eq -1) { throw "RCON auth failed" }
    # MC sends a trailing empty auth packet; drain it if present.
    Start-Sleep -Milliseconds 150
    while ($stream.DataAvailable) { [void](Read-Packet $stream) }
    $i = 10
    foreach ($cmd in $Command) {
        $i++
        Send-Packet $stream $i 2 $cmd
        # Wait for the first response byte (structure searches and world
        # probes can take a while); then drain with a settle window so
        # multi-packet replies are not split across commands.
        $deadline = [DateTime]::UtcNow.AddSeconds(300)
        while (-not $stream.DataAvailable -and [DateTime]::UtcNow -lt $deadline) {
            Start-Sleep -Milliseconds 200
        }
        $out = ""
        $settle = [DateTime]::UtcNow.AddSeconds(3)
        while ([DateTime]::UtcNow -lt $settle) {
            while ($stream.DataAvailable) {
                $pkt = Read-Packet $stream
                if ($pkt -eq $null) { break }
                $out += $pkt.text
                $settle = [DateTime]::UtcNow.AddSeconds(1)
            }
            Start-Sleep -Milliseconds 200
        }
        Write-Output "=== RCON> $cmd"
        Write-Output $out
    }
} finally {
    $stream.Close()
    $client.Close()
}
