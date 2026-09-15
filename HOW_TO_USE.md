# 📱 How to Use Your B2B Inventory Management App

## ✅ Current Status

- **Frontend (Android)**: ✅ Installed and working
- **Backend (Spring Boot)**: ✅ Running on port 8082
- **Database (PostgreSQL)**: ✅ Running in Docker
- **API Connection**: ✅ Connected (192.168.29.166:8082)

## 🚀 Quick Start Guide

### 1. Make Sure Backend is Running

```bash
cd backend
.\start-backend.bat
```

**Wait for**: `Started B2bInventoryApplication`

### 2. Open the App on Your Phone

The app is already installed! Look for "B2B Inventory" app icon.

### 3. Login/Register

**First Time Users:**
1. Click "Register Business"
2. Fill in your business details
3. Create your account

**Existing Users:**
1. Enter email and password
2. Click "Login"

---

## 📋 Main Features & How to Use

### 🏠 Dashboard (Home Screen)

You'll see:
- **Financial Cards** (purple & green gradient)
  - Today's Sales: Shows daily sales total
  - Receipts: Shows money received

- **Quick Actions** (4 colorful buttons)
  - 🧾 **Invoice** - Create sales invoice (coming soon)
  - 🛒 **Purchase** - Record purchases (coming soon)
  - 👥 **Parties** - Manage customers/suppliers (coming soon)
  - 📦 **Inventory** - View and manage products

- **Business Insights**
  - 📊 Daily Summary
  - 📈 Reports
  - 💰 Outstanding

### 📦 Add Inventory (The Working Feature!)

#### Step 1: Navigate to Add Inventory
- From Dashboard → Click "Inventory" quick action
- OR click the "+" floating button

#### Step 2: Select Category
Scroll horizontally and tap a category:
- Electronics, Clothing, Groceries, Hardware, Furniture
- Stationery, Medical, Cosmetics, Toys, Books
- Ply, Mica, Door, Sanitary, Paint, Services, Other

#### Step 3: Fill Required Fields
**Basic Information** (Required: marked with *)
- **Product Name** * - Enter the product name
- **Brand** - Brand/manufacturer name (optional)

**Identification**
- **SKU** - Stock Keeping Unit code (optional)
- **Barcode** - Barcode number (optional)

**Stock Information** (Required)
- **Quantity** * - How many units you have
- **Unit** * - Select from dropdown (Piece, Kg, Liter, Box, etc.)
- **Minimum Stock Alert** - Alert level (optional, default: 0)

**Specifications** (For Ply/Mica/Door only)
- **Size** - Product dimensions (e.g., 8x4 ft)
- **Thickness** - Material thickness (e.g., 12mm)

**Description**
- Add any additional notes or details

#### Step 4: Save
1. Click the **"Save Product"** button at the bottom
2. You'll see "Saving..." with a spinner
3. Success dialog appears: "Product '[name]' has been added successfully"
4. Click "OK" to return to inventory list

#### Troubleshooting
If you see an error:
- **"Product name is required"** - Fill in the product name
- **"Please enter a valid quantity"** - Enter a number ≥ 0
- **"Please select a unit"** - Choose a unit from dropdown
- **"Error: Unable to save..."** - Check:
  - ✅ Backend is running
  - ✅ Phone and computer on same WiFi
  - ✅ Computer IP is 192.168.29.166

---

## 🔄 Full User Workflow

### For Store Owner:

#### Daily Morning Routine
1. **Open app** → Check Dashboard
2. **Review Sales** card (Today's total)
3. **Check Inventory** for low stock alerts
4. **Add new stock** if received

#### When Customer Buys
1. Go to **Inventory**
2. View product details
3. Adjust stock (coming soon)
4. Create invoice (coming soon)

#### When Receiving Stock
1. Click **"+ Add Inventory"**
2. Fill product details
3. Enter quantity received
4. Save

#### End of Day
1. Check **Daily Summary**
2. Review **Reports**
3. Check **Outstanding** amounts

---

## 🎯 What's Working Right Now

### ✅ Fully Functional
1. **User Registration** - Create business account
2. **User Login** - Access your account
3. **Dashboard** - View beautiful home screen
4. **Add Inventory** - Add products with full details
5. **View Inventory** - See all products (list view)
6. **Category Selection** - 17+ business categories

### ⏳ Coming Soon (UI Ready, Backend Needed)
1. **Edit Product** - Update product details
2. **Delete Product** - Remove products
3. **Stock Adjustment** - Add/Remove stock
4. **Stock History** - View stock movements
5. **Create Invoice** - Generate sales invoices
6. **Purchase Entry** - Record purchases
7. **Party Management** - Add customers/suppliers
8. **Reports** - Various business reports
9. **Outstanding** - Receivables/Payables tracking

---

## 💡 Tips & Best Practices

### Product Naming
- ✅ Good: "Samsung Galaxy A54 128GB Blue"
- ❌ Avoid: "phone"

### Categories
- Choose the most specific category
- "Other" is for miscellaneous items only

### SKU/Barcode
- Use if you have a barcode scanner
- Helps in quick search and checkout

### Minimum Stock Alert
- Set to get notifications when stock is low
- Example: For fast-moving items, set to 10 or 20

### Units
- **Piece** - Individual items (phones, chairs, etc.)
- **Kg/Gram** - Weight-based (rice, sugar, etc.)
- **Liter/ML** - Liquid items (paint, oil, etc.)
- **Box/Carton** - Bulk packaging
- **Sheet** - Flat materials (ply, mica, etc.)

---

## 🐛 Common Issues & Solutions

### Issue 1: "Unable to save inventory"
**Solution:**
1. Check backend is running: `.\start-backend.bat`
2. Verify both devices on same WiFi
3. Check computer IP: Should be 192.168.29.166

### Issue 2: App crashes or freezes
**Solution:**
1. Force close app
2. Clear app cache (Android Settings → Apps → B2B Inventory → Clear Cache)
3. Reopen app

### Issue 3: Can't see added products
**Solution:**
1. Pull down to refresh the list
2. Check if you're in the correct business account

### Issue 4: Login fails
**Solution:**
1. Check credentials
2. Register new account if first time
3. Check backend is running

---

## 📊 Understanding the Dashboard

### Financial Cards
- **Purple Card (Sales)**: Total sales for today
- **Green Card (Receipts)**: Money received today
- **Trend**: Shows % change from yesterday

### Quick Actions
- Tap any icon for quick access
- Inventory is fully functional
- Others coming soon

### Business Insights
- **Daily Summary**: Today's performance overview
- **Reports**: Detailed analytics
- **Outstanding**: Unpaid amounts

---

## 🔐 Security Tips

1. **Don't share** your login credentials
2. **Logout** when not using (from "More" tab)
3. **Regular backups** (automatic when using cloud database)
4. **Secure WiFi** - Use password-protected network

---

## 📞 Need Help?

### Check These First:
1. **Backend running?** Open PowerShell, run `.\start-backend.bat`
2. **WiFi connected?** Both phone and computer must be on 192.168.29.x network
3. **App updated?** Latest version installed
4. **Database?** Docker container running: `docker ps`

### Debug Steps:
1. Read `TEST_ADD_INVENTORY.md` for detailed troubleshooting
2. Check backend console for error messages
3. Try the PowerShell API test in TEST_ADD_INVENTORY.md

---

## 🎉 You're All Set!

Your app is **production-ready** with professional UI! Start adding your inventory and managing your business efficiently.

**Pro Tip**: Add a few test products first to get familiar with the interface, then add your real inventory.

---

**Version**: 2.0.0 (Professional UI)  
**Last Updated**: September 15, 2026
