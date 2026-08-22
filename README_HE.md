# Alpine Trip 2026 – Android

אפליקציית Android מקומית לטיול האלפים 16–22/09/2026.

## מה כלול

- ממשק הטיול המלא מתוך גרסת ה-HTML האחרונה, בתוך WebView מקומי.
- מסמכי Travel Confirmations, חירום ומסמך הטיול מוטמעים כחלק מה-APK ונפתחים דרך Android.
- Alpine Route Watch מקומי: בדיקה אוטומטית ב-07:00, 11:00 ו-15:00 לפי Europe/Zurich בתאריכים 17–22/09/2026.
- מזג אוויר: Open-Meteo, ללא API key.
- כבישי שווייץ: Traffic Situations API של opentransportdata.swiss, דורש Token חינמי שמוזן באפליקציה.
- התראה (Notification) בכל בדיקה: ירוק / צהוב / אדום.
- אחרי אתחול הטלפון האפליקציה מתזמנת מחדש את הבדיקות.

## בניית APK ללא Android Studio

הפרויקט כולל GitHub Actions workflow בשם `Build Alpine Trip APK`.

1. פתח חשבון GitHub אם אין.
2. צור Repository חדש.
3. העלה את *תוכן* התיקייה הזו ל-root של ה-Repository (לא תיקיית ZIP אחת).
4. פתח Actions -> Build Alpine Trip APK.
5. לחץ Run workflow.
6. בסיום פתח את ה-run והורד Artifact בשם AlpineTrip-Android-APK.
7. חלץ את הקובץ `app-debug.apk` והעבר לטלפון.

זה APK חתום ב-debug key ומתאים להתקנה מקומית. אין צורך ב-Google Play.

## הגדרה ראשונה בטלפון

1. התקן את `app-debug.apk`. Android עשוי לבקש לאפשר התקנה ממקור זה.
2. פתח Alpine Trip 2026 ואשר Notifications.
3. במסך הראשי לחץ `הגדרות מעקב`.
4. לחץ `אישור תזמון` ואפשר `Alarms & reminders` / `התראות ותזכורות` כדי לקבל בדיקה בשעות המדויקות.
5. צור Token עבור Traffic Situations API באתר opentransportdata.swiss API Manager והדבק אותו בהגדרות המעקב.
6. לחץ `שמור`.
7. לחץ `בדיקה מלאה עכשיו` כדי לבצע בדיקת ניסיון.

## הערות חשובות

- הבדיקות האוטומטיות דורשות חיבור נתונים פעיל בזמן הבדיקה.
- אם הרשאת Exact Alarm לא ניתנה, Android עשוי לבצע את הבדיקה באיחור.
- נתוני הכבישים השווייצריים הם DATEX II רשמי. בגרסה זו האפליקציה מסננת הודעות פעילות לפי שמות המעברים/היישובים במסלול של אותו יום.
- קטעי הכביש באיטליה אינם נבדקים דרך ה-API השווייצרי; בזמן ניווט Google Maps ממשיך לספק מידע תנועה חי.
