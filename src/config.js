const path = require("node:path");

require("dotenv").config();

const rootDir = path.resolve(__dirname, "..");

const config = {
  port: Number(process.env.PORT || 3000),
  uploadLimitMb: Number(process.env.UPLOAD_LIMIT_MB || 20),
  deepseek: {
    apiKey: process.env.DEEPSEEK_API_KEY || "",
    baseUrl: process.env.DEEPSEEK_BASE_URL || "https://api.deepseek.com",
    model: process.env.DEEPSEEK_MODEL || "deepseek-v4-flash",
    maxTokens: Number(process.env.DEEPSEEK_MAX_TOKENS || 4096),
    timeoutMs: Number(process.env.DEEPSEEK_TIMEOUT_MS || 90000),
  },
  tmpDir: process.env.TMPDIR || process.env.TEMP || path.join(rootDir, ".tmp"),
};

module.exports = { config, rootDir };
