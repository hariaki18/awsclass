param([string]$BaseUrl = 'http://127.0.0.1:5173')
$ErrorActionPreference = 'Stop'
$uri = [Uri]$BaseUrl
if($uri.Scheme -ne 'https' -and $uri.Host -notin @('localhost','127.0.0.1')) { throw 'Use HTTPS for a remote server.' }
$origin = $uri.GetLeftPart([UriPartial]::Authority)
$email = Read-Host 'Admin email'
$current = Read-Host 'Current password' -AsSecureString
$next = Read-Host 'New password (12+ characters)' -AsSecureString
$confirm = Read-Host 'Confirm new password' -AsSecureString
$currentText = [PSCredential]::new('admin',$current).GetNetworkCredential().Password
$nextText = [PSCredential]::new('admin',$next).GetNetworkCredential().Password
$confirmText = [PSCredential]::new('admin',$confirm).GetNetworkCredential().Password
if($nextText -cne $confirmText) { throw 'New passwords do not match.' }
if($nextText.Length -lt 12 -or [Text.Encoding]::UTF8.GetByteCount($nextText) -gt 72) { throw 'Use at least 12 characters, at most 72 UTF-8 bytes.' }
$session = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
$headers = @{Origin=$origin}
try {
  Invoke-RestMethod "$origin/api/auth/login" -Method Post -WebSession $session -Headers $headers -ContentType 'application/json' -Body (@{email=$email;password=$currentText}|ConvertTo-Json) | Out-Null
  Invoke-RestMethod "$origin/api/auth/change-password" -Method Post -WebSession $session -Headers $headers -ContentType 'application/json' -Body (@{currentPassword=$currentText;password=$nextText}|ConvertTo-Json) | Out-Null
  Write-Output 'Password changed. All existing sessions were revoked. Sign in again with the new password.'
} finally {
  try { Invoke-RestMethod "$origin/api/auth/logout" -Method Post -WebSession $session -Headers $headers | Out-Null } catch {}
  $currentText=$null; $nextText=$null; $confirmText=$null
}
