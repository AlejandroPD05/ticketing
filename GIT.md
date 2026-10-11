# GIT.md — notas de configuración del repositorio

Apuntes de higiene de Git para este proyecto. No afectan al funcionamiento
del helpdesk; son referencia para el futuro.

## Estado del repo

- Remoto: `https://github.com/AlejandroPD05/ticketing` (`origin/main`).
- Historial reescrito el **2026-10-11**: se purgó `db.properties`, así que los
  hashes de los commits cambiaron. Si en otro equipo/clon quedó copia del
  historial viejo, hay que volver a clonar (o `git fetch` + reset a
  `origin/main`); hacer `merge` con un clon antiguo reviviría el fichero.

## Qué nunca se sube

Está en `.gitignore`:

| Ruta / patrón | Motivo |
| --- | --- |
| `target/` | artefactos de Maven |
| `db.properties` | credenciales locales de BD (no versionar jamás) |
| `.idea/`, `.vscode/`, `.iml`, `.classpath`, `.project` | configuración de IDE |
| `__pycache__/`, `*.pyc` | caché de Python (`tools/seed_generator.py`) |
| `Thumbs.db`, `.DS_Store` | basura de sistema |

La configuración de BD se comparte con la plantilla
`src/main/resources/db.properties.example` (sin secretos reales).

## Cómo se configura la conexión

`ConexionDB` resuelve en este orden:

1. Variables de entorno `HELPDESK_DB_URL` / `HELPDESK_DB_USER` / `HELPDESK_DB_PASSWORD`.
2. `src/main/resources/db.properties` → **copia el `.example` y rellénalo**.
3. Valores por defecto: `jdbc:mysql://localhost:3306/helpdesk`, `helpdesk`/`helpdesk`.

## Flujo de commits habitual

```bash
git add -A
git status --short          # revisar que no se cuela nada raro
git commit -m "tipo: descripción" -m "viñetas con el detalle"
git push                    # main sigue a origin/main
```

Convención usada en los mensajes: `feat:` (funcionalidad), `chore:` (mantenimiento),
`fix:` (corrección), con cuerpo en viñetas.

## LIMPIEZA: si se cuela un secreto en el historial

Borrarlo del árbol actual **no basta**: el objeto sigue accesible desde
commits antiguos. Procedimiento probado el 2026-10-11:

```powershell
# 0. Backup siempre: clonar antes de reescribir
git clone "<ruta-o-url-del-repo>" "$env:TEMP\opencode\ticketing-backup"

# 1. Instalar la herramienta (una sola vez)
pip install --user git-filter-repo
# en Windows el binario queda en %APPDATA%\Python\Python314\Scripts

# 2. Reescribir el historial quitando el fichero
& "$env:APPDATA\Python\Python314\Scripts\git-filter-repo.exe" `
    --invert-paths --path src/main/resources/db.properties --force

git reflog expire --expire=now --all
git gc --prune=now

# 3. Verificar que ya no aparece en ningún commit ni blob
git log --all --oneline -- src/main/resources/db.properties   # → vacío
git rev-list --objects --all | Select-String "db.properties"  # → solo .example

# 4. Volver a apuntar al remoto y forzar el push
git remote add origin https://github.com/AlejandroPD05/ticketing.git
git push --force origin main
git branch --set-upstream-to=origin/main main
```

Notas:

- `git-filter-repo` **borra el remoto `origin`** a propósito: hay que
  volver a añadirlo antes de hacer push.
- Si el secreto es una clave real (token, contraseña de prod), cambiarla
  de inmediato en el servicio de origen: el historial reescrito no garantiza
  que nadie con un clon previo la conserve.
- `git rm --cached <fichero>` + `.gitignore` sirve para dejar de versionar un
  archivo **sin** tocar el historial (es lo primero que se intenta).
