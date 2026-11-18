import { defineConfig } from 'vite'

export default defineConfig({
  server: {
    // הפורט חייב להישאר 5173: הוא מה שרשום אצל גוגל תחת
    // Authorized JavaScript origins, ו-origin אחר נדחה.
    port: 5173,

    // בלי זה vite קופץ בשקט לפורט הפנוי הבא כשהפורט תפוס,
    // מדפיס את זה בשורה קטנה, והדף נפתח כרגיל — עד שגוגל
    // מחזירה origin_mismatch על פורט שלא רשמת. עדיף שייכשל ברעש.
    strictPort: true,

    // כל בקשה ל-api/ עוברת לשרת ה-Ktor, כדי שהדפדפן ידבר עם מקור אחד.
    // שרת ה-Node ב-3000 נשאר כרפרנס.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
