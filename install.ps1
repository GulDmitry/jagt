# Installs jagt for the current user, no admin rights: a release jar, a Temurin 25 JRE, a launcher on the user's PATH.
# JAGT_VERSION pins a release (default: the latest), JAGT_HOME the install root, JAGT_BIN the launcher's
# folder, JAGT_INSTALL_FEED and JAGT_INSTALL_JRE_FEED the two download bases.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
if ($PSVersionTable.PSEdition -eq 'Desktop') {
  [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
}

function Get-Setting($name, $default) {
  $value = [Environment]::GetEnvironmentVariable($name)
  if ($value) { $value } else { $default }
}

function Fail($message) {
  [Console]::Error.WriteLine("install.ps1: $message")
  exit 1
}

function Get-Download($url, $file) {
  Invoke-WebRequest -Uri $url -OutFile $file -UseBasicParsing -TimeoutSec 600 | Out-Null
}

function Test-Checksum($file, $published) {
  $want = ("$published".Trim() -split '\s+')[0]
  if (-not $want) { Fail "no checksum published for $(Split-Path -Leaf $file)" }
  if ((Get-FileHash -Algorithm SHA256 -Path $file).Hash -ne $want) {
    Fail "checksum mismatch for $(Split-Path -Leaf $file), nothing installed"
  }
}

$isWindowsHost = ($null -eq $IsWindows) -or $IsWindows
$jreOs = if ($isWindowsHost) { 'windows' } elseif ($IsMacOS) { 'mac' } else { 'linux' }
$javaName = if ($isWindowsHost) { 'java.exe' } else { 'java' }

$feed = Get-Setting 'JAGT_INSTALL_FEED' 'https://github.com/GulDmitry/jagt/releases'
$jreFeed = Get-Setting 'JAGT_INSTALL_JRE_FEED' 'https://api.adoptium.net/v3/binary/latest/25/ga'
$localAppData = Get-Setting 'LOCALAPPDATA' (Join-Path $HOME '.local/share')
$root = Get-Setting 'JAGT_HOME' (Join-Path $localAppData 'jagt')
$bin = Get-Setting 'JAGT_BIN' (Join-Path $root 'bin')

$version = Get-Setting 'JAGT_VERSION' ''
if (-not $version) {
  try {
    Invoke-WebRequest -Uri "$feed/latest" -Method Head -MaximumRedirection 0 -UseBasicParsing -TimeoutSec 15 | Out-Null
    Fail "cannot read the latest release from $feed"
  } catch {
    $location = $_.Exception.Response.Headers.Location
    if (-not $location) { Fail "cannot read the latest release from $feed" }
    $version = ($location.ToString() -split '/')[-1]
  }
}
$version = $version -replace '^v', ''
if ($version -notmatch '^[0-9A-Za-z._-]+$') { Fail "invalid version '$version'" }

$staging = Join-Path ([IO.Path]::GetTempPath()) "jagt-install-$([guid]::NewGuid())"
New-Item -ItemType Directory -Path $staging | Out-Null
try {
  $release = "$feed/download/v$version"
  $jar = "jagt-$version.jar"
  try {
    Get-Download "$release/$jar" (Join-Path $staging $jar)
    Get-Download "$release/jagt-$version.sha256" (Join-Path $staging 'jar.sha256')
  } catch {
    Fail "cannot download $jar from ${release}: $($_.Exception.Message)"
  }
  $line = Get-Content (Join-Path $staging 'jar.sha256') | Where-Object { $_ -match " \*?$([regex]::Escape($jar))$" } | Select-Object -First 1
  Test-Checksum (Join-Path $staging $jar) $line

  $runtime = Join-Path $root 'runtime'
  if (-not (Test-Path (Join-Path $runtime "bin/$javaName"))) {
    $archive = Join-Path $staging 'jre.zip'
    $jreUrl = "$jreFeed/$jreOs/x64/jre/hotspot/normal/eclipse"
    try {
      $response = Invoke-WebRequest -Uri $jreUrl -OutFile $archive -UseBasicParsing -TimeoutSec 600 -PassThru
      $final = if ($response.BaseResponse.RequestMessage) { $response.BaseResponse.RequestMessage.RequestUri } else { $response.BaseResponse.ResponseUri }
      Get-Download "$final.sha256.txt" (Join-Path $staging 'jre.sha256')
    } catch {
      Fail "cannot download the Java runtime from ${jreUrl}: $($_.Exception.Message)"
    }
    Test-Checksum $archive (Get-Content (Join-Path $staging 'jre.sha256') | Select-Object -First 1)
    $unpacked = Join-Path $staging 'unpacked'
    Expand-Archive -Path $archive -DestinationPath $unpacked
    $top = @(Get-ChildItem -Path $unpacked -Directory)
    if ($top.Count -ne 1 -or -not (Test-Path (Join-Path $top[0].FullName "bin/$javaName"))) {
      Fail 'the Java runtime holds no java'
    }
    New-Item -ItemType Directory -Force -Path $root | Out-Null
    if (Test-Path $runtime) { Remove-Item -Recurse -Force $runtime }
    Move-Item -Path $top[0].FullName -Destination $runtime
  }

  $versions = Join-Path $root 'versions'
  $target = Join-Path $versions $version
  if (Test-Path $target) { Remove-Item -Recurse -Force $target }
  New-Item -ItemType Directory -Force -Path $target, $bin | Out-Null
  Move-Item -Path (Join-Path $staging $jar) -Destination (Join-Path $target 'jagt.jar')
  Set-Content -Path (Join-Path $root 'current') -Value $version -Encoding ascii

  $launcher = Join-Path $bin 'jagt.cmd'
  $lines = @(
    '@echo off',
    'setlocal',
    "set /p JAGT_VERSION=<`"$(Join-Path $root 'current')`"",
    "`"$(Join-Path $runtime "bin/$javaName")`" -jar `"$(Join-Path $versions '%JAGT_VERSION%/jagt.jar')`" %*"
  )
  Set-Content -Path "$launcher.new" -Value $lines -Encoding ascii
  Move-Item -Force -Path "$launcher.new" -Destination $launcher

  Write-Output "jagt $version installed in $root, launcher $launcher"
  $userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
  if (-not (@("$userPath" -split ';') -contains $bin)) {
    [Environment]::SetEnvironmentVariable('Path', ((@($userPath, $bin) | Where-Object { $_ }) -join ';'), 'User')
    Write-Output "added $bin to your PATH, open a new terminal to run jagt"
  }
} finally {
  Remove-Item -Recurse -Force $staging -ErrorAction SilentlyContinue
}
