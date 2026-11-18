import './style.css'
import { sounds, unlockAudio, playSend, playReceive, startTyping, stopTyping } from './sounds.js'

const app = document.querySelector('#app')

// דפדפנים מתירים אודיו רק אחרי אינטראקציה. המאזין הזה יורד
// אחרי המגע הראשון ולא עולה שוב.
addEventListener('pointerdown', unlockAudio, { once: true })
addEventListener('keydown', unlockAudio, { once: true })

const state = {
  token: null,
  email: '',
  name: '',
  sending: false,
  active: true,

  // ה-seq האחרון שכבר הצגנו. הפינג מבקש רק מה שאחריו, ולכן
  // פינג שאבד ברשת לא גורם להודעה להיעלם — הבקשה הבאה תבקש
  // שוב מאותה נקודה ותקבל את אותו דבר.
  after: 0,
  transcript: [],
  outOfPower: false,
  agentOnline: true,
  blocked: false,
  poll: null,
}

/** כל כמה זמן שואלים אם יש חדש. */
const POLL_MS = 1000

// כששיחה סגורה — נגמר החשמל או שהסוכן מנותק — ממשיכים לתשאל
// לאט. זה מה שמאפשר לה לראות שרוני הטעין אותה בלי לרענן, וזה
// זול מספיק כדי לרוץ ברקע בלי להרגיש.
const IDLE_POLL_MS = 8000

const MAX_COMPOSER_HEIGHT = 150 // בערך חמש שורות, ואז גלילה פנימית

const TOKEN_KEY = 'dwr.token'
const NOTICE_KEY = 'dwr.notice'

// הקידומת VITE_ היא מה שמאפשר ל-vite לחשוף את המשתנה לדפדפן.
const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID

// localStorage זורק בחלון פרטי ובדפדפנים שחוסמים אחסון,
// אז כל גישה עטופה. אין טוקן שמור זה מצב תקין, לא שגיאה.
const store = {
  get() {
    try { return localStorage.getItem(TOKEN_KEY) } catch { return null }
  },
  set(value) {
    try { localStorage.setItem(TOKEN_KEY, value) } catch { /* לא נורא */ }
  },
  clear() {
    try { localStorage.removeItem(TOKEN_KEY) } catch { /* לא נורא */ }
  },
}

// ---------------------------------------------------------------
//  עזרים
// ---------------------------------------------------------------

const esc = (s) =>
  String(s).replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c],
  )

// הופך כתובות בטקסט של הסוכן לקישורים לחיצים
const linkify = (s) =>
  esc(s).replace(
    /(https?:\/\/[^\s<]+)/g,
    '<a href="$1" target="_blank" rel="noopener noreferrer">$1</a>',
  )

async function api(path, options) {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  const data = await res.json().catch(() => ({}))
  if (!res.ok) {
    const err = new Error(data.error || 'שגיאה לא צפויה')
    err.data = data
    err.status = res.status
    throw err
  }
  return data
}

// הפרצוף של ג׳ימיקלוד
const avatar = (extraClass = '') => `
  <svg class="avatar ${extraClass}" viewBox="0 0 48 48" role="img" aria-label="ג׳ימיקלוד">
    <circle cx="24" cy="24" r="24" class="av-bg" />
    <circle cx="24" cy="7.5" r="2.6" class="av-bolt" />
    <path d="M24 10v4" class="av-stroke" />
    <rect x="6.5" y="21" width="3.5" height="9" rx="1.75" class="av-body" />
    <rect x="38" y="21" width="3.5" height="9" rx="1.75" class="av-body" />
    <rect x="11" y="14" width="26" height="23" rx="8" class="av-body" />
    <circle cx="18.5" cy="24" r="3.1" class="av-face" />
    <circle cx="29.5" cy="24" r="3.1" class="av-face" />
    <circle cx="18.5" cy="24.6" r="1.3" class="av-pupil" />
    <circle cx="29.5" cy="24.6" r="1.3" class="av-pupil" />
    <path d="M19.5 30.5q4.5 3 9 0" class="av-smile" />
  </svg>`

const footer = `
  <footer class="disclaimer">
    <p>* רוני לא מודע להתכתבות</p>
    <p>** AI טיפש ועלול להמציא עליי דברים</p>
    <p><a href="/privacy.html">מדיניות פרטיות</a></p>
  </footer>`

// ---------------------------------------------------------------
//  מסך 1 — כניסה עם גוגל
//
//  אין טופס מייל, אין קישור לאשר ואין מסך המתנה. גוגל כבר
//  מוודאת שהכתובת שלה, וזו בדיוק העבודה שהיא עושה.
// ---------------------------------------------------------------

// ה-JS לא בונה את דף הבית. הוא כתוב ב-index.html כדי שסורקים
// שלא מריצים JavaScript יראו אותו — ראה ההערה שם. כאן רק
// מרכיבים לתוכו את הכפתור ומציגים הודעה אם יש.
function showLoginNotice(message) {
  const box = document.querySelector('#error')
  if (box) box.textContent = message || ''
}

// הסקריפט של גוגל נטען async, אז הוא לא בהכרח מוכן כשהדף מצויר.
function waitForGoogle(timeoutMs = 8000) {
  return new Promise((resolve) => {
    const started = Date.now()
    const tick = () => {
      const gsi = window.google?.accounts?.id
      if (gsi) return resolve(gsi)
      if (Date.now() - started > timeoutMs) return resolve(null)
      setTimeout(tick, 50)
    }
    tick()
  })
}

async function mountGoogleButton() {
  const fail = (message) => {
    const box = document.querySelector('#error')
    if (box) box.textContent = message
  }

  if (!GOOGLE_CLIENT_ID) {
    return fail('הכניסה עם גוגל לא מוגדרת. חסר VITE_GOOGLE_CLIENT_ID.')
  }

  const gsi = await waitForGoogle()
  if (!gsi) {
    return fail('לא הצלחנו לטעון את הכניסה של גוגל. אולי חוסם פרסומות?')
  }

  gsi.initialize({
    client_id: GOOGLE_CLIENT_ID,
    callback: onGoogleCredential,
  })

  const slot = document.querySelector('#g-button')

  // האתר בהיר תמיד, אז 'outline' מתיישב איתו.
  const paint = () => {
    slot.innerHTML = ''

    // גוגל מקבלת רוחב בין 200 ל-400, ו-transform מגדיל אותו
    // אחר כך בלי שהדפדפן ידע. בלי החישוב הזה הכפתור חורג מהכרטיס
    // במסכים צרים — ולכן מחשבים לאחור מהרוחב הפנוי בפועל.
    const scale = parseFloat(getComputedStyle(slot).getPropertyValue('--g-scale')) || 1
    const room = (slot.parentElement?.clientWidth || 320) - 24
    const width = Math.round(Math.min(400, Math.max(200, room / scale)))

    gsi.renderButton(slot, {
      theme: 'outline',
      size: 'large',
      shape: 'pill',
      text: 'continue_with',
      locale: 'he',
      width,
    })
  }

  paint()
}

// גוגל קוראת לזה עם ה-ID token. הוא לא נבדק כאן — הדפדפן
// לא יכול לאמת כלום. השרת הוא זה שבודק את החתימה.
async function onGoogleCredential(response) {
  const slot = document.querySelector('#g-button')
  slot?.classList.add('busy')

  try {
    const data = await api('/api/login', {
      method: 'POST',
      body: JSON.stringify({ credential: response.credential }),
    })
    startSession(data)
  } catch (err) {
    slot?.classList.remove('busy')
    const box = document.querySelector('#error')
    if (box) box.textContent = err.message
  }
}

function startSession({ token, email, name }) {
  state.token = token
  state.email = email || ''
  state.name = name || ''
  state.active = true
  store.set(token)
  clearTranscript()
  state.after = 0

  setSignedIn(true)
  startPolling()
}

function signOut(message = '') {
  store.clear()
  clearTranscript()
  state.outOfPower = false
  state.token = null
  state.active = true
  // בלי זה גוגל תיכנס מחדש אוטומטית ותסתור את היציאה.
  stopPolling()
  try { window.google?.accounts?.id?.disableAutoSelect() } catch { /* לא נורא */ }

  // טעינה מחדש מחזירה את דף הבית מ-index.html, במקום לשכפל
  // את המבנה שלו כאן. ההודעה עוברת דרך sessionStorage כי
  // הדף נבנה מחדש מאפס.
  try { sessionStorage.setItem(NOTICE_KEY, message) } catch { /* לא נורא */ }
  location.reload()
}

// ---------------------------------------------------------------
//  תמליל מקומי
//
//  ה-Outbox בשרת מחזיק רק את ההודעות של הסוכן — מה שהיא כתבה
//  לא נמצא שם. בלי התמליל הזה רענון מצייר מונולוג: כל התשובות
//  בלי אף שאלה.
//
//  sessionStorage ולא localStorage בכוונה: הוא נמחק כשהיא
//  סוגרת את הלשונית, בדיוק כמו שהסוכן מבטיח לה. תמונות לא
//  נשמרות — הן base64 כבד, והמכסה תתפוצץ.
// ---------------------------------------------------------------

const TRANSCRIPT_KEY = 'dwr.transcript'

function saveTranscript() {
  try {
    sessionStorage.setItem(
      TRANSCRIPT_KEY,
      JSON.stringify({ after: state.after, messages: state.transcript.slice(-80) }),
    )
  } catch { /* מלא או חסום — נמשיך בלי */ }
}

/** מצייר מחדש מה שהיה. מחזיר true אם היה משהו לשחזר. */
function restoreTranscript() {
  let saved = null
  try {
    saved = JSON.parse(sessionStorage.getItem(TRANSCRIPT_KEY) || 'null')
  } catch { /* פגום — מתחילים נקי */ }

  if (!saved?.messages?.length) return false

  state.transcript = saved.messages
  state.after = saved.after || 0
  for (const m of saved.messages) addMessage(m.who, { text: m.text }, false)
  return true
}

function clearTranscript() {
  state.transcript = []
  try { sessionStorage.removeItem(TRANSCRIPT_KEY) } catch { /* לא נורא */ }
}

// ---------------------------------------------------------------
//  מסך 3 — השיחה
// ---------------------------------------------------------------

function renderChat() {
  app.innerHTML = `
    <div class="chat">
      <header class="chat-header">
        ${avatar('avatar-lg')}
        <div class="who">
          <h1>
            ג׳ימיקלוד
            <span class="dot" id="dot" aria-hidden="true"></span>
          </h1>
          <p class="status" id="status">מחובר</p>
        </div>
        <div class="match" id="match" hidden>
          <svg class="heart" viewBox="0 0 24 24" aria-hidden="true">
            <defs>
              <linearGradient id="fill" x1="0" y1="1" x2="0" y2="0">
                <stop id="fill-on"  offset="0%" stop-color="currentColor" />
                <stop id="fill-off" offset="0%" stop-color="transparent" />
              </linearGradient>
            </defs>
            <path id="heart-path"
              d="M12 21s-7.5-4.7-9.5-9.2C1.1 8.4 3 5 6.4 5c2 0 3.4 1.1 4.3 2.3l1.3 1.7 1.3-1.7C14.2 6.1 15.6 5 17.6 5 21 5 22.9 8.4 21.5 11.8 19.5 16.3 12 21 12 21z"
              fill="url(#fill)" stroke="currentColor" stroke-width="1.6"
              stroke-linejoin="round" />
            <path id="heart-crack" d="M12 8.5 10.2 12h3.2L11.6 16" fill="none"
              stroke="currentColor" stroke-width="1.4" stroke-linecap="round" hidden />
          </svg>
          <span class="match-num" id="match-num"></span>
        </div>
      </header>

      <div class="messages" id="messages" role="log" aria-live="polite"></div>

      <p class="ended" id="ended" hidden>
        <span id="ended-text">השיחה הסתיימה.</span>
        <button type="button" class="link-button" id="more-power" hidden>לבקש עוד חשמל?</button>
        <button type="button" class="link-button" id="again">להתחיל חדשה</button>
      </p>

      <div class="signin-bar" id="signin-bar" hidden>
        <div id="g-button" class="g-button"></div>
        <p class="error" id="error" role="alert"></p>
      </div>

      <form class="composer" id="composer">
        <textarea
          id="input" rows="1" maxlength="1000" autocomplete="off"
          placeholder="כתבי משהו…" aria-label="הודעה"></textarea>
        <button type="submit" id="send" aria-label="שליחה">
          <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
            <path d="M21 12 3 4l3 8-3 8z" fill="currentColor"/>
          </svg>
        </button>
      </form>
      ${footer}
    </div>

    <div class="lightbox" id="lightbox" hidden>
      <button type="button" class="lightbox-close" aria-label="סגירה">&times;</button>
      <img id="lightbox-img" alt="" />
    </div>`

  const form = document.querySelector('#composer')
  const input = document.querySelector('#input')

  input.focus()

  // השורה גדלה עם הטקסט, עד תקרה ואז גוללת בפנים.
  // scrollHeight לא כולל את המסגרת, ובלי התוספת הזו נוצר פס גלילה
  // מיותר של שני פיקסלים בכל גובה.
  const grow = () => {
    input.style.height = 'auto'
    const full = input.scrollHeight + (input.offsetHeight - input.clientHeight)
    input.style.height = Math.min(full, MAX_COMPOSER_HEIGHT) + 'px'
    input.style.overflowY = full > MAX_COMPOSER_HEIGHT ? 'auto' : 'hidden'
  }
  input.addEventListener('input', grow)

  // Enter שולח, Shift+Enter יורד שורה
  input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      form.requestSubmit()
    }
  })

  form.addEventListener('submit', async (e) => {
    e.preventDefault()
    const text = input.value.trim()
    if (!text || state.sending || !state.active || state.blocked) return

    input.value = ''
    grow()
    addMessage('me', { text })
    playSend()
    await send(text)
  })

  document.querySelector('#again').addEventListener('click', () => signOut())
  setupMorePower()

  setupLightbox()
}

// ---------------------------------------------------------------
//  בקשה לעוד חשמל
//
//  לא מגדילה שום תקציב — רק רושמת ב-DB שהיא ביקשה, ורוני
//  מאפס ידנית. הכפתור מנוטרל אחרי לחיצה אחת: לחיצות חוזרות
//  לא מזרזות אותו, והן רק מרגישות כמו התחננות.
// ---------------------------------------------------------------

function setupMorePower() {
  const button = document.querySelector('#more-power')
  if (!button) return

  button.addEventListener('click', async () => {
    button.disabled = true
    button.textContent = 'שולח…'
    try {
      const data = await api('/api/request-tokens', {
        method: 'POST',
        body: JSON.stringify({ token: state.token }),
      })
      button.textContent = 'ביקשנו ✓'

      // רק אם זו לא כבר ההודעה האחרונה. הכפתור חוזר לחיים
      // כשרוני מטעין, ולחיצה נוספת אחר כך לא צריכה לערום
      // עוד עותק של אותו משפט.
      const last = state.transcript[state.transcript.length - 1]
      if (data?.message && last?.text !== data.message) {
        addMessage('agent', { text: data.message })
      }
    } catch {
      button.disabled = false
      button.textContent = 'לא הצליח, לנסות שוב?'
    }
  })
}

// ---------------------------------------------------------------
//  השתקה
//
//  הבחירה נשמרת בדפדפן שלה. צליל ההקלדה הוא זה שהכי קל
//  להתעצבן ממנו, ואסור שהיא תצטרך לסגור את הדף בשבילו.
// ---------------------------------------------------------------

function paintMatch(percent) {
  const box = document.querySelector('#match')
  if (!box) return

  // null = עוד לא נמדד. מסתירים במקום להראות 0, כי 0 אומר
  // "נבדקה ונפסלה" ולא "טרם נבדקה".
  if (percent === null || percent === undefined) {
    box.hidden = true
    return
  }

  box.hidden = false
  const broken = percent < 0
  const value = broken ? 0 : Math.max(0, Math.min(100, percent))

  // הלב מתמלא מלמטה: שני עצירות באותו אחוז יוצרות קו חד בין
  // המלא לריק, בלי מעבר מטושטש.
  document.querySelector('#fill-on').setAttribute('offset', `${value}%`)
  document.querySelector('#fill-off').setAttribute('offset', `${value}%`)

  document.querySelector('#heart-crack').hidden = !broken
  box.classList.toggle('match-broken', broken)
  document.querySelector('#match-num').textContent = broken ? '' : `${value}%`
  box.title = broken ? 'לא מתאים' : `התאמה ${value}%`
}

// ---------------------------------------------------------------
//  מצב החיבור — נקודה ירוקה או אדומה, ונעילת הכתיבה
// ---------------------------------------------------------------

/**
 * מצב "עוד לא מחוברת": הצ׳אט מצויר במלואו, אבל במקום תיבת
 * הכתיבה יושב כפתור גוגל. בלי מסך נחיתה — היא רואה מיד לאן
 * היא הגיעה, ורק אז מתבקשת להזדהות.
 */
function setSignedIn(signedIn) {
  const composer = document.querySelector('#composer')
  const bar = document.querySelector('#signin-bar')
  if (composer) composer.hidden = !signedIn
  if (bar) bar.hidden = signedIn

  const status = document.querySelector('#status')
  if (status && !signedIn) status.textContent = 'מחכה לך'
  const dot = document.querySelector('#dot')
  if (dot) dot.classList.toggle('dot-off', !signedIn)

  if (signedIn) document.querySelector('#input')?.focus()
}

function setActive(active) {
  state.active = active

  // הסוכן מנותק מהספק = הוא לא יכול לענות לאף אחת, לא רק לה.
  // זה גובר על כל סיבה אחרת, כי בלעדיו אין בכלל שיחה.
  const offline = !state.agentOnline
  if (offline) active = false

  const dot = document.querySelector('#dot')
  const status = document.querySelector('#status')
  const input = document.querySelector('#input')
  const send = document.querySelector('#send')
  const ended = document.querySelector('#ended')

  if (dot) dot.classList.toggle('dot-off', !active)
  if (status) {
    status.textContent = offline ? 'בשיפוצים' : active ? 'מחובר' : 'לא מחובר'
  }
  if (ended) ended.hidden = active

  // "עוד חשמל" רק כשזו באמת הסיבה. שיחה שפגה מחוסר פעילות
  // נסגרת מסיבה אחרת לגמרי, והכפתור שם היה מבלבל.
  const morePower = document.querySelector('#more-power')
  if (morePower) {
    morePower.hidden = active || offline || !state.outOfPower
    // רוני הטעין והשיחה חזרה — הכפתור חוזר למצב ההתחלתי,
    // אחרת "ביקשנו ✓" היה נשאר שם לנצח.
    if (active) {
      morePower.disabled = false
      morePower.textContent = 'לבקש עוד חשמל?'
    }
  }

  // כשנגמר החשמל, הבועה של ג׳ימיקלוד כבר הסבירה — אין צורך
  // בשורה גנרית מעליה.
  const endedText = document.querySelector('#ended-text')
  if (endedText) {
    endedText.hidden = state.outOfPower && !offline
    // ניתוק מהספק הוא לא באג שלה ולא סוף השיחה — רוני מטעין
    // ומפעיל מחדש. הניסוח קל בכוונה: היא לא צריכה לדעת מה
    // זה קרדיטים של API.
    if (offline) endedText.textContent = 'ג׳ימיקלוד בשיפוצים קטנים. חוזר עוד מעט.'
  }

  // כשנגמר החשמל, "להתחיל חדשה" הוא עצה גרועה: שיחה חדשה
  // תיפול על אותה תקרה. הדרך היחידה קדימה היא לבקש עוד.
  const again = document.querySelector('#again')
  if (again) again.hidden = state.outOfPower || offline

  if (input) {
    input.disabled = !active
    input.placeholder = active
      ? 'כתבי משהו…'
      : offline
        ? 'ג׳ימיקלוד חוזר עוד מעט'
        : 'השיחה נסגרה'
  }
  if (send) send.disabled = !active
}

// ---------------------------------------------------------------
//  נפסלה: הבוט מתנתק ממנה לנצח — אדום, offline, בלי דרך חזרה.
//  השרת אוכף את זה (match שלילי שורד ב-DB, וכל הודעה נבלעת);
//  זה רק הצד הויזואלי. הפרידה כבר הוצגה כהודעה רגילה לפני זה.
// ---------------------------------------------------------------
function setBlocked() {
  if (state.blocked) return
  state.blocked = true
  stopPolling()
  hideTyping()

  const dot = document.querySelector('#dot')
  const status = document.querySelector('#status')
  const input = document.querySelector('#input')
  const send = document.querySelector('#send')
  if (dot) dot.classList.add('dot-off')
  if (status) status.textContent = 'מנותק'
  if (input) {
    input.disabled = true
    input.placeholder = 'השיחה נסגרה'
  }
  if (send) send.disabled = true

  const again = document.querySelector('#again')
  if (again) again.hidden = true
  const morePower = document.querySelector('#more-power')
  if (morePower) morePower.hidden = true
}

function addMessage(who, { text = '', images = [] }, record = true) {
  if (record) {
    state.transcript.push({ who, text })
    saveTranscript()
  }

  const list = document.querySelector('#messages')
  const el = document.createElement('div')
  el.className = `msg msg-${who}`

  const parts = []
  if (text) parts.push(`<p class="text">${linkify(text).replace(/\n/g, '<br>')}</p>`)

  if (images.length) {
    const src = (img) => `data:${esc(img.mime)};base64,${esc(img.data)}`
    parts.push(
      `<div class="photos${images.length > 1 ? ' photos-grid' : ''}">` +
        images
          .map(
            (img) =>
              `<button type="button" class="photo" data-src="${src(img)}">
                 <img src="${src(img)}" alt="${esc(img.alt)}" />
               </button>`,
          )
          .join('') +
        '</div>',
    )
  }

  el.innerHTML =
    (who === 'agent' ? avatar('avatar-sm') : '') +
    `<div class="bubble">${parts.join('')}</div>`

  // אינדיקטור ההקלדה תמיד נשאר הכי למטה: אם הוא מוצג, ההודעה
  // החדשה (שלה או שלו) נכנסת מעליו ולא אחריו.
  const typing = list.querySelector('#typing')
  if (typing) list.insertBefore(el, typing)
  else list.appendChild(el)
  list.scrollTop = list.scrollHeight
}

function showTyping() {
  // הפינג רץ כל שנייה וכל עוד הסוכן חושב הוא מדווח "מקליד".
  // בלי המשמרת הזאת נוספת בועה חדשה בכל פינג.
  if (document.querySelector('#typing')) return

  const list = document.querySelector('#messages')
  const el = document.createElement('div')
  el.className = 'msg msg-agent typing'
  el.id = 'typing'
  el.innerHTML =
    avatar('avatar-sm') +
    `<div class="bubble"><span class="dots"><i></i><i></i><i></i></span></div>`
  list.appendChild(el)
  list.scrollTop = list.scrollHeight
}

function hideTyping() {
  document.querySelectorAll('#typing').forEach((el) => el.remove())
}

// ---------------------------------------------------------------
//  שליחה
//
//  התשובה ל-POST אומרת רק שההודעה נקלטה — היא לא מכילה תוכן.
//  הסוכן עובד ברקע, והתשובה תגיע דרך הפינג. זה מה שמאפשר לו
//  לקחת עשר שניות בלי שהבקשה תיתקע.
// ---------------------------------------------------------------

async function send(text) {
  state.sending = true
  document.querySelector('#send').disabled = true
  showTyping()

  try {
    await api('/api/message', {
      method: 'POST',
      body: JSON.stringify({ token: state.token, text }),
    })
    // לא מסירים את "מקליד" כאן — הוא יורד כשהפינג ידווח
    // שהסוכן סיים.
  } catch (err) {
    hideTyping()
    if (err.status === 401) {
      state.sending = false
      return signOut('הסשן פג. אפשר להיכנס שוב.')
    }
    // 409 = נגמר החשמל. ההודעה בדמות של ג׳ימיקלוד כבר ממתינה
    // בתיבת היוצא ותגיע בפינג הבא, אז פס שגיאה אדום לידה הוא
    // רק רעש. רק סוגרים את הקלט.
    if (err.status === 409) {
      state.outOfPower = true
      setActive(false)
      return
    }
    addMessage('error', { text: err.message })
    if (err.data?.active === false) setActive(false)
  } finally {
    state.sending = false
    if (state.active) {
      const button = document.querySelector('#send')
      if (button) button.disabled = false
      document.querySelector('#input')?.focus()
    }
  }
}

// ---------------------------------------------------------------
//  פינג
//
//  שואל כל שנייה מה הצטבר. לא משתמש ב-setInterval אלא משרשר
//  setTimeout אחרי כל תשובה: בקשה איטית לא תיערם על הבאה
//  אחריה, וכשהלשונית ברקע הדפדפן ממילא מאט אותנו.
// ---------------------------------------------------------------

function startPolling() {
  stopPolling()
  const tick = async () => {
    if (!state.token) return
    try {
      const data = await api('/api/poll', {
        method: 'POST',
        body: JSON.stringify({ token: state.token, after: state.after }),
      })

      for (const message of data.messages || []) {
        addMessage(message.isError ? 'error' : 'agent', message)
        // הסמן של השרת ולא Math.max: הרצף חי בזיכרון ומתאפס
        // בהפעלה מחדש, ואז max נתקע על מספר מהחיים הקודמים —
        // השרת מחזיר את אותה הודעה בכל פינג והיא מצוירת שוב
        // ושוב. ההודעות מגיעות מסודרות, אז האחרונה היא הנכונה.
        state.after = message.seq
        playReceive()
      }
      if (data.messages?.length) saveTranscript()

      // נפסלה — הבוט מתנתק ממנה לנצח. הפרידה כבר הוצגה למעלה.
      if (typeof data.matchPercent === 'number' && data.matchPercent < 0) {
        setBlocked()
        return
      }

      if (data.pending) {
        showTyping()
        startTyping()
      } else {
        hideTyping()
        stopTyping()
      }

      // הדגל לפני setActive: הוא זה שקובע איזה כפתור מוצג.
      // שניהם נקראים מהשרת בכל פינג ולא נדבקים. אחרת הטענה
      // ידנית של רוני לא הייתה מורגשת עד רענון.
      state.outOfPower = data.outOfPower === true
      state.agentOnline = data.agentOnline !== false
      paintMatch(data.matchPercent)

      // אין stopPolling כאן בכוונה. שיחה סגורה היא לא שיחה
      // מתה: רוני יכול להטעין לה מכסה או קרדיטים בכל רגע,
      // והפינג האיטי הוא מה שיגלה את זה. סשן שבאמת פג מוחזר
      // כ-401 ומטופל ב-catch.
      setActive(data.active !== false)
    } catch (err) {
      if (err.status === 401) return signOut('הסשן פג. אפשר להיכנס שוב.')
      // תקלת רשת חולפת: לא מציגים כלום, פשוט מנסים שוב.
    }
    state.poll = setTimeout(tick, state.active ? POLL_MS : IDLE_POLL_MS)
  }
  tick()
}

function stopPolling() {
  if (state.poll) clearTimeout(state.poll)
  state.poll = null
  stopTyping()
}

// ---------------------------------------------------------------
//  הגדלת תמונה בלחיצה
// ---------------------------------------------------------------

function setupLightbox() {
  const box = document.querySelector('#lightbox')
  const img = document.querySelector('#lightbox-img')

  const open = (src, alt) => {
    img.src = src
    img.alt = alt
    box.hidden = false
    document.body.classList.add('no-scroll')
    box.querySelector('.lightbox-close').focus()
  }

  const close = () => {
    box.hidden = true
    img.src = ''
    document.body.classList.remove('no-scroll')
  }

  document.querySelector('#messages').addEventListener('click', (e) => {
    const button = e.target.closest('.photo')
    if (button) open(button.dataset.src, button.querySelector('img').alt)
  })

  box.addEventListener('click', close)
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && !box.hidden) close()
  })
}

// אין מסך נחיתה. הצ׳אט נפתח לכולן מיד — מי שנכנסה רואה לאן
// הגיעה לפני שהיא מתבקשת להזדהות. הכניסה עם גוגל יושבת במקום
// תיבת הכתיבה, וההודעה הראשונה נכתבת על ידי הסוכן ומגיעה בפינג.
renderChat()

const savedToken = store.get()
if (savedToken) {
  state.token = savedToken
  setSignedIn(true)
  restoreTranscript()
  startPolling()
} else {
  setSignedIn(false)

  let notice = ''
  try {
    notice = sessionStorage.getItem(NOTICE_KEY) || ''
    sessionStorage.removeItem(NOTICE_KEY)
  } catch { /* לא נורא */ }

  showLoginNotice(notice)
  mountGoogleButton()
}
