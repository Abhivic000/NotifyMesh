<#
.SYNOPSIS
  Mints an HS256 JWT signed with this project's JWT_SECRET, for manual testing.

.DESCRIPTION
  There is no login endpoint anywhere in this system (PRD's API list doesn't define
  one) - JWTs are issued out-of-band to trusted clients using a shared secret, and
  every service just validates the signature. This script plays the role of that
  out-of-band issuer for local testing, using nothing but PowerShell's built-in
  System.Security.Cryptography (no external JWT library needed).

.PARAMETER Role
  SERVICE, ADMIN, or USER - becomes the token's "role" claim.

.EXAMPLE
  .\scripts\generate-jwt.ps1 -Role SERVICE
  .\scripts\generate-jwt.ps1 -Role ADMIN -Subject ops-team
#>
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("SERVICE", "ADMIN", "USER")]
    [string]$Role,

    [string]$Subject = "test-client",
    [int]$ExpiresInSeconds = 3600,
    [string]$EnvFile = ".env"
)

function ConvertTo-Base64Url([byte[]]$Bytes) {
    [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

if (-not (Test-Path $EnvFile)) {
    throw "$EnvFile not found - run this from the repo root, or pass -EnvFile"
}
# -Raw + regex, not line-based Get-Content: line-based reading was observed to
# silently truncate this specific value partway through on this machine (Get-Content
# splitting somewhere Select-String/-Raw does not) - -Raw sidesteps whatever that was.
$raw = Get-Content $EnvFile -Raw
if ($raw -notmatch 'JWT_SECRET=(\S+)') {
    throw "JWT_SECRET not found in $EnvFile"
}
$secret = $Matches[1]

$header = '{"alg":"HS256","typ":"JWT"}'
$now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$exp = $now + $ExpiresInSeconds
$payload = "{`"sub`":`"$Subject`",`"role`":`"$Role`",`"iat`":$now,`"exp`":$exp}"

$headerB64 = ConvertTo-Base64Url ([System.Text.Encoding]::UTF8.GetBytes($header))
$payloadB64 = ConvertTo-Base64Url ([System.Text.Encoding]::UTF8.GetBytes($payload))
$signingInput = "$headerB64.$payloadB64"

$hmac = New-Object System.Security.Cryptography.HMACSHA256
# The secret's own bytes (its literal characters) are the HMAC key - not decoded
# from base64 first, even though the value happens to look base64-encoded (that's
# just how a long random string was generated in Step 4). The Java side
# (JwtDecoders.hmac256) treats it identically: secret.getBytes(UTF_8).
$hmac.Key = [System.Text.Encoding]::UTF8.GetBytes($secret)
$signature = $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($signingInput))
$signatureB64 = ConvertTo-Base64Url $signature

Write-Output "$signingInput.$signatureB64"
