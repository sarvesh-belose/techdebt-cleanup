#requires -Version 5
<#
.SYNOPSIS
    First-run setup for TechDebt Cleanup. Writes dev.config.yml so the platform
    auto-registers your project + scan sources on the next start.

.DESCRIPTION
    Prompts for:
      * Project name, Azure Repos URL, default branch
      * Veracode credentials (apiId, apiKey, SCA workspace GUID, SAST app GUID)
      * SonarQube credentials (URL, token, project key, coverage threshold)
      * GitHub Copilot CLI enablement (offers to run `gh auth login` if missing)

    All inputs are optional — skip a source by pressing Enter on its first prompt.
    Re-run anytime; existing dev.config.yml is overwritten after confirmation.
#>

$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

function Read-Required([string]$prompt, [string]$default = $null) {
    while ($true) {
        $promptText = if ($default) { "$prompt [$default]" } else { $prompt }
        $value = Read-Host $promptText
        if ([string]::IsNullOrWhiteSpace($value)) {
            if ($default) { return $default }
            Write-Host "  required." -ForegroundColor Yellow
            continue
        }
        return $value.Trim()
    }
}

function Read-Optional([string]$prompt, [string]$default = $null) {
    $promptText = if ($default) { "$prompt [$default]" } else { "$prompt (blank to skip)" }
    $value = Read-Host $promptText
    if ([string]::IsNullOrWhiteSpace($value)) { return $default }
    return $value.Trim()
}

function Read-Secret([string]$prompt) {
    $secure = Read-Host -AsSecureString $prompt
    if (-not $secure -or $secure.Length -eq 0) { return $null }
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}

function Test-Command([string]$name) {
    $null -ne (Get-Command $name -ErrorAction SilentlyContinue)
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host " TechDebt Cleanup - first-run setup" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# ---- Existing config check ----
$configPath = Join-Path $PSScriptRoot 'dev.config.yml'
if (Test-Path $configPath) {
    Write-Host "dev.config.yml already exists." -ForegroundColor Yellow
    $overwrite = Read-Host "Overwrite? (y/N)"
    if ($overwrite -notmatch '^[Yy]') {
        Write-Host "Aborted. Edit dev.config.yml by hand or remove it and re-run." -ForegroundColor Yellow
        exit 0
    }
}

# ---- Prerequisites ----
Write-Host "--- Checking prerequisites ---"
$missing = @()
foreach ($tool in @('java','mvn','node','npm','git')) {
    if (Test-Command $tool) {
        Write-Host ("  {0,-6} ok" -f $tool) -ForegroundColor Green
    } else {
        Write-Host ("  {0,-6} MISSING" -f $tool) -ForegroundColor Red
        $missing += $tool
    }
}
if ($missing.Count -gt 0) {
    Write-Host ""
    Write-Host "Install the missing tools above before continuing." -ForegroundColor Red
    exit 1
}
Write-Host ""

# ---- Project ----
Write-Host "--- Project ---"
$projectName  = Read-Required "Project name (e.g. payments-svc)"
$repoUrl      = Read-Required "Azure Repos URL (https://.../_git/<repo>)"
$defaultBranch = Read-Required "Default branch" "main"
$description  = Read-Optional "Description"
Write-Host ""

# ---- Veracode ----
Write-Host "--- Veracode (leave first prompt blank to skip) ---"
$veraApiId = Read-Optional "Veracode API ID (hex)"
$veraSecret = $null
$veraScaGuid = $null
$veraSastGuid = $null
if ($veraApiId) {
    $veraApiKey = Read-Secret "Veracode API key (hex)"
    if ([string]::IsNullOrWhiteSpace($veraApiKey)) {
        Write-Host "  API key required when ID provided. Skipping Veracode." -ForegroundColor Yellow
    } else {
        $veraSecret = "$veraApiId`:$veraApiKey"
        $veraScaGuid  = Read-Optional "Veracode SCA workspace GUID"
        $veraSastGuid = Read-Optional "Veracode SAST application GUID"
    }
}
Write-Host ""

# ---- SonarQube ----
Write-Host "--- SonarQube (leave first prompt blank to skip) ---"
$sonarUrl   = Read-Optional "Sonar base URL (e.g. https://sonar.acme.com)"
$sonarToken = $null
$sonarKey   = $null
$sonarCov   = 80
if ($sonarUrl) {
    $sonarToken = Read-Secret "Sonar user token"
    $sonarKey   = Read-Optional "Sonar project key" $projectName
    $covInput   = Read-Optional "Coverage threshold %" "80"
    if ($covInput -match '^\d+$') { $sonarCov = [int]$covInput }
}
Write-Host ""

# ---- Copilot ----
Write-Host "--- GitHub Copilot CLI ---"
$copilotChoice = Read-Optional "Enable Copilot generator? (y/N)" "n"
$copilotEnabled = $copilotChoice -match '^[Yy]'
if ($copilotEnabled) {
    if (-not (Test-Command 'gh')) {
        Write-Host "  GitHub CLI ('gh') not found on PATH." -ForegroundColor Yellow
        Write-Host "  Install from https://cli.github.com/, then run 'gh auth login' before starting." -ForegroundColor Yellow
        $copilotEnabled = $false
    } else {
        $authStatus = & gh auth status 2>&1
        $authed = ($LASTEXITCODE -eq 0)
        if (-not $authed) {
            Write-Host "  gh is not authenticated."
            $runLogin = Read-Optional "Run 'gh auth login' now? (Y/n)" "y"
            if ($runLogin -match '^[Yy]') {
                Write-Host "  Launching 'gh auth login' — follow the device-flow prompts in this window."
                & gh auth login
                if ($LASTEXITCODE -ne 0) {
                    Write-Host "  gh auth login failed; leaving Copilot disabled." -ForegroundColor Yellow
                    $copilotEnabled = $false
                }
            } else {
                Write-Host "  Skipping login; Copilot will remain disabled until 'gh auth login' completes." -ForegroundColor Yellow
                $copilotEnabled = $false
            }
        }
    }
}
Write-Host ""

# ---- Build dev.config.yml ----
$copilotGen = if ($copilotEnabled) { "copilot" } else { "noop" }

$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine("# Generated by setup.ps1 on $(Get-Date -Format o)")
[void]$sb.AppendLine("# Re-run setup.bat to overwrite. Do NOT commit (already in .gitignore).")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("techdebt:")
[void]$sb.AppendLine("  dev:")
[void]$sb.AppendLine("    bootstrap:")
[void]$sb.AppendLine("      enabled: true")
[void]$sb.AppendLine("      copilotGenerator: $copilotGen")
[void]$sb.AppendLine("      projects:")
[void]$sb.AppendLine("        - name: $projectName")
[void]$sb.AppendLine("          repoUrl: $repoUrl")
[void]$sb.AppendLine("          defaultBranch: $defaultBranch")
if ($description) {
    [void]$sb.AppendLine("          description: $description")
}
[void]$sb.AppendLine("          triggerInitialScan: true")
[void]$sb.AppendLine("          scanSources:")

if ($veraSecret -and $veraScaGuid) {
    [void]$sb.AppendLine("            - source: VERACODE_SCA")
    [void]$sb.AppendLine("              externalProjectId: $veraScaGuid")
    [void]$sb.AppendLine("              apiBaseUrl: https://api.veracode.com")
    [void]$sb.AppendLine("              secret: '$veraSecret'")
    [void]$sb.AppendLine("              cronExpression: '0 0 2 * * *'")
}
if ($veraSecret -and $veraSastGuid) {
    [void]$sb.AppendLine("            - source: VERACODE_SAST")
    [void]$sb.AppendLine("              externalProjectId: $veraSastGuid")
    [void]$sb.AppendLine("              apiBaseUrl: https://api.veracode.com")
    [void]$sb.AppendLine("              secret: '$veraSecret'")
    [void]$sb.AppendLine("              cronExpression: '0 0 3 * * *'")
}
if ($sonarUrl -and $sonarToken) {
    [void]$sb.AppendLine("            - source: SONARQUBE")
    [void]$sb.AppendLine("              externalProjectId: $sonarKey")
    [void]$sb.AppendLine("              apiBaseUrl: $sonarUrl")
    [void]$sb.AppendLine("              secret: '$sonarToken'")
    [void]$sb.AppendLine("              coverageThresholdPct: $sonarCov")
    [void]$sb.AppendLine("              cronExpression: '0 30 2 * * *'")
}

# If no scan sources, drop the empty list rather than leaving a bare key.
$yaml = $sb.ToString()
if ($yaml -match 'scanSources:\s*\r?\n\s*$') {
    $yaml = $yaml -replace '\s+scanSources:\s*$', ''
}

Set-Content -LiteralPath $configPath -Value $yaml -Encoding UTF8 -NoNewline:$false

Write-Host "============================================" -ForegroundColor Green
Write-Host " dev.config.yml written" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host ""
Write-Host "Configured sources:" -ForegroundColor Cyan
if ($veraSecret -and $veraScaGuid)  { Write-Host "  - Veracode SCA"  -ForegroundColor Green }
if ($veraSecret -and $veraSastGuid) { Write-Host "  - Veracode SAST" -ForegroundColor Green }
if ($sonarUrl -and $sonarToken)     { Write-Host "  - SonarQube"     -ForegroundColor Green }
Write-Host "  - Copilot generator: $copilotGen" -ForegroundColor Green
Write-Host ""
Write-Host "Next:"
Write-Host "  1. Run build.bat fast    (one-time build; can skip if already built)"
Write-Host "  2. Run start.bat         (launches backend on 9200, frontend on 9211)"
Write-Host "  3. Open http://localhost:9211 — your project + scan results appear after the initial scan completes"
Write-Host ""
$launchNow = Read-Host "Run start.bat now? (y/N)"
if ($launchNow -match '^[Yy]') {
    Write-Host ""
    & "$PSScriptRoot\start.bat"
}
