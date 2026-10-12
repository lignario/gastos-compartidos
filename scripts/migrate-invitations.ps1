param([switch]$Apply)
$ErrorActionPreference = 'Stop'
$project = 'gastos-uy0626'
$taskToken = gcloud auth print-access-token
if ($LASTEXITCODE -ne 0) { throw 'Google authentication unavailable.' }
$headers = @{ Authorization = "Bearer $taskToken"; 'x-goog-user-project' = $project }
$rootUrl = "https://firestore.googleapis.com/v1/projects/$project/databases/(default)/documents"
$groups = @()
$page = ''
do {
    $response = Invoke-RestMethod -Headers $headers "$rootUrl/groups?pageSize=1000&pageToken=$page"
    $groups += @($response.documents)
    $page = $response.nextPageToken
} while ($page)
$seen = @{}
$writes = @()
foreach ($group in $groups) {
    $code = $group.fields.inviteCode.stringValue
    if ($code -cnotmatch '^[A-Z2-9]{6,32}$') { throw 'Unexpected existing invitation format; no changes made.' }
    if ($seen.ContainsKey($code)) { throw 'Duplicate invitation codes; no changes made.' }
    $seen[$code] = $true
    $groupId = $group.name.Split('/')[-1]
    $existing = $null
    try { $existing = Invoke-RestMethod -Headers $headers "$rootUrl/invitations/$code" }
    catch { if ([int]$_.Exception.Response.StatusCode -ne 404) { throw } }
    if ($existing) {
        if ($existing.fields.groupId.stringValue -ne $groupId) { throw 'Invitation points to another group; no changes made.' }
    } else {
        $writes += @{update=@{name="projects/$project/databases/(default)/documents/invitations/$code";fields=@{groupId=@{stringValue=$groupId}}};currentDocument=@{exists=$false}}
    }
}
if ($writes.Count -gt 450) { throw 'Migration needs batching review; no changes made.' }
Write-Output "Groups checked: $($groups.Count). New invitation index records: $($writes.Count). Existing groups will not be modified."
if ($Apply -and $writes.Count -gt 0) {
    $backupDir = Join-Path (Split-Path $PSScriptRoot -Parent) ('.toolchain/rollout-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
    New-Item -ItemType Directory -Path $backupDir | Out-Null
    $groups | ConvertTo-Json -Depth 50 | Set-Content -LiteralPath (Join-Path $backupDir 'groups-before.json') -Encoding utf8
    $release = Invoke-RestMethod -Headers $headers "https://firebaserules.googleapis.com/v1/projects/$project/releases/cloud.firestore"
    $ruleset = Invoke-RestMethod -Headers $headers "https://firebaserules.googleapis.com/v1/$($release.rulesetName)"
    $ruleset | ConvertTo-Json -Depth 50 | Set-Content -LiteralPath (Join-Path $backupDir 'rules-before.json') -Encoding utf8
    $result = Invoke-RestMethod -Method Post -Headers $headers -ContentType 'application/json' -Uri "${rootUrl}:commit" -Body (@{writes=$writes} | ConvertTo-Json -Depth 30)
    Write-Output "Atomic migration committed: $(@($result.writeResults).Count) invitation index records. Private backup: $backupDir"
}
Remove-Variable taskToken,headers
