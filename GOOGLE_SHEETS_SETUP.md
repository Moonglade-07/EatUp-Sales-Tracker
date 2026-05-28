# EatUp Sales Tracker - Google Sheets Setup Guide
Follow these steps to connect your app to a Google Sheet for live sales tracking.
## Step 1: Create a Google Sheet
Create a new Google Sheet and name it "EatUp Sales Records".
## Step 2: Add the Sync Script
1.In your sheet, go to Extensions > Apps Script.\
2.Delete everything and paste this code:

--------------------------------------

```
function doPost(e) {
  try {
    var data = JSON.parse(e.postData.contents);
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var sheetName = data.monthName;
    var sheet = ss.getSheetByName(sheetName) || ss.insertSheet(sheetName);
    
    if (sheet.getLastRow() === 0) {
      sheet.appendRow(["# ID", "Date", "Shop Name", "Order Details", "Amount", "Delivery", "Discount", "Profit", "SyncID"]);
      sheet.getRange(1, 1, 1, 9).setBackground("#2e7d32").setFontColor("white").setFontWeight("bold");
      sheet.setFrozenRows(1);
      sheet.hideColumns(9); 
    }

    var lastRow = sheet.getLastRow();
    if (lastRow > 1) {
      var range = sheet.getRange(2, 9, lastRow - 1, 1);
      var syncIds = range.getValues().flat();
      if (syncIds.indexOf(data.syncId) !== -1) return ContentService.createTextOutput("Already Synced");
    }

    var lastCell = (lastRow > 1) ? sheet.getRange(lastRow, 2).getDisplayValue() : "";
    if (lastCell != "" && !lastCell.includes(data.date)) {
       sheet.appendRow(["", "DAILY TOTAL", "", "", "=SUM(E2:E" + lastRow + ")", "=SUM(F2:F" + lastRow + ")", "=SUM(G2:G" + lastRow + ")", "=SUM(H2:H" + lastRow + ")"]);
       sheet.getRange(sheet.getLastRow(), 1, 1, 8).setBackground("#f5f5f5").setFontWeight("bold");
       sheet.appendRow([""]); 
    }

    if (!lastCell.includes(data.date)) {
      sheet.appendRow(["", "--- " + data.date + " ---"]);
      sheet.getRange(sheet.getLastRow(), 2).setFontWeight("bold").setHorizontalAlignment("center");
    }

    data.shops.forEach(function(shop, index) {
      sheet.appendRow([
        data.orderNumber, data.date, shop.shopName, shop.items, shop.foodAmount,
        (index === 0 ? data.delivery : 0), (index === 0 ? data.discount : 0),
        shop.shopProfit + (index === 0 ? (data.delivery - data.discount) : 0),
        data.syncId
      ]);
      updateMasterDues(ss, shop.shopName, data.monthName.toUpperCase(), shop.shopCost);
    });
    return ContentService.createTextOutput("Success");
  } catch (err) { return ContentService.createTextOutput("Error: " + err.message); }
}

function updateMasterDues(ss, shopName, monthColName, cost) {
  var duesSheet = ss.getSheetByName("RESTAURANT DUES") || ss.insertSheet("RESTAURANT DUES");
  if (duesSheet.getLastRow() === 0) {
    duesSheet.appendRow(["RESTAURANT NAME"]);
    duesSheet.getRange(1, 1).setBackground("#1565c0").setFontColor("white").setFontWeight("bold");
    duesSheet.setFrozenRows(1);
    duesSheet.setFrozenColumns(1);
  }
  var headers = duesSheet.getRange(1, 1, 1, Math.max(duesSheet.getLastColumn(), 1)).getValues()[0];
  var colIndex = headers.indexOf(monthColName) + 1;
  if (colIndex === 0) {
    colIndex = duesSheet.getLastColumn() + 1;
    duesSheet.getRange(1, colIndex).setValue(monthColName).setBackground("#1565c0").setFontColor("white").setFontWeight("bold");
  }
  var restaurants = duesSheet.getRange(1, 1, Math.max(duesSheet.getLastRow(), 1), 1).getValues().flat();
  var rowIndex = restaurants.indexOf(shopName) + 1;
  if (rowIndex === 0) {
    rowIndex = duesSheet.getLastRow() + 1;
    duesSheet.getRange(rowIndex, 1).setValue(shopName).setFontWeight("bold");
  }
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
5.Click Deploy and copy the Web App URL.\

## Step 4: Link to App
1.Open the EatUp app on your phone.\
2.Go to Settings and paste your URL.\
3.Your sales will now sync automatically!\




