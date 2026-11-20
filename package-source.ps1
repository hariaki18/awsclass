param([string]$OutputDirectory = (Join-Path $PSScriptRoot '..\handover'))
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath($PSScriptRoot)
$verification=Get-Content -Raw (Join-Path $root 'verification-results.json') | ConvertFrom-Json
if(-not $verification.passed -or @($verification.results | Where-Object {-not $_.passed}).Count){throw 'Run npm run verify successfully before packaging.'}
$documents=@('README.md','HANDOVER.md','API.md','TEST-RESULTS.md','KNOWN-LIMITATIONS.md','TESTING.md','AUTH-SETUP.md','DATABASE-MIGRATIONS.md','HEALTH-LOGGING.md','MAIL-PROVIDER-INTEGRATION.md','MYSQL-SETUP.md')
$documents+='LOCAL-MAIL.md'
$documents+='GMAIL-SMTP.md'
$documents+=@('GIT-PUSH.md','BACKEND-COMPLETE-GUIDE.md')
$rootFiles=@('.env.example','.gitignore','package.json','verification-results.json','package-source.ps1')+$documents
$files=[Collections.Generic.List[string]]::new()
foreach($file in $rootFiles){$p=Join-Path $root $file;if(-not(Test-Path -LiteralPath $p)){throw "Missing handover file: $file"};$files.Add($p)}
Get-ChildItem -LiteralPath $root -File | Where-Object {$_.Extension -in @('.mjs','.ps1')} | ForEach-Object {if(-not $files.Contains($_.FullName)){$files.Add($_.FullName)}}
foreach($directory in @('frontend/src','frontend/public','frontend/test','backend/src','backend/.mvn')) {
  Get-ChildItem -LiteralPath (Join-Path $root $directory) -Recurse -File | ForEach-Object {$files.Add($_.FullName)}
}
foreach($file in @('frontend/package.json','frontend/package-lock.json','frontend/index.html','frontend/vite.config.js','backend/pom.xml','backend/mvnw','backend/mvnw.cmd')){$files.Add((Join-Path $root $file))}
$manifest=@($files | Sort-Object -Unique | ForEach-Object {
  $relative=$_.Substring($root.Length+1).Replace('\','/')
  if($relative -match '(^|/)(data|node_modules|target|dist|logs|\.backups|\.git)(/|$)' -or $relative -match '\.(mv|lock)\.db$|(^|/)\.jwt-secret$'){throw "Forbidden archive entry: $relative"}
  [pscustomobject]@{path=$relative;sha256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToLowerInvariant();size=(Get-Item -LiteralPath $_).Length}
})
$output=[IO.Path]::GetFullPath($OutputDirectory);New-Item -ItemType Directory -Path $output -Force | Out-Null
$zipPath=Join-Path $output ('XpertsEdgeHrms-source-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'.zip')
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive=[IO.Compression.ZipFile]::Open($zipPath,[IO.Compression.ZipArchiveMode]::Create)
try {
  foreach($entry in $manifest){[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,(Join-Path $root $entry.path),('XpertsEdgeHrms/'+$entry.path),[IO.Compression.CompressionLevel]::Optimal)|Out-Null}
  $item=$archive.CreateEntry('XpertsEdgeHrms/SOURCE-MANIFEST.json');$writer=[IO.StreamWriter]::new($item.Open());try{$writer.Write(($manifest | ConvertTo-Json -Depth 4))}finally{$writer.Dispose()}
}finally{$archive.Dispose()}
$hash=(Get-FileHash -LiteralPath $zipPath -Algorithm SHA256).Hash.ToLowerInvariant()
Set-Content -LiteralPath ($zipPath+'.sha256') -Value ($hash+'  '+[IO.Path]::GetFileName($zipPath))
Write-Output "Created $zipPath ($($manifest.Count) source/doc files + manifest)"
Write-Output "SHA-256: $hash"

