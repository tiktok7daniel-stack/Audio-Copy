package com.cloneLocal.app.ui

/** All UI strings, English + Arabic. Labels are written in natural case; the UI uppercases English ones. */
class Strings(val ar: Boolean) {
    private fun s(en: String, a: String) = if (ar) a else en

    val appTag get() = s("SYSTEM · VOICE CLONING ENGINE", "النظام · محرك استنساخ الصوت")
    val heroTop get() = s("CLONE ANY", "استنسخ أي")
    val heroGlow get() = s("VOICE.", "صوت.")
    val heroSub get() = s(
        "Drop in a short clip of a voice and type what it should say. Everything runs on your phone — no internet, no upload.",
        "ضع مقطعًا قصيرًا لأي صوت واكتب ما تريد أن يقوله. كل شيء يعمل على هاتفك — بدون إنترنت وبدون رفع.",
    )

    val navHome get() = s("Home", "الرئيسية")
    val navLibrary get() = s("Library", "المكتبة")
    val navSettings get() = s("Settings", "الإعدادات")

    val engineOffline get() = s("ENGINE OFFLINE", "المحرك غير جاهز")
    val engineOfflineMsg get() = s("Download the voice model once to start cloning.", "نزّل نموذج الصوت مرة واحدة لتبدأ الاستنساخ.")
    val openSettings get() = s("OPEN SETTINGS", "افتح الإعدادات")

    val refLabel get() = s("REFERENCE VOICE", "الصوت المرجعي")
    val refHint get() = s("Pick an audio or video file", "اختر ملف صوت أو فيديو")
    val refTip get() = s("The first 15 seconds are used. Clean speech works best.", "تُستخدم أول 15 ثانية. الكلام النظيف يعطي أفضل نتيجة.")
    val refTextLabel get() = s("WORDS SPOKEN IN THE CLIP", "الكلام المقال في المقطع")
    val refTextHint get() = s("Type exactly what the clip says", "اكتب ما يقوله المقطع بالضبط")
    val textLabel get() = s("TEXT TO SPEAK", "النص المراد نطقه")
    val textHint get() = s("Chinese or English text", "نص بالإنجليزية أو الصينية")
    val generate get() = s("GENERATE VOICE", "ولّد الصوت")
    val generating get() = s("GENERATING…", "جارٍ التوليد…")
    val cancel get() = s("CANCEL", "إلغاء")
    val statusSteps get() = if (ar) listOf("جارٍ تحميل المحرك…", "جارٍ قراءة الصوت المرجعي…", "جارٍ التوليد — النصوص الطويلة تأخذ وقتًا…")
    else listOf("Loading the voice engine…", "Reading the reference voice…", "Synthesizing — long text takes longer…")

    val complete get() = s("COMPLETE", "اكتمل")
    val extracting get() = s("GENERATING", "قيد التوليد")
    val failed get() = s("FAILED", "فشل")
    val play get() = s("PLAY", "تشغيل")
    val pause get() = s("PAUSE", "إيقاف")
    val saveMusic get() = s("EXPORT TO MUSIC", "تصدير إلى الموسيقى")
    val saved get() = s("Saved to Music/VoxGate.", "تم الحفظ في Music/VoxGate.")

    val whatItDoes get() = s("WHAT IT DOES", "ماذا يفعل")
    val f1t get() = s("Fully offline", "بدون إنترنت")
    val f1d get() = s("The model runs on your phone. Your voices never leave it.", "النموذج يعمل على هاتفك. أصواتك لا تغادره أبدًا.")
    val f2t get() = s("Any audio or video", "أي صوت أو فيديو")
    val f2d get() = s("Pull a voice straight out of a video file or recording.", "استخرج الصوت مباشرة من فيديو أو تسجيل.")
    val f3t get() = s("One tap, one WAV", "ضغطة واحدة، ملف واحد")
    val f3d get() = s("Every result is saved to your library and ready to export.", "كل نتيجة تُحفظ في مكتبتك وجاهزة للتصدير.")
    val legal get() = s(
        "Only clone voices you own or have permission to use. Cloning someone without consent can be illegal.",
        "استنسخ فقط أصواتًا تملكها أو لديك إذن باستخدامها. استنساخ صوت شخص بدون موافقته قد يكون مخالفًا للقانون.",
    )

    val errNoRef get() = s("Pick a reference file first.", "اختر ملف الصوت المرجعي أولًا.")
    val errNoRefText get() = s("Type the words spoken in the reference clip.", "اكتب الكلام المقال في المقطع المرجعي.")
    val errNoText get() = s("Type the text to speak.", "اكتب النص المراد نطقه.")
    val errNoModel get() = s("The voice model is not downloaded yet.", "نموذج الصوت لم يُنزَّل بعد.")
    val errRead get() = s("Could not read that file", "تعذّرت قراءة الملف")
    val errGen get() = s("Generation failed", "فشل التوليد")
    val errTip get() = s("Try a shorter clip or a shorter text, then retry.", "جرّب مقطعًا أقصر أو نصًا أقصر ثم أعد المحاولة.")

    val libTitle get() = s("YOUR LIBRARY", "مكتبتك")
    val libEmpty get() = s("Nothing generated yet.", "لا يوجد شيء بعد.")
    val libEmptyBtn get() = s("GO TO HOME", "اذهب للرئيسية")
    val items get() = s("clips", "مقاطع")

    val setTitle get() = s("SETTINGS", "الإعدادات")
    val language get() = s("LANGUAGE", "اللغة")
    val quality get() = s("QUALITY", "الجودة")
    val qualityNote get() = s("Higher quality uses more steps and takes longer on a phone.", "الجودة الأعلى تستخدم خطوات أكثر وتأخذ وقتًا أطول على الهاتف.")
    val qFast get() = s("Fast", "سريع")
    val qBalanced get() = s("Balanced", "متوازن")
    val qBest get() = s("Best", "أفضل")
    val model get() = s("VOICE MODEL", "نموذج الصوت")
    val modelReady get() = s("READY", "جاهز")
    val modelMissing get() = s("NOT INSTALLED", "غير مثبت")
    val downloadModel get() = s("DOWNLOAD MODEL", "تنزيل النموذج")
    val redownload get() = s("DOWNLOAD AGAIN", "نزّل مجددًا")
    val downloading get() = s("DOWNLOADING", "جارٍ التنزيل")
    val extractingModel get() = s("Extracting model…", "جارٍ فك ضغط النموذج…")
    val modelUrl get() = s("MODEL URL", "رابط النموذج")
    val modelNote get() = s("Chinese + English only. A few hundred MB, downloaded once.", "الصينية والإنجليزية فقط. بضع مئات الميغابايت، تُنزَّل مرة واحدة.")
    val clearCache get() = s("CLEAR CACHE", "مسح الذاكرة المؤقتة")
    val deleteModel get() = s("DELETE MODEL", "حذف النموذج")
    val about get() = s("VoxGate 1.1 — offline voice cloning, built on open-source models.", "VoxGate 1.1 — استنساخ صوت بدون إنترنت، مبني على نماذج مفتوحة المصدر.")
    val dlFailed get() = s("Download failed", "فشل التنزيل")
    val noInternet get() = s("No internet connection or the site is unreachable.", "لا يوجد إنترنت أو الموقع غير متاح.")
}
