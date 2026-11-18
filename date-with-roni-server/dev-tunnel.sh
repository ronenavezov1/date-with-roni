#!/usr/bin/env bash
# ============================================================
#  מנהרת SSH ל-Postgres שעל השרת.
#
#  ה-DB לא מפרסם פורט ולא נגיש מהאינטרנט — וזה נשאר נכון.
#  המנהרה לא פותחת אותו לעולם; היא מעבירה אותו דרך חיבור ה-SSH
#  המוצפן שלך בלבד, ורק אל 127.0.0.1 של המחשב הזה.
#
#      ./dev-tunnel.sh
#
#  משאיר את הטרמינל תפוס. Ctrl-C סוגר.
# ============================================================
set -euo pipefail

# כתובת השרת לא נכנסת לגיט: היא יושבת ב-server.env בשורש הריפו, ליד המפתח
if [ -z "${SERVER:-}" ] && [ -f "$(dirname "$0")/../server.env" ]; then . "$(dirname "$0")/../server.env"; fi
SERVER="${SERVER:?חסר SERVER — צור server.env בשורש הריפו עם SERVER=ubuntu@<ip>}"
LOCAL_PORT="${LOCAL_PORT:-5432}"

# המפתח יושב בשורש הריפו ולא ב-~/.ssh, ואין סוכן SSH פעיל —
# בלי -i מפורש, ssh לא מציע שום מפתח ונופל על publickey.
SSH_KEY="${SSH_KEY:-$(cd "$(dirname "$0")/.." && pwd)/mynewserver}"
[ -f "$SSH_KEY" ] || { echo "לא נמצא מפתח ב-$SSH_KEY"; exit 1; }

echo "פותח מנהרה: localhost:${LOCAL_PORT} → ${SERVER} → localhost:5432"
echo "השאר את החלון הזה פתוח. Ctrl-C לסגירה."
echo

exec ssh -N \
    -i "$SSH_KEY" -o IdentitiesOnly=yes \
    -L "${LOCAL_PORT}:localhost:5432" \
    -o ExitOnForwardFailure=yes \
    -o ServerAliveInterval=30 \
    -o ServerAliveCountMax=3 \
    "${SERVER}"

# ExitOnForwardFailure    בלעדיו, אם הפורט המקומי כבר תפוס, ssh מתחבר
#                         בהצלחה *בלי* ההעברה. אתה מקבל מנהרה שנראית
#                         פתוחה ולא מעבירה כלום, והשגיאות אחר כך
#                         מבלבלות לגמרי.
# ServerAliveInterval     החיבור לא ייפול בשקט אחרי חוסר פעילות.
