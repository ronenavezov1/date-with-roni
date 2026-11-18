// ============================================================
//  צלילים
//
//  נוצרים ב-Web Audio API ולא נטענים כקבצים: אין בקשות רשת,
//  אין נכסים לפרוס, ואין מה שייחסם על ידי CSP. שלושה צלילים
//  קצרים שנבנים מאוסילטור אחד.
//
//  דפדפנים חוסמים אודיו לפני אינטראקציה של המשתמשת. לכן
//  ההקשר נוצר רק במגע הראשון שלה — ועד אז אנחנו שקטים.
// ============================================================

const MUTE_KEY = 'dwr.muted'

let ctx = null
let unlocked = false

export const sounds = {
  get muted() {
    try { return localStorage.getItem(MUTE_KEY) === '1' } catch { return false }
  },
  set muted(value) {
    try { localStorage.setItem(MUTE_KEY, value ? '1' : '0') } catch { /* לא נורא */ }
    if (value) stopTyping()
  },
}

/** נקרא במגע הראשון. לפני זה הדפדפן ממילא לא ינגן. */
export function unlockAudio() {
  if (unlocked) return
  try {
    ctx = new (window.AudioContext || window.webkitAudioContext)()
    unlocked = true
  } catch { /* אין אודיו. לא נורא. */ }
}

/**
 * צליל אחד: תדר, אורך, עוצמה.
 *
 * העלייה והדעיכה חייבות להיות הדרגתיות — אוסילטור שנעצר
 * בבת אחת מייצר נקישה חדה ולא נעימה.
 */
function tone({ freq, from = freq, duration = 0.09, gain = 0.05, type = 'sine' }) {
  if (!ctx || sounds.muted) return
  const now = ctx.currentTime
  const osc = ctx.createOscillator()
  const vol = ctx.createGain()

  osc.type = type
  osc.frequency.setValueAtTime(from, now)
  if (freq !== from) osc.frequency.exponentialRampToValueAtTime(freq, now + duration)

  vol.gain.setValueAtTime(0, now)
  vol.gain.linearRampToValueAtTime(gain, now + 0.012)
  vol.gain.exponentialRampToValueAtTime(0.0001, now + duration)

  osc.connect(vol).connect(ctx.destination)
  osc.start(now)
  osc.stop(now + duration + 0.02)
}

/** שליחה — עולה. */
export function playSend() {
  tone({ from: 620, freq: 880, duration: 0.08, gain: 0.045 })
}

/** קבלה — יורד, שני צלילים. */
export function playReceive() {
  tone({ from: 880, freq: 660, duration: 0.09, gain: 0.05 })
  setTimeout(() => tone({ freq: 520, duration: 0.11, gain: 0.038 }), 85)
}

// ------------------------------------------------------------
//  הקלדה
//
//  נקישה רכה מאוד, כל 700ms. זה הצליל שהכי קל להתעצבן ממנו,
//  ולכן הוא חלש בסדר גודל מהאחרים ולא רץ יותר מדקה — סוכן
//  שנתקע לא אמור לנקוש לנצח.
// ------------------------------------------------------------
let typingTimer = null
let typingStarted = 0
const TYPING_MAX_MS = 60_000

export function startTyping() {
  if (typingTimer || sounds.muted) return
  typingStarted = Date.now()
  const tick = () => {
    if (Date.now() - typingStarted > TYPING_MAX_MS) return stopTyping()
    tone({ freq: 1400, duration: 0.02, gain: 0.012, type: 'triangle' })
    typingTimer = setTimeout(tick, 700)
  }
  tick()
}

export function stopTyping() {
  if (typingTimer) clearTimeout(typingTimer)
  typingTimer = null
}
