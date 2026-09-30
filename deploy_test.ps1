$ErrorActionPreference = "Stop"

$baseUrl = "http://localhost:8080/api/v1"
Write-Host "Testing Deployment at $baseUrl..."

# 0. Register
Write-Host "Testing Register..."
$registerBody = @{
    email = "user@example.com"
    password = "password123"
} | ConvertTo-Json

try {
    Invoke-RestMethod -Uri "$baseUrl/auth/register" -Method Post -Body $registerBody -ContentType "application/json" | Out-Null
    Write-Host "Register successful."
} catch {
    Write-Host "Register skipped or failed (might already exist)."
}

# 1. Login
Write-Host "Testing Login..."
$loginBody = @{
    email = "user@example.com"
    password = "password123"
} | ConvertTo-Json

$loginJson = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$accessToken = $loginJson.accessToken

if (-not $accessToken) {
    Write-Error "Login failed! Missing accessToken."
    exit 1
}
Write-Host "Login successful."

$headers = @{
    Authorization = "Bearer $accessToken"
    "Content-Type" = "application/json"
}

# 2. AI Chat
Write-Host "Testing AI Chat..."
$chatBody = @{
    message = "Say hello!"
} | ConvertTo-Json

$chatJson = Invoke-RestMethod -Uri "$baseUrl/ai/chat" -Method Post -Headers $headers -Body $chatBody
$conversationId = $chatJson.conversationId

if (-not $conversationId) {
    Write-Error "AI Chat failed! Missing conversationId."
    exit 1
}
Write-Host "AI Chat successful, conversationId: $conversationId"

# 3. Conversations
Write-Host "Testing Get Conversations..."
$convJson = Invoke-RestMethod -Uri "$baseUrl/conversations" -Method Get -Headers $headers

if ($convJson.Count -eq 0) {
    Write-Error "No conversations found!"
    exit 1
}
Write-Host "Get Conversations successful, found $($convJson.Count) conversations."

# 4. Usage
Write-Host "Testing Get Usage..."
$usageJson = Invoke-RestMethod -Uri "$baseUrl/usage" -Method Get -Headers $headers

Write-Host "Get Usage successful, total tokens used: $($usageJson.totalTokens)"

Write-Host "ALL TESTS PASSED!"
