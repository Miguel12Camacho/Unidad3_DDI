param(
    [string]$OutputDirectory = "build/webos"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$packageCommand = Get-Command ares-package -ErrorAction SilentlyContinue

if (-not $packageCommand) {
    throw "No se encontró ares-package. Instala el CLI oficial: npm install --global @webos-tools/cli"
}

Push-Location $root
try {
    python scripts/prepare_tv_apps.py
    New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
    & ares-package platforms/webos -o $OutputDirectory
    if ($LASTEXITCODE -ne 0) {
        throw "ares-package terminó con código $LASTEXITCODE"
    }
    $packages = Get-ChildItem -LiteralPath $OutputDirectory -Filter "*.ipk"
    if (-not $packages) {
        throw "ares-package no generó ningún archivo .ipk en $OutputDirectory"
    }
    $packages | Select-Object -ExpandProperty FullName
} finally {
    Pop-Location
}
