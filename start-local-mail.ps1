param([switch]$ServerOnly)
$ErrorActionPreference='Stop'
$directory=Join-Path $PSScriptRoot '.local-mail'
$binary=Join-Path $directory 'bin\mailpit.exe'
if(-not(Test-Path -LiteralPath $binary)){
  New-Item -ItemType Directory -Force -Path $directory | Out-Null
  $download=Join-Path $directory 'mailpit.zip'
  Invoke-WebRequest 'https://github.com/axllent/mailpit/releases/download/v1.33.0/mailpit-windows-amd64.zip' -OutFile $download
  if((Get-FileHash -LiteralPath $download -Algorithm SHA256).Hash -ne '47122DD828FF80DCAEF62B2952C478B178AFADADF066C990489608E537121BFD'){throw 'Mailpit download integrity check failed.'}
  Expand-Archive -LiteralPath $download -DestinationPath (Join-Path $directory 'bin') -Force
}
if(-not(Test-NetConnection 127.0.0.1 -Port 8025 -InformationLevel Quiet -WarningAction SilentlyContinue)){
  # Do not inherit forwarding/relay settings from the parent shell.
  $start=[Diagnostics.ProcessStartInfo]::new()
  $start.FileName=$binary;$start.WorkingDirectory=$PSScriptRoot;$start.UseShellExecute=$false;$start.CreateNoWindow=$true
  foreach($key in @($start.EnvironmentVariables.Keys)){if($key -like 'MP_*'){$start.EnvironmentVariables.Remove($key)}}
  $database=Join-Path $directory 'messages.db';$log=Join-Path $directory 'mailpit.log'
  $start.Arguments='--listen 127.0.0.1:8025 --smtp 127.0.0.1:1025 --database "'+$database+'" --log-file "'+$log+'" --max 0 --max-message-size 2 --smtp-allowed-recipients "@xpertsedge\.com$" --disable-version-check --smtp-disable-rdns'
  $process=[Diagnostics.Process]::Start($start)
  Write-Output "Local capture server started (PID $($process.Id)); SMTP 127.0.0.1:1025."
} else {Write-Output 'An existing local service on port 8025 will be used. Verify it is Mailpit if sending fails.'}
if(-not $ServerOnly){& (Join-Path $PSScriptRoot 'start-mysql.ps1') -LocalMail}
