$ErrorActionPreference = 'Stop'

$root = $PSScriptRoot
$projectRoot = Split-Path $root -Parent
$jdk = Join-Path $root 'jdk-17.0.21+9'
$bt = Join-Path $root 'sdk\android-14'
$platform = Join-Path $root 'sdk\android-35'

$env:JAVA_HOME = $jdk
$env:Path = (Join-Path $jdk 'bin') + ';' + $env:Path

$build = Join-Path $root 'build'
Remove-Item $build -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$build\gen", "$build\classes", "$build\dex", (Join-Path $root 'assets') | Out-Null

Copy-Item (Join-Path $projectRoot 'weather.html') (Join-Path $root 'assets\weather.html') -Force

Write-Host '=> aapt2 compile'
& "$bt\aapt2.exe" compile --dir (Join-Path $root 'res') -o "$build\res.zip"
if ($LASTEXITCODE -ne 0) { throw 'aapt2 compile failed' }

Write-Host '=> aapt2 link'
& "$bt\aapt2.exe" link -o "$build\base.apk" -I "$platform\android.jar" --manifest (Join-Path $root 'AndroidManifest.xml') -A (Join-Path $root 'assets') --java "$build\gen" "$build\res.zip" --auto-add-overlay
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link failed' }

Write-Host '=> javac'
$sources = @(Get-ChildItem -Path (Join-Path $root 'java'), "$build\gen" -Recurse -Filter *.java | ForEach-Object { $_.FullName })
& "$jdk\bin\javac.exe" -encoding UTF-8 -source 8 -target 8 -classpath "$platform\android.jar" -d "$build\classes" $sources
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

Write-Host '=> d8'
$classes = @(Get-ChildItem -Path "$build\classes" -Recurse -Filter *.class | ForEach-Object { $_.FullName })
& "$bt\d8.bat" --lib "$platform\android.jar" --min-api 23 --output "$build\dex" $classes
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }

Write-Host '=> add classes.dex'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open("$build\base.apk", 'Update')
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, "$build\dex\classes.dex", 'classes.dex', [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
$zip.Dispose()

Write-Host '=> zipalign'
& "$bt\zipalign.exe" -f -p 4 "$build\base.apk" "$build\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }

Write-Host '=> keystore'
$keystore = Join-Path $root 'debug.keystore'
if (-not (Test-Path $keystore)) {
    & "$jdk\bin\keytool.exe" -genkeypair -v -keystore $keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
    if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }
}

Write-Host '=> apksigner'
& "$bt\apksigner.bat" sign --ks $keystore --ks-pass pass:android --key-pass pass:android --out "$build\PixelWeather.apk" "$build\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'apksigner failed' }

Write-Host '=> verify'
& "$bt\apksigner.bat" verify --print-certs "$build\PixelWeather.apk"
if ($LASTEXITCODE -ne 0) { throw 'apksigner verify failed' }
& "$bt\aapt2.exe" dump badging "$build\PixelWeather.apk"

$out = Join-Path $projectRoot 'PixelWeather.apk'
Copy-Item "$build\PixelWeather.apk" $out -Force
Write-Host "DONE: $out"
Get-Item $out | Select-Object FullName, Length, LastWriteTime | Format-List
