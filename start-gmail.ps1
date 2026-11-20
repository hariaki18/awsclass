param([string]$GmailAddress,[string]$MailboxEmail)
$ErrorActionPreference='Stop'
if(-not $GmailAddress){$GmailAddress=Read-Host 'Gmail / Google Workspace sender email'}
if($GmailAddress -notmatch '^[^\s@]+@[^\s@]+\.[^\s@]+$'){throw 'Enter a valid sender email.'}
if(-not $MailboxEmail){$MailboxEmail=Read-Host 'Existing local mailbox allowed to send through this Gmail account (name@xpertsedge.com)'}
if($MailboxEmail -notmatch '^[a-z0-9_-]+(\.[a-z0-9_-]+)*@xpertsedge\.com$'){throw 'Enter an existing local mailbox email.'}
$securePassword=Read-Host 'Google App Password (16 characters; NOT your normal Google password)' -AsSecureString
$pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
$keys=@('GMAIL_SMTP_USERNAME','GMAIL_SMTP_APP_PASSWORD','GMAIL_ALLOWED_MAILBOXES');$previous=@{}
foreach($key in $keys){$previous[$key]=[Environment]::GetEnvironmentVariable($key,'Process')}
try{
  $env:GMAIL_SMTP_USERNAME=$GmailAddress
  $env:GMAIL_SMTP_APP_PASSWORD=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer).Replace(' ','')
  if($env:GMAIL_SMTP_APP_PASSWORD.Length -ne 16){throw 'Enter the 16-character Google App Password.'}
  $env:GMAIL_ALLOWED_MAILBOXES=$MailboxEmail
  & (Join-Path $PSScriptRoot 'start-local-mail.ps1') -ServerOnly
  Write-Output "External messages from $MailboxEmail will be sent as $GmailAddress. Replies will go to Gmail; this does not connect Gmail Inbox."
  & (Join-Path $PSScriptRoot 'start-mysql.ps1') -Gmail
}finally{
  [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
  foreach($key in $keys){[Environment]::SetEnvironmentVariable($key,$previous[$key],'Process')}
}
