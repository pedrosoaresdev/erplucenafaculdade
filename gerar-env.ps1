# Gera o arquivo .env com chaves aleatórias seguras (Windows PowerShell 5+ ou PowerShell 7).
# Uso, na pasta do projeto:  powershell -ExecutionPolicy Bypass -File .\gerar-env.ps1
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (Test-Path '.env') { Write-Host 'O arquivo .env já existe. Nada foi alterado (apague-o para gerar de novo).'; exit 0 }

function Nova-Chave([int]$bytes) {
    $b = New-Object byte[] $bytes
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($b); $rng.Dispose()
    return [Convert]::ToBase64String($b)
}

$conteudo = Get-Content '.env.example' -Raw -Encoding UTF8
$conteudo = $conteudo -replace '(?m)^ERP_DB_PASSWORD=.*$', ('ERP_DB_PASSWORD=' + ((Nova-Chave 24) -replace '[+/=]', ''))
$conteudo = $conteudo -replace '(?m)^ERP_JWT_SECRET=.*$', ('ERP_JWT_SECRET=' + (Nova-Chave 48))
$conteudo = $conteudo -replace '(?m)^ERP_ENCRYPTION_KEY=.*$', ('ERP_ENCRYPTION_KEY=' + (Nova-Chave 32))
[System.IO.File]::WriteAllText((Join-Path $PSScriptRoot '.env'), $conteudo, (New-Object System.Text.UTF8Encoding $false))
Write-Host 'Arquivo .env criado com chaves seguras. Guarde uma cópia dele em local seguro (ex.: gerenciador de senhas).'
Write-Host 'Próximo passo:  docker compose up --build'
