# Project Run Guide (Windows)

Project ko local machine par chalane ka pura tarika — step by step.
Sahi order hai: **1. Docker (database) → 2. Backend → 3. Frontend (Android app)**.
Backend database ke bina start nahi hota, isliye Docker sabse pehle.

```
Android app (phone)  ──Wi-Fi──>  Backend (Spring Boot, port 8082)  ──>  PostgreSQL (Docker, port 5434)
```

---

## 0. Ek baar ka setup (pehli baar hi)

Ye cheezein installed honi chahiye:

| Tool | Kyun | Check command |
|---|---|---|
| Docker Desktop | PostgreSQL database chalane ke liye | `docker --version` |
| Java 21 (JDK) | Backend ke liye | `java -version` |
| Android SDK + `adb` | App build/install ke liye | `adb version` |
| Phone par USB debugging ON | App install ke liye | Developer options → USB debugging |

**`.env` file** (project root mein) — agar nahi hai to `.env.example` copy karo:

```powershell
Copy-Item .env.example .env
```

Phir `.env` mein ye values zaroor hon:

```properties
POSTGRES_DB=inventory_platform
POSTGRES_USER=postgres
POSTGRES_PASSWORD=<apna password>
POSTGRES_PORT=5434
DB_URL=jdbc:postgresql://localhost:5434/inventory_platform
DB_USERNAME=postgres
DB_PASSWORD=<wahi password>
SERVER_PORT=8082
JWT_SECRET=<kam se kam 32 bytes ka base64>
```

`JWT_SECRET` banane ke liye (PowerShell):

```powershell
[Convert]::ToBase64String((1..48 | % { Get-Random -Max 256 }))
```

> **Port 5434 kyun?** Port 5432 par pehle se native PostgreSQL aur purana v1 container
> (`b2b-inventory-db`, database `b2b_inventory`) chal rahe hain. v2 ka apna database
> port **5434** par hai. Galti se 5432 wala DB use kiya to backend ye error dega:
> `Found non-empty schema(s) "public" but no schema history table`.

**Firewall** (ek baar, Administrator ke roop mein) — taaki phone backend tak pahunch sake:

```
ADD-FIREWALL-RULE-8082.bat   (right-click → Run as administrator)
```

---

## 1. Docker — database start karo

Project root se:

```powershell
docker compose up -d postgres
```

Check karo ki database ready hai (`healthy` aana chahiye):

```powershell
docker inspect -f '{{.State.Health.Status}}' inventory-platform-db
```

Useful commands:

| Kaam | Command |
|---|---|
| Status dekho | `docker compose ps` |
| DB logs | `docker compose logs -f postgres` |
| DB band karo | `docker compose down` |
| DB shell | `docker exec -it inventory-platform-db psql -U postgres -d inventory_platform` |

> `docker compose down -v` mat chalana jab tak data delete na karna ho — `-v` database ka
> pura data (volume) mita deta hai.

---

## 2. Backend — Spring Boot start karo

```powershell
cd backend
.\gradlew.bat bootRun
```

- Pehli baar 1–2 minute lag sakte hain (dependencies download hoti hain).
- Backend `.env` file khud padh leta hai — alag se env variables set karne ki zaroorat nahi.
- Database tables Flyway migrations se apne aap ban jaati hain.
- Ready hone par log mein ye line aayegi:

  ```
  Started B2bInventoryApplication in XX seconds
  ```

Backend chal raha hai ya nahi, check karo (dusre terminal mein):

```powershell
curl http://localhost:8082/api/products
```

`401 Authentication required` aana **sahi hai** — matlab backend chal raha hai, bas login chahiye.

Backend band karne ke liye uss terminal mein `Ctrl + C`.

### Option: backend bhi Docker mein (Gradle ke bina)

Database + backend dono ek saath container mein:

```powershell
docker compose --profile full up -d --build
```

Isme Step 2 ki zaroorat nahi. (Development ke liye Gradle wala tarika behtar hai —
code change jaldi dikhte hain.)

---

## 3. Frontend — Android app build + install

1. Phone ko USB se connect karo, unlock karo, "Allow USB debugging" par OK dabao.
2. Check karo phone dikh raha hai:

   ```powershell
   adb devices
   ```

   Device ke saamne `device` likha hona chahiye (`unauthorized` nahi).
3. Project root se install script chalao:

   ```powershell
   powershell -ExecutionPolicy Bypass -File INSTALL-APP.ps1
   ```

Ye script khud:
- PC ka LAN IP nikalti hai (jaise `192.168.1.8`),
- app ko `http://<LAN-IP>:8082/` backend URL ke saath build karti hai,
- phone par install karke app khol deti hai.

Sirf dobara install karna ho (build ke bina):

```powershell
powershell -ExecutionPolicy Bypass -File INSTALL-APP.ps1 -SkipBuild
```

> **Phone aur PC ek hi Wi-Fi par hone chahiye.** Agar Wi-Fi badla ya PC ka IP badla,
> to `INSTALL-APP.ps1` dobara chalao — IP app ke andar build ke time fix hota hai.

App khulne ke baad **Register** se naya business banao, ya existing account se login karo.

---

## Roz ka short version

```powershell
docker compose up -d postgres                 # 1. database
cd backend; .\gradlew.bat bootRun             # 2. backend (ye terminal khula rakho)
# naye terminal mein, project root se:
powershell -ExecutionPolicy Bypass -File INSTALL-APP.ps1   # 3. app (sirf code badla ho ya IP badla ho tab)
```

---

## Common problems

| Problem | Wajah | Solution |
|---|---|---|
| `Bind for 0.0.0.0:5432 failed: port is already allocated` | `.env` mein `POSTGRES_PORT=5432` hai | `.env` mein `POSTGRES_PORT=5434` karo |
| `Found non-empty schema(s) "public" but no schema history table` | Backend purane v1 database se connect ho raha hai | `.env` mein `DB_URL=jdbc:postgresql://localhost:5434/inventory_platform` |
| `Set POSTGRES_PASSWORD in .env` / `Set JWT_SECRET in .env` | `.env` mein value missing | `.env` mein value bharo |
| `Port 8082 was already in use` | Backend pehle se chal raha hai | Purana terminal band karo, ya `netstat -ano \| findstr :8082` se PID nikal ke process band karo |
| `adb: The paging file is too small` | PC ki RAM bhar gayi | Kuch apps band karo, phir `adb start-server` |
| `adb devices` mein `unauthorized` | Phone par permission nahi di | Phone unlock karke "Allow USB debugging" OK karo |
| `adb install failed` (iQOO/vivo) | USB install blocked | Developer options → "Install via USB" ON karo |
| App mein "network error" / connect nahi ho raha | Firewall, alag Wi-Fi, ya IP badal gaya | `ADD-FIREWALL-RULE-8082.bat` (admin), same Wi-Fi, `INSTALL-APP.ps1` dobara |

Phone se backend reach ho raha hai ya nahi, check karo:

```powershell
adb shell "curl -s -o /dev/null -w '%{http_code}' http://<LAN-IP>:8082/api/products"
```

`401` aaya = connection theek hai.
