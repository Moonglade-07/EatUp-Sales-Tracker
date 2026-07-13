# EatUp Sales Tracker 📱💰
### Definitive Titan Maximum Edition (v1.4.5)

**EatUp Sales Tracker** is a professional-grade sales management and business intelligence suite designed specifically for food delivery entrepreneurs and small restaurant owners. It bridges the gap between traditional pen-and-paper ledgers and modern data analytics, offering an offline-first experience that syncs perfectly to the cloud.

---

## 🏗️ Project Vision
Most small food businesses lose track of their true margins due to delivery fees, platform discounts, and fluctuating cost prices. **EatUp** solves this by providing a unified profit engine that calculates `(Price - Cost) + Delivery - Discount` for every single order, ensuring you know exactly how much you earn at the end of the day.

---

## ✨ Comprehensive Features

### 📊 Growth Analytics Hub (Titan Update)
The analytics hub is the "brain" of the application, transforming raw data into strategic insights.
- **Market Trends Tab**:
    - **Weekly Velocity**: Grouped bar charts showing side-by-side Sales and Profit for a rolling 7-day window.
    - **Weekday Analysis**: Intelligent SQL logic that calculates true historical averages for each day of the week (e.g., "Typical Saturday Performance"), ignoring inactive periods.
    - **Monthly Revenue Trend**: A 12-month rolling line chart to visualize long-term business growth.
- **Product Strategy Tab**:
    - **Top 10 Performers**: Ranked lists of items by both Total Revenue and Total Profit.
    - **Top 5 Partners**: Ranked lists of restaurants/hotels based on their contribution to your earnings.
    - **Multi-Month Selection**: Ability to aggregate performance data across non-contiguous months (e.g., see how Jan, Mar, and Jun performed together).
    - **Bundle Opportunities**: Identifies the top 10 item pairs frequently ordered together, helping you design effective combo offers.

### 📝 Core Sales Management
- **Daily Dashboard**: A high-impact summary of today's Orders, Sales, and Profit, followed by an expandable list of the day's transactions.
- **Intelligent Catalog**:
    - Manage multiple restaurants.
    - Track menu items with specific **Cost Price** and **List Price**.
    - Automatic history tracking: Order line items store the price *at the time of order*, so your historical data remains accurate even if catalog prices change.
- **Order History**: A dedicated section to browse, edit, or delete past orders with deep-filtering by date and month.

### ☁️ Cloud Integration & Accounting
- **Offline-First Sync**: Record orders anywhere, even with no internet. The app uses `WorkManager` to automatically sync data to Google Sheets once you're back online.
- **Google Apps Script Engine**:
    - **Finalize Month**: A powerful script that performs a deep-scan of the monthly sheet to provide definitive totals.
    - **Restaurant Dues**: Automatically calculates how much is owed to each partner hotel based on pure cost data.
    - **Metadata Shield**: Every sync includes a JSON snapshot of the order items stored in Column J, ensuring fail-safe recovery if the database is ever lost.

---

## 🛠️ Technical Architecture

### Android App
- **UI**: 100% Jetpack Compose with Material 3 components for a fluid, modern experience.
- **Persistence**: Room Database with complex SQL triggers and views for analytics.
- **Threading**: Kotlin Coroutines and Flows for real-time UI updates.
- **Charts**: Vico 2.0.0 (Cartesian Chart Host) for high-performance data visualization.
- **Network**: Retrofit 2 & GSON for communicating with the Google Apps Script API.

### Spreadsheet Backend
- **Platform**: Google Sheets.
- **Automation**: Custom Google Apps Script (Javascript) handling atomic row insertion, re-sequencing, and month-end balancing.
- **Security**: Idempotent synchronization using unique `SyncID` to prevent duplicate records.

---

## 🚀 Getting Started

### 1. Installation
Download the latest APK (`Eatupv1.4.5.apk`) from the [Releases](https://github.com/Moonglade-07/EatUp-Sales-Tracker/releases) page and install it on your Android device (Minimum Android 8.0).

### 2. Google Sheets Setup
To enable cloud sync, you must deploy the EatUp Accounting Script:
1. Create a new Google Sheet.
2. Go to **Extensions > Apps Script**.
3. Copy the code from `GOOGLE_APPS_SCRIPT.js` into the editor.
4. Click **Deploy > New Deployment** (Type: Web App, Access: Anyone).
5. Copy the **Web App URL**.

### 3. Connect the App
1. Open EatUp > Side Menu > **Settings**.
2. Paste your Web App URL into the "Google Sheets URL" field.
3. Start recording orders—they will now appear in your sheet automatically!

---

## 🛡️ Reliability & Recovery
- **Force Re-sync**: Found a mistake? Use the "Force Re-sync Specific Date" in Settings to re-upload any single day's data.
- **Full Restore**: Lost your phone? Enter your Sync URL on a new device and click "Restore All Data" to rebuild your local database from the cloud snapshots.

---
*EatUp Sales Tracker: Empowering local food businesses through data-driven growth.*
