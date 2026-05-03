const express = require("express");
const multer = require("multer");
const cors = require("cors");
const helmet = require("helmet");
const morgan = require("morgan");

const { config } = require("./config");
const { HttpError } = require("./errors");
const { extractDocument } = require("./services/documentExtractor");
const { createWorkbookPlanWithDeepSeek } = require("./services/deepseekClient");
const { createFallbackWorkbookPlan, normalizeWorkbookPlan } = require("./services/workbookPlanner");
const { writeWorkbook } = require("./services/xlsxWriter");

const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: config.uploadLimitMb * 1024 * 1024 },
});

function createApp() {
  const app = express();

  app.use(helmet());
  app.use(cors());
  app.use(express.json({ limit: "1mb" }));
  app.use(morgan("combined"));

  app.get("/health", (req, res) => {
    res.json({
      ok: true,
      service: "doc-to-xlsx-backend",
      deepseekConfigured: Boolean(config.deepseek.apiKey),
    });
  });

  app.post("/convert", upload.single("file"), async (req, res, next) => {
    try {
      const documentContext = await extractDocument(req.file);
      const instructions = typeof req.body.instructions === "string" ? req.body.instructions : "";
      const modelPlan = await createWorkbookPlanWithDeepSeek(documentContext, instructions);
      const plan = modelPlan
        ? normalizeWorkbookPlan(modelPlan, documentContext)
        : createFallbackWorkbookPlan(documentContext);
      const xlsxBuffer = await writeWorkbook(plan, { sourceFile: documentContext.fileName });
      const outputName = makeOutputFileName(req.file.originalname);

      res.setHeader("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
      res.setHeader("Content-Disposition", `attachment; filename="${outputName}"`);
      res.send(Buffer.from(xlsxBuffer));
    } catch (error) {
      next(error);
    }
  });

  app.post("/convert/plan", upload.single("file"), async (req, res, next) => {
    try {
      const documentContext = await extractDocument(req.file);
      const instructions = typeof req.body.instructions === "string" ? req.body.instructions : "";
      const modelPlan = await createWorkbookPlanWithDeepSeek(documentContext, instructions);
      const plan = modelPlan
        ? normalizeWorkbookPlan(modelPlan, documentContext)
        : createFallbackWorkbookPlan(documentContext);
      res.json(plan);
    } catch (error) {
      next(error);
    }
  });

  app.use((error, req, res, next) => {
    if (error instanceof multer.MulterError) {
      return res.status(413).json({ error: `Upload failed: ${error.message}` });
    }

    if (error instanceof HttpError) {
      return res.status(error.status).json({ error: error.message, details: error.details });
    }

    console.error(error);
    return res.status(500).json({ error: "Internal server error." });
  });

  return app;
}

function makeOutputFileName(inputName = "converted") {
  const base = inputName.replace(/\.[^.]+$/, "").replace(/[^\w.-]+/g, "_") || "converted";
  return `${base}.xlsx`;
}

module.exports = { createApp };
