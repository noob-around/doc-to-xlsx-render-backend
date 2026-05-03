const ExcelJS = require("exceljs");

async function writeWorkbook(plan, metadata = {}) {
  const workbook = new ExcelJS.Workbook();
  workbook.creator = "AI Assisted Conversion Backend";
  workbook.created = new Date();

  for (const sheetPlan of plan.sheets) {
    const worksheet = workbook.addWorksheet(sheetPlan.name);
    worksheet.columns = sheetPlan.columns.map((header) => ({
      header,
      key: header,
      width: Math.min(Math.max(String(header).length + 4, 12), 42),
    }));

    for (const row of sheetPlan.rows) {
      worksheet.addRow(row);
    }

    applyColumnFormats(worksheet, sheetPlan.columns);
    styleWorksheet(worksheet);
  }

  if (plan.notes?.length || metadata.sourceFile) {
    const meta = workbook.addWorksheet("_conversion");
    meta.addRows([
      ["source_file", metadata.sourceFile || ""],
      ["generated_at", new Date().toISOString()],
      ["notes", (plan.notes || []).join("\n")],
    ]);
    meta.columns = [{ width: 18 }, { width: 80 }];
  }

  return workbook.xlsx.writeBuffer();
}

function applyColumnFormats(worksheet, columns) {
  columns.forEach((header, index) => {
    const column = worksheet.getColumn(index + 1);
    if (/%|率/.test(String(header))) {
      column.numFmt = "0%";
    }
    if (/收入|金额|价格|费用|成本|万元|元/.test(String(header))) {
      column.numFmt = "#,##0.00";
    }
  });
}

function styleWorksheet(worksheet) {
  worksheet.views = [{ state: "frozen", ySplit: 1 }];
  const header = worksheet.getRow(1);
  header.font = { bold: true, color: { argb: "FFFFFFFF" } };
  header.fill = { type: "pattern", pattern: "solid", fgColor: { argb: "FF1F4E78" } };
  header.alignment = { vertical: "middle", wrapText: true };

  worksheet.eachRow((row) => {
    row.eachCell((cell) => {
      cell.border = {
        top: { style: "thin", color: { argb: "FFE3E8EF" } },
        left: { style: "thin", color: { argb: "FFE3E8EF" } },
        bottom: { style: "thin", color: { argb: "FFE3E8EF" } },
        right: { style: "thin", color: { argb: "FFE3E8EF" } },
      };
      cell.alignment = { vertical: "top", wrapText: true };
    });
  });
}

module.exports = { writeWorkbook };
