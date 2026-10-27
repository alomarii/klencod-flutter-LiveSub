خطوات التشغيل:
1) نزّل نموذج Vosk للغة الكلام (مثلاً vosk-model-small-en-us-0.15) من https://alphacephei.com/vosk/models
2) فك الضغط وانسخ محتوياته داخل: app/src/main/assets/model/   (يجب أن يظهر مثلاً app/src/main/assets/model/am, conf, graph ...)
3) افتح المجلد في Android Studio ← Sync ← Run على هاتف أندرويد 10 أو أحدث.
4) لتغيير اللغات عدّل sourceLang / targetLang في CaptureService.kt (ويجب أن يطابق نموذج Vosk لغة المصدر).
