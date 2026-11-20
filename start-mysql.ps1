param(
  [string]$DatabaseHost='127.0.0.1',
  [ValidateRange(1,65535)][int]$DatabasePort=3306,
  [ValidatePattern('^[a-zA-Z0-9_]+$')][string]$DatabaseName='xpertsedgehrms',
  [string]$DatabaseUser='hrms_app',
  [ValidateRange(1,65535)][int]$ServerPort=8080,
  [switch]$LocalMail,
  [switch]$Gmail,
  [switch]$GmailApi
)
$ErrorActionPreference='Stop'
if($DatabaseHost -notmatch '^[a-zA-Z0-9.-]+$'){throw 'Invalid database host.'}
if(-not(Test-NetConnection -ComputerName $DatabaseHost -Port $DatabasePort -InformationLevel Quiet)){
  throw "MySQL is not reachable on ${DatabaseHost}:$DatabasePort. Install/start MySQL first; see MYSQL-SETUP.md."
}
$securePassword=Read-Host 'MySQL application user password' -AsSecureString
$pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
$keys=@('MYSQL_URL','MYSQL_USERNAME','MYSQL_PASSWORD','SPRING_PROFILES_ACTIVE','SERVER_PORT')
$previous=@{}; foreach($key in $keys){$previous[$key]=[Environment]::GetEnvironmentVariable($key,'Process')}
try {
  $env:MYSQL_PASSWORD=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
  # Local development only. Use VERIFY_IDENTITY and a trusted certificate for remote production.
  $env:MYSQL_URL="jdbc:mysql://${DatabaseHost}:${DatabasePort}/${DatabaseName}?sslMode=PREFERRED&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
  $env:MYSQL_USERNAME=$DatabaseUser
  $env:SERVER_PORT="$ServerPort"
  $env:SPRING_PROFILES_ACTIVE=if($GmailApi){'mysql,local-mail,gmail-api'}elseif($Gmail){'mysql,local-mail,gmail'}elseif($LocalMail){'mysql,local-mail'}else{'mysql'}
  Push-Location (Join-Path $PSScriptRoot 'backend')
  try { & .\mvnw.cmd spring-boot:run; if($LASTEXITCODE -ne 0){throw 'Backend startup failed; inspect the preceding error.'} }
  finally { Pop-Location }
} finally {
  [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
  foreach($key in $keys){[Environment]::SetEnvironmentVariable($key,$previous[$key],'Process')}
}
