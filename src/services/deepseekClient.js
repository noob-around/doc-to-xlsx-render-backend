const { HttpError } = require("../errors");
const { config } = require("../config");

async function createWorkbookPlanWithDeepSeek(documentContext, instructions = "") {
  if (!config.deepseek.apiKey) {
    return null;
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), config.deepseek.timeoutMs);

  try {
    const response = await fetch(`${config.deepseek.baseUrl.replace(/\/$/, "")}/chat/completions`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${config.deepseek.apiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model: config.deepseek.model,
        thinking: { type: "disabled" },
        temperature: 0.1,
        max_tokens: config.deepseek.maxTokens,
        response_format: { type: "json_object" },
        messages: [
          {
            role: "system",
            content:
              "You convert uploaded Chinese or English document content into an Excel workbook plan. Return only valid JSON matching this shape: {\"sheets\":[{\"name\":\"Sheet name\",\"columns\":[\"Header\"],\"rows\":[[\"cell\"]]}],\"notes\":[\"optional note\"]}. Keep table structure when present. Use concise worksheet names. Every row must have the same number of cells as columns; use empty strings for missing cells.",
          },
          {
            role: "user",
            content: JSON.stringify({
              fileName: documentContext.fileName,
              fileType: documentContext.kind,
              userInstructions: instructions,
              extractedTables: documentContext.tables,
              extractedText: documentContext.text.slice(0, 60000),
            }),
          },
        ],
      }),
      signal: controller.signal,
    });

    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new HttpError(response.status, "DeepSeek API request failed.", payload);
    }

    const content = payload?.choices?.[0]?.message?.content;
    if (!content) {
      throw new HttpError(502, "DeepSeek API returned an empty response.", payload);
    }

    return JSON.parse(content);
  } catch (error) {
    if (error.name === "AbortError") {
      throw new HttpError(504, "DeepSeek API request timed out.");
    }
    if (error instanceof HttpError) {
      throw error;
    }
    throw new HttpError(502, "DeepSeek API response could not be parsed.", { cause: error.message });
  } finally {
    clearTimeout(timeout);
  }
}

module.exports = { createWorkbookPlanWithDeepSeek };
