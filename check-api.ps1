$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:5173/api'
$script:headers = @{ Origin = 'http://127.0.0.1:5173' }
$script:passed = 0
$script:session = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
function Request($method, $path, $body, $status = 200) {
  $args = @{Uri="$base$path";Method=$method;Headers=$script:headers;WebSession=$script:session;SkipHttpErrorCheck=$true;TimeoutSec=15}
  if ($null -ne $body) { $args.ContentType='application/json'; $args.Body=($body | ConvertTo-Json -Compress) }
  $r = Invoke-WebRequest @args
  if ([int]$r.StatusCode -ne $status) { throw "$method $path expected $status, got $($r.StatusCode): $($r.Content)" }
  $script:passed++
  if ($status -ne 204) { return ($r.Content | ConvertFrom-Json) }
}
Request GET '/mail/mailboxes' $null 401 | Out-Null
Request POST '/auth/login' @{email='admin@xpertsedge.com';password='wrong'} 401 | Out-Null
$login = Request POST '/auth/login' @{email=' ADMIN@XPERTSKILLS.COM ';password='Admin@123'}
if ($login.user.name -ne 'Admin' -or $login.user.initials -ne 'A') { throw 'Incorrect admin profile' }
Request GET '/me' $null | Out-Null
Request GET '/notifications' $null | Out-Null
$check=Request GET '/mail/check' $null
if($check.ok){throw 'Disconnected provider reported success'}
$prefix='audit'+[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$box=$null
try {
  foreach($address in @($null, '.', 'a..b', '.abc', 'abc.', ('a'*65))) {
    Request POST '/mail/mailboxes' @{address=$address;password='TestOnly@123'} 400 | Out-Null
  }
  foreach($password in @($null, 'short', ('a'*73))) {
    Request POST '/mail/mailboxes' @{address=$prefix;password=$password} 400 | Out-Null
  }
  foreach($quota in @('oops', 0, -1, 1.5, 1001)) {
    Request POST '/mail/mailboxes' @{address=$prefix;password='TestOnly@123';quotaGb=$quota} 400 | Out-Null
  }
  $box=Request POST '/mail/mailboxes' @{address=$prefix;password=' TestOnly@123 ';quotaGb=2;displayName='Audit temporary'}
  $notices=Request GET '/notifications' $null
  $notice=@($notices | Where-Object { $_.body -like "$prefix *" -and $_.title -eq 'Mailbox record created' })[0]
  if(!$notice -or $notice.read){throw 'Real unread notification was not created'}
  $read=Request POST "/notifications/$($notice.id)/read" $null
  if(!$read.read){throw 'Mark as read did not persist'}
  Request DELETE '/notifications/9223372036854775807' $null 404 | Out-Null
  if($box.PSObject.Properties.Name -contains 'passwordHash'){throw 'Password hash exposed'}
  Request POST '/mail/mailboxes' @{address=$prefix;password='TestOnly@123'} 400 | Out-Null
  Request PUT "/mail/mailboxes/$($box.id)" @{displayName='Changed';quotaGb=3} | Out-Null
  Request POST "/mail/mailboxes/$($box.id)/reset-password" @{password='short'} 400 | Out-Null
  Request POST "/mail/mailboxes/$($box.id)/reset-password" @{password='NewTestOnly@123'} | Out-Null
  $s=Request POST "/mail/mailboxes/$($box.id)/toggle" $null
  if($s.active){throw 'Suspend failed'}
  $s=Request POST "/mail/mailboxes/$($box.id)/toggle" $null
  if(!$s.active){throw 'Reactivate failed'}
  foreach($alias in @($null, '.', 'a..b', ('a'*65))) {
    Request POST '/mail/aliases' @{alias=$alias;target=$prefix} 400 | Out-Null
  }
  Request POST '/mail/aliases' @{alias=$prefix+'missing';target='nonexistent'} 400 | Out-Null
  $a=Request POST '/mail/aliases' @{alias=$prefix+'alias';target=$prefix.ToUpperInvariant()}
  Request POST '/mail/mailboxes' @{address=$a.alias;password='TestOnly@123'} 400 | Out-Null
  Request POST '/mail/aliases' @{alias=$prefix;target=$prefix} 400 | Out-Null
  Request DELETE "/mail/aliases/$($a.id)" $null 204
  Request DELETE "/mail/aliases/$($a.id)" $null 404 | Out-Null
  Request POST '/mail/aliases' @{alias=$prefix+'cascade';target=$prefix} | Out-Null
  Request DELETE "/mail/mailboxes/$($box.id)" $null 204
  $all=Request GET '/mail/aliases' $null
  if(@($all | Where-Object target -eq $prefix).Count){throw 'Orphan aliases remain'}
  Request PUT "/mail/mailboxes/$($box.id)" @{quotaGb=2} 404 | Out-Null
  $box=$null
} finally {
  if($box){Request DELETE "/mail/mailboxes/$($box.id)" $null 204}
  $notices=Request GET '/notifications' $null
  foreach($n in $notices) { if($n.body.StartsWith($prefix)) { Request DELETE "/notifications/$($n.id)" $null 204 } }
}
"PASS: $script:passed API checks; temporary test mailbox and aliases removed."
Request POST '/auth/logout' $null 204
