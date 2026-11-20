param(
  [string]$GoogleClientId,
  [string]$CompanyAdminMailbox,
  [ValidateRange(1,65535)][int]$ServerPort=8081
)
$ErrorActionPreference='Stop'
if(-not $GoogleClientId){$GoogleClientId=Read-Host 'Google OAuth Client ID (ends with .apps.googleusercontent.com)'}
if($GoogleClientId -notmatch '^[0-9]+-[a-z0-9]+\.apps\.googleusercontent\.com$'){throw 'Enter the OAuth Web application client ID from Google Cloud.'}
if(-not $CompanyAdminMailbox){$CompanyAdminMailbox=Read-Host 'Company Webmail administrator email (example: admin@xpertsedge.com)'}
if($CompanyAdminMailbox -notmatch '^[a-z0-9_-]+(?:[.][a-z0-9_-]+)*@xpertsedge[.]com$'){throw 'Enter an existing local @xpertsedge.com mailbox email.'}
$secureSecret=Read-Host 'Google OAuth Client Secret (hidden prompt; never paste it into chat)' -AsSecureString
$pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureSecret)
$keys=@('GOOGLE_CLIENT_ID','GOOGLE_CLIENT_SECRET','GOOGLE_REDIRECT_URI','GMAIL_COMPANY_ADMIN_MAILBOX');$previous=@{}
foreach($key in $keys){$previous[$key]=[Environment]::GetEnvironmentVariable($key,'Process')}
try{
  $env:GOOGLE_CLIENT_ID=$GoogleClientId
  $env:GOOGLE_CLIENT_SECRET=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
  if([string]::IsNullOrWhiteSpace($env:GOOGLE_CLIENT_SECRET)){throw 'The client secret is required.'}
  $env:GMAIL_COMPANY_ADMIN_MAILBOX=$CompanyAdminMailbox.Trim().ToLowerInvariant()
  # Must exactly match an Authorized redirect URI of the OAuth client in Google Cloud.
  $env:GOOGLE_REDIRECT_URI="http://localhost:${ServerPort}/api/webmail/gmail/callback"
  & (Join-Path $PSScriptRoot 'start-local-mail.ps1') -ServerOnly
  Write-Output 'Gmail API enabled. Sign in to Webmail, open Settings, then click Connect Gmail. Credentials are not saved to disk.'
  & (Join-Path $PSScriptRoot 'start-mysql.ps1') -GmailApi -ServerPort $ServerPort
}finally{
  [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
  foreach($key in $keys){[Environment]::SetEnvironmentVariable($key,$previous[$key],'Process')}
}
