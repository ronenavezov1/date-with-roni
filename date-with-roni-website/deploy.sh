#!/usr/bin/env bash
# ============================================================
#  בנייה ופריסה לשרת.
#
#      npm run build
#
#  אפשר לדרוס יעד:
#      SERVER=ubuntu@1.2.3.4 REMOTE_PATH=/var/www/other npm run build
#
#  בנייה בלי לשלוח:
#      npm run build:local
# ============================================================
set -euo pipefail

# כתובת השרת לא נכנסת לגיט: היא יושבת ב-server.env בשורש הריפו, ליד המפתח
if [ -z "${SERVER:-}" ] && [ -f "$(dirname "$0")/../server.env" ]; then . "$(dirname "$0")/../server.env"; fi
SERVER="${SERVER:?חסר SERVER — צור server.env בשורש הריפו עם SERVER=ubuntu@<ip>}"
REMOTE_PATH="${REMOTE_PATH:-/var/www/date-with-roni}"

cd "$(dirname "$0")"

# ---------------------------------------------------------------
#  המפתח יושב בשורש הריפו ולא ב-~/.ssh, ואין סוכן SSH פעיל —
#  אז בלי -i מפורש, ssh לא מציע שום מפתח ונופל על
#  "Permission denied (publickey)".
#
#  המשתמש הוא ubuntu. השרת חוסם כניסת root במפורש.
# ---------------------------------------------------------------
SSH_KEY="${SSH_KEY:-$(cd .. && pwd)/mynewserver}"

if [ -f "$SSH_KEY" ]; then
    RSH="ssh -i $SSH_KEY -o IdentitiesOnly=yes"
else
    RSH="ssh"
    echo "⚠  לא נמצא מפתח ב-$SSH_KEY — מסתמך על ~/.ssh או על הסוכן"
fi

echo "▸ בונה…"
npx vite build

# ---------------------------------------------------------------
#  שער בטיחות. הפריסה מוחקת בשרת קבצים שאינם ב-dist, ולכן
#  dist ריקה או חלקית הייתה מוחקת את האתר החי. set -e תופס
#  בנייה שנכשלה, אבל לא בנייה שהצליחה והוציאה כלום.
# ---------------------------------------------------------------
[ -f dist/index.html ] || { echo "✗ אין dist/index.html — לא שולח כלום"; exit 1; }
[ -d dist/assets ]     || { echo "✗ אין dist/assets — לא שולח כלום"; exit 1; }

echo "▸ שולח אל ${SERVER}:${REMOTE_PATH}"

# שלושה שלבים, ולא rsync אחד, כדי שלא יהיה רגע שבור באמצע:
#
#   1. הנכסים החדשים עולים ראשונים, בלי למחוק כלום. בשלב הזה
#      השרת מחזיק גם את הישנים וגם את החדשים — כל אחת שגולשת
#      עכשיו עדיין מקבלת גרסה שלמה.
#   2. index.html עולה ומחליף את המצביע. מכאן הדפדפנים מבקשים
#      את הנכסים החדשים, שכבר שם.
#   3. ניקוי היתומים.
#
# ב-rsync יחיד עם --delete, הנכסים הישנים היו נמחקים לפני
# ש-index.html התעדכן, וכל טעינה באותן שניות הייתה נשברת.

rsync -a -e "$RSH" --info=stats1 --exclude=index.html dist/ "${SERVER}:${REMOTE_PATH}/"
rsync -a -e "$RSH" dist/index.html                          "${SERVER}:${REMOTE_PATH}/"
rsync -a -e "$RSH" --delete --info=del dist/                "${SERVER}:${REMOTE_PATH}/"

echo "✓ פרוס: https://date-with-roni.com"
