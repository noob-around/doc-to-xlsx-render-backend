const fs = require("node:fs/promises");
const os = require("node:os");
const path = require("node:path");
const { execFile } = require("node:child_process");
const { promisify } = require("node:util");

const mammoth = require("mammoth");
const JSZip = require("jszip");
const { XMLParser } = require("fast-xml-parser");

const { HttpError } = require("../errors");

const execFileAsync = promisify(execFile);

const SUPPORTED_EXTENSIONS = new Set([".docx", ".doc", ".txt"]);

async function extractDocument(file) {
  if (!file) {
    throw new HttpError(400, "Missing uploaded file. Use multipart field name 'file'.");
  }

  const extension = path.extname(file.originalname || "").toLowerCase();
  if (!SUPPORTED_EXTENSIONS.has(extension)) {
    throw new HttpError(415, "Unsupported file type. Upload .docx, .doc, or .txt.");
  }

  if (extension === ".txt") {
    return extractTxt(file);
  }

  if (extension === ".docx") {
    return extractDocx(file.buffer, file.originalname);
  }

  return extractLegacyDoc(file);
}

function extractTxt(file) {
  const text = file.buffer.toString("utf8").replace(/\u0000/g, "").trim();
  if (!text) {
    throw new HttpError(422, "The txt file did not contain readable text.");
  }
  return {
    fileName: file.originalname,
    kind: "txt",
    text,
    tables: [],
  };
}

async function extractDocx(buffer, fileName) {
  const [raw, tables] = await Promise.all([
    mammoth.extractRawText({ buffer }),
    extractDocxTables(buffer),
  ]);

  const tableText = tablesToMarkdown(tables);
  const text = [raw.value.trim(), tableText].filter(Boolean).join("\n\n");

  if (!text.trim()) {
    throw new HttpError(422, "The docx file did not contain readable text.");
  }

  return {
    fileName,
    kind: "docx",
    text,
    tables,
    warnings: raw.messages || [],
  };
}

async function extractLegacyDoc(file) {
  const workspace = await fs.mkdtemp(path.join(os.tmpdir(), "doc-convert-"));
  const sourcePath = path.join(workspace, safeBaseName(file.originalname || "upload.doc"));

  await fs.writeFile(sourcePath, file.buffer);

  try {
    const docxPath = await convertWithLibreOffice(sourcePath, workspace);
    const buffer = await fs.readFile(docxPath);
    return {
      ...(await extractDocx(buffer, file.originalname)),
      kind: "doc",
    };
  } catch (error) {
    throw new HttpError(
      422,
      "Could not read the .doc file. Install LibreOffice in the runtime or upload .docx/.txt.",
      { cause: error.message }
    );
  } finally {
    await fs.rm(workspace, { recursive: true, force: true });
  }
}

async function convertWithLibreOffice(inputPath, outputDir) {
  const candidates = process.platform === "win32" ? ["soffice.exe", "soffice"] : ["soffice", "libreoffice"];
  let lastError;

  for (const binary of candidates) {
    try {
      await execFileAsync(
        binary,
        ["--headless", "--convert-to", "docx", "--outdir", outputDir, inputPath],
        { timeout: 60000, windowsHide: true }
      );

      const converted = path.join(outputDir, `${path.parse(inputPath).name}.docx`);
      await fs.access(converted);
      return converted;
    } catch (error) {
      lastError = error;
    }
  }

  throw lastError || new Error("LibreOffice executable was not found.");
}

async function extractDocxTables(buffer) {
  const zip = await JSZip.loadAsync(buffer);
  const documentXml = await zip.file("word/document.xml")?.async("string");
  if (!documentXml) {
    return [];
  }

  const parser = new XMLParser({
    ignoreAttributes: false,
    removeNSPrefix: true,
    textNodeName: "#text",
  });
  const parsed = parser.parse(documentXml);
  const body = parsed?.document?.body;
  const blocks = normalizeArray(body?.tbl);

  return blocks
    .map((table) => normalizeArray(table.tr).map((row) => normalizeArray(row.tc).map(cellText)))
    .filter((rows) => rows.length > 0 && rows.some((row) => row.some(Boolean)));
}

function cellText(cell) {
  const paragraphs = normalizeArray(cell?.p)
    .map((paragraph) => collectText(paragraph).replace(/\s+/g, " ").trim())
    .filter(Boolean);
  return paragraphs.join("\n");
}

function collectText(node) {
  if (node === null || node === undefined) {
    return "";
  }
  if (typeof node === "string" || typeof node === "number") {
    return String(node);
  }
  if (Array.isArray(node)) {
    return node.map(collectText).join("");
  }
  if (typeof node !== "object") {
    return "";
  }

  const ownText = node.t ? collectText(node.t) : "";
  const childText = Object.entries(node)
    .filter(([key]) => !key.startsWith("@_") && key !== "t")
    .map(([, value]) => collectText(value))
    .join("");

  return ownText + childText;
}

function tablesToMarkdown(tables) {
  if (!tables.length) {
    return "";
  }

  return tables
    .map((rows, index) => {
      const body = rows.map((row) => `| ${row.map((cell) => cell || "").join(" | ")} |`).join("\n");
      return `Table ${index + 1}:\n${body}`;
    })
    .join("\n\n");
}

function normalizeArray(value) {
  if (!value) {
    return [];
  }
  return Array.isArray(value) ? value : [value];
}

function safeBaseName(name) {
  return path.basename(name).replace(/[^\w.-]/g, "_");
}

module.exports = {
  SUPPORTED_EXTENSIONS,
  extractDocument,
};
