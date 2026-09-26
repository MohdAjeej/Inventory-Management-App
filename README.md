# 📦 B2B Inventory Management App

A full-stack inventory management solution for businesses with Android app and Spring Boot backend.

## 🚀 Features

### 📱 Android App
- **Dashboard** - Real-time business overview with stats
- **Inventory Management** - Full product CRUD with search, filters, and sorting
- **Analytics** - Business insights with top categories and stock analysis
- **Receipts** - Transaction history (stock in/out tracking)
- **User Authentication** - Secure login and business registration
- **Bottom Navigation** - Fixed navigation across all screens

### 🔧 Backend API
- RESTful API with Spring Boot
- PostgreSQL database
- JWT authentication (ready to integrate)
- Docker containerized
- Product, Business, and User management

---

## 📋 Prerequisites

### For Backend:
- Docker Desktop installed
- Java 21 (for local development)
- PostgreSQL (via Docker)

### For Android App:
- Android Studio
- Android device or emulator (API 24+)
- ADB tools

---

## 🛠️ Setup Instructions

### 1️⃣ **Backend Setup**

#### Option A: Using Docker (Recommended)

```powershell
# Start database and backend
docker-compose up -d

# Check status
docker ps

# View logs
docker logs -f b2b-inventory-backend
```

The backend will be available at: **http://localhost:8082**

#### Option B: Local Development

```powershell
cd backend

# Run with Gradle
.\gradlew.bat bootRun
```

#### Database Access
- **Host:** localhost
- **Port:** 5433
- **Database:** b2b_inventory
- **Username:** postgres
- **Password:** postgres123

### 2️⃣ **Android App Setup**

#### Build the APK

```powershell
cd android

# Clean build
.\gradlew.bat clean

# Build debug APK
.\gradlew.bat assembleDebug
```

#### Install on Device

```powershell
# Check connected devices
adb devices

# Install APK
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### 3️⃣ **Configuration**

Update the API base URL in `android/app/src/main/java/com/b2binventory/app/data/ApiConfig.kt`:

```kotlin
object ApiConfig {
    const val BASE_URL = "http://10.0.2.2:8082/"  // For emulator
    // const val BASE_URL = "http://YOUR_IP:8082/"  // For physical device
}
```

To find your IP address:
```powershell
ipconfig | findstr IPv4
```

---

## 🎯 Usage

### First Time Setup

1. **Start Backend:**
   ```powershell
   docker-compose up -d
   ```

2. **Install App:**
   ```powershell
   adb install -r android\app\build\outputs\apk\debug\app-debug.apk
   ```

3. **Register Business:**
   - Open the app
   - Tap "Create Account"
   - Fill in business details
   - Register

4. **Login:**
   - Use your email and password
   - Access the dashboard

### Test Credentials
```
Email: test@example.com
Password: test123
```

---

## 📱 App Screens

### 🏠 Dashboard
- Total Products count
- Total Stock quantity
- Low Stock alerts
- Out of Stock warnings
- Quick actions (Add Product, View Inventory, Analytics, Receipts)

### 📦 Inventory
- Search products by name, SKU, brand
- Filter by category with counts
- Sort by name or stock
- View in List or Grid mode
- Stock status badges (In Stock, Low Stock, Out of Stock)
- Product cards with emoji icons

### 📊 Analytics
- Overview cards (Total Products, Total Stock, Low Stock, Out of Stock)
- Top Categories with product counts
- Top Products by stock levels
- Real-time data from backend

### 🧾 Receipts
- Transaction history (Stock In/Out)
- Search receipts
- Filter by transaction type
- Date and time stamps
- Product details and quantities

### ⚙️ More (Settings)
- User profile
- Business information
- Logout

---

## 🔌 API Endpoints

### Authentication
```
POST /api/auth/login
POST /api/auth/register-business
```

### Products
```
GET    /api/inventory/products?businessId={id}&category={category}&search={query}
POST   /api/inventory/products?businessId={id}&userId={id}
GET    /api/inventory/products/{id}
PUT    /api/inventory/products/{id}?userId={userId}
DELETE /api/inventory/products/{id}
```

### Stock Management
```
POST /api/inventory/products/{id}/stock?userId={userId}
GET  /api/inventory/products/{id}/stock-history
```

---

## 🐳 Docker Commands

### Start Services
```powershell
docker-compose up -d
```

### Stop Services
```powershell
docker-compose down
```

### View Logs
```powershell
# Backend logs
docker logs -f b2b-inventory-backend

# Database logs
docker logs -f b2b-inventory-db
```

### Restart Services
```powershell
docker-compose restart
```

### Rebuild Backend
```powershell
docker-compose build backend
docker-compose up -d backend
```

---

## 🔧 Development

### Backend Development

```powershell
cd backend

# Run locally
.\gradlew.bat bootRun

# Run tests
.\gradlew.bat test

# Build JAR
.\gradlew.bat build
```

### Android Development

```powershell
cd android

# Clean build
.\gradlew.bat clean

# Build debug
.\gradlew.bat assembleDebug

# Build release
.\gradlew.bat assembleRelease

# Run lint
.\gradlew.bat lint
```

---

## 📂 Project Structure

```
b2b-inventory-app/
├── android/                    # Android application
│   └── app/
│       └── src/main/java/com/b2binventory/app/
│           ├── data/          # API, models, session
│           ├── ui/
│           │   ├── analytics/ # Analytics screen
│           │   ├── auth/      # Login, Register
│           │   ├── dashboard/ # Home screen
│           │   ├── inventory/ # Product management
│           │   ├── receipts/  # Transaction history
│           │   ├── navigation/# Bottom nav, routes
│           │   └── settings/  # Settings screen
│           └── theme/         # Material Design theme
│
├── backend/                    # Spring Boot backend
│   └── src/main/java/com/b2binventory/
│       ├── controller/        # REST controllers
│       ├── domain/           # Entity models
│       ├── dto/              # Data transfer objects
│       ├── repository/       # JPA repositories
│       └── service/          # Business logic
│
├── docker-compose.yml         # Docker configuration
└── README.md                  # This file
```

---

## 🎨 Design

- **Material Design 3** UI components
- **Color Scheme:**
  - Primary: Blue (#2196F3)
  - Success: Green (#4CAF50)
  - Warning: Orange (#FFA726)
  - Error: Red (#F44336)
- **Icons:** Material Icons with category emojis
- **Typography:** Roboto font family

---

## 🐛 Troubleshooting

### Backend Not Starting
```powershell
# Check if port 8082 is in use
netstat -ano | findstr :8082

# Stop conflicting process
taskkill /PID <PID> /F

# Restart Docker containers
docker-compose down
docker-compose up -d
```

### Database Connection Issues
```powershell
# Check database is running
docker ps | findstr postgres

# Restart database
docker restart b2b-inventory-db

# Check logs
docker logs b2b-inventory-db
```

### App Can't Connect to Backend
1. Check backend is running: `docker ps`
2. Verify API URL in `ApiConfig.kt`
3. For emulator: use `10.0.2.2:8082`
4. For device: use your computer's IP address

### Build Failures
```powershell
# Clean Gradle cache
cd android
.\gradlew.bat clean

# Or backend
cd backend
.\gradlew.bat clean

# Delete .gradle folders if needed
Remove-Item -Recurse -Force .gradle
```

---

## 📝 Environment Variables

Create a `.env` file in the root directory (optional):

```env
# Database
POSTGRES_DB=b2b_inventory
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres123

# Backend
SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/b2b_inventory
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres123
SERVER_PORT=8082
```

---

## 🚀 Deployment

### Backend Deployment
1. Build production JAR
2. Configure production database
3. Deploy to cloud (AWS, Azure, GCP, Heroku)
4. Update API URL in Android app

### Android Deployment
1. Generate signed APK/AAB
2. Update version in `build.gradle`
3. Test thoroughly
4. Publish to Google Play Store

---

## 📄 API Documentation

API documentation is available via Swagger UI (when backend is running):
```
http://localhost:8082/swagger-ui.html
```

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

---

## 📜 License

This project is licensed under the MIT License.

---

## 👥 Authors

- **Development Team** - Initial work

---

## 🙏 Acknowledgments

- Spring Boot framework
- Jetpack Compose for Android UI
- Material Design guidelines
- PostgreSQL database

---

## 📞 Support

For issues and questions:
- Create an issue on GitHub
- Email: support@b2binventory.com

---

## 🔄 Version History

### v1.0.0 (Current)
- ✅ Dashboard with real-time stats
- ✅ Full inventory management
- ✅ Analytics screen
- ✅ Receipts/Transaction history
- ✅ Bottom navigation
- ✅ Search, filter, sort functionality
- ✅ Backend API integration
- ✅ Docker containerization

---

**Made with ❤️ for businesses worldwide**
