$ErrorActionPreference = 'Stop'
# Read persisted variables explicitly, including when the IDE was opened before they were saved.
foreach ($taskName in @('WEBRTC_TURN_URLS', 'WEBRTC_TURN_USERNAME', 'WEBRTC_TURN_CREDENTIAL', 'JWT_SECRET')) {
    $taskValue = [Environment]::GetEnvironmentVariable($taskName, 'User')
    if (-not [string]::IsNullOrWhiteSpace($taskValue)) {
        [Environment]::SetEnvironmentVariable($taskName, $taskValue, 'Process')
    }
}
foreach ($taskName in @('WEBRTC_TURN_URLS', 'WEBRTC_TURN_USERNAME', 'WEBRTC_TURN_CREDENTIAL')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($taskName, 'Process'))) {
        throw "Missing setting: $taskName"
    }
}
Push-Location $PSScriptRoot
try { & .\gradlew.bat --no-daemon bootRun } finally { Pop-Location }
