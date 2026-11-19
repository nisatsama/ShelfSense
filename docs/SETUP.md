# ShelfSense Local & Docker Setup Guide (Windows / Linux / macOS)

## Prerequisites
- Java 21+ (tested with Temurin OpenJDK 25 targeting Java 21)
- Docker Desktop (for PostgreSQL or full-stack Docker Compose)
- Python 3.11+ (tested with Python 3.14.2)
- Node.js 20+ & npm 10+

## 1. Environment Variables
Copy `.env.example` to `.env` and set a strong Base64 256-bit JWT secret:
```powershell
Copy-Item .env.example .env
# Example secret generation in PowerShell:
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Minimum 0 -Maximum 256 }))
```

## 2. Start PostgreSQL
```powershell
docker compose -f infrastructure/docker-compose.yml up -d
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
```powershell
$env:SPRING_DATASOURCE_PASSWORD = "shelfsense_dev"
$env:SHELF_SENSE_JWT_SECRET = "<your-base64-256-bit-secret>"
$env:SHELF_SENSE_BOOTSTRAP_ADMIN_USERNAME = "admin@shelfsense.local"
$env:SHELF_SENSE_BOOTSTRAP_ADMIN_PASSWORD = "ChangeMeAdminPass123!"
$env:SHELF_SENSE_ML_URL = "http://localhost:8001"
.\mvnw.cmd spring-boot:run
```
Run backend tests:
```powershell
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
