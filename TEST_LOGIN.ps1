# Test Login API
$baseUrl = "http://192.168.29.166:8082"

Write-Host "Testing B2B Inventory Backend..." -ForegroundColor Cyan
Write-Host ""

# Test 1: Register a test user
Write-Host "1. Registering test business..." -ForegroundColor Yellow
$registerBody = @{
    businessName = "Test Shop"
    businessType = "Retail Shop"
    businessMobile = "9876543210"
    businessEmail = "test@shop.com"
    address = "123 Test Street"
    city = "Mumbai"
    state = "Maharashtra"
    country = "India"
    gstNumber = "29ABCDE1234F1Z5"
    name = "Test User"
    email = "test@example.com"
    password = "test123"
} | ConvertTo-Json

try {
    $registerResponse = Invoke-RestMethod -Uri "$baseUrl/api/auth/register-business" -Method Post -Body $registerBody -ContentType "application/json"
    Write-Host "✅ Registration successful!" -ForegroundColor Green
    Write-Host "Response: $($registerResponse | ConvertTo-Json)" -ForegroundColor Gray
    Write-Host ""
} catch {
    if ($_.Exception.Response.StatusCode -eq 400) {
        Write-Host "⚠️  User already exists (this is OK)" -ForegroundColor Yellow
    } else {
        Write-Host "❌ Registration failed: $_" -ForegroundColor Red
    }
    Write-Host ""
}

# Test 2: Login
Write-Host "2. Testing login..." -ForegroundColor Yellow
$loginBody = @{
    email = "test@example.com"
    password = "test123"
} | ConvertTo-Json

try {
    $loginResponse = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
    Write-Host "✅ Login successful!" -ForegroundColor Green
    Write-Host "Response:" -ForegroundColor Gray
    Write-Host "  userId: $($loginResponse.userId)" -ForegroundColor Cyan
    Write-Host "  businessId: $($loginResponse.businessId)" -ForegroundColor Cyan
    Write-Host "  name: $($loginResponse.name)" -ForegroundColor Cyan
    Write-Host "  email: $($loginResponse.email)" -ForegroundColor Cyan
    Write-Host "  role: $($loginResponse.role)" -ForegroundColor Cyan
    Write-Host ""
    
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "TEST CREDENTIALS:" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "Email:    test@example.com" -ForegroundColor White
    Write-Host "Password: test123" -ForegroundColor White
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Use these credentials to login in your app!" -ForegroundColor Cyan
    
} catch {
    Write-Host "❌ Login failed: $_" -ForegroundColor Red
    Write-Host "Error details: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""
Write-Host "3. Testing product API..." -ForegroundColor Yellow
try {
    $products = Invoke-RestMethod -Uri "$baseUrl/api/inventory/products?businessId=1"
    Write-Host "✅ Products API working! Found $($products.Count) products" -ForegroundColor Green
} catch {
    Write-Host "⚠️  Products API: $($_.Exception.Message)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Backend is ready for testing!" -ForegroundColor Green
