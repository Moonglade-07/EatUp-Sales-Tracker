# EatUp Sales Tracker - Google Sheets Setup Guide
Follow these steps to connect your app to a Google Sheet for live sales tracking.
## Step 1: Create a Google Sheet
Create a new Google Sheet and name it "EatUp Sales Records".
## Step 2: Add the Sync Script
1.In your sheet, go to Extensions > Apps Script.\
2.Delete everything and paste this code:

--------------------------------------

```
/**
 * EatUp Sales Tracker
 * =========================================================
 * 
 * It is built to ensure 100% accuracy across both legacy and new data.
 * 
 * KEY FEATURES INCLUDED:
 * ----------------------
 * 1. BACKWARD COMPATIBILITY: Intelligently heals orders where Column K is missing.
 * 2. METADATA SHIELD (COLUMN J): Persistent JSON snapshots for 100% Restore accuracy.
 * 3. SHOP-COST LEDGER (COLUMN K): Dedicated accounting column for precise hotel dues.
 * 4. CONCURRENCY PROTECTION: LockService prevents sync collisions during high-speed bursts.
 * 5. NUCLEAR REPAIR & AUDIT: Global tool to fix sorting, re-numbering, and re-sum all dues.
 * 6. ATOMIC ORDER INTEGRITY: Nuclear delete-and-rewrite pattern for zero duplicate rows.
 * 7. BOUNDED SECTION PURGE: Precision logic that protects future records during edits.
 */

// ============================================================================
// 1. TOOLBAR MENU & SYSTEM ENTRY
// ============================================================================

/**
 * Triggered automatically when the spreadsheet is opened.
 * Builds the professional "EatUp Tools" menu for the business owner.
 */
function onOpen() {
  var spreadsheetUi = SpreadsheetApp.getUi();
  
  spreadsheetUi.createMenu('EatUp Tools')
      .addItem('FORCE REPAIR (Fix All Months, Orders & Dues)', 'nuclearRepairAllSheets')
      .addItem('Finalize Month (Deep Balance & Close)', 'finalizeMonthlyReport')
      .addSeparator()
      .addItem('Refresh Dues Manually', 'manualDuesRecalculation')
      .addToUi();
}

/** 
 * COMPATIBILITY BRIDGES
 * These map every possible old button name to the latest logic.
 */
function repairAndSortSheet() { 
  nuclearRepairAllSheets(); 
}

function repairAllMonthlySheets() { 
  nuclearRepairAllSheets(); 
}

/**
 * Manually forces a refresh of today's totals and adds a clean visual gap.
 */
function finalizeDayReport() {
  var activeSpreadsheet = SpreadsheetApp.getActiveSpreadsheet();
  var activeSheet = activeSpreadsheet.getActiveSheet();
  var allDateValues = activeSheet.getRange("B:B").getDisplayValues().flat();
  var latestHeaderRow = -1;
  
  // Search from the bottom up to find the current day's header boundary
  for (var i = allDateValues.length - 1; i >= 0; i--) {
    if (allDateValues[i].indexOf("---") !== -1) {
      latestHeaderRow = i + 1;
      break;
    }
  }
  
  if (latestHeaderRow !== -1) {
    // Phase 1: Clear existing "DAILY TOTAL" rows to prevent duplication mess
    var sheetLastRow = activeSheet.getLastRow();
    for (var k = sheetLastRow; k > latestHeaderRow; k--) {
      var currentLabel = activeSheet.getRange(k, 2).getDisplayValue();
      if (currentLabel === "DAILY TOTAL") {
        activeSheet.deleteRow(k);
      }
    }
    
    // Phase 2: Add a fresh, accurate total row using hard-calculated numbers
    addHardTotalAtomic(activeSheet, "DAILY TOTAL", "#eeeeee", "black", latestHeaderRow);
    
    // Phase 3: Add a visual row gap for tomorrow's work
    activeSheet.appendRow([""]);
  }
}

// ============================================================================
// 2. THE DISASTER RECOVERY ENGINE (GET REQUEST)
// ============================================================================

/**
 * Triggered by the "Restore from Cloud" button in the Android App.
 * Rebuilds the entire mobile app database from the Spreadsheet rows.
 */
function doGet(e) {
  try {
    var activeSpreadsheet = SpreadsheetApp.getActiveSpreadsheet();
    var finalResponseObject = { restaurants: [], menuItems: [], orders: [], lineItems: [] };
    
    // A. RESTORE CATALOG DATA (Foundational Definitions)
    var catalogBackupSheet = activeSpreadsheet.getSheetByName("MASTER_CATALOG");
    if (catalogBackupSheet) {
      var catalogJsonContent = catalogBackupSheet.getRange(2, 1).getValue();
      if (catalogJsonContent) { 
        var parsedFullCatalog = JSON.parse(catalogJsonContent); 
        finalResponseObject.restaurants = parsedFullCatalog.restaurants || []; 
        finalResponseObject.menuItems = parsedFullCatalog.menuItems || []; 
      }
    }

    // B. RESTORE HISTORY (Orders & Individual Items)
    var spreadsheetSheets = activeSpreadsheet.getSheets();
    spreadsheetSheets.forEach(function(currentMonthSheet) {
      var currentMonthSheetName = currentMonthSheet.getName();
      
      // We only process sheets named following the sales convention (e.g. "JUN sales")
      if (currentMonthSheetName.includes(" sales")) {
        var fullSheetDataMatrix = currentMonthSheet.getDataRange().getDisplayValues(); 
        
        for (var i = 1; i < fullSheetDataMatrix.length; i++) {
          var currentRowData = fullSheetDataMatrix[i];
          var orderNumA = currentRowData[0];   // Column A: Order ID
          var dateStringB = currentRowData[1]; // Column B: Date Text
          var shopNameC = currentRowData[2];   // Column C: Shop Name
          var orderDetailsD = currentRowData[3]; // Column D: Text Summary
          var foodAmountE = currentRowData[4];  // Column E: Amount
          var delChargeF = currentRowData[5];  // Column F: Delivery
          var discAmountG = currentRowData[6];  // Column G: Discount
          var profitH = currentRowData[7];      // Column H: Profit
          var syncIdI = currentRowData[8];      // Column I: Unique Sync ID
          var metadataJ = currentRowData[9];    // Column J: Metadata Shield

          // Guard: Skip headers, empty rows, or totals
          if (!orderNumA || isNaN(orderNumA) || !syncIdI) {
            continue;
          }

          // Check if we have already initialized this Parent Order in the response list
          var parentOrderObject = finalResponseObject.orders.find(function(order) {
             return order.syncId === syncIdI; 
          });

          if (!parentOrderObject) {
            // New transaction detected - Parse date and create the Parent record
            var timestampMillis = safeParseDate(dateStringB).getTime();
            finalResponseObject.orders.push({ 
              date: timestampMillis, 
              timestamp: timestampMillis, 
              dailyOrderNumber: parseInt(orderNumA), 
              deliveryCharge: cleanNum(delChargeF), 
              discount: cleanNum(discAmountG), 
              totalListPrice: 0, 
              totalCostPrice: 0, 
              profit: 0, 
              isSynced: true, 
              syncId: syncIdI 
            });
            parentOrderObject = finalResponseObject.orders[finalResponseObject.orders.length - 1];
          }

          // DATA RECONSTRUCTION LOGIC (THE HEART OF DATA INTEGRITY)
          if (metadataJ && metadataJ.startsWith("[")) {
            // Path 1: Metadata Shield (High-Precision Snapshots)
            var itemsListMetadata = JSON.parse(metadataJ);
            itemsListMetadata.forEach(function(item) { 
              finalResponseObject.lineItems.push({ 
                itemName: item.name, 
                restaurantName: shopNameC, 
                quantity: item.qty, 
                costPriceAtTime: item.cost, 
                listPriceAtTime: item.list, 
                syncId: syncIdI 
              }); 
              parentOrderObject.totalListPrice += (item.list * item.qty); 
              parentOrderObject.totalCostPrice += (item.cost * item.qty); 
              parentOrderObject.profit += ((item.list - item.cost) * item.qty); 
            });
          } else {
            // Path 2: Legacy Healing (Catalog Fallback)
            // Rebuilds missing cost prices using current catalog or row math
            var currentShopFoodAmt = cleanNum(foodAmountE); 
            var currentShopProfitAmt = cleanNum(profitH);
            parentOrderObject.totalListPrice += currentShopFoodAmt; 
            parentOrderObject.profit += currentShopProfitAmt;
            
            var itemsStringArray = orderDetailsD.split(" + ");
            itemsStringArray.forEach(function(singleItemEntry) { 
              var entrySplit = singleItemEntry.split(" x "); 
              if (entrySplit.length == 2) { 
                var itemQty = parseInt(entrySplit[0]); 
                var itemName = entrySplit[1]; 
                
                // Intelligent Lookup in current catalog foundation
                var catalogMatchItem = finalResponseObject.menuItems.find(function(m) { 
                  return m.name.toLowerCase() === itemName.toLowerCase(); 
                }); 
                
                var solvedCost = catalogMatchItem ? catalogMatchItem.costPrice : (currentShopFoodAmt - currentShopProfitAmt) / itemQty; 
                var solvedList = catalogMatchItem ? catalogMatchItem.listPrice : currentShopFoodAmt / itemQty; 
                
                finalResponseObject.lineItems.push({ 
                  itemName: itemName, 
                  restaurantName: shopNameC, 
                  quantity: itemQty, 
                  costPriceAtTime: solvedCost, 
                  listPriceAtTime: solvedList, 
                  syncId: syncIdI 
                }); 
              } 
            });
          }
        }
      }
    });
    
    return ContentService.createTextOutput(JSON.stringify(finalResponseObject)).setMimeType(ContentService.MimeType.JSON);
    
  } catch (error) { 
    return ContentService.createTextOutput(JSON.stringify({error: error.message})).setMimeType(ContentService.MimeType.JSON); 
  }
}

// ============================================================================
// 3. THE SYNC ENGINE (POST REQUEST)
// ============================================================================

/**
 * Handles incoming data packets from Android. 
 * Manages Atomic writes, Multi-Month routing, and Deletion synchronization.
 */
function doPost(e) {
  // ATOMIC LOCK: Ensures only one order is written at a time to prevent corruption
  var scriptLock = LockService.getScriptLock();
  
  try {
    scriptLock.waitLock(30000); // Wait up to 30 seconds for current sync to complete
    var payload = JSON.parse(e.postData.contents);
    var spreadsheet = SpreadsheetApp.getActiveSpreadsheet();

    // A. CATALOG BACKUP LOGIC
    if (payload.restaurants && payload.menuItems) {
      var bSheet = spreadsheet.getSheetByName("MASTER_CATALOG") || spreadsheet.insertSheet("MASTER_CATALOG");
      bSheet.clear();
      bSheet.appendRow(["Backup Header State"]);
      bSheet.appendRow([JSON.stringify(payload)]);
      bSheet.hideSheet();
      return ContentService.createTextOutput("Backup Success");
    }

    // B. MONTH ROUTING (Intelligent Tab Switching)
    var targetMonthTabName = payload.monthName.trim();
    var workingSheetTab = spreadsheet.getSheetByName(targetMonthTabName) || spreadsheet.insertSheet(targetMonthTabName);
    
    if (workingSheetTab.getLastRow() === 0) {
       initializeNewMonthSheet(workingSheetTab);
    }

    // C. ATOMIC ID WIPE (Nuclear Update/Delete Pattern)
    // Deletes all rows matching the SyncID before writing the updated version
    var allSyncIdColumnValues = workingSheetTab.getRange("I:I").getDisplayValues().flat();
    var foundDayHeaderToRecalculate = -1;
    var impactedHotelList = [];
    
    for (var i = allSyncIdColumnValues.length - 1; i >= 0; i--) { 
      if (allSyncIdColumnValues[i] === payload.syncId) { 
        foundDayHeaderToRecalculate = findDateHeaderForRow(workingSheetTab, i + 1);
        var oldHotelName = workingSheetTab.getRange(i+1, 3).getValue();
        if (oldHotelName && impactedHotelList.indexOf(oldHotelName) === -1) {
          impactedHotelList.push(oldHotelName);
        }
        workingSheetTab.deleteRow(i + 1); 
      } 
    }
    SpreadsheetApp.flush(); 

    // D. NUCLEAR STRIKE DELETE HANDLER
    if (payload.isDelete) {
      if (foundDayHeaderToRecalculate !== -1) {
        addHardTotalAtomic(workingSheetTab, "DAILY TOTAL", "#eeeeee", "black", foundDayHeaderToRecalculate);
      }
      // Trigger Dues recalculation for hotels in the deleted order
      impactedHotelList.forEach(function(hotel) {
        recalculateDuesForShop(spreadsheet, hotel, payload.monthName.toUpperCase());
      });
      return ContentService.createTextOutput("Deleted Successfully");
    }

    // E. PHYSICAL ROW INSERTION
    insertOrderAtomic(workingSheetTab, payload, spreadsheet);
    
    // SELF-HEALING ACCOUNTING: Recalculate balances for every hotel in this sync
    payload.shops.forEach(function(sData) {
      recalculateDuesForShop(spreadsheet, sData.shopName, payload.monthName.toUpperCase());
    });

    return ContentService.createTextOutput("Success");
    
  } catch (error) { 
    return ContentService.createTextOutput("Error: " + error.message); 
  } finally { 
    scriptLock.releaseLock(); 
  }
}

/**
 * Positions data chronologically and clears messy totals within a day's scope.
 */
function insertOrderAtomic(sheet, data, spreadsheet) {
  var bColumnDisplayValues = sheet.getRange("B:B").getDisplayValues().flat();
  var dateHeaderLabelString = "--- " + data.date + " ---";
  var headerRowIndexFound = bColumnDisplayValues.indexOf(dateHeaderLabelString) + 1;

  // 1. CHRONOLOGICAL SECTION MANAGEMENT
  if (headerRowIndexFound === 0) {
    var incomingDateObject = safeParseDate(data.date); 
    var finalInsertionTargetRow = sheet.getLastRow() + 1;
    
    for (var i = 1; i < bColumnDisplayValues.length; i++) {
      if (bColumnDisplayValues[i].indexOf("---") !== -1) {
        if (safeParseDate(bColumnDisplayValues[i]) > incomingDateObject) { 
          finalInsertionTargetRow = i + 1; 
          break; 
        }
      }
    }
    sheet.insertRowsBefore(finalInsertionTargetRow, 2);
    var hRangeObj = sheet.getRange(finalInsertionTargetRow, 2);
    hRangeObj.setNumberFormat("@").setValue(dateHeaderLabelString).setFontWeight("bold").setHorizontalAlignment("center");
    
    headerRowIndexFound = finalInsertionTargetRow;
    bColumnDisplayValues = sheet.getRange("B:B").getDisplayValues().flat(); 
  }

  // 2. BOUNDED PURGE: Identify Boundaries and Wipe Totals
  var sheetCurrentTotalRows = sheet.getLastRow();
  var nextDayHeaderLimitRowIndex = sheetCurrentTotalRows + 1;
  
  for (var k = headerRowIndexFound + 1; k <= sheetCurrentTotalRows; k++) { 
    if (bColumnDisplayValues[k-1].indexOf("---") !== -1) { 
      nextDayHeaderLimitRowIndex = k; 
      break; 
    } 
  }

  for (var r = nextDayHeaderLimitRowIndex - 1; r > headerRowIndexFound; r--) {
    var colBText = sheet.getRange(r, 2).getDisplayValue();
    var colAText = sheet.getRange(r, 1).getDisplayValue();
    if (colBText === "DAILY TOTAL" || colAText === "" || colBText === "MONTHLY TOTAL") { 
      sheet.deleteRow(r); 
      nextDayHeaderLimitRowIndex--; 
    }
  }
  SpreadsheetApp.flush();

  // 3. PHYSICAL DATA WRITE
  var insertRowCoordinate = nextDayHeaderLimitRowIndex;
  sheet.insertRowsBefore(insertRowCoordinate, data.shops.length);
  
  data.shops.forEach(function(shop, index) {
    // Column K (Index 10) stores pure ShopCost for accounting precision
    var finalOrderRowDataArray = [
      data.orderNumber, "'" + data.date, shop.shopName, shop.items, shop.foodAmount, 
      (index === 0 ? data.delivery : 0), (index === 0 ? data.discount : 0), 
      shop.shopProfit + (index === 0 ? (data.delivery - data.discount) : 0), 
      data.syncId, shop.itemsMetadata, shop.shopCost
    ];
    
    var dataRangeObj = sheet.getRange(insertRowCoordinate + index, 1, 1, 11);
    dataRangeObj.setValues([finalOrderRowDataArray]);
    
    // Explicitly Reset Formatting to normal (fixes bold glitch)
    dataRangeObj.setFontWeight("normal");
    dataRangeObj.setFontColor("black");
    dataRangeObj.setBackground(null);
  });

  addHardTotalAtomic(sheet, "DAILY TOTAL", "#eeeeee", "black", headerRowIndexFound);
}

// ============================================================================
// 4. NUCLEAR REPAIR & LEGACY HEALING SYSTEM
// ============================================================================

/**
 * The Ultimate Fix-Everything Utility.
 * 1. Repairs corrupted dates across all sheets.
 * 2. HEALS Column K for old rows using Amount - Profit logic.
 * 3. Re-sequences all IDs (Group-Aware).
 * 4. Rebuilds the Dues matrix from accurate source logs.
 */
function nuclearRepairAllSheets() {
  var activeSpreadsheetInstance = SpreadsheetApp.getActiveSpreadsheet();
  var allSheetsList = activeSpreadsheetInstance.getSheets();
  
  // Phase 1: Deep Audit of all Sales Tabs
  allSheetsList.forEach(function(currentTab) { 
    if (currentTab.getName().includes(" sales")) {
      performDeepAuditAndLegacyHeal(currentTab, activeSpreadsheetInstance); 
    }
  });
  
  // Phase 2: Full Wipe and Recalculation of Restaurant Dues
  var masterDuesSheet = activeSpreadsheetInstance.getSheetByName("RESTAURANT DUES");
  if (masterDuesSheet && masterDuesSheet.getLastRow() > 1 && masterDuesSheet.getLastColumn() > 1) {
    masterDuesSheet.getRange(2, 2, masterDuesSheet.getLastRow() - 1, masterDuesSheet.getLastColumn() - 1).clearContent();
  }
  
  allSheetsList.forEach(function(currentTab) {
    if (currentTab.getName().includes(" sales")) {
      var monthlyRawValues = currentTab.getDataRange().getValues();
      var monthHeaderIdentifier = currentTab.getName().split(" ")[0].toUpperCase() + " SALES";
      for (var i = 1; i < monthlyRawValues.length; i++) {
        if (monthlyRawValues[i][0] > 0 && monthlyRawValues[i][2] != "") {
           // Increment based audit rebuild
           updateDuesIncrementally(activeSpreadsheetInstance, monthlyRawValues[i][2], monthHeaderIdentifier, cleanNum(monthlyRawValues[i][10]));
        }
      }
    }
  });
  
  SpreadsheetApp.getUi().alert("Audit Complete! All data repaired, IDs re-numbered, and dues synchronized perfectly.");
}

function performDeepAuditAndLegacyHeal(shInstance, ss) {
  var displayValuesMatrix = shInstance.getDataRange().getDisplayValues();
  var ordersAuditList = [];
  
  // Extract records and repair historical format errors
  for (var i = 1; i < displayValuesMatrix.length; i++) {
    var currentRowData = displayValuesMatrix[i];
    if (currentRowData[0] != "" && !isNaN(currentRowData[0]) && currentRowData[8] != "") {
       // Repair flipped/long dates
       if (currentRowData[1].length > 10) { 
         var parts = currentRowData[1].split(" "); var map = {Jan:'01',Feb:'02',Mar:'03',Apr:'04',May:'05',Jun:'06',Jul:'07',Aug:'08',Sep:'09',Oct:'10',Nov:'11',Dec:'12'};
         currentRowData[1] = parts[2] + "/" + map[parts[1]] + "/" + parts[3];
       }
       
       // HEALING: If Column K (idx 10) is empty for this old record, auto-calculate it
       if (currentRowData[10] == "" || currentRowData[10] == null || cleanNum(currentRowData[10]) === 0) {
          var rowAmtE = cleanNum(currentRowData[4]);
          var rowProH = cleanNum(currentRowData[7]);
          currentRowData[10] = rowAmtE - rowProH; 
       }
       
       ordersAuditList.push(currentRowData);
    }
  }
  
  if (ordersAuditList.length === 0) return;

  // Final Chronological Sort
  ordersAuditList.sort(function(a, b) { 
    var timeA = safeParseDate(a[1]); var timeB = safeParseDate(b[1]); 
    if (timeA.getTime() !== timeB.getTime()) return timeA - timeB;
    return a[0] - b[0];
  });

  // Re-write the entire sheet from scratch with perfect formatting
  shInstance.clear(); 
  initializeNewMonthSheet(shInstance);
  
  var currentLocalDate = "", dayIdSequenceCounter = 0, lastSyncIdEncountered = "";
  ordersAuditList.forEach(function(row) {
    var rowSyncId = row[8];
    if (row[1] !== currentLocalDate) { 
      // Before drawing new date, finish previous day
      if (currentLocalDate !== "") { 
        var headerPosFound = findDateHeader(shInstance, currentLocalDate);
        if (headerPosFound !== -1) addHardTotalAtomic(shInstance, "DAILY TOTAL", "#eeeeee", "black", headerPosFound);
        shInstance.appendRow([""]); 
      } 
      var headerRangeObj = shInstance.getRange(shInstance.getLastRow() + 1, 2); 
      headerRangeObj.setNumberFormat("@").setValue("--- " + row[1] + " ---").setFontWeight("bold").setHorizontalAlignment("center");
      currentLocalDate = row[1]; dayIdSequenceCounter = 1; lastSyncIdEncountered = rowSyncId;
    } else {
      // GROUP-AWARE SEQUENCING: Link rows with matching transaction IDs
      if (rowSyncId !== lastSyncIdEncountered) { dayIdSequenceCounter++; lastSyncIdEncountered = rowSyncId; }
    }
    
    // Assign corrected sequential ID and write row
    row[0] = dayIdSequenceCounter;
    shInstance.appendRow(row);
    shInstance.getRange(shInstance.getLastRow(), 1, 1, 11).setFontWeight("normal");
  });
  
  var finalHPosition = findDateHeader(shInstance, currentLocalDate);
  if (finalHPosition !== -1) addHardTotalAtomic(shInstance, "DAILY TOTAL", "#eeeeee", "black", finalHPosition);
}

// ============================================================================
// 5. SUMMATION & DUES LOGIC (SELF-HEALING)
// ============================================================================

/**
 * Intelligent Dues Auditor: Scans Column K costs and overwrites the balance.
 * Uses fallback math if Column K is missing to prevent zeros in history.
 */
function recalculateDuesForShop(activeSs, shopName, monthColName) {
  var salesSheetObj = activeSs.getSheetByName(monthColName.toLowerCase());
  if (!salesSheetObj) return;
  
  var fullSheetDataMatrix = salesSheetObj.getDataRange().getValues();
  var finalAccurateTotal = 0;
  
  for (var i = 1; i < fullSheetDataMatrix.length; i++) {
    // Column C is RestaurantName, Column K is ShopCost
    if (fullSheetDataMatrix[i][2] === shopName && fullSheetDataMatrix[i][0] > 0) {
       var valueInK = cleanNum(fullSheetDataMatrix[i][10]);
       
       // HEALING FALLBACK: If Column K was empty for this specific row
       if (valueInK === 0) {
         valueInK = cleanNum(fullSheetDataMatrix[i][4]) - cleanNum(fullSheetDataMatrix[i][7]);
       }
       
       finalAccurateTotal += valueInK; 
    }
  }
  
  writeDuesResultToMatrix(activeSs, shopName, monthColName, finalAccurateTotal);
}

function writeDuesResultToMatrix(ss, shop, col, finalValue) {
  var matrixSheetObj = ss.getSheetByName("RESTAURANT DUES") || ss.insertSheet("RESTAURANT DUES");
  if (matrixSheetObj.getLastRow() === 0) { 
    matrixSheetObj.appendRow(["RESTAURANT NAME"]); 
    var hR = matrixSheetObj.getRange(1, 1); hR.setBackground("#1565c0").setFontColor("white").setFontWeight("bold"); matrixSheetObj.setFrozenRows(1); 
  }
  
  var allHeadersArray = matrixSheetObj.getRange(1, 1, 1, Math.max(matrixSheetObj.getLastColumn(), 1)).getValues()[0];
  var targetColIdx = allHeadersArray.indexOf(col) + 1;
  
  if (targetColIdx === 0) { 
    targetColIdx = matrixSheetObj.getLastColumn() + 1; 
    var newHeaderCell = matrixSheetObj.getRange(1, targetColIdx);
    newHeaderCell.setValue(col).setBackground("#1565c0").setFontColor("white").setFontWeight("bold"); 
  }
  
  var allShopsArray = matrixSheetObj.getRange(1, 1, matrixSheetObj.getLastRow(), 1).getValues().flat();
  var targetRowIdx = allShopsArray.indexOf(shop) + 1;
  
  if (targetRowIdx === 0) { 
    targetRowIdx = matrixSheetObj.getLastRow() + 1; 
    var newNameCell = matrixSheetObj.getRange(targetRowIdx, 1);
    newNameCell.setValue(shop).setFontWeight("bold"); 
  }
  
  // Overwrite the dues with the perfectly recalculated sum
  var targetAmountCell = matrixSheetObj.getRange(targetRowIdx, targetColIdx);
  targetAmountCell.setValue(finalValue).setNumberFormat("₹#,##0.00");
}

function updateDuesIncrementally(ss, name, col, val) {
  var targetSheet = ss.getSheetByName("RESTAURANT DUES") || ss.insertSheet("RESTAURANT DUES");
  if (targetSheet.getLastRow() === 0) { targetSheet.appendRow(["RESTAURANT NAME"]); targetSheet.getRange(1, 1).setBackground("#1565c0").setFontColor("white").setFontWeight("bold").setFrozenRows(1); }
  var heads = targetSheet.getRange(1, 1, 1, Math.max(targetSheet.getLastColumn(), 1)).getValues()[0];
  var cIdx = heads.indexOf(col) + 1;
  if (cIdx === 0) { cIdx = targetSheet.getLastColumn() + 1; targetSheet.getRange(1, cIdx).setValue(col).setBackground("#1565c0").setFontColor("white").setFontWeight("bold"); }
  var rests = targetSheet.getRange(1, 1, targetSheet.getLastRow(), 1).getValues().flat();
  var rIdx = rests.indexOf(name) + 1;
  if (rIdx === 0) { rIdx = targetSheet.getLastRow() + 1; targetSheet.getRange(rIdx, 1).setValue(name).setFontWeight("bold"); }
  var cellObj = targetSheet.getRange(rIdx, cIdx); 
  cellObj.setValue(cleanNum(cellObj.getValue()) + val).setNumberFormat("₹#,##0.00");
}

// ============================================================================
// 6. LOW-LEVEL MATH & DATA HELPERS
// ============================================================================

/**
 * Calculates a hard daily total for a section. Uses values to prevent #N/A.
 */
function addHardTotalAtomic(sheet, label, bgColor, textColor, headerRow) {
  var lastSheetRow = sheet.getLastRow(); 
  var allBVals = sheet.getRange("B:B").getDisplayValues().flat(); 
  var sectionBoundaryRow = lastSheetRow + 1;
  
  // Determine where current date scope ends
  for (var k = headerRow + 1; k <= lastSheetRow; k++) { 
    if (allBVals[k-1].indexOf("---") !== -1) { 
      sectionBoundaryRow = k; 
      break; 
    } 
  }
  
  var blockDataValues = sheet.getRange(headerRow + 1, 1, Math.max(sectionBoundaryRow - 1 - headerRow, 1), 8).getDisplayValues();
  var sAmt = 0, sDel = 0, sDis = 0, sPro = 0;
  
  for (var i = 0; i < blockDataValues.length; i++) { 
    var idCell = blockDataValues[i][0];
    if (idCell && !isNaN(idCell) && parseInt(idCell) > 0) { 
      sAmt += cleanNum(blockDataValues[i][4]); 
      sDel += cleanNum(blockDataValues[i][5]); 
      sDis += cleanNum(blockDataValues[i][6]); 
      sPro += cleanNum(blockDataValues[i][7]); 
    } 
  }
  
  sheet.insertRowBefore(sectionBoundaryRow);
  var totalRowRange = sheet.getRange(sectionBoundaryRow, 1, 1, 11);
  totalRowRange.setValues([["", label, "", "", sAmt, sDel, sDis, sPro, "", "", ""]]);
  totalRowRange.setBackground(bgColor).setFontColor(textColor).setFontWeight("bold");
}

function cleanNum(val) { 
  if (typeof val === "number") return val; 
  var cleanedText = String(val).replace(/[^0-9.-]+/g, ""); 
  return parseFloat(cleanedText) || 0; 
}

function safeParseDate(inputStr) { 
  var sanit = String(inputStr).replace(/---/g, "").trim(); 
  var parts = sanit.split("/"); 
  if (parts.length !== 3) return new Date(0); 
  return new Date(parts[2], parts[1]-1, parts[0]); 
}

function findDateHeader(sh, dt) {
  var valuesB = sh.getRange("B:B").getDisplayValues().flat();
  return valuesB.indexOf("--- " + dt + " ---") + 1;
}

function findDateHeaderForRow(sheetInstance, row) {
  var bValuesUpToRow = sheetInstance.getRange(1, 2, row, 1).getDisplayValues().flat();
  for (var i = bValuesUpToRow.length - 1; i >= 0; i--) { 
    if (bValuesUpToRow[i].indexOf("---") !== -1) return i + 1; 
  }
  return -1;
}

function initializeNewMonthSheet(sh) { 
  sh.appendRow(["# ID", "Date", "Shop Name", "Order Details", "Amount", "Delivery", "Discount", "Profit", "SyncID", "Metadata", "ShopCost"]); 
  var hRange = sh.getRange(1, 1, 1, 11);
  hRange.setBackground("#2e7d32"); hRange.setFontColor("white"); hRange.setFontWeight("bold");
  sh.setFrozenRows(1); 
  sh.hideColumns(9, 3); 
}

function finalizeMonthlyReport() { 
  addHardTotalAtomic(SpreadsheetApp.getActiveSpreadsheet().getActiveSheet(), "MONTHLY TOTAL", "#1b5e20", "white", 1); 
}

function manualDuesRecalculation() { 
  SpreadsheetApp.getUi().alert("Note: This logic is automatic. To force a full system audit of all months, click FORCE REPAIR."); 
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




