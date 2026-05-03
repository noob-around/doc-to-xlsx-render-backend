# Doc to XLSX Backend

Node/Express backend for converting `.docx`, `.doc`, or `.txt` uploads into `.xlsx`.

The service extracts document text and tables locally, asks DeepSeek to normalize the content into a workbook JSON plan when `DEEPSEEK_API_KEY` is configured, then generates the final workbook with ExcelJS. If no API key is configured, it still returns a deterministic fallback workbook from extracted tables or lines of text.

## API

```http
GET /health
```

```http
POST /convert
Content-Type: multipart/form-data

file=<docx|doc|txt>
instructions=<optional conversion hint>
```

Returns an `.xlsx` attachment.

```http
POST /convert/plan
Content-Type: multipart/form-data
```

Returns the workbook JSON plan for debugging or frontend previews.

## Local Run

```bash
npm install
cp .env.example .env
npm start
```

PowerShell upload example:

```powershell
curl.exe -F "file=@test1.docx" http://localhost:3000/convert -o test1.xlsx
```

## Render

This repo includes a Dockerfile and `render.yaml`. The Docker image installs LibreOffice so legacy `.doc` files can be converted before extraction.

On Render, set `DEEPSEEK_API_KEY` as a secret environment variable.

## Test Fixtures

```bash
npm test
```

This converts `test1.docx` and `test2.docx` into `test-output/`.
