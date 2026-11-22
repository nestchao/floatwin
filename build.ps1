param(
    [switch]$Install,
    [switch]$Run,
    [switch]$Clean
)

$ErrorActionPreference = 'Stop'

$root     = $PSScriptRoot
$appDir   = Join-Path $root 'app'
$buildDir = Join-Path $root 'build'
$distDir  = Join-Path $root 'dist'

$sdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT }
           elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME }
           else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }

$btRoot = Join-Path $sdkRoot 'build-tools'
if (-not (Test-Path $btRoot)) { throw "Android build-tools not found under $btRoot" }
$bt = (Get-ChildItem $btRoot -Directory | Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1).FullName

$platformsRoot = Join-Path $sdkRoot 'platforms'
$preferred = Join-Path $platformsRoot 'android-36\android.jar'
$platform = if (Test-Path $preferred) { $preferred }
            else { (Get-ChildItem $platformsRoot -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'android.jar') } |
                    Sort-Object Name -Descending | Select-Object -First 1 | ForEach-Object { Join-Path $_.FullName 'android.jar' }) }
if (-not $platform -or -not (Test-Path $platform)) { throw "android.jar not found under $platformsRoot" }

$exe = if ($IsWindows) { '.exe' } else { '' }

$aapt2     = Join-Path $bt "aapt2$exe"
$zipalign  = Join-Path $bt "zipalign$exe"
$apksigner = Join-Path $bt "apksigner.bat"
$d8        = Join-Path $bt "d8.bat"
$javac     = (Get-Command javac).Source
$keytool   = (Get-Command keytool).Source

foreach ($tool in @($aapt2, $platform, $zipalign, $apksigner, $d8, $javac, $keytool)) {
    if (-not (Test-Path $tool)) { throw "missing tool: $tool" }
}

if ($Clean -and (Test-Path $buildDir)) {
    Remove-Item $buildDir -Recurse -Force
}

$genDir     = Join-Path $buildDir 'gen'
$classesDir = Join-Path $buildDir 'classes'
$dexDir     = Join-Path $buildDir 'dex'
$resZip     = Join-Path $buildDir 'res.zip'
$unsigned   = Join-Path $buildDir 'app-unsigned.apk'
$aligned    = Join-Path $buildDir 'app-aligned.apk'
$signed     = Join-Path $distDir 'floatwin.apk'
$keystore   = Join-Path $root 'debug.keystore'

foreach ($d in @($buildDir, $distDir, $genDir, $classesDir, $dexDir)) {
    New-Item -ItemType Directory -Force -Path $d | Out-Null
}

Write-Host "==> compile resources"
& $aapt2 compile --dir (Join-Path $appDir 'res') -o $resZip
if ($LASTEXITCODE -ne 0) { throw 'aapt2 compile failed' }

Write-Host "==> link resources"
& $aapt2 link `
    -o $unsigned `
    -I $platform `
    --manifest (Join-Path $appDir 'AndroidManifest.xml') `
    -R $resZip `
    --java $genDir `
    --min-sdk-version 29 `
    --target-sdk-version 36 `
    --version-code 3 `
    --version-name '1.1.1' `
    --auto-add-overlay
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link failed' }

Write-Host "==> compile java"
$sources = @()
$sources += Get-ChildItem (Join-Path $appDir 'java') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$sources += Get-ChildItem $genDir -Recurse -Filter *.java | ForEach-Object { $_.FullName }

& $javac `
    -encoding UTF-8 `
    -source 8 -target 8 `
    -bootclasspath $platform `
    -classpath $platform `
    -d $classesDir `
    $sources
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

Write-Host "==> dex"
$classFiles = Get-ChildItem $classesDir -Recurse -Filter *.class | ForEach-Object { $_.FullName }
& $d8 --release --lib $platform --min-api 29 --output $dexDir $classFiles
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }

Write-Host "==> package classes.dex"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open($unsigned, [System.IO.Compression.ZipArchiveMode]::Update)
try {
    $old = $zip.GetEntry('classes.dex')
    if ($old) { $old.Delete() }
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
        $zip, (Join-Path $dexDir 'classes.dex'), 'classes.dex') | Out-Null
} finally {
    $zip.Dispose()
}

Write-Host "==> zipalign"
& $zipalign -f -p 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }

if (-not (Test-Path $keystore)) {
    Write-Host "==> generate debug keystore"
    & $keytool -genkeypair -keystore $keystore -alias androiddebugkey `
        -storepass android -keypass android `
        -dname 'CN=Android Debug,O=Android,C=US' `
        -keyalg RSA -keysize 2048 -validity 10000 | Out-Null
}

Write-Host "==> sign"
& $apksigner sign --ks $keystore --ks-pass pass:android --key-pass pass:android --out $signed $aligned
if ($LASTEXITCODE -ne 0) { throw 'apksigner failed' }

Write-Host "==> verify"
& $apksigner verify --print-certs $signed | Select-Object -First 3

$size = [math]::Round((Get-Item $signed).Length / 1KB, 1)
Write-Host "built: $signed ($size KB)"

if ($Install -or $Run) {
    Write-Host "==> install"
    & adb install -r -t $signed
    if ($LASTEXITCODE -ne 0) {
        Write-Host "==> adb install blocked (MIUI?), retry with root pm install"
        & adb push $signed /data/local/tmp/floatwin.apk
        & adb shell su -c 'pm install -r -t /data/local/tmp/floatwin.apk'
        if ($LASTEXITCODE -ne 0) { throw 'root pm install failed' }
    }

    Write-Host "==> grant overlay"
    & adb shell su -c 'appops set com.local.floatwin SYSTEM_ALERT_WINDOW allow'
}

if ($Run) {
    Write-Host "==> launch"
    & adb shell am start -n com.local.floatwin/.MainActivity
}
