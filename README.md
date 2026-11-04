# Astraya Glyph

An Always-on Glyph Toy for the Nothing Phone (4a) Pro: the Astraya emblem,
the time and the weather on the 13×13 Glyph Matrix, a new slide every minute.

## Install on the phone

1. Download the latest build on the phone:
   `https://github.com/Saransh-mittal/astraya-glyph/releases/latest/download/astraya-glyph.apk`
2. Open it. The first time, Android asks to allow your browser to
   "Install unknown apps": allow it, go back and tap **Install**.
3. Open **Astraya Glyph** and tap **Use my location for weather** (approximate is enough).
4. Settings → **Glyph Interface** → **Flip to Glyph** → **Always-on Glyph Toy** → **Astraya**.
5. Lay the phone face down.

A newer build installs over the old one the same way (steps 1–2).

## How it works

- The system wakes the toy about once a minute (`EVENT_AOD`); the minute of the
  day picks the slide: emblem → time → weather. Weather is skipped until there
  is data less than 3 hours old.
- Weather comes from [Open-Meteo](https://open-meteo.com) (free, no key), using
  the location saved when you open the app, rounded to about 1 km. The toy never
  reads location in the background.
- Art lives as text in `Slides.kt`. `python3 -I tools/preview.py` draws every
  slide to `preview/slides.png` without building the app.

## Building

GitHub Actions builds every push to `main` and publishes it as a release. The
Glyph Matrix SDK is downloaded from
[Nothing's repository](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit)
during the build and checked against a pinned SHA-256: its licence does not allow
redistributing it, so it is not in this repository. Its licence also forbids
commercial use. This is a personal toy.

Signing uses one fixed key (secrets `ASTRAYA_KEYSTORE_B64`,
`ASTRAYA_KEYSTORE_PASSWORD`) so updates install over each other. The key lives
only on the owner's Mac (`astraya.jks`, git-ignored) and in those secrets.
