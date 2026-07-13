/**
 * EatUp Sales Tracker - Definitive Titan Maximum Edition (v13.8.3)
 * =================================================================
 *
 * THIS IS THE FINAL, FULLY EXPANDED SYSTEM ACCOUNTING ENGINE.
 * IT IS BUILT TO BE INDESTRUCTIBLE, SCALABLE, AND MERCHANT-FRIENDLY.
 *
 * -----------------------------------------------------------------
 * VERSION 13.8.3 HIGHLIGHTS:
 * 1. FIXED SYNTAX ERROR: Resolved "Invalid left-hand side in assignment"
 *    on line 627 and other logic-check locations.
 * 2. VARIABLE CONSISTENCY: Fixed name mismatches in restoration loops.
 * 3. DATE-TARGETED REPAIR: Prompt-based healing for specific days.
 * 4. GLOBAL CHRONO-SYNC: Backdated orders now find headers anywhere.
 * 5. TOTAL-WIPE-AND-WRITE: Prevents double/triple total rows.
 * 6. LEGACY HEALING: Automatically fills Column K for old data.
 * 7. CONCURRENCY PROTECTION: LockService for multi-sync safety.
 * -----------------------------------------------------------------
 *
 * CORE SYSTEMS INCLUDED:
 * ----------------------
 * 1. BULLETPROOF DATE PARSER: Handles DD/MM or MM/DD flips automatically.
 * 2. REVERSE-SEARCH SYNC: High-speed sync optimized for large sheets.
 * 3. GLOBAL FALLBACK SEARCH: Ensures backdated orders never vanish.
 * 4. TIME-MERGE PROTECTION: Support for anchored timestamps from phone.
 * 5. METADATA SHIELD (COL J): Persistent JSON snapshots for recovery.
 * 6. SELF-HEALING DUES (COL K): Scoped recalculation for hotel ledgers.
 * 7. NUCLEAR GLOBAL REPAIR: One-click cross-sheet healing and sorting.
 * 8. ATOMIC INSERTION: Sequential write pattern with zero-mess totals.
 */

// ============================================================================
// SECTION 1: SYSTEM MENU & USER INTERFACE
// ============================================================================

/**
 * Triggered automatically whenever the spreadsheet file is opened.
 * Builds the professional "EatUp Tools" command center in the top toolbar.
 */
function onOpen() {
  var spreadsheetUi = SpreadsheetApp.getUi();

  // Construct the custom menu with descriptive action items
  spreadsheetUi.createMenu('EatUp Tools')
      .addItem('FORCE REPAIR (Audit All Months & Dues)', 'nuclearRepairAllSheets')
      .addItem('Repair Specific Date (Auto-Fix a Day)', 'repairSpecificDatePrompt')
      .addItem('Finalize Month (Deep Balance & Close)', 'finalizeMonthlyReport')
      .addSeparator()
      .addItem('Refresh Dues Manually', 'manualDuesRecalculation')
      .addToUi();
}

/**
 * COMPATIBILITY BRIDGES
 * These map every possible historical button name to the latest Titan logic.
 * This ensures that buttons cached in your browser will never throw errors.
 */
function repairAndSortSheet() {
  nuclearRepairAllSheets();
}

function repairAllMonthlySheets() {
  nuclearRepairAllSheets();
}

function targetedRepairPrompt() {
  var uiObject = SpreadsheetApp.getUi();
  uiObject.alert("This tool has been upgraded! Please use 'Repair Specific Date' from the menu instead. It is much more accurate.");
}

/**
 * Manually refreshes the daily total for the current view and adds a gap.
 * Useful for "sealing" a day at the end of a shift.
 */
function finalizeDayReport() {
  var activeSpreadsheetObject = SpreadsheetApp.getActiveSpreadsheet();
  var activeSheetTab = activeSpreadsheetObject.getActiveSheet();

  // Locate all values in column B to find date headers
  var allBColumnDisplayValues = activeSheetTab.getRange("B:B").getDisplayValues().flat();
  var targetHeaderRowCoordinate = -1;

  // Search from the bottom up to locate the boundary of the current date section
  for (var i = allBColumnDisplayValues.length - 1; i >= 0; i--) {
    if (allBColumnDisplayValues[i].indexOf("---") !== -1) {
      targetHeaderRowCoordinate = i + 1;
      break;
    }
  }

  if (targetHeaderRowCoordinate !== -1) {
    // Stage 1: Aggressive cleanup of existing totals to prevent stacking duplication
    var currentLastSheetRowIdx = activeSheetTab.getLastRow();

    for (var k = currentLastSheetRowIdx; k > targetHeaderRowCoordinate; k--) {
      var currentLabelTextValue = activeSheetTab.getRange(k, 2).getDisplayValue();
      if (currentLabelTextValue === "DAILY TOTAL") {
        activeSheetTab.deleteRow(k);
      }
    }

    // Stage 2: Append a fresh summary row using hard-calculated static numbers
    addHardTotalAtomic(activeSheetTab, "DAILY TOTAL", "#eeeeee", "black", targetHeaderRowCoordinate);

    // Stage 3: Provide a visual row gap for the next chronological entry
    activeSheetTab.appendRow([""]);
  }
}

// ============================================================================
// SECTION 2: THE TOTAL RESTORE ENGINE (HTTP GET)
// ============================================================================

/**
 * Triggered by the "Restore from Cloud" request from the Android Mobile App.
 * Performs a deep-scan of the entire ledger to rebuild the mobile SQL database.
 * Synchronized field names match Android v13.5 perfectly.
 */
function doGet(e) {
  try {
    var ssObjectHandle = SpreadsheetApp.getActiveSpreadsheet();
    var finalResponseObject = { restaurants: [], menuItems: [], orders: [], lineItems: [] };

    // PHASE A: CATALOG RECOVERY (Logical Foundation)
    var catalogBackupTabSheet = ssObjectHandle.getSheetByName("MASTER_CATALOG");

    if (catalogBackupTabSheet) {
      var catalogJsonRawStringData = catalogBackupTabSheet.getRange(2, 1).getValue();
      if (catalogJsonRawStringData) {
        var parsedFullCatalogStateData = JSON.parse(catalogJsonRawStringData);
        finalResponseObject.restaurants = parsedFullCatalogStateData.restaurants || [];
        finalResponseObject.menuItems = parsedFullCatalogStateData.menuItems || [];
      }
    }

    // PHASE B: HISTORICAL TRANSACTIONS (Scanning all Monthly Tabs)
    var spreadsheetTabsListArray = ssObjectHandle.getSheets();
    spreadsheetTabsListArray.forEach(function(currentMonthTabSheet) {
      var currentMonthTabNameStr = currentMonthTabSheet.getName();

      // We only read from sheets containing actual sales logs
      if (currentMonthTabNameStr.includes(" sales")) {
        var fullSheetDataMatrixValues = currentMonthTabSheet.getDataRange().getDisplayValues();

        for (var i = 1; i < fullSheetDataMatrixValues.length; i++) {
          var currentRowDataRowValues = fullSheetDataMatrixValues[i];
          var orderIdInColA = currentRowDataRowValues[0];     // Column A: Sequential ID
          var dateTextInColB = currentRowDataRowValues[1];    // Column B: Date string
          var shopNameInColC = currentRowDataRowValues[2];    // Column C: Source Hotel
          var itemSumInColD = currentRowDataRowValues[3];     // Column D: Text Summary
          var foodAmtInColE = currentRowDataRowValues[4];     // Column E: Amount
          var delChargeInColF = currentRowDataRowValues[5];   // Column F: Delivery
          var discAmtInColG = currentRowDataRowValues[6];     // Column G: Discount
          var profitAmtInColH = currentRowDataRowValues[7];   // Column H: Net Profit
          var syncIdInColI = currentRowDataRowValues[8];      // Column I: Unique Sync ID
          var metadataInColJ = currentRowDataRowValues[9];    // Column J: Metadata snapshot

          // Guard Clause: Only process rows that are valid order entries
          if (!orderIdInColA || isNaN(orderIdInColA) || !syncIdInColI) {
            continue;
          }

          // Check if we have already initialized this Parent Order record
          var existingOrderRefObject = finalResponseObject.orders.find(function(order) {
             return order.syncId === syncIdInColI;
          });

          if (!existingOrderRefObject) {
            // New transaction detected - Parse date string and create the Parent record
            var orderTimestampMillisVal = bulletproofDateParser(dateTextInColB).getTime();
            finalResponseObject.orders.push({
              date: orderTimestampMillisVal,
              timestamp: orderTimestampMillisVal,
              dailyOrderNumber: parseInt(orderIdInColA),
              deliveryCharge: cleanNum(delChargeInColF),
              discount: cleanNum(discAmtG = discAmtInColG),
              totalListPrice: 0,
              totalCostPrice: 0,
              profit: 0,
              isSynced: true,
              syncId: syncIdInColI
            });
            existingOrderRefObject = finalResponseObject.orders[finalResponseObject.orders.length - 1];
          }

          // ITEM RECONSTRUCTION LOGIC
          if (metadataInColJ && metadataInColJ.startsWith("[")) {
            // Path 1: Metadata Shield (High-Precision Snapshots)
            var snapshotItemsArrayList = JSON.parse(metadataInColJ);
            snapshotItemsArrayList.forEach(function(item) {
              finalResponseObject.lineItems.push({
                itemName: item.name,
                restaurantName: shopNameInColC,
                quantity: item.qty,
                costPriceAtTime: item.cost,
                listPriceAtTime: item.list,
                syncId: syncIdInColI
              });
              existingOrderRefObject.totalListPrice += (item.list * item.qty);
              existingOrderRefObject.totalCostPrice += (item.cost * item.qty);
              existingOrderRefObject.profit += ((item.list - item.cost) * item.qty);
            });
          } else {
            // Path 2: Legacy Healing (Catalog Fallback)
            var totalShopRevenueValueNum = cleanNum(foodAmtInColE);
            var totalShopProfitValueNum = cleanNum(profitAmtInColH);
            existingOrderRefObject.totalListPrice += totalShopRevenueValueNum;
            existingOrderRefObject.profit += totalShopProfitValueNum;

            var itemsDescriptionParts = itemSumInColD.split(" + ");
            itemsDescriptionParts.forEach(function(singleItemEntryText) {
              var entrySplitPartsList = singleItemEntryText.split(" x ");
              if (entrySplitPartsList.length == 2) {
                var itemQuantityCountVal = parseInt(entrySplitPartsList[0]);
                var dishItemNameString = entrySplitPartsList[1];

                // Smart Lookup in catalog foundation
                var catalogMatchDishObject = finalResponseObject.menuItems.find(function(m) {
                  return m.name.toLowerCase() === dishItemNameString.toLowerCase();
                });

                var solvedCostAtTime = catalogMatchDishObject ? catalogMatchDishObject.costPrice : (totalShopRevenueValueNum - totalShopProfitValueNum) / itemQuantityCountVal;
                var solvedListAtTime = catalogMatchDishObject ? catalogMatchDishObject.listPrice : totalShopRevenueValueNum / itemQuantityCountVal;

                finalResponseObject.lineItems.push({
                  itemName: dishItemNameString,
                  restaurantName: shopNameInColC,
                  quantity: itemQuantityCountVal,
                  costPriceAtTime: solvedCostAtTime,
                  listPriceAtTime: solvedListAtTime,
                  syncId: syncIdInColI
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
// SECTION 3: THE ATOMIC SYNC ENGINE (HTTP POST)
// ============================================================================

/**
 * Handles all data ingestion from the Android App.
 * Optimized with GLOBAL SEARCH and SCRIPT LOCKING for indestructible synchronization.
 */
function doPost(e) {
  // ATOMIC LOCK: Prevents row corruption during simultaneous syncs
  var lockObjectHandle = LockService.getScriptLock();

  try {
    lockObjectHandle.waitLock(60000); // 60 second timeout queue (Boosted for batch resync)
    var requestDataPayload = JSON.parse(e.postData.contents);
    var activeSsInstanceObj = SpreadsheetApp.getActiveSpreadsheet();

    // A. CATALOG MIRROR HANDLER
    if (requestDataPayload.restaurants && requestDataPayload.menuItems) {
      var catalogTabSheetTab = activeSsInstanceObj.getSheetByName("MASTER_CATALOG") || activeSsInstanceObj.insertSheet("MASTER_CATALOG");
      catalogTabSheetTab.clear();
      catalogTabSheetTab.appendRow(["Mirror Backup Data State"]);
      catalogTabSheetTab.appendRow([JSON.stringify(requestDataPayload)]);
      catalogTabSheetTab.hideSheet();
      return ContentService.createTextOutput("Backup Success");
    }

    // B. TARGET MONTH ROUTING
    var targetMonthTabNameStr = requestDataPayload.monthName.trim();
    var workingMonthTabSheet = activeSsInstanceObj.getSheetByName(targetMonthTabNameStr) || activeSsInstanceObj.insertSheet(targetMonthTabNameStr);

    if (workingMonthTabSheet.getLastRow() === 0) {
       initializeNewMonthSheet(workingMonthTabSheet);
    }

    // C. GLOBAL ATOMIC WIPE
    // We scan the WHOLE sheet for the SyncID to ensure moved orders are deleted from their old locations.
    var idColumnValuesList = workingMonthTabSheet.getRange("I:I").getDisplayValues().flat();
    var dayHeaderToRefreshIndex = -1;
    var impactedHotelsArrayList = [];

    for (var i = idColumnValuesList.length - 1; i >= 0; i--) {
      if (idColumnValuesList[i] === requestDataPayload.syncId) {
        var absoluteRowCoordinate = i + 1;
        dayHeaderToRefreshIndex = findDateHeaderForRow(workingMonthTabSheet, absoluteRowCoordinate);

        var shopNameOnThisRow = workingMonthTabSheet.getRange(absoluteRowCoordinate, 3).getValue();
        if (shopNameOnThisRow && impactedHotelsArrayList.indexOf(shopNameOnThisRow) === -1) {
          impactedHotelsArrayList.push(shopNameOnThisRow);
        }
        // Nuclear deletion of the old version to allow clean rewrite or move
        workingMonthTabSheet.deleteRow(absoluteRowCoordinate);
      }
    }
    SpreadsheetApp.flush();

    // Refresh totals for the section we just deleted from (if any)
    if (dayHeaderToRefreshIndex !== -1) {
      // Safety check: ensure header still exists
      var headerVal = workingMonthTabSheet.getRange(dayHeaderToRefreshIndex, 2).getValue();
      if (headerVal.indexOf("---") !== -1) {
        addHardTotalAtomic(workingMonthTabSheet, "DAILY TOTAL", "#eeeeee", "black", dayHeaderToRefreshIndex);
      }
    }

    // D. NUCLEAR STRIKE DELETE HANDLER
    if (requestDataPayload.isDelete) {
      impactedHotelsArrayList.forEach(function(hotelName) {
        recalculateDuesForShop(activeSsInstanceObj, hotelName, requestDataPayload.monthName.toUpperCase());
      });
      return ContentService.createTextOutput("Deleted Successfully");
    }

    // E. CHRONOLOGICAL ATOMIC INSERTION
    // This function handles the creation of headers and finding the right shelf.
    insertOrderAtomicTitan(workingMonthTabSheet, requestDataPayload, activeSsInstanceObj);

    // SELF-HEALING ACCOUNTING: Force recalculation for every hotel in this sync packet
    requestDataPayload.shops.forEach(function(shopElement) {
      recalculateDuesForShop(activeSsInstanceObj, shopElement.shopName, requestDataPayload.monthName.toUpperCase());
    });

    return ContentService.createTextOutput("Success");

  } catch (error) {
    return ContentService.createTextOutput("Error: " + error.message);
  } finally {
    lockObjectHandle.releaseLock();
  }
}

/**
 * Precision insertion logic.
 * Uses GLOBAL SEARCH to ensure backdated orders never "vanish" at the bottom.
 */
function insertOrderAtomicTitan(sheetHandle, data, spreadsheet) {
  var fullBColumnValues = sheetHandle.getRange("B:B").getDisplayValues().flat();
  var dateHeaderLabelText = "--- " + data.date + " ---";

  // GLOBAL SEARCH: Look through the whole sheet for this day's header
  // This solves the bug where backdated orders went to the bottom if header wasn't found.
  var headerIndexMatchFound = fullBColumnValues.lastIndexOf(dateHeaderLabelText) + 1;

  if (headerIndexMatchFound === 0) {
    // Stage A: Header not found anywhere - Perform full chronological "Shelf" placement
    var incomingOrderDateObj = bulletproofDateParser(data.date);
    var targetInsertAtRowCoord = sheetHandle.getLastRow() + 1;

    // Find the right chronological spot
    for (var i = 1; i < fullBColumnValues.length; i++) {
      if (fullBColumnValues[i].indexOf("---") !== -1) {
        var existingSectionDateObj = bulletproofDateParser(fullBColumnValues[i]);
        if (existingSectionDateObj > incomingOrderDateObj) {
          targetInsertAtRowCoord = i + 1;
          break;
        }
      }
    }

    // Insert new section
    sheetHandle.insertRowsBefore(targetInsertAtRowCoord, 2);
    var hRangeObj = sheetHandle.getRange(targetInsertAtRowCoord, 2);
    hRangeObj.setNumberFormat("@").setValue(dateHeaderLabelText).setFontWeight("bold").setHorizontalAlignment("center");

    headerIndexMatchFound = targetInsertAtRowCoord;
    // Refresh local list after insertion
    fullBColumnValues = sheetHandle.getRange("B:B").getDisplayValues().flat();
  }

  // Stage B: Bounded Section Cleanup (Identify scope of the day)
  var currentSheetSizeTotal = sheetHandle.getLastRow();
  var dayBoundaryEndRowIdx = currentSheetSizeTotal + 1;

  // Find where this day ends (next header or bottom)
  for (var k = headerIndexMatchFound + 1; k <= currentSheetSizeTotal; k++) {
    if (fullBColumnValues[k-1].indexOf("---") !== -1) {
      dayBoundaryEndRowIdx = k;
      break;
    }
  }

  // Nuclearly wipe non-order rows inside this day's scope (Wipe-Before-Write)
  // This prevents the "Double Total" bug in targeted repairs or fast syncs.
  for (var r = dayBoundaryEndRowIdx - 1; r > headerIndexMatchFound; r--) {
    var bColLabelStrVal = sheetHandle.getRange(r, 2).getDisplayValue();
    var aColIdValNum = sheetHandle.getRange(r, 1).getDisplayValue();
    if (bColLabelStrVal.indexOf("TOTAL") !== -1 || aColIdValNum === "") {
      sheetHandle.deleteRow(r);
      dayBoundaryEndRowIdx--;
    }
  }
  SpreadsheetApp.flush();

  // Stage C: Physical Row Insertion
  var finalInsertionPointIdx = dayBoundaryEndRowIdx;
  sheetHandle.insertRowsBefore(finalInsertionPointIdx, data.shops.length);

  data.shops.forEach(function(shopRec, idx) {
    // Column K (Index 10) stores pure ShopCost for accounting precision
    var finalDataRowArray = [
      data.orderNumber, "'" + data.date, shopRec.shopName, shopRec.items, shopRec.foodAmount,
      (idx === 0 ? data.delivery : 0), (idx === 0 ? data.discount : 0),
      shopRec.shopProfit + (idx === 0 ? (data.delivery - data.discount) : 0),
      data.syncId, shopRec.itemsMetadata, shopRec.shopCost
    ];

    var dataRangeObjHandle = sheetHandle.getRange(finalInsertionPointIdx + idx, 1, 1, 11);
    dataRangeObjHandle.setValues([finalDataRowArray]);

    // Formatting Reset: Ensure clean, non-bold rows
    dataRangeObjHandle.setFontWeight("normal");
    dataRangeObjHandle.setFontColor("black");
    dataRangeObjHandle.setBackground(null);
  });

  // Stage D: Re-Seal the Section
  addHardTotalAtomic(sheetHandle, "DAILY TOTAL", "#eeeeee", "black", headerIndexMatchFound);
}

// ============================================================================
// SECTION 4: POWER USER TOOLS (DATE-TARGETED REPAIR)
// ============================================================================

/**
 * PRO FEATURE: Prompts for a specific DATE to perform an automated audit.
 * Much better than row ranges as dates are permanent anchors.
 */
function repairSpecificDatePrompt() {
  var uiPromptObj = SpreadsheetApp.getUi();

  var dateResponse = uiPromptObj.prompt("Repair Specific Day", "Enter the date to fix (DD/MM/YYYY):", uiPromptObj.ButtonSet.OK_CANCEL);
  if (dateResponse.getSelectedButton() != uiPromptObj.Button.OK) { return; }

  var requestedDateStrVal = dateResponse.getResponseText().trim();
  executeDateAudit(requestedDateStrVal);
}

function executeDateAudit(dateStringVal) {
  var activeSheetHandle = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
  var fullBValuesArray = activeSheetHandle.getRange("B:B").getDisplayValues().flat();
  var headerLabelTextStr = "--- " + dateStringVal + " ---";

  // Find the header coordinate
  var headerRowCoordinateIdx = fullBValuesArray.indexOf(headerLabelTextStr) + 1;

  if (headerRowCoordinateIdx === 0) {
    SpreadsheetApp.getUi().alert("Error: Date header for " + dateStringVal + " not found on this specific sheet.");
    return;
  }

  // Phase 1: Determine Boundaries of this day
  var lastSheetRowCoordinate = activeSheetHandle.getLastRow();
  var boundaryEndRowIdxCoord = lastSheetRowCoordinate + 1;
  for (var k = headerRowCoordinateIdx + 1; k <= lastSheetRowCoordinate; k++) {
    if (fullBValuesArray[k-1].indexOf("---") !== -1) { boundaryEndRowIdxCoord = k; break; }
  }

  // Phase 2: Nuclear Wipe of messy rows in this specific day boundary
  // This fixes the "Double Total" issue for good.
  for (var r = boundaryEndRowIdxCoord - 1; r > headerRowCoordinateIdx; r--) {
    var valBLabelText = activeSheetHandle.getRange(r, 2).getDisplayValue();
    var valAIdText = activeSheetHandle.getRange(r, 1).getDisplayValue();
    if (valBLabelText.indexOf("TOTAL") !== -1 || valAIdText === "") {
      activeSheetHandle.deleteRow(r);
      boundaryEndRowIdxCoord--;
    }
  }
  SpreadsheetApp.flush();

  // Phase 3: Re-Sequence IDs (Group-Aware Perfect Logic)
  var remainingRowsCountNum = boundaryEndRowIdxCoord - 1 - headerRowCoordinateIdx;
  if (remainingRowsCountNum > 0) {
    var currentSectionDataMatrix = activeSheetHandle.getRange(headerRowCoordinateIdx + 1, 1, remainingRowsCountNum, 11).getDisplayValues();
    var sequentialIdCounterNum = 0;
    var lastSyncIdStringVal = "";

    for (var i = 0; i < currentSectionDataMatrix.length; i++) {
      var rowSyncId = currentSectionDataMatrix[i][8]; // SyncID is in Col I

      if (rowSyncId !== "") {
        // Increment number only if the transaction changed
        if (rowSyncId !== lastSyncIdStringVal) {
          sequentialIdCounterNum++;
          lastSyncIdStringVal = rowSyncId;
        }

        // Physically update the row on the sheet
        activeSheetHandle.getRange(headerRowCoordinateIdx + 1 + i, 1).setValue(sequentialIdCounterNum);

        // Clean row formatting while we are here
        var currentRowRangeRef = activeSheetHandle.getRange(headerRowCoordinateIdx + 1 + i, 1, 1, 11);
        currentRowRangeRef.setFontWeight("normal");
        currentRowRangeRef.setBackground(null);
      }
    }
  }

  // Phase 4: Re-calculate and draw ONE fresh Total
  addHardTotalAtomic(activeSheetHandle, "DAILY TOTAL", "#eeeeee", "black", headerRowCoordinateIdx);

  SpreadsheetApp.flush(); // Force screen to update for merchant
  SpreadsheetApp.getUi().alert("Day " + dateStringVal + " has been perfectly repaired and organized.");
}

// ============================================================================
// SECTION 5: GLOBAL NUCLEAR REPAIR SYSTEM
// ============================================================================

/**
 * The Ultimate Fix-Everything Utility.
 * 1. Repairs dates and heals missing costs.
 * 2. Re-sequences all Order IDs with Group-Aware logic.
 * 3. Rebuilds the Restaurant Dues matrix from accurate source logs.
 */
function nuclearRepairAllSheets() {
  var activeSsInstanceObj = SpreadsheetApp.getActiveSpreadsheet();
  var allSheetsListArray = activeSsInstanceObj.getSheets();

  // Phase 1: Audit all sales tabs for dates and IDs
  allSheetsListArray.forEach(function(currentTabObj) {
    if (currentTabObj.getName().includes(" sales")) {
      performFullAuditAndHeal(currentTabObj, activeSsInstanceObj);
    }
  });

  // Phase 2: Full Wipe and Recalculation of Dues Matrix
  var masterDuesTabObj = activeSsInstanceObj.getSheetByName("RESTAURANT DUES");
  if (masterDuesTabObj && masterDuesTabObj.getLastRow() > 1 && masterDuesTabObj.getLastColumn() > 1) {
    masterDuesTabObj.getRange(2, 2, masterDuesTabObj.getLastRow() - 1, masterDuesTabObj.getLastColumn() - 1).clearContent();
  }

  allSheetsListArray.forEach(function(currentTabObj) {
    if (currentTabObj.getName().includes(" sales")) {
      var rawMatrixValuesMatrix = currentTabObj.getDataRange().getValues();
      var monthHeaderIdentifierString = currentTabObj.getName().split(" ")[0].toUpperCase() + " SALES";
      for (var i = 1; i < rawMatrixValuesMatrix.length; i++) {
        if (rawMatrixValuesMatrix[i][0] > 0 && rawMatrixValuesMatrix[i][2] != "") {
           updateDuesIncrementally(activeSsInstanceObj, rawMatrixValuesMatrix[i][2], monthHeaderIdentifierString, cleanNum(rawMatrixValuesMatrix[i][10]));
        }
      }
    }
  });

  SpreadsheetApp.getUi().alert("Global Healing Complete! Everything perfectly synchronized.");
}

function performFullAuditAndHeal(shInstanceObj, ss) {
  var displayValuesMatrixValues = shInstanceObj.getDataRange().getDisplayValues();
  var refinedRecordListArray = [];

  for (var i = 1; i < displayValuesMatrixValues.length; i++) {
    var rDataRow = displayValuesMatrixValues[i];
    if (rDataRow[0] != "" && !isNaN(rDataRow[0]) && rDataRow[8] != "") {
       // Repair corrupted date formats (The US/UK fix)
       if (rDataRow[1].length > 10) {
         var partsArrList = rDataRow[1].split(" ");
         var map = {Jan:'01',Feb:'02',Mar:'03',Apr:'04',May:'05',Jun:'06',Jul:'07',Aug:'08',Sep:'09',Oct:'10',Nov:'11',Dec:'12'};
         rDataRow[1] = partsArrList[2] + "/" + map[partsArrList[1]] + "/" + partsArrList[3];
       }
       // HEALING: Back-fill missing costs (Column K)
       if (cleanNum(rDataRow[10]) === 0) {
          rDataRow[10] = cleanNum(rDataRow[4]) - cleanNum(rDataRow[7]);
       }
       refinedRecordListArray.push(rDataRow);
    }
  }

  if (refinedRecordListArray.length === 0) return;

  // Final Chronological Perfect Sorting
  refinedRecordListArray.sort(function(a, b) {
    var timeAObj = bulletproofDateParser(a[1]);
    var timeBObj = bulletproofDateParser(b[1]);
    if (timeAObj.getTime() !== timeBObj.getTime()) return timeAObj - timeBObj;
    return a[0] - b[0]; // Sort by ID within the same day
  });

  // NUCLEAR RE-DRAW
  shInstanceObj.clear();
  initializeNewMonthSheet(shInstanceObj);

  var currentLocalDateStr = "", daySequenceIdNum = 0, lastSyncIdStrValue = "";
  refinedRecordListArray.forEach(function(processedRowData) {
    var syncIdRefValue = processedRowData[8];
    if (processedRowData[1] !== currentLocalDateStr) {
      // Before drawing new date, finish previous day with a total
      if (currentLocalDateStr !== "") {
        var headerPosCoordIdx = findDateHeader(shInstanceObj, currentLocalDateStr);
        if (headerPosCoordIdx !== -1) addHardTotalAtomic(shInstanceObj, "DAILY TOTAL", "#eeeeee", "black", headerPosCoordIdx);
        shInstanceObj.appendRow([""]);
      }
      // Draw new section header
      var nextHeaderRangeRef = shInstanceObj.getRange(shInstanceObj.getLastRow() + 1, 2);
      nextHeaderRangeRef.setNumberFormat("@").setValue("--- " + processedRowData[1] + " ---").setFontWeight("bold").setHorizontalAlignment("center");
      currentLocalDateStr = processedRowData[1]; daySequenceIdNum = 1; lastSyncIdStrValue = syncIdRefValue;
    } else {
      // Group-Aware logic for ID sequence
      if (syncIdRefValue !== lastSyncIdStrValue) {
        daySequenceIdNum++;
        lastSyncIdStrValue = syncIdRefValue;
      }
    }

    processedRowData[0] = daySequenceIdNum;
    shInstanceObj.appendRow(processedRowData);
    shInstanceObj.getRange(shInstanceObj.getLastRow(), 1, 1, 11).setFontWeight("normal");
  });

  // Seal the final date section of the sheet
  var finalHPosCoordIdx = findDateHeader(shInstanceObj, currentLocalDateStr);
  if (finalHPosCoordIdx !== -1) addHardTotalAtomic(shInstanceObj, "DAILY TOTAL", "#eeeeee", "black", finalHPosCoordIdx);
}

// ============================================================================
// SECTION 6: SUMMATION & DUES LOGIC (SELF-HEALING)
// ============================================================================

/**
 * Intelligent Dues Auditor: Scans Column K costs and overwrites the balance.
 */
function recalculateDuesForShop(ssObj, hotelName, colLabel) {
  var salesTabInstance = ssObj.getSheetByName(colLabel.toLowerCase());
  if (!salesTabInstance) return;

  var fullSheetMatrixValuesArray = salesTabInstance.getDataRange().getValues();
  var summationAccurateTotalSum = 0;

  for (var i = 1; i < fullSheetMatrixValuesArray.length; i++) {
    // Column C is ShopName, Column K is Cost
    if (fullSheetMatrixValuesArray[i][2] === hotelName && fullSheetMatrixValuesArray[i][0] > 0) {
       var costInColKVal = cleanNum(fullSheetMatrixValuesArray[i][10]);
       if (costInColKVal === 0) {
         costInColKVal = cleanNum(fullSheetMatrixValuesArray[i][4]) - cleanNum(fullSheetMatrixValuesArray[i][7]);
       }
       summationAccurateTotalSum += costInColKVal;
    }
  }

  writeDuesResultToMatrix(ssObj, hotelName, colLabel, summationAccurateTotalSum);
}

function writeDuesResultToMatrix(activeSsObj, hotel, headerLabel, totalValue) {
  var matrixSheetTabHandle = activeSsObj.getSheetByName("RESTAURANT DUES") || activeSsObj.insertSheet("RESTAURANT DUES");
  if (matrixSheetTabHandle.getLastRow() === 0) {
    matrixSheetTabHandle.appendRow(["RESTAURANT NAME"]);
    var firstHeaderCell = matrixSheetTabHandle.getRange(1, 1);
    firstHeaderCell.setBackground("#1565c0").setFontColor("white").setFontWeight("bold");
    matrixSheetTabHandle.setFrozenRows(1);
  }

  var allHeadersListArray = matrixSheetTabHandle.getRange(1, 1, 1, Math.max(matrixSheetTabHandle.getLastColumn(), 1)).getValues()[0];
  var targetIdxFoundCoord = allHeadersListArray.indexOf(headerLabel) + 1;

  if (targetIdxFoundCoord === 0) {
    targetIdxFoundCoord = matrixSheetTabHandle.getLastColumn() + 1;
    var newHeaderCellObj = matrixSheetTabHandle.getRange(1, targetIdxFoundCoord);
    newHeaderCellObj.setValue(headerLabel).setBackground("#1565c0").setFontColor("white").setFontWeight("bold");
  }

  var hotelNamesListArray = matrixSheetTabHandle.getRange(1, 1, matrixSheetTabHandle.getLastRow(), 1).getValues().flat();
  var rIdxFoundCoord = hotelNamesListArray.indexOf(hotel) + 1;

  if (rIdxFoundCoord === 0) {
    rIdxFoundCoord = matrixSheetTabHandle.getLastRow() + 1;
    var newHotelNameCell = matrixSheetTabHandle.getRange(rIdxFoundCoord, 1);
    newHotelNameCell.setValue(hotel).setFontWeight("bold");
  }

  matrixSheetTabHandle.getRange(rIdxFoundCoord, targetIdxFoundCoord).setValue(totalValue).setNumberFormat("₹#,##0.00");
}

function updateDuesIncrementally(activeSsHandle, hotel, col, val) {
  var duesSheetObject = activeSsHandle.getSheetByName("RESTAURANT DUES") || activeSsHandle.insertSheet("RESTAURANT DUES");
  if (duesSheetObject.getLastRow() === 0) { duesSheetObject.appendRow(["RESTAURANT NAME"]); duesSheetObject.getRange(1, 1).setBackground("#1565c0").setFontColor("white").setFontWeight("bold").setFrozenRows(1); }
  var headersArrayList = duesSheetObject.getRange(1, 1, 1, Math.max(duesSheetObject.getLastColumn(), 1)).getValues()[0];
  var cIdxCoord = headersArrayList.indexOf(col) + 1;
  if (cIdxCoord === 0) { cIdxCoord = duesSheetObject.getLastColumn() + 1; duesSheetObject.getRange(1, cIdxCoord).setValue(col).setBackground("#1565c0").setFontColor("white").setFontWeight("bold"); }
  var rNamesArrayList = duesSheetObject.getRange(1, 1, duesSheetObject.getLastRow(), 1).getValues().flat();
  var rIdxCoord = rNamesArrayList.indexOf(hotel) + 1;
  if (rIdxCoord === 0) { rIdxCoord = duesSheetObject.getLastRow() + 1; duesSheetObject.getRange(rIdxCoord, 1).setValue(hotel).setFontWeight("bold"); }
  var cellRefObj = duesSheetObject.getRange(rIdxCoord, cIdxCoord);
  cellRefObj.setValue(cleanNum(cellRefObj.getValue()) + val).setNumberFormat("₹#,##0.00");
}

// ============================================================================
// SECTION 7: LOW-LEVEL HELPERS
// ============================================================================

/**
 * Logic-driven daily total calculated with hard numbers to prevent UI lag.
 */
function addHardTotalAtomic(shRefObj, label, bgColor, textColor, headerRowCoordinateIdx) {
  var totalRowsInSheetTab = shRefObj.getLastRow();
  var bColumnValuesArray = shRefObj.getRange("B:B").getDisplayValues().flat();
  var boundaryRowCoordinateIdx = totalRowsInSheetTab + 1;

  for (var k = headerRowCoordinateIdx + 1; k <= totalRowsInSheetTab; k++) {
    if (bColumnValuesArray[k-1].indexOf("---") !== -1) { boundaryRowCoordinateIdx = k; break; }
  }

  var dailyDataRowsBlockMatrixValues = shRefObj.getRange(headerRowCoordinateIdx + 1, 1, Math.max(boundaryRowCoordinateIdx - 1 - headerRowCoordinateIdx, 1), 8).getDisplayValues();
  var sAmtTotal = 0, sDelTotal = 0, sDisTotal = 0, sProTotal = 0;

  for (var i = 0; i < dailyDataRowsBlockMatrixValues.length; i++) {
    if (dailyDataRowsBlockMatrixValues[i][0] > 0) {
      sAmtTotal += cleanNum(dailyDataRowsBlockMatrixValues[i][4]); sDelTotal += cleanNum(dailyDataRowsBlockMatrixValues[i][5]);
      sDisTotal += cleanNum(dailyDataRowsBlockMatrixValues[i][6]); sProTotal += cleanNum(dailyDataRowsBlockMatrixValues[i][7]);
    }
  }

  shRefObj.insertRowBefore(boundaryRowCoordinateIdx);
  var totalRowRangeRefObj = shRefObj.getRange(boundaryRowCoordinateIdx, 1, 1, 11);
  totalRowRangeRefObj.setValues([["", label, "", "", sAmtTotal, sDelTotal, sDisTotal, sProTotal, "", "", ""]]).setBackground(bgColor).setFontColor(textColor).setFontWeight("bold");
}

function bulletproofDateParser(inputDateStringVal) {
  var sanitizedTextStr = String(inputDateStringVal).replace(/---/g, "").trim();
  var partsArrList = sanitizedTextStr.split("/");
  if (partsArrList.length !== 3) return new Date(0);
  var dayNum = parseInt(partsArrList[0]), monthNum = parseInt(partsArrList[1]), yearNum = parseInt(partsArrList[2]);
  if (monthNum > 12) { var temp = dayNum; dayNum = monthNum; monthNum = temp; }
  return new Date(yearNum, monthNum - 1, dayNum);
}

function cleanNum(valContent) { if (typeof valContent === "number") return valContent; var cleanedStr = String(valContent).replace(/[^0-9.-]+/g, ""); return parseFloat(cleanedStr) || 0; }
function findDateHeader(shObjRef, dateStrValue) { var colBVals = shObjRef.getRange("B:B").getDisplayValues().flat(); return colBVals.lastIndexOf("--- " + dateStrValue + " ---") + 1; }
function findDateHeaderForRow(shHandle, rowIdx) { var valsUpToRow = shHandle.getRange(1, 2, rowIdx, 1).getDisplayValues().flat(); for (var i = valsUpToRow.length - 1; i >= 0; i--) { if (valsUpToRow[i].indexOf("---") !== -1) return i + 1; } return -1; }
function initializeNewMonthSheet(shTab) {
  shTab.appendRow(["# ID", "Date", "Shop Name", "Order Details", "Amount", "Delivery", "Discount", "Profit", "SyncID", "Metadata", "ShopCost"]);
  var headerRangeRef = shTab.getRange(1, 1, 1, 11);
  headerRangeRef.setBackground("#2e7d32").setFontColor("white").setFontWeight("bold");
  shTab.setFrozenRows(1);
  shTab.hideColumns(9, 3);
}
/**
 * Specialized Month-End Engine.
 * Sums EVERY order in the sheet regardless of section boundaries.
 */
function finalizeMonthlyReport() {
  var ssObject = SpreadsheetApp.getActiveSpreadsheet();
  var sheetTab = ssObject.getActiveSheet();
  var lastRowIdx = sheetTab.getLastRow();

  // Phase 1: Nuclear Wipe of existing Monthly Totals to prevent stacking
  var bColumnDisplayList = sheetTab.getRange("B:B").getDisplayValues().flat();
  for (var i = lastRowIdx; i >= 1; i--) {
    if (bColumnDisplayList[i-1] === "MONTHLY TOTAL") {
      sheetTab.deleteRow(i);
    }
  }
  SpreadsheetApp.flush();

  // Phase 2: Aggregate EVERYTHING in the sheet
  var fullMatrixData = sheetTab.getDataRange().getValues();
  var monthlyAmt = 0, monthlyDel = 0, monthlyDisc = 0, monthlyProf = 0;

  for (var k = 1; k < fullMatrixData.length; k++) {
    var rowIdValue = fullMatrixData[k][0];
    // We only sum rows that have a numeric ID (Actual Orders)
    if (rowIdValue && !isNaN(rowIdValue) && rowIdValue > 0) {
      monthlyAmt += cleanNum(fullMatrixData[k][4]);
      monthlyDel += cleanNum(fullMatrixData[k][5]);
      monthlyDisc += cleanNum(fullMatrixData[k][6]);
      monthlyProf += cleanNum(fullMatrixData[k][7]);
    }
  }

  // Phase 3: Insert the Definitive Summary at Row 2 (Immediately under Header)
  sheetTab.insertRowBefore(2);
  var summaryRangeObj = sheetTab.getRange(2, 1, 1, 11);
  summaryRangeObj.setValues([["", "MONTHLY TOTAL", "", "", monthlyAmt, monthlyDel, monthlyDisc, monthlyProf, "", "", ""]])
    .setBackground("#1b5e20")
    .setFontColor("white")
    .setFontWeight("bold");

  SpreadsheetApp.getUi().alert("Monthly Report Finalized! Overall totals calculated for all days.");
}
function manualDuesRecalculation() { SpreadsheetApp.getUi().alert("Note: Logic is automatic. To force a full system healing audit, click FORCE REPAIR."); }
