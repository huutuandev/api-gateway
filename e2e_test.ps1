$ErrorActionPreference = "Stop"
$ProgressPreference = 'SilentlyContinue'
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Write-Host "Building project..."
mvn clean package -DskipTests | Out-Null

Write-Host "Starting Spring Boot app..."
$env:TZ = "UTC"
$app = Start-Process java -ArgumentList "-Duser.timezone=UTC -jar target/ai-gateway-0.0.1-SNAPSHOT.jar" -PassThru -NoNewWindow
Start-Sleep -Seconds 12

try {
    Write-Host "Registering user..."
    $regBody = '{"email":"e2e' + (Get-Date -UFormat %s) + '@example.com","password":"Password123"}'
    $regResp = Invoke-WebRequest -Uri "http://localhost:8080/api/v1/auth/register" -Method POST -ContentType "application/json" -Body $regBody -UseBasicParsing
    Write-Host "Register: $($regResp.StatusCode)"

    Write-Host "Logging in..."
    $loginResp = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/login" -Method POST -ContentType "application/json" -Body $regBody -UseBasicParsing
    $token = $loginResp.accessToken
    Write-Host "JWT obtained successfully!"

    Write-Host "Calling /api/v1/ai/chat (1st request)..."
    $chatBody = '{"message":"Say hi in one word"}'
    $chatResp1 = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/ai/chat" -Method POST -ContentType "application/json" -Headers @{"Authorization"="Bearer $token"} -Body $chatBody -UseBasicParsing
    Write-Host "AI Response: $($chatResp1.message.content)"
    Write-Host "Token Usage: Total = $($chatResp1.usage.totalTokens)"

    Write-Host "Testing rate limit (spamming requests)..."
    for ($i = 2; $i -le 7; $i++) {
        try {
            $resp = Invoke-WebRequest -Uri "http://localhost:8080/api/v1/ai/chat" -Method POST -ContentType "application/json" -Headers @{"Authorization"="Bearer $token"} -Body $chatBody -UseBasicParsing
            Write-Host "Request ${i}: HTTP $($resp.StatusCode)"
        } catch {
            Write-Host "Request ${i}: HTTP $($_.Exception.Response.StatusCode.value__)"
        }
    }
} finally {
    Write-Host "Killing Spring Boot app..."
    Stop-Process -Id $app.Id -Force
}
