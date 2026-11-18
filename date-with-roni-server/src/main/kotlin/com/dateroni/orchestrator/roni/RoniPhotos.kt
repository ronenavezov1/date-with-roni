package com.dateroni.orchestrator.roni

import com.dateroni.Env
import com.dateroni.objects.Image
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

// ============================================================
//  טעינת התמונות מהדיסק.
//
//  המודל אף פעם לא רואה base64 ולא מייצר אותו — הוא בוחר
//  אינדקס מרשימת הכיתובים, וקוטלין טוענת את הקובץ. זה בדיוק
//  "המודל מייצר טקסט, קוטלין מחליטה מה קורה".
//
//  התמונות מוקטנות לפני השליחה. PNG של 3MB הופך ל-base64 של
//  4MB בתשובת פינג אחת — איטי בסלולר וכבד בזיכרון. 1200 פיקסלים
//  ו-JPEG באיכות 0.82 מורידים את זה לכ-200KB בלי הבדל שנראה
//  לעין במסך טלפון.
//
//  ההקטנה בזמן טעינה ולא סקריפט חד-פעמי: הקבצים המקוריים
//  נשארים כמו שהם, ואפשר לזרוק תמונה לתיקייה בלי שלב ביניים.
//  התוצאה נשמרת במטמון — כל תמונה מעובדת פעם אחת בכל חיי התהליך.
// ============================================================

/** הצלע הארוכה אחרי ההקטנה. */
private const val MAX_EDGE = 1200

private const val JPEG_QUALITY = 0.82f

private val log = LoggerFactory.getLogger("RoniPhotos")

object RoniPhotos {

    private val dir = File(Env.get("PHOTOS_DIR", "photos"))
    private val cache = ConcurrentHashMap<Int, Image>()

    private val mimeByExtension = mapOf(
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "webp" to "image/webp",
        "gif" to "image/gif",
    )

    val isEmpty: Boolean get() = RoniFacts.photos.isEmpty()

    /** הרשימה שהמודל רואה: אינדקס וכיתוב, בלי הקבצים עצמם. */
    fun index(): String =
        RoniFacts.photos.mapIndexed { i, p -> "$i. ${p.caption}" }.joinToString("\n")

    fun isValidIndex(index: Int): Boolean = index in RoniFacts.photos.indices

    /** אותה רשימה, בלי מה שכבר נשלח. ריק = הכל נשלח. */
    fun indexExcluding(exclude: Set<Int>): String =
        RoniFacts.photos.mapIndexed { i, p -> i to p }
            .filter { it.first !in exclude }
            .joinToString("\n") { "${it.first}. ${it.second.caption}" }

    /** טוען את התמונות שנבחרו. אינדקס לא תקין או קובץ חסר — מדולג. */
    fun load(indexes: List<Int>): List<Image> =
        indexes.distinct()
            .filter { it in RoniFacts.photos.indices }
            .mapNotNull { load(it) }

    private fun load(index: Int): Image? = cache.getOrPut(index) {
        val entry = RoniFacts.photos[index]
        val mime = mimeByExtension[entry.file.substringAfterLast('.', "").lowercase()]
        if (mime == null) {
            log.warn("פורמט לא נתמך, מדולג: {}", entry.file)
            return null
        }
        val file = File(dir, entry.file)
        if (!file.isFile) {
            log.warn("קובץ תמונה חסר, מדולג: {}", file.path)
            return null
        }
        val shrunk = shrink(file)

        Image(
            id = index.toString(),
            mime = if (shrunk != null) "image/jpeg" else mime,
            data = Base64.getEncoder().encodeToString(shrunk ?: file.readBytes()),
            alt = entry.caption,
        )
    }

    /** מקטין ומקודד ל-JPEG. null אם משהו נכשל — אז נשלח המקור. */
    private fun shrink(file: File): ByteArray? = runCatching {
        val source = ImageIO.read(file) ?: return null

        val scale = MAX_EDGE.toDouble() / maxOf(source.width, source.height)
        val target = if (scale >= 1.0) source else {
            val w = (source.width * scale).toInt().coerceAtLeast(1)
            val h = (source.height * scale).toInt().coerceAtLeast(1)
            BufferedImage(w, h, BufferedImage.TYPE_INT_RGB).also { out ->
                out.createGraphics().apply {
                    setRenderingHint(
                        java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR,
                    )
                    drawImage(source.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH), 0, 0, null)
                    dispose()
                }
            }
        }

        // JPEG לא יודע שקיפות. המרה ל-RGB מונעת רקע שחור בפינות
        // של PNG עם ערוץ אלפא.
        val rgb = if (target.type == BufferedImage.TYPE_INT_RGB) target else {
            BufferedImage(target.width, target.height, BufferedImage.TYPE_INT_RGB).also {
                it.createGraphics().apply {
                    color = java.awt.Color.WHITE
                    fillRect(0, 0, target.width, target.height)
                    drawImage(target, 0, 0, null)
                    dispose()
                }
            }
        }

        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val out = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(out).use { stream ->
            writer.output = stream
            writer.write(
                null,
                javax.imageio.IIOImage(rgb, null, null),
                writer.defaultWriteParam.apply {
                    compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = JPEG_QUALITY
                },
            )
        }
        writer.dispose()
        out.toByteArray()
    }.getOrNull()
}
