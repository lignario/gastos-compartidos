param([Parameter(Mandatory=$true)][string]$Keytool)
$ErrorActionPreference = 'Stop'
$projectDir = Split-Path $PSScriptRoot -Parent
$keyDir = Join-Path $projectDir '.signing'
$keyPath = Join-Path $keyDir 'upload.jks'
$propertiesPath = Join-Path $projectDir 'signing.properties'
if ((Test-Path -LiteralPath $keyPath) -or (Test-Path -LiteralPath $propertiesPath)) {
    throw 'Signing material already exists; refusing to overwrite it.'
}
New-Item -ItemType Directory -Path $keyDir -Force | Out-Null
$secretBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($secretBytes)
$rng.Dispose()
$env:GASTOS_UPLOAD_PASSWORD = [Convert]::ToBase64String($secretBytes)
try {
    & $Keytool -genkeypair -keystore $keyPath -storetype JKS -alias upload -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Gastos Compartidos Upload' -storepass:env GASTOS_UPLOAD_PASSWORD -keypass:env GASTOS_UPLOAD_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Key generation failed.' }
    $lines = @('storeFile=.signing/upload.jks', 'keyAlias=upload', "storePassword=$env:GASTOS_UPLOAD_PASSWORD", "keyPassword=$env:GASTOS_UPLOAD_PASSWORD")
    [IO.File]::WriteAllLines($propertiesPath, $lines)
    & $Keytool -exportcert -rfc -keystore $keyPath -alias upload -storepass:env GASTOS_UPLOAD_PASSWORD -file (Join-Path $keyDir 'upload-certificate.pem')
    if ($LASTEXITCODE -ne 0) { throw 'Certificate export failed.' }
    Write-Output 'Upload key created. Back up .signing and signing.properties in a secure location.'
} finally {
    Remove-Item Env:GASTOS_UPLOAD_PASSWORD -ErrorAction SilentlyContinue
}
