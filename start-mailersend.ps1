param(
  [string]$DatabaseHost='127.0.0.1',
  [ValidateRange(1,65535)][int]$DatabasePort=3306,
  [ValidatePattern('^[a-zA-Z0-9_]+$')][string]$DatabaseName='xpertsedgehrms',
  [string]$DatabaseUser='hrms_app',
  [ValidateRange(1,65535)][int]$ServerPort=8080
)
$ErrorActionPreference='Stop'
if($DatabaseHost -notmatch '^[a-zA-Z0-9.-]+$'){throw 'Invalid database host.'}
if(-not(Test-NetConnection -ComputerName $DatabaseHost -Port $DatabasePort -InformationLevel Quiet)){throw "MySQL is not reachable on ${DatabaseHost}:$DatabasePort."}
$mysqlSecure=Read-Host 'MySQL application user password' -AsSecureString
$tokenSecure=Read-Host 'MailerSend API token' -AsSecureString
$webhookSecure=Read-Host 'MailerSend inbound signing secret' -AsSecureString
$eventsWebhookSecure=Read-Host 'MailerSend delivery-events signing secret (press Enter until the webhook is created)' -AsSecureString
function Plain([Security.SecureString]$value){$pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($value);try{return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)}finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)}}
$keys=@('MYSQL_URL','MYSQL_USERNAME','MYSQL_PASSWORD','SPRING_PROFILES_ACTIVE','SERVER_PORT','HRMS_MAIL_DOMAIN','MAILERSEND_API_TOKEN','MAILERSEND_INBOUND_DOMAIN','MAILERSEND_INBOUND_SIGNING_SECRET','MAILERSEND_EVENTS_SIGNING_SECRET')
$previous=@{};foreach($key in $keys){$previous[$key]=[Environment]::GetEnvironmentVariable($key,'Process')}
try {
  $env:MYSQL_PASSWORD=Plain $mysqlSecure
  $env:MYSQL_URL="jdbc:mysql://${DatabaseHost}:${DatabasePort}/${DatabaseName}?sslMode=PREFERRED&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
  $env:MYSQL_USERNAME=$DatabaseUser;$env:SERVER_PORT="$ServerPort";$env:SPRING_PROFILES_ACTIVE='mysql'
  $env:HRMS_MAIL_DOMAIN='xpertsedgetech.com';$env:MAILERSEND_API_TOKEN=Plain $tokenSecure;$env:MAILERSEND_INBOUND_DOMAIN='inbound.xpertsedgetech.com';$env:MAILERSEND_INBOUND_SIGNING_SECRET=Plain $webhookSecure;$env:MAILERSEND_EVENTS_SIGNING_SECRET=Plain $eventsWebhookSecure
  Push-Location (Join-Path $PSScriptRoot 'backend');try{& .\mvnw.cmd spring-boot:run;if($LASTEXITCODE -ne 0){throw 'Backend startup failed; inspect the preceding error.'}}finally{Pop-Location}
} finally {foreach($key in $keys){[Environment]::SetEnvironmentVariable($key,$previous[$key],'Process')}}
