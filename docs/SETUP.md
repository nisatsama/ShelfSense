# ShelfSense Local & Docker Setup Guide (Windows / Linux / macOS)

## Prerequisites
- Java 21+ (tested with Temurin OpenJDK 25 targeting Java 21)
- Docker Desktop (for PostgreSQL or full-stack Docker Compose)
- Python 3.11+ (tested with Python 3.14.2)
- Node.js 20+ & npm 10+

## 1. Environment Variables
Copy `.env.example` to `.env` (which is git-ignored and never committed) and configure your local development secrets:
```powershell
Copy-Item .env.example .env
```

Open `.env` and configure:
1. **PostgreSQL credentials**: Set `POSTGRES_USER` (e.g. `shelfsense`) and choose a secure password for `POSTGRES_PASSWORD` and `SPRING_DATASOURCE_PASSWORD`.
2. **JWT Secret**: Generate and set a strong Base64 256-bit secret:
```powershell
# Example secret generation in PowerShell:
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Minimum 0 -Maximum 256 }))
```
3. **Admin Bootstrap (Optional)**: If you wish to seed an initial administrator, configure `SHELF_SENSE_BOOTSTRAP_ADMIN_USERNAME` and `SHELF_SENSE_BOOTSTRAP_ADMIN_PASSWORD` (minimum 12 characters).

## 2. Start PostgreSQL
Docker Compose automatically loads `.env` from the project root and initializes the PostgreSQL container with your configured credentials:
```powershell
docker compose up -d postgres
```
Or when targeting the infrastructure compose file from the project root:
```powershell
docker compose --env-file .env -f infrastructure/docker-compose.yml up -d
```

## 3. Run the ML Pipeline & FastAPI Service
```powershell
cd ml
python -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt
python -m src.data.download
python -m src.data.preprocess
python -m src.training.train
python -m src.evaluation.evaluate
pytest -v
uvicorn src.api.main:app --host 0.0.0.0 --port 8001
```

## 4. Run the Spring Boot Backend
Configure your environment variables (matching the values in your `.env` file):
```powershell
$env:SPRING_DATASOURCE_PASSWORD = "<your-postgres-password-from-env>"
$env:SHELF_SENSE_JWT_SECRET = "<your-base64-256-bit-secret>"
$env:SHELF_SENSE_BOOTSTRAP_ADMIN_USERNAME = "admin@shelfsense.local"
$env:SHELF_SENSE_BOOTSTRAP_ADMIN_PASSWORD = "<your-admin-password-min-12-chars>"
$env:SHELF_SENSE_ML_URL = "http://localhost:8001"
.\mvnw.cmd spring-boot:run
```
Run backend tests:
```powershell
# Tests automatically read credentials from .env if present, or from environment variables
.\mvnw.cmd test
```

## 5. Run the React Frontend
```powershell
cd frontend
npm install
npm run dev
# Opens at http://localhost:5173 (proxies /api -> http://localhost:8080)
```
Run frontend tests & production build:
```powershell
npm test
npm run build
```

## 6. Full-Stack Docker Compose
Ensure `.env` exists with `SHELF_SENSE_JWT_SECRET` set and trained model artifacts exist under `ml/artifacts/`:
```powershell
docker compose up --build -d
```
- Frontend: `http://localhost:3000`
- Backend API: `http://localhost:8080`
- ML Service: `http://localhost:8001/health`
- PostgreSQL: `localhost:5432`
