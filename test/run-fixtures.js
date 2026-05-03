const fs = require("node:fs/promises");
const path = require("node:path");

const { extractDocument } = require("../src/services/documentExtractor");
const { createFallbackWorkbookPlan, normalizeWorkbookPlan } = require("../src/services/workbookPlanner");
const { writeWorkbook } = require("../src/services/xlsxWriter");

async function main() {
  const root = path.resolve(__dirname, "..");
  const fixtures = ["test1.docx", "test2.docx"];
  const outputDir = path.join(root, "test-output");
  await fs.mkdir(outputDir, { recursive: true });

  for (const fixture of fixtures) {
    const buffer = await fs.readFile(path.join(root, fixture));
    const documentContext = await extractDocument({
      originalname: fixture,
      buffer,
    });
    const plan = normalizeWorkbookPlan(createFallbackWorkbookPlan(documentContext), documentContext);
    const workbook = await writeWorkbook(plan, { sourceFile: fixture });
    const outputPath = path.join(outputDir, fixture.replace(/\.docx$/i, ".xlsx"));
    await fs.writeFile(outputPath, Buffer.from(workbook));
    console.log(`${fixture}: ${documentContext.text.length} chars, ${documentContext.tables.length} tables -> ${outputPath}`);
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
