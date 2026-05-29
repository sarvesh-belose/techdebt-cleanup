#requires -Version 5
<#
.SYNOPSIS
    Interactive setup for TechDebt Cleanup. Writes dev.config.yml so the platform
    auto-registers your projects + scan sources on the next start.

.PARAMETER Mode
    'add'   -- append exactly one new project to an existing dev.config.yml
              (use this when joining a new team's project)
    (none)  -- fresh setup. If dev.config.yml exists, prompts Add / Overwrite / Quit.

.DESCRIPTION
    Fresh setup loops to onboard as many projects as you want in one session.
    Each project gets its own Veracode + Sonar credentials so you can mix
    (e.g. payments-team Veracode for one service, risk-team Veracode for another).

    Tokens are entered via SecureString so they don't appear in shell history.
    dev.config.yml is gitignored.
#>

param([string]$Mode = '')

$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

# ─── Helpers ────────────────────────────────────────────────────────────────

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

function Read-YesNo([string]$prompt, [string]$default = 'n') {
    $shown = if ($default -eq 'y') { "$prompt (Y/n)" } else { "$prompt (y/N)" }
    $value = Read-Host $shown
    if ([string]::IsNullOrWhiteSpace($value)) { return ($default -eq 'y') }
    return ($value -match '^[Yy]')
}

function Test-Command([string]$name) {
    $null -ne (Get-Command $name -ErrorAction SilentlyContinue)
}

function Banner([string]$text) {
    Write-Host ""
    Write-Host ("--- {0} ---" -f $text) -ForegroundColor Cyan
}

# ─── Validation helpers ────────────────────────────────────────────────────

# PowerShell 5 defaults to TLS 1.0; banking enterprises typically require 1.2+.
[Net.ServicePointManager]::SecurityProtocol = `
    [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12

function Hex-Decode([string]$hex) {
    if ([string]::IsNullOrWhiteSpace($hex)) { return ,@() }
    if ($hex.Length % 2 -ne 0) { throw "hex string length must be even" }
    $bytes = New-Object byte[] ($hex.Length / 2)
    for ($i = 0; $i -lt $bytes.Length; $i++) {
        $bytes[$i] = [Convert]::ToByte($hex.Substring($i * 2, 2), 16)
    }
    return ,$bytes
}

function Hex-Encode([byte[]]$bytes) {
    ($bytes | ForEach-Object { '{0:x2}' -f $_ }) -join ''
}

function Invoke-HmacSha256([byte[]]$data, [byte[]]$key) {
    $hmac = [System.Security.Cryptography.HMACSHA256]::new()
    try {
        $hmac.Key = $key
        return ,$hmac.ComputeHash($data)
    } finally {
        $hmac.Dispose()
    }
}

# Mirror of backend's VeracodeHmacSigner - same key-derivation chain.
function New-VeracodeHmacHeader {
    param([string]$ApiId, [string]$ApiKey, [string]$HttpMethod, [string]$UriPath)
    $ts    = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $rng   = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $nonceBytes = New-Object byte[] 16
    $rng.GetBytes($nonceBytes); $rng.Dispose()
    $nonce = Hex-Encode $nonceBytes

    $data        = "$ApiId$ts$nonce$HttpMethod$UriPath"
    $apiKeyBytes = Hex-Decode $ApiKey
    $k1 = Invoke-HmacSha256 (Hex-Decode $nonce) $apiKeyBytes
    $k2 = Invoke-HmacSha256 ([Text.Encoding]::UTF8.GetBytes("$ts")) $k1
    $k3 = Invoke-HmacSha256 ([Text.Encoding]::UTF8.GetBytes('vcode_request_version_1')) $k2
    $sig = Invoke-HmacSha256 ([Text.Encoding]::UTF8.GetBytes($data)) $k3
    return "VERACODE-HMAC-SHA-256 id=$ApiId,ts=$ts,nonce=$nonce,sig=$(Hex-Encode $sig)"
}

function Get-HttpStatusFromException($err) {
    # PS5 throws WebException; PS7 throws HttpResponseException. Both expose Response.
    try {
        $resp = $err.Exception.Response
        if ($resp -and $resp.StatusCode) {
            return [int]$resp.StatusCode
        }
    } catch { }
    return $null
}

function Test-RepoUrl([string]$url) {
    if (-not $url) { return @{ ok = $false; reason = "empty" } }
    if ($url -notmatch '^https?://[^\s]+/_git/[^\s]+$') {
        return @{ ok = $false; reason = "expected https://.../_git/<repo>" }
    }
    return @{ ok = $true; reason = "format ok" }
}

function Test-ProjectName([string]$name) {
    if (-not $name) { return @{ ok = $false; reason = "empty" } }
    if ($name -notmatch '^[a-zA-Z0-9][a-zA-Z0-9._\-]{0,199}$') {
        return @{ ok = $false; reason = "use alphanumeric / dot / dash / underscore, max 200 chars" }
    }
    $cfg = Join-Path $PSScriptRoot 'dev.config.yml'
    if (Test-Path $cfg) {
        $existing = Select-String -Path $cfg -Pattern '^\s+-\s+name:\s*(.+?)\s*$' -AllMatches
        foreach ($m in $existing) {
            $existingName = ($m.Matches[0].Groups[1].Value).Trim()
            if ($existingName -ieq $name) {
                return @{ ok = $false; reason = "already in dev.config.yml" }
            }
        }
    }
    return @{ ok = $true; reason = "ok" }
}

function Test-VeracodeCredentials {
    param([string]$ApiId, [string]$ApiKey, [string]$BaseUrl = 'https://api.veracode.com')
    if ($ApiId -notmatch '^[0-9a-fA-F]{8,}$') {
        return @{ ok = $false; reason = "API ID must be hex (got $($ApiId.Length) chars)" }
    }
    if ($ApiKey -notmatch '^[0-9a-fA-F]{16,}$') {
        return @{ ok = $false; reason = "API key must be hex (got $($ApiKey.Length) chars)" }
    }
    $path = '/appsec/v1/applications?size=1'
    try {
        $auth = New-VeracodeHmacHeader -ApiId $ApiId -ApiKey $ApiKey -HttpMethod 'GET' -UriPath $path
        $null = Invoke-WebRequest -Uri "$BaseUrl$path" `
            -Headers @{ Authorization = $auth } `
            -UseBasicParsing -TimeoutSec 10 -ErrorAction Stop
        return @{ ok = $true; reason = "live API ping accepted" }
    } catch {
        $status = Get-HttpStatusFromException $_
        if ($status -in 401, 403) {
            return @{ ok = $false; reason = "Veracode rejected the credentials (HTTP $status)" }
        }
        if ($status) {
            return @{ ok = $false; reason = "Veracode returned HTTP $status" }
        }
        return @{ ok = $false; reason = "could not reach $BaseUrl -- $($_.Exception.Message)" }
    }
}

function Test-SonarCredentials {
    param([string]$Url, [string]$Token)
    if ($Url -notmatch '^https?://') {
        return @{ ok = $false; reason = "URL must start with http:// or https://" }
    }
    $clean = $Url.TrimEnd('/')
    try {
        $ping = Invoke-WebRequest -Uri "$clean/api/system/ping" `
            -UseBasicParsing -TimeoutSec 10 -ErrorAction Stop
        if ($ping.Content.Trim() -ne 'pong') {
            return @{ ok = $false; reason = "$clean responded but did not say 'pong' (is this really SonarQube?)" }
        }
    } catch {
        return @{ ok = $false; reason = "could not reach $clean -- $($_.Exception.Message)" }
    }
    try {
        $authHeader = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("$Token`:"))
        $resp = Invoke-RestMethod -Uri "$clean/api/authentication/validate" `
            -Headers @{ Authorization = $authHeader } `
            -TimeoutSec 10 -ErrorAction Stop
        if ($resp.valid) {
            return @{ ok = $true; reason = "token valid" }
        }
        return @{ ok = $false; reason = "Sonar rejected the token (valid=false)" }
    } catch {
        $status = Get-HttpStatusFromException $_
        if ($status -in 401, 403) {
            return @{ ok = $false; reason = "Sonar rejected the token (HTTP $status)" }
        }
        return @{ ok = $false; reason = "token validation failed: $($_.Exception.Message)" }
    }
}

function Confirm-OnFailure {
    param([string]$Kind, [string]$Reason)
    Write-Host ("  [VALIDATION FAILED] {0}: {1}" -f $Kind, $Reason) -ForegroundColor Red
    Write-Host "  [R]etry  [S]ave anyway  [Q]uit setup" -ForegroundColor Yellow
    while ($true) {
        $choice = (Read-Host "  Choice").Trim().ToUpper()
        switch ($choice) {
            'R' { return 'retry' }
            'S' { return 'save' }
            'Q' { Write-Host "Aborted." -ForegroundColor Yellow; exit 0 }
            default { Write-Host "  Enter R, S, or Q." -ForegroundColor Yellow }
        }
    }
}

# ─── Prompts ────────────────────────────────────────────────────────────────

function Prompt-Project([hashtable]$Previous = $null) {
    Banner "Project"

    while ($true) {
        $projectName = Read-Required "Project name (e.g. payments-svc)"
        $r = Test-ProjectName $projectName
        if ($r.ok) { break }
        $decision = Confirm-OnFailure "project name" $r.reason
        if ($decision -eq 'save') { break }
    }

    while ($true) {
        $repoUrl = Read-Required "Azure Repos URL (https://.../_git/<repo>)"
        $r = Test-RepoUrl $repoUrl
        if ($r.ok) { break }
        $decision = Confirm-OnFailure "repo URL" $r.reason
        if ($decision -eq 'save') { break }
    }

    $defaultBranch = Read-Required "Default branch" "main"
    $description   = Read-Optional "Description"

    # --- Veracode ---
    Banner "Veracode for $projectName (blank to skip)"
    $reuseVera = $false
    if ($Previous -and $Previous.veracode.secret) {
        $reuseVera = Read-YesNo "Reuse Veracode apiId:apiKey from previous project?" "y"
    }
    $veracode = @{ secret = $null; scaGuid = $null; sastGuid = $null }
    if ($reuseVera) {
        $veracode.secret = $Previous.veracode.secret
        Write-Host "  (reusing previous Veracode credentials; skipping re-validation)" -ForegroundColor DarkGray
    } else {
        while ($true) {
            $apiId = Read-Optional "Veracode API ID (hex)"
            if (-not $apiId) { break }
            $apiKey = Read-Secret "Veracode API key (hex)"
            if ([string]::IsNullOrWhiteSpace($apiKey)) {
                Write-Host "  API key required when ID provided; skipping Veracode for this project." -ForegroundColor Yellow
                break
            }
            Write-Host "  Validating Veracode credentials live (HMAC-signed ping)..." -ForegroundColor DarkGray
            $r = Test-VeracodeCredentials -ApiId $apiId -ApiKey $apiKey
            if ($r.ok) {
                Write-Host ("  [OK] {0}" -f $r.reason) -ForegroundColor Green
                $veracode.secret = "$apiId`:$apiKey"
                break
            }
            $decision = Confirm-OnFailure "Veracode" $r.reason
            if ($decision -eq 'save') {
                $veracode.secret = "$apiId`:$apiKey"
                break
            }
        }
    }
    if ($veracode.secret) {
        $veracode.scaGuid  = Read-Optional "Veracode SCA workspace GUID (blank to skip SCA)"
        $veracode.sastGuid = Read-Optional "Veracode SAST application GUID (blank to skip SAST)"
    }

    # --- Sonar ---
    Banner "SonarQube for $projectName (blank to skip)"
    $reuseSonar = $false
    if ($Previous -and $Previous.sonar.token) {
        $reuseSonar = Read-YesNo "Reuse SonarQube URL+token from previous project?" "y"
    }
    $sonar = @{ url = $null; token = $null; key = $null; cov = 80 }
    if ($reuseSonar) {
        $sonar.url = $Previous.sonar.url
        $sonar.token = $Previous.sonar.token
        Write-Host "  (reusing previous Sonar credentials; skipping re-validation)" -ForegroundColor DarkGray
    } else {
        while ($true) {
            $url = Read-Optional "Sonar base URL (e.g. https://sonar.acme.com)"
            if (-not $url) { break }
            $token = Read-Secret "Sonar user token"
            if ([string]::IsNullOrWhiteSpace($token)) {
                Write-Host "  Sonar token required; skipping Sonar for this project." -ForegroundColor Yellow
                break
            }
            Write-Host "  Validating Sonar credentials live..." -ForegroundColor DarkGray
            $r = Test-SonarCredentials -Url $url -Token $token
            if ($r.ok) {
                Write-Host ("  [OK] {0}" -f $r.reason) -ForegroundColor Green
                $sonar.url = $url
                $sonar.token = $token
                break
            }
            $decision = Confirm-OnFailure "Sonar" $r.reason
            if ($decision -eq 'save') {
                $sonar.url = $url
                $sonar.token = $token
                break
            }
        }
    }
    if ($sonar.url -and $sonar.token) {
        $sonar.key = Read-Optional "Sonar project key" $projectName
        $covInput  = Read-Optional "Coverage threshold %" "80"
        if ($covInput -match '^\d+$') { $sonar.cov = [int]$covInput }
    }

    return @{
        name          = $projectName
        repoUrl       = $repoUrl
        defaultBranch = $defaultBranch
        description   = $description
        veracode      = $veracode
        sonar         = $sonar
    }
}

# ─── YAML rendering ────────────────────────────────────────────────────────

function Render-Header([string]$copilotGen) {
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.AppendLine("# Generated by setup.ps1 on $(Get-Date -Format o)")
    [void]$sb.AppendLine("# Re-run setup.bat to add more projects. Do NOT commit (already in .gitignore).")
    [void]$sb.AppendLine("")
    [void]$sb.AppendLine("techdebt:")
    [void]$sb.AppendLine("  dev:")
    [void]$sb.AppendLine("    bootstrap:")
    [void]$sb.AppendLine("      enabled: true")
    [void]$sb.AppendLine("      copilotGenerator: $copilotGen")
    [void]$sb.AppendLine("      projects:")
    return $sb.ToString()
}

function Render-Project([hashtable]$p) {
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.AppendLine("        - name: $($p.name)")
    [void]$sb.AppendLine("          repoUrl: $($p.repoUrl)")
    [void]$sb.AppendLine("          defaultBranch: $($p.defaultBranch)")
    if ($p.description) {
        [void]$sb.AppendLine("          description: $($p.description)")
    }
    [void]$sb.AppendLine("          triggerInitialScan: true")

    $hasAnySource = ($p.veracode.secret -and ($p.veracode.scaGuid -or $p.veracode.sastGuid)) `
                    -or ($p.sonar.url -and $p.sonar.token)
    if (-not $hasAnySource) {
        [void]$sb.AppendLine("          # no scan sources configured -- add via UI or re-run setup add")
        return $sb.ToString()
    }
    [void]$sb.AppendLine("          scanSources:")
    if ($p.veracode.secret -and $p.veracode.scaGuid) {
        [void]$sb.AppendLine("            - source: VERACODE_SCA")
        [void]$sb.AppendLine("              externalProjectId: $($p.veracode.scaGuid)")
        [void]$sb.AppendLine("              apiBaseUrl: https://api.veracode.com")
        [void]$sb.AppendLine("              secret: '$($p.veracode.secret)'")
        [void]$sb.AppendLine("              cronExpression: '0 0 2 * * *'")
    }
    if ($p.veracode.secret -and $p.veracode.sastGuid) {
        [void]$sb.AppendLine("            - source: VERACODE_SAST")
        [void]$sb.AppendLine("              externalProjectId: $($p.veracode.sastGuid)")
        [void]$sb.AppendLine("              apiBaseUrl: https://api.veracode.com")
        [void]$sb.AppendLine("              secret: '$($p.veracode.secret)'")
        [void]$sb.AppendLine("              cronExpression: '0 0 3 * * *'")
    }
    if ($p.sonar.url -and $p.sonar.token) {
        [void]$sb.AppendLine("            - source: SONARQUBE")
        [void]$sb.AppendLine("              externalProjectId: $($p.sonar.key)")
        [void]$sb.AppendLine("              apiBaseUrl: $($p.sonar.url)")
        [void]$sb.AppendLine("              secret: '$($p.sonar.token)'")
        [void]$sb.AppendLine("              coverageThresholdPct: $($p.sonar.cov)")
        [void]$sb.AppendLine("              cronExpression: '0 30 2 * * *'")
    }
    return $sb.ToString()
}

# ─── Copilot bootstrap (one-time per machine) ───────────────────────────────

function Setup-Copilot {
    Banner "GitHub Copilot CLI"
    $enable = Read-YesNo "Enable Copilot generator? (gh CLI required)" "n"
    if (-not $enable) { return "noop" }
    if (-not (Test-Command 'gh')) {
        Write-Host "  GitHub CLI ('gh') not found. Install from https://cli.github.com/." -ForegroundColor Yellow
        Write-Host "  Leaving Copilot disabled." -ForegroundColor Yellow
        return "noop"
    }
    & gh auth status 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        if (Read-YesNo "  gh is not authenticated. Run 'gh auth login' now?" "y") {
            & gh auth login
            if ($LASTEXITCODE -ne 0) {
                Write-Host "  gh auth login failed; leaving Copilot disabled." -ForegroundColor Yellow
                return "noop"
            }
        } else {
            Write-Host "  Skipping login; Copilot disabled until 'gh auth login' completes." -ForegroundColor Yellow
            return "noop"
        }
    }
    return "copilot"
}

# ─── Modes ──────────────────────────────────────────────────────────────────

$configPath = Join-Path $PSScriptRoot 'dev.config.yml'
$existing   = Test-Path $configPath

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host " TechDebt Cleanup - setup" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan

# Decide effective mode
$effectiveMode = $Mode.ToLower()
if ($effectiveMode -ne 'add' -and $effectiveMode -ne 'overwrite' -and $existing) {
    Write-Host ""
    Write-Host "dev.config.yml already exists." -ForegroundColor Yellow
    Write-Host "  [A] Add another project (preserves existing config)"
    Write-Host "  [O] Overwrite -- start fresh"
    Write-Host "  [Q] Quit"
    while ($true) {
        $choice = (Read-Host "Choice").Trim().ToUpper()
        switch ($choice) {
            'A' { $effectiveMode = 'add'; break }
            'O' { $effectiveMode = 'overwrite'; break }
            'Q' { Write-Host "Aborted." -ForegroundColor Yellow; exit 0 }
            default { Write-Host "Enter A, O, or Q." -ForegroundColor Yellow; continue }
        }
        break
    }
} elseif ($effectiveMode -eq 'add' -and -not $existing) {
    Write-Host ""
    Write-Host "dev.config.yml does not exist yet -- running fresh setup instead of 'add'." -ForegroundColor Yellow
    $effectiveMode = 'fresh'
} elseif (-not $existing) {
    $effectiveMode = 'fresh'
}

if (-not $effectiveMode -or $effectiveMode -eq 'overwrite') {
    $effectiveMode = if ($effectiveMode -eq 'overwrite') { 'fresh' } else { 'fresh' }
}

# ─── Prerequisites ─────────────────────────────────────────────────────────
Banner "Checking prerequisites"
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

# ─── ADD mode ──────────────────────────────────────────────────────────────
if ($effectiveMode -eq 'add') {
    Banner "Appending one project to existing dev.config.yml"
    $project = Prompt-Project
    $rendered = Render-Project $project

    $current = [IO.File]::ReadAllText($configPath)
    if (-not $current.EndsWith("`n")) { $current += "`n" }
    [IO.File]::WriteAllText($configPath, $current + $rendered)

    Write-Host ""
    Write-Host "============================================" -ForegroundColor Green
    Write-Host " Project '$($project.name)' appended." -ForegroundColor Green
    Write-Host "============================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Restart the backend (stop.bat then start.bat) to register it."
    Write-Host "Or register via the UI at http://localhost:9211 -- no restart needed."
    exit 0
}

# ─── FRESH mode ────────────────────────────────────────────────────────────
$copilotGen = Setup-Copilot

$projects = @()
$previous = $null
do {
    if ($projects.Count -gt 0) {
        Write-Host ""
        Write-Host ("Project '{0}' captured. Total so far: {1}" -f $projects[-1].name, $projects.Count) `
            -ForegroundColor Green
    }
    $project = Prompt-Project $previous
    $projects += $project
    $previous = $project
    $more = Read-YesNo "Add another project?" "n"
} while ($more)

$yaml = Render-Header $copilotGen
foreach ($p in $projects) {
    $yaml += Render-Project $p
}

Set-Content -LiteralPath $configPath -Value $yaml -Encoding UTF8

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host " dev.config.yml written with $($projects.Count) project(s)" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
foreach ($p in $projects) {
    $sources = @()
    if ($p.veracode.secret -and $p.veracode.scaGuid)  { $sources += 'SCA' }
    if ($p.veracode.secret -and $p.veracode.sastGuid) { $sources += 'SAST' }
    if ($p.sonar.url -and $p.sonar.token)             { $sources += 'Sonar' }
    $sourceList = if ($sources.Count -gt 0) { $sources -join ', ' } else { '(none -- UI later)' }
    Write-Host ("  - {0,-25} sources: {1}" -f $p.name, $sourceList) -ForegroundColor Green
}
Write-Host ("  Copilot generator: {0}" -f $copilotGen) -ForegroundColor Green
Write-Host ""
Write-Host "Next:"
Write-Host "  1. build.bat fast       (one-time build)"
Write-Host "  2. start.bat            (backend 9200, frontend 9211)"
Write-Host "  3. http://localhost:9211 -- projects auto-register, initial scans run"
Write-Host ""
Write-Host "Later, to add more projects:"
Write-Host "  setup.bat add           (or add-project.bat)"
Write-Host ""

if (Read-YesNo "Run start.bat now?" "n") {
    Write-Host ""
    & "$PSScriptRoot\start.bat"
}
