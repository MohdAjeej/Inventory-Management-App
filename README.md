# B2B Inventory Management App

A comprehensive B2B-only inventory management system with full-featured Android app and Spring Boot backend.

## Stack

### Android
- **Kotlin** with Jetpack Compose
- **Material 3 Design** for modern UI
- **Navigation Compose** for screen navigation
- **Retrofit** for REST API integration
- **Kotlin Coroutines** for asynchronous operations
- **SessionManager** for user session persistence
- Complete MVVM-ready architecture

### Backend
- **Java 21** with modern features
- **Spring Boot 3.4.5**
- **Spring Web** for RESTful APIs
- **Spring Data JPA** with Hibernate
- **Spring Security** with BCrypt hashing
- **PostgreSQL** database support
- Clean architecture with service layer pattern

## Features

### Complete Feature Set

#### Authentication & Authorization
- ✅ Business registration with complete details
- ✅ User login with secure password hashing
- ✅ Role-based access control (ADMIN, INVENTORY_MANAGER, STAFF)
- ✅ Session management

#### Inventory Management
- ✅ Product creation with multiple attributes:
  - Category, name, brand, SKU, barcode
  - Unit, size, thickness, color, model
  - Description and images
  - Minimum stock alerts
- ✅ Product listing with search and category filters
- ✅ Product details view
- ✅ Product editing (coming soon in UI)
- ✅ Low stock and out-of-stock alerts

#### Stock Management
- ✅ Add stock with quantity and reason
- ✅ Reduce stock with quantity and reason
- ✅ Complete stock movement history
- ✅ Track who made changes and when
- ✅ Preview stock adjustments before confirming

#### Dashboard & Analytics
- ✅ Inventory overview with statistics
- ✅ Total products and stock count
- ✅ Out-of-stock product alerts
- ✅ Low-stock product warnings
- ✅ Recent product listings

#### Categories Supported
- Ply (with thickness and size)
- Mica (with thickness, size, and color)
- Hardware (with model)
- Door (with color and model)
- Sanitary
- Paint (with color)
- Custom/Other

### Future Features (UI Placeholders Ready)
- Employee management
- Custom category management
- Inventory reports and analytics
- Business profile editing

## Project Structure

### Backend Structure
```
backend/src/main/java/com/b2binventory/
├── B2bInventoryApplication.java
├── config/
│   └── SecurityConfig.java
├── controller/
│   ├── AuthController.java
│   ├── BusinessController.java
│   ├── ProductController.java
│   └── StockController.java
├── service/
│   ├── AuthService.java
│   ├── BusinessService.java
│   ├── ProductService.java
│   └── StockService.java
├── repository/
│   ├── BusinessRepository.java
│   ├── UserRepository.java
│   ├── ProductRepository.java
│   └── StockMovementRepository.java
├── domain/
│   ├── Business.java
│   ├── AppUser.java
│   ├── Role.java
│   ├── Product.java
│   ├── StockMovement.java
│   └── StockMovementType.java
├── dto/
│   ├── LoginRequest.java
│   ├── RegisterBusinessRequest.java
│   ├── ProductRequest.java
│   └── StockRequest.java
└── exception/
    ├── ApiExceptionHandler.java
    └── ResourceNotFoundException.java
```

### Android Structure
```
android/app/src/main/java/com/b2binventory/app/
├── MainActivity.kt
├── data/
│   ├── ApiConfig.kt
│   ├── ApiService.kt
│   ├── ApiClient.kt
│   ├── Models.kt
│   └── SessionManager.kt
├── ui/
│   ├── navigation/
│   │   └── AppNavigation.kt
│   ├── auth/
│   │   ├── LoginScreen.kt
│   │   └── RegisterBusinessScreen.kt
│   ├── dashboard/
│   │   └── DashboardScreen.kt
│   ├── inventory/
│   │   ├── InventoryScreen.kt
│   │   ├── AddInventoryScreen.kt
│   │   ├── ProductDetailsScreen.kt
│   │   ├── EditProductScreen.kt
│   │   ├── StockAdjustmentScreen.kt
│   │   └── StockHistoryScreen.kt
│   ├── employees/
│   │   └── EmployeeScreen.kt
│   ├── categories/
│   │   └── CategoriesScreen.kt
│   ├── reports/
│   │   └── InventoryReportsScreen.kt
│   └── settings/
│       └── SettingsScreen.kt
└── theme/
    ├── Color.kt
    ├── Theme.kt
    └── Type.kt
```

## API Endpoints

### Authentication
- `POST /api/auth/register-business` - Register new business
- `POST /api/auth/login` - User login

### Products
- `GET /api/inventory/products` - List products (with filters)
- `POST /api/inventory/products` - Create product
- `PUT /api/inventory/products/{id}` - Update product
- `GET /api/inventory/products/{id}` - Get product details
- `DELETE /api/inventory/products/{id}` - Soft delete product
- `GET /api/inventory/products/low-stock` - Get low stock products
- `GET /api/inventory/products/out-of-stock` - Get out of stock products

### Stock Management
- `POST /api/inventory/products/{id}/stock` - Adjust stock
- `GET /api/inventory/products/{id}/history` - Get stock history
- `GET /api/inventory/stock-history` - Get business stock history

### Business
- `GET /api/business/{id}` - Get business details
- `PUT /api/business/{id}` - Update business

## Setup Instructions

### Quick Start with Docker (Recommended)

The easiest way to run the backend and database:

```powershell
docker compose up -d
```

This will:
- Start PostgreSQL database
- Build and start Spring Boot backend
- Create all necessary tables automatically

Backend will be available at `http://localhost:8080`

**For detailed Docker instructions, see [DOCKER_SETUP.md](DOCKER_SETUP.md)**

### Manual Setup (Alternative)

If you prefer to run without Docker:

1. **Install PostgreSQL** (if not already installed)

2. **Create Database**
   ```sql
   CREATE DATABASE b2b_inventory;
   ```

3. **Configure Environment Variables** (Optional)
   Set these environment variables or edit `application.yml`:
   - `DB_URL` (default: jdbc:postgresql://localhost:5432/b2b_inventory)
   - `DB_USERNAME` (default: postgres)
   - `DB_PASSWORD` (default: postgres)

4. **Run Backend**
   ```bash
   cd backend
   ./gradlew bootRun
   ```

   Windows:
   ```powershell
   cd backend
   gradlew.bat bootRun
   ```

   Backend starts on `http://localhost:8080`

5. **Database Tables** are automatically created on first run via Hibernate DDL

### Android Setup

1. **Open Project**
   - Open the `android` folder in Android Studio

2. **Configure API Base URL**
   Edit `android/app/src/main/java/com/b2binventory/app/data/ApiConfig.kt`:
   
   - For Android Emulator:
     ```kotlin
     const val BASE_URL = "http://10.0.2.2:8080/"
     ```
   
   - For Physical Device:
     ```kotlin
     const val BASE_URL = "http://YOUR_COMPUTER_IP:8080/"
     ```
     (Replace YOUR_COMPUTER_IP with your actual IP address)

3. **Sync and Build**
   - Let Gradle sync
   - Build and run the app

## User Flow

### First Time Setup
1. **Register Business**
   - Fill business details (name, type, mobile, address, GST, etc.)
   - Create admin user account
   - Automatic login after registration

2. **Dashboard**
   - View inventory overview
   - See stock statistics
   - Check low-stock and out-of-stock alerts
   - Navigate to various sections

3. **Add Products**
   - Select category
   - Fill product details
   - Set initial stock quantity
   - Set minimum stock alert level

4. **Manage Inventory**
   - Search and filter products
   - View product details
   - Adjust stock (add/reduce)
   - View stock movement history

5. **Stock Adjustments**
   - Choose add or reduce
   - Enter quantity
   - Add reason (optional)
   - Preview changes before confirming

6. **Monitor**
   - Dashboard shows alerts
   - Stock history tracks all changes
   - Who made changes and when

## Technologies & Libraries

### Backend Dependencies
- Spring Boot Starter Web
- Spring Boot Starter Data JPA
- Spring Boot Starter Security
- Spring Security Crypto
- PostgreSQL Driver
- Spring Boot Starter Test

### Android Dependencies
- Kotlin 2.1.0
- Compose BOM 2025.02.00
- Material 3
- Navigation Compose 2.8.7
- Retrofit 2.11.0
- Gson Converter 2.11.0
- Kotlin Coroutines 1.10.1

## Database Schema

### Tables
- **businesses** - Company information
- **users** - User accounts with roles
- **products** - Product inventory
- **stock_movements** - All stock change history

### Key Relationships
- User ↔ Business (Many-to-One)
- Product ↔ Business (Many-to-One)
- StockMovement ↔ Product (Many-to-One)
- StockMovement ↔ User (Many-to-One)

## Security Features
- BCrypt password hashing
- Role-based access control
- Business data isolation
- User authentication required for all operations
- CSRF protection (disabled for API endpoints)

## Out of Scope
The following features are intentionally excluded to keep the app focused on inventory management:
- ❌ Billing & Invoicing
- ❌ Sales & Orders
- ❌ Purchase Management
- ❌ Customer Management
- ❌ Supplier Management
- ❌ Payment Processing
- ❌ Accounting & Bookkeeping
- ❌ E-commerce Integration

## Contributing
This is a complete B2B inventory management system. Feel free to extend with additional features as needed.

## License
[Your License Here]

## Version
**1.0.0** - Complete inventory management with stock tracking
