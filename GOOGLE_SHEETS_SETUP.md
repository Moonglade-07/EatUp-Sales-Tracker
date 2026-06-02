# EatUp Sales Tracker - Google Sheets Setup Guide
Follow these steps to connect your app to a Google Sheet for live sales tracking.
## Step 1: Create a Google Sheet
Create a new Google Sheet and name it "EatUp Sales Records".
## Step 2: Add the Sync Script
1.In your sheet, go to Extensions > Apps Script.\
2.Delete everything and paste this code:

--------------------------------------

```
// 1. TOOLBAR MENU (Refresh Sheet to see it)
function onOpen() {
  var ui = SpreadsheetApp.getUi();
  ui.createMenu('EatUp Tools')
      .addItem('Finalize Month (Add Totals)', 'finalizeMonthlyReport')
      .addToUi();
}

// 2. THE TOTAL RESTORE ENGINE (GET)
function doGet(e) {
  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var response = { restaurants: [], menuItems: [], orders: [], lineItems: [] };

    // A. RESTORE CATALOG
    var bSheet = ss.getSheetByName("MASTER_CATALOG");
    if (bSheet) {
      var raw = bSheet.getRange(2, 1).getValue();
      if (raw) {
        var catalog = JSON.parse(raw);
        response.restaurants = catalog.restaurants || [];
        response.menuItems = catalog.menuItems || [];
      }
    }

    // B. RESTORE HISTORY
    var sheets = ss.getSheets();
    sheets.forEach(function(s) {
      var name = s.getName();
      if (name.includes(" sales")) {
        var data = s.getDataRange().getDisplayValues(); 
        for (var i = 1; i < data.length; i++) {
          var row = data[i];
          var orderId = row[0]; // Col A
          var syncId = row[8];  // Col I
          if (!orderId || isNaN(orderId) || !syncId) continue;

          var existingOrder = response.orders.find(function(o) { return o.syncId === syncId; });
          if (!existingOrder) {
            var timestamp = parseSheetDate(row[1]).getTime();
            response.orders.push({
              date: timestamp, timestamp: timestamp,
              dailyOrderNumber: parseInt(orderId),
              deliveryCharge: parseFloat(row[5] || 0),
              discount: parseFloat(row[6] || 0),
              totalListPrice: 0, totalCostPrice: 0, profit: 0,
              isSynced: true, syncId: syncId
            });
            existingOrder = response.orders[response.orders.length - 1];
          }

          var shopAmount = parseFloat(row[4] || 0);
          var shopProfit = parseFloat(row[7] || 0);
          existingOrder.totalListPrice += shopAmount;
          existingOrder.profit += shopProfit;

          row[3].split(" + ").forEach(function(itemStr) {
            var parts = itemStr.split(" x ");
            if (parts.length == 2) {
              response.lineItems.push({
                itemName: parts[1], restaurantName: row[2],
                quantity: parseInt(parts[0]), costPriceAtTime: 0, 
                listPriceAtTime: shopAmount / parseInt(parts[0]),
                syncId: syncId 
              });
            }
          });
        }
      }
    });
    return ContentService.createTextOutput(JSON.stringify(response)).setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({error: err.message})).setMimeType(ContentService.MimeType.JSON);
  }
}

function parseSheetDate(str) {
  var parts = str.split("/");
  return new Date(parts[2], parts[1]-1, parts[0]);
}

// 3. THE MAIN SYNC ENGINE (POST)
function doPost(e) {
  try {
    var data = JSON.parse(e.postData.contents);
    var ss = SpreadsheetApp.getActiveSpreadsheet();

    if (data.restaurants && data.menuItems) {
      var bSheet = ss.getSheetByName("MASTER_CATALOG") || ss.insertSheet("MASTER_CATALOG");
      bSheet.clear().appendRow(["Backup Data"]).appendRow([JSON.stringify(data)]).hideSheet();
      return ContentService.createTextOutput("Backup Success");
    }

    var sheet = ss.getSheetByName(data.monthName) || ss.insertSheet(data.monthName);
    if (sheet.getLastRow() === 0) {
      sheet.appendRow(["# ID", "Date", "Shop Name", "Order Details", "Amount", "Delivery", "Discount", "Profit", "SyncID"]);
      sheet.getRange(1, 1, 1, 9).setBackground("#2e7d32").setFontColor("white").setFontWeight("bold").setFrozenRows(1); 
      sheet.hideColumns(9);
    }

    var lastR = sheet.getLastRow();
    if (lastR > 1) {
      var ids = sheet.getRange(2, 9, lastR - 1, 1).getValues().flat();
      for (var i = ids.length - 1; i >= 0; i--) { if (ids[i] === data.syncId) sheet.deleteRow(i + 2); }
    }

    lastR = sheet.getLastRow();
    var lastCell = (lastR > 1) ? sheet.getRange(lastR, 2).getDisplayValue() : "";
    if (lastCell != "" && !lastCell.includes(data.date) && !lastCell.includes("TOTAL") && !lastCell.includes("---")) {
       addTotalRow(sheet, "DAILY TOTAL", "#eeeeee", "black");
       sheet.appendRow([""]); 
    }
    if (!lastCell.includes(data.date)) {
      sheet.appendRow(["", "--- " + data.date + " ---"]);
      sheet.getRange(sheet.getLastRow(), 2).setFontWeight("bold").setHorizontalAlignment("center");
    }

    data.shops.forEach(function(shop, index) {
      sheet.appendRow([data.orderNumber, data.date, shop.shopName, shop.items, shop.foodAmount, (index === 0 ? data.delivery : 0), (index === 0 ? data.discount : 0), shop.shopProfit + (index === 0 ? (data.delivery - data.discount) : 0), data.syncId]);
      updateMasterDues(ss, shop.shopName, data.monthName.toUpperCase(), shop.shopCost);
    });
    return ContentService.createTextOutput("Success");
  } catch (err) { return ContentService.createTextOutput("Error: " + err.message); }
}

function finalizeMonthlyReport() {
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
  addTotalRow(sheet, "DAILY TOTAL", "#eeeeee", "black");
  sheet.appendRow([""]);
  addTotalRow(sheet, "MONTHLY TOTAL", "#1b5e20", "white");
}

function addTotalRow(sheet, label, bgColor, textColor) {
  var lastR = sheet.getLastRow(); if (lastR < 2) return;
  var startR = 2; var bVals = sheet.getRange(1, 2, lastR, 1).getDisplayValues().flat();
  for (var i = bVals.length - 1; i >= 0; i--) { if (bVals[i].toString().indexOf("---") !== -1) { startR = i + 1; break; } }
  var sumS = (label === "MONTHLY TOTAL") ? 2 : startR + 1;
  function getF(col) { return "=SUMIFS(" + col + sumS + ":" + col + lastR + ", $A" + sumS + ":$A" + lastR + ", \">0\")"; }
  sheet.appendRow(["", label, "", "", getF("E"), getF("F"), getF("G"), getF("H")]);
  sheet.getRange(sheet.getLastRow(), 1, 1, 8).setBackground(bgColor).setFontColor(textColor).setFontWeight("bold");
}

function updateMasterDues(ss, shopName, monthColName, cost) {
  var duesSheet = ss.getSheetByName("RESTAURANT DUES") || ss.insertSheet("RESTAURANT DUES");
  if (duesSheet.getLastRow() === 0) { duesSheet.appendRow(["RESTAURANT NAME"]).getRange(1, 1).setBackground("#1565c0").setFontColor("white").setFrozenRows(1); }
  var headers = duesSheet.getRange(1, 1, 1, Math.max(duesSheet.getLastColumn(), 1)).getValues()[0];
  var colIndex = headers.indexOf(monthColName) + 1;
  if (colIndex === 0) { colIndex = duesSheet.getLastColumn() + 1; duesSheet.getRange(1, colIndex).setValue(monthColName).setBackground("#1565c0").setFontColor("white").setFontWeight("bold"); }
  var restaurants = duesSheet.getRange(1, 1, duesSheet.getLastRow(), 1).getValues().flat();
  var rowIndex = restaurants.indexOf(shopName) + 1;
  if (rowIndex === 0) rowIndex = duesSheet.getLastRow() + 1, duesSheet.getRange(rowIndex, 1).setValue(shopName).setFontWeight("bold");
  var currentVal = duesSheet.getRange(rowIndex, colIndex).getValue() || 0;
  duesSheet.getRange(rowIndex, colIndex).setValue(currentVal + cost).setNumberFormat("₹#,##0.00");
}
```

--------------------------------------


## Step 3: Deploy as Web App
1.Click Deploy > New Deployment.\
2.Select Web App.\
3.Set "Execute as" to Me.\
4.Set "Who has access" to Anyone.\
5.Click Deploy and copy the Web App URL.

## Step 4: Link to App
1.Open the EatUp app on your phone.\
2.Go to Settings and paste your URL.\
3.Your sales will now sync automatically!




