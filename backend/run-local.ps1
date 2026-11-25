$ErrorActionPreference = "Stop"
$envFile = Join-Path $PSScriptRoot "..\.env"
if (-not (Test-Path $envFile)) {
    Write-Error "No se encontró el archivo .env en la raíz del proyecto."
    exit 1
}
Get-Content $envFile | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object {
    $n, $v = $_ -split '=', 2
    [Environment]::SetEnvironmentVariable($n.Trim(), $v.Trim(), "Process")
}
if (-not $env:DB_PASSWORD -or $env:BOOTSTRAP_ODONTOLOGO_PASSWORD.Length -lt 8) {
    Write-Error "Faltan DB_PASSWORD o BOOTSTRAP_ODONTOLOGO_PASSWORD (mínimo 8 caracteres) en .env"
    exit 1
}
Write-Host "Conectando a $env:DB_URL ..."
Set-Location $PSScriptRoot
# Los argumentos se reenvían a Gradle; por ejemplo, .\run-local.ps1 -PwithFrontend (demo con el frontend).
.\gradlew.bat bootRun @args
