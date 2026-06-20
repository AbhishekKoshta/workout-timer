# Workout Timer (Android)

An interval / HIIT workout timer. Fully adjustable:

- **Total workout** length (minutes)
- **Work** seconds per round
- **Rest** seconds per round
- **Beep countdown** window — beeps the last N seconds of every phase (e.g. 5‑4‑3‑2‑1)
- **Get ready** lead-in

Each round = one *work* block. Rounds repeat until the total workout time is filled.
Sound is generated on-device (no audio files): short beeps for the countdown, a
distinct tone when WORK starts, another when REST starts, and a finish chime.
Optional vibration on phase change. Screen stays on while the timer runs.

Your requested preset is the default: **20 min total, 30s work, 10s rest, beep last 5s.**

---

## Getting the `.apk`

This project couldn't be compiled in the assistant's sandbox (it has no network
access to Google's Android SDK servers). Pick whichever path is easiest for you —
both produce the same app.

### Option A — Build the APK on GitHub (zero setup on your Mac) ✅ recommended

1. Create a new GitHub repo (e.g. `workout-timer`).
2. Upload the contents of this `WorkoutTimerApp/` folder to it (including the
   `.github/` folder). Easiest: on the repo page choose **Add file → Upload files**,
   drag everything in, and commit.
3. Go to the repo's **Actions** tab. The **Build APK** workflow runs automatically.
4. When it finishes (~2–3 min), open the run and download the
   **`WorkoutTimer-debug-apk`** artifact. Inside is `app-debug.apk`.
5. Copy the `.apk` to your phone and install it (enable "install unknown apps"
   for your file manager / browser when prompted).

### Option B — Build locally in Android Studio

1. Install [Android Studio](https://developer.android.com/studio).
2. **Open** this `WorkoutTimerApp/` folder. Let it sync (it downloads Gradle + SDK).
3. **Build → Build Bundle(s) / APK(s) → Build APK(s)**, or just press **Run** with a
   phone/emulator connected. The APK lands in `app/build/outputs/apk/debug/`.

---

## Use it right now, no build needed

`web/index.html` is the exact same timer as a single web page. Open it on your
phone's browser and tap **START** (the first tap unlocks sound). Use the browser's
**Add to Home Screen** to get a full-screen, app-like icon. Great for using today
while the APK builds.

---

## Project layout

```
WorkoutTimerApp/
├─ app/                         Android app module (Kotlin)
│  └─ src/main/
│     ├─ java/.../MainActivity.kt   timer engine + beeps
│     └─ res/                       layouts, colors, icon
├─ .github/workflows/build-apk.yml  CI that compiles the APK
├─ web/index.html               instant-use web version
├─ build.gradle.kts / settings.gradle.kts / gradle.properties
└─ README.md
```

App id: `com.abhishek.workouttimer` · minSdk 24 (Android 7+) · targetSdk 34.
